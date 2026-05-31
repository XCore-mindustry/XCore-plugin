from __future__ import annotations

import gzip
import json
import logging
import time
from pathlib import Path

import fakeredis.aioredis
import pytest

from xcore_metrics_gateway.discovery import SnapshotDiscovery
from xcore_metrics_gateway.poller import SnapshotPoller
from xcore_metrics_gateway.prometheus import render_metrics
from xcore_metrics_gateway.redis_client import RedisMetricsClient
from xcore_metrics_gateway.settings import Settings
from xcore_metrics_gateway.self_metrics import GatewaySelfMetrics
from xcore_metrics_gateway.store import SeriesStore


FIXTURES_DIR = Path(__file__).parent / "fixtures"


def _encode(payload: dict[str, object]) -> bytes:
    return gzip.compress(json.dumps(payload).encode("utf-8"))


def _settings(**overrides: object) -> Settings:
    values: dict[str, object] = {
        "redis_discovery_interval_ms": 30000,
        "redis_poll_interval_ms": 3000,
        "redis_scan_count": 100,
        "redis_mget_batch_size": 100,
        "redis_command_timeout_ms": 500,
        "max_servers": 200,
        "max_series_per_server": 5000,
        "max_total_series": 250000,
        "max_labels_per_metric": 8,
        "max_label_value_length": 80,
        "stale_snapshot_age_seconds": 45,
        "max_compressed_snapshot_bytes": 131072,
        "max_uncompressed_snapshot_bytes": 524288,
    }
    values.update(overrides)
    return Settings(**values)


def _load_fixture(name: str) -> dict[str, object]:
    return json.loads((FIXTURES_DIR / name).read_text(encoding="utf-8"))


def _fresh_created_at_unix_ms() -> int:
    return int(time.time() * 1000)


def _stale_created_at_unix_ms() -> int:
    return 1_000


@pytest.mark.asyncio
async def test_discovery_and_poller_populate_store_from_redis_snapshot() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=False)
    settings = _settings()
    client = RedisMetricsClient(settings, redis=redis)
    discovery = SnapshotDiscovery(settings, client)
    store = SeriesStore()
    self_metrics = GatewaySelfMetrics()
    poller = SnapshotPoller(settings, client, discovery, store, self_metrics)

    await redis.set(
        "xcore:metrics:snapshot:mini-hexed",
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "mini-hexed",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": _fresh_created_at_unix_ms(),
                "startTimeUnixMs": 0,
                "sequence": 2,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {},
                        "value": 12,
                    }
                ],
            }
        ),
        ex=60,
    )

    assert await discovery.discover_once() is True
    assert discovery.current_keys() == ("xcore:metrics:snapshot:mini-hexed",)
    assert await poller.poll_once() is True

    snapshots, node_states = store.render_snapshot()
    assert snapshots["mini-hexed"].server == "mini-hexed"
    assert node_states[0].server == "mini-hexed"
    assert node_states[0].up is True

    rendered = render_metrics(snapshots, node_states, self_metrics.snapshot())
    assert 'mindustry_players_online{server="mini-hexed"} 12' in rendered
    assert "xcore_metrics_gateway_snapshots_total 1" in rendered

    await client.close()


