from __future__ import annotations

from xcore_metrics_gateway.runtime import GatewayRuntime
from xcore_metrics_gateway.settings import Settings
from xcore_metrics_gateway.store import SeriesStore


class _StubRedisClient:
    async def close(self) -> None:
        return None


def test_health_snapshot_reports_starting_before_runtime_is_ready() -> None:
    runtime = GatewayRuntime(Settings(), SeriesStore(), _StubRedisClient())

    health = runtime.health_snapshot(tracked_servers=0)

    assert health["status"] == "starting"
    assert health["reasons"] == ["discovery_pending", "poll_pending"]


def test_health_snapshot_reports_ok_when_runtime_is_healthy() -> None:
    runtime = GatewayRuntime(Settings(), SeriesStore(), _StubRedisClient())
    runtime.redis_up = True
    runtime._discovery_ready = True
    runtime._poll_ready = True
    runtime._last_discovery_ok = True
    runtime._last_poll_ok = True
    runtime._self_metrics.set_redis_up(True)
    runtime._self_metrics.set_discovered_targets(2)
    runtime._self_metrics.set_last_discovery_duration_seconds(0.2)

    health = runtime.health_snapshot(tracked_servers=2)

    assert health["status"] == "ready"
    assert health["redis_up"] is True
    assert health["discovered_targets"] == 2
    assert health["tracked_servers"] == 2
    assert health["stale_nodes"] == 0
    assert health["reasons"] == []
    assert runtime.self_metrics_snapshot.last_discovery_duration_seconds == 0.2


def test_health_snapshot_reports_degraded_with_failure_reasons() -> None:
    runtime = GatewayRuntime(Settings(), SeriesStore(), _StubRedisClient())
    runtime._discovery_ready = True
    runtime._poll_ready = True
    runtime._last_discovery_ok = False
    runtime._last_poll_ok = False
    runtime.redis_up = False
    runtime._self_metrics.record_discovery_failure()
    runtime._self_metrics.record_poll_failure()

    health = runtime.health_snapshot(tracked_servers=0)

    assert health["status"] == "degraded"
    assert health["redis_up"] is False
    assert health["reasons"] == [
        "redis_unavailable",
        "discovery_failures",
        "poll_failures",
    ]
    assert health["self_metrics"]["discovery_failures_total"] == 1
    assert health["self_metrics"]["poll_failures_total"] == 1


def test_health_snapshot_reports_degraded_when_stale_nodes_exist() -> None:
    store = SeriesStore()
    store.mark_stale("mini-hexed", snapshot_age_seconds=91.0)
    runtime = GatewayRuntime(Settings(), store, _StubRedisClient())
    runtime._discovery_ready = True
    runtime._poll_ready = True
    runtime._last_discovery_ok = True
    runtime._last_poll_ok = True
    runtime.redis_up = True
    runtime._self_metrics.set_redis_up(True)
    runtime._self_metrics.set_discovered_targets(1)

    health = runtime.health_snapshot(tracked_servers=1)

    assert health["status"] == "degraded"
    assert health["stale_nodes"] == 1
    assert health["reasons"] == ["stale_snapshots"]
    assert health["self_metrics"]["stale_nodes"] == 1
