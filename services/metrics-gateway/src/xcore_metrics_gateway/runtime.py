from __future__ import annotations

import asyncio

from .discovery import SnapshotDiscovery
from .poller import SnapshotPoller
from .redis_client import RedisMetricsClient
from .settings import Settings
from .self_metrics import GatewaySelfMetrics, GatewaySelfMetricsSnapshot
from .store import SeriesStore


class GatewayRuntime:
    def __init__(
        self,
        settings: Settings,
        store: SeriesStore,
        redis_client: RedisMetricsClient,
    ) -> None:
        self._settings = settings
        self._redis_client = redis_client
        self._self_metrics = GatewaySelfMetrics()
        self._discovery = SnapshotDiscovery(settings, redis_client)
        self._poller = SnapshotPoller(
            settings,
            redis_client,
            self._discovery,
            store,
            self._self_metrics,
        )
        self._tasks: list[asyncio.Task[None]] = []
        self.redis_up = False

    def health_snapshot(self, *, tracked_servers: int) -> dict[str, object]:
        self_metrics = self.self_metrics_snapshot
        status = "ok"
        reasons: list[str] = []
        if not self.redis_up:
            status = "degraded"
            reasons.append("redis_unavailable")
        if self_metrics.discovery_failures_total > 0:
            status = "degraded"
            reasons.append("discovery_failures")
        if self_metrics.poll_failures_total > 0:
            status = "degraded"
            reasons.append("poll_failures")

        return {
            "status": status,
            "redis_up": self.redis_up,
            "discovered_targets": self_metrics.discovered_targets,
            "tracked_servers": tracked_servers,
            "reasons": reasons,
            "self_metrics": {
                "snapshots_total": self_metrics.snapshots_total,
                "discovery_failures_total": self_metrics.discovery_failures_total,
                "poll_failures_total": self_metrics.poll_failures_total,
                "decode_failures_total": self_metrics.decode_failures_total,
                "validation_failures_total": self_metrics.validation_failures_total,
            },
        }

    @property
    def discovered_targets(self) -> int:
        return len(self._discovery.current_keys())

    @property
    def self_metrics_snapshot(self) -> GatewaySelfMetricsSnapshot:
        return self._self_metrics.snapshot()

    async def start(self) -> None:
        if self._tasks:
            return
        self._tasks = [
            asyncio.create_task(self._discovery_loop(), name="snapshot-discovery"),
            asyncio.create_task(self._poll_loop(), name="snapshot-poller"),
        ]

    async def stop(self) -> None:
        for task in self._tasks:
            task.cancel()
        if self._tasks:
            await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()
        await self._redis_client.close()

    async def _discovery_loop(self) -> None:
        while True:
            self.redis_up = await self._discovery.discover_once()
            if not self.redis_up:
                self._self_metrics.record_discovery_failure()
            self._self_metrics.set_redis_up(self.redis_up)
            self._self_metrics.set_discovered_targets(self.discovered_targets)
            await asyncio.sleep(self._settings.redis_discovery_interval_ms / 1000)

    async def _poll_loop(self) -> None:
        while True:
            poll_ok = await self._poller.poll_once()
            if not poll_ok:
                self._self_metrics.record_poll_failure()
            self.redis_up = self.redis_up and poll_ok if self._tasks else poll_ok
            self._self_metrics.set_redis_up(self.redis_up)
            self._self_metrics.set_discovered_targets(self.discovered_targets)
            await asyncio.sleep(self._settings.redis_poll_interval_ms / 1000)
