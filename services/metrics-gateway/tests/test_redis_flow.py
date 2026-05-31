from __future__ import annotations

import gzip
import json

import fakeredis.aioredis
import pytest

from xcore_metrics_gateway.discovery import SnapshotDiscovery
from xcore_metrics_gateway.poller import SnapshotPoller
from xcore_metrics_gateway.prometheus import render_metrics
from xcore_metrics_gateway.redis_client import RedisMetricsClient
from xcore_metrics_gateway.settings import Settings
from xcore_metrics_gateway.self_metrics import GatewaySelfMetrics
from xcore_metrics_gateway.store import SeriesStore


def _encode(payload: dict[str, object]) -> bytes:
    return gzip.compress(json.dumps(payload).encode("utf-8"))


def _settings() -> Settings:
    return Settings(
        redis_discovery_interval_ms=30000,
        redis_poll_interval_ms=3000,
        redis_scan_count=100,
        redis_mget_batch_size=100,
        redis_command_timeout_ms=500,
        max_servers=200,
        max_series_per_server=5000,
        max_total_series=250000,
        max_labels_per_metric=8,
        max_label_value_length=80,
        max_compressed_snapshot_bytes=131072,
        max_uncompressed_snapshot_bytes=524288,
    )


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
                "createdAtUnixMs": 1_000,
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
                "createdAtUnixMs": 1_000,
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
                "createdAtUnixMs": 1_000,
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
    assert self_metrics.snapshot().decode_failures_total == 1

    await client.close()