@pytest.mark.asyncio
async def test_discovery_and_poller_accept_plugin_shaped_snapshot_contract() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=False)
    settings = _settings(stale_snapshot_age_seconds=10_000_000)
    client = RedisMetricsClient(settings, redis=redis)
    discovery = SnapshotDiscovery(settings, client)
    store = SeriesStore()
    self_metrics = GatewaySelfMetrics()
    poller = SnapshotPoller(settings, client, discovery, store, self_metrics)
    payload = _load_fixture("plugin-snapshot-contract.json")
    payload["createdAtUnixMs"] = _fresh_created_at_unix_ms()

    await redis.set(
        "xcore:metrics:snapshot:mini-pvp",
        _encode(payload),
        ex=60,
    )

    assert await discovery.discover_once() is True
    assert await poller.poll_once() is True

    snapshots, node_states = store.render_snapshot()
    assert snapshots["mini-pvp"].server == "mini-pvp"
    assert snapshots["mini-pvp"].sequence == 0
    assert node_states[0].server == "mini-pvp"
    assert node_states[0].up is True
    assert node_states[0].stale is False

    rendered = render_metrics(snapshots, node_states, self_metrics.snapshot())
    assert 'mindustry_player_joins_total{server="mini-pvp"} 2' in rendered
    assert 'mindustry_players_online{server="mini-pvp"} 9' in rendered
    assert (
        'xcore_command_duration_seconds_bucket{le="0.1",server="mini-pvp"} 1'
        in rendered
    )
    assert (
        'xcore_command_duration_seconds_bucket{le="0.5",server="mini-pvp"} 2'
        in rendered
    )
    assert (
        'xcore_command_duration_seconds_bucket{le="1",server="mini-pvp"} 3'
        in rendered
    )
    assert (
        'xcore_command_duration_seconds_bucket{le="+Inf",server="mini-pvp"} 3'
        in rendered
    )
    assert 'xcore_command_duration_seconds_sum{server="mini-pvp"} 1.25' in rendered
    assert 'xcore_command_duration_seconds_count{server="mini-pvp"} 3' in rendered
    assert rendered.count('server="mini-pvp"') >= 8

    await client.close()


@pytest.mark.asyncio
async def test_poller_marks_missing_when_discovered_key_expires() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=False)
    settings = _settings()
    client = RedisMetricsClient(settings, redis=redis)
    discovery = SnapshotDiscovery(settings, client)
    store = SeriesStore()
    self_metrics = GatewaySelfMetrics()
    poller = SnapshotPoller(settings, client, discovery, store, self_metrics)

    key = "xcore:metrics:snapshot:mini-hexed"
    await redis.set(
        key,
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "mini-hexed",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": _fresh_created_at_unix_ms(),
                "startTimeUnixMs": 0,
                "sequence": 2,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {},
                        "value": 12,
                    }
                ],
            }
        ),
        ex=60,
    )

    await discovery.discover_once()
    await poller.poll_once()
    await redis.delete(key)

    assert await poller.poll_once() is True
    _, node_states = store.render_snapshot()
    assert node_states[0].server == "mini-hexed"
    assert node_states[0].up is False

    await client.close()


@pytest.mark.asyncio
async def test_invalid_snapshot_does_not_replace_previous_valid_snapshot() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=False)
    settings = _settings()
    client = RedisMetricsClient(settings, redis=redis)
    discovery = SnapshotDiscovery(settings, client)
    store = SeriesStore()
    self_metrics = GatewaySelfMetrics()
    poller = SnapshotPoller(settings, client, discovery, store, self_metrics)

    key = "xcore:metrics:snapshot:mini-hexed"
    await redis.set(
        key,
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "mini-hexed",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": _fresh_created_at_unix_ms(),
                "startTimeUnixMs": 0,
                "sequence": 2,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {},
                        "value": 12,
                    }
                ],
            }
        ),
        ex=60,
    )

    await discovery.discover_once()
    await poller.poll_once()

    await redis.set(
        key,
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "mini-hexed",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": 2_000,
                "startTimeUnixMs": 0,
                "sequence": 3,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {"server": "bad"},
                        "value": 99,
                    }
                ],
            }
        ),
        ex=60,
    )

    assert await poller.poll_once() is True
    snapshots, node_states = store.render_snapshot()
    assert snapshots["mini-hexed"].sequence == 2
    assert node_states[0].up is True
    assert self_metrics.snapshot().validation_failures_total == 1

    await client.close()


