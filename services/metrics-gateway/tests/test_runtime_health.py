from __future__ import annotations

from xcore_metrics_gateway.runtime import GatewayRuntime
from xcore_metrics_gateway.settings import Settings
from xcore_metrics_gateway.store import SeriesStore


class _StubRedisClient:
    async def close(self) -> None:
        return None


def test_health_snapshot_reports_ok_when_runtime_is_healthy() -> None:
    runtime = GatewayRuntime(Settings(), SeriesStore(), _StubRedisClient())
    runtime.redis_up = True
    runtime._self_metrics.set_redis_up(True)
    runtime._self_metrics.set_discovered_targets(2)

    health = runtime.health_snapshot(tracked_servers=2)

    assert health["status"] == "ok"
    assert health["redis_up"] is True
    assert health["discovered_targets"] == 2
    assert health["tracked_servers"] == 2
    assert health["reasons"] == []


def test_health_snapshot_reports_degraded_with_failure_reasons() -> None:
    runtime = GatewayRuntime(Settings(), SeriesStore(), _StubRedisClient())
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