@pytest.mark.asyncio
async def test_stale_snapshot_is_removed_from_render_view_but_keeps_node_state() -> (
    None
):
    redis = fakeredis.aioredis.FakeRedis(decode_responses=False)
    settings = _settings()
    client = RedisMetricsClient(settings, redis=redis)
    discovery = SnapshotDiscovery(settings, client)
    store = SeriesStore()
    self_metrics = GatewaySelfMetrics()
    poller = SnapshotPoller(settings, client, discovery, store, self_metrics)

    await redis.set(
        "xcore:metrics:snapshot:mini-hexed",
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "mini-hexed",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": _stale_created_at_unix_ms(),
                "startTimeUnixMs": 0,
                "sequence": 2,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {},
                        "value": 12,
                    }
                ],
            }
        ),
        ex=60,
    )

    await discovery.discover_once()
    assert await poller.poll_once() is True

    snapshots, node_states = store.render_snapshot()
    assert snapshots == {}
    assert node_states[0].server == "mini-hexed"
    assert node_states[0].up is False
    assert node_states[0].stale is True
    assert node_states[0].snapshot_age_seconds is not None

    await client.close()


@pytest.mark.asyncio
async def test_snapshot_server_mismatch_does_not_replace_valid_snapshot() -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=False)
    settings = _settings()
    client = RedisMetricsClient(settings, redis=redis)
    discovery = SnapshotDiscovery(settings, client)
    store = SeriesStore()
    self_metrics = GatewaySelfMetrics()
    poller = SnapshotPoller(settings, client, discovery, store, self_metrics)

    key = "xcore:metrics:snapshot:mini-hexed"
    await redis.set(
        key,
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "mini-hexed",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": _fresh_created_at_unix_ms(),
                "startTimeUnixMs": 0,
                "sequence": 2,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {},
                        "value": 12,
                    }
                ],
            }
        ),
        ex=60,
    )

    await discovery.discover_once()
    await poller.poll_once()

    await redis.set(
        key,
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "wrong-server",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": 2_000,
                "startTimeUnixMs": 0,
                "sequence": 3,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {},
                        "value": 99,
                    }
                ],
            }
        ),
        ex=60,
    )

    assert await poller.poll_once() is True
    snapshots, node_states = store.render_snapshot()
    assert snapshots["mini-hexed"].sequence == 2
    assert node_states[0].up is True
    assert self_metrics.snapshot().validation_failures_total == 1

    await client.close()


@pytest.mark.asyncio
async def test_poller_logs_bounded_warning_summary_for_repeated_same_issue(
    caplog: pytest.LogCaptureFixture,
) -> None:
    redis = fakeredis.aioredis.FakeRedis(decode_responses=False)
    settings = _settings()
    client = RedisMetricsClient(settings, redis=redis)
    discovery = SnapshotDiscovery(settings, client)
    store = SeriesStore()
    self_metrics = GatewaySelfMetrics()
    poller = SnapshotPoller(settings, client, discovery, store, self_metrics)

    await redis.set(
        "xcore:metrics:snapshot:mini-hexed",
        _encode(
            {
                "schemaVersion": "metrics.snapshot.v1",
                "server": "mini-hexed",
                "nodeId": "mini-hexed-01",
                "producer": "xcore-plugin",
                "createdAtUnixMs": _fresh_created_at_unix_ms(),
                "startTimeUnixMs": 0,
                "sequence": 2,
                "intervalMs": 15000,
                "samples": [
                    {
                        "name": "mindustry_players_online",
                        "type": "gauge",
                        "labels": {"server": "bad"},
                        "value": 12,
                    }
                ],
            }
        ),
        ex=60,
    )

    await discovery.discover_once()
    with caplog.at_level(logging.WARNING):
        assert await poller.poll_once() is True
        assert await poller.poll_once() is True

    poll_summary_logs = [
        record.message
        for record in caplog.records
        if record.message.startswith("Poll summary:")
    ]
    assert len(poll_summary_logs) == 1
    assert "validation_failures=1" in poll_summary_logs[0]

    await client.close()
