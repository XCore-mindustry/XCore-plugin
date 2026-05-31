from __future__ import annotations

import asyncio
import logging
import time

from .discovery import SnapshotDiscovery
from .poller import SnapshotPoller
from .redis_client import RedisMetricsClient
from .settings import Settings
from .self_metrics import GatewaySelfMetrics, GatewaySelfMetricsSnapshot
from .store import SeriesStore


LOGGER = logging.getLogger(__name__)


class GatewayRuntime:
    def __init__(
        self,
        settings: Settings,
        store: SeriesStore,
        redis_client: RedisMetricsClient,
    ) -> None:
        self._settings = settings
        self._store = store
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
        self._discovery_ready = False
        self._poll_ready = False
        self._last_discovery_ok: bool | None = None
        self._last_poll_ok: bool | None = None
        self._last_logged_discovery_state: tuple[bool, int] | None = None

    def health_snapshot(self, *, tracked_servers: int) -> dict[str, object]:
        stale_nodes = self._current_stale_nodes()
        self._self_metrics.set_stale_nodes(stale_nodes)
        self_metrics = self.self_metrics_snapshot
        reasons: list[str] = []
        if not self._discovery_ready:
            reasons.append("discovery_pending")
        if not self._poll_ready:
            reasons.append("poll_pending")
        if self._discovery_ready and self._poll_ready and not self.redis_up:
            reasons.append("redis_unavailable")
        if self._last_discovery_ok is False:
            reasons.append("discovery_failures")
        if self._last_poll_ok is False:
            reasons.append("poll_failures")
        if self_metrics.stale_nodes > 0:
            reasons.append("stale_snapshots")

        if not self._discovery_ready or not self._poll_ready:
            status = "starting"
        elif reasons:
            status = "degraded"
        else:
            status = "ready"

        return {
            "status": status,
            "redis_up": self.redis_up,
            "discovered_targets": self_metrics.discovered_targets,
            "tracked_servers": tracked_servers,
            "stale_nodes": self_metrics.stale_nodes,
            "reasons": reasons,
            "self_metrics": {
                "stale_nodes": self_metrics.stale_nodes,
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
            started = time.perf_counter()
            discovery_ok = await self._discovery.discover_once()
            self._last_discovery_ok = discovery_ok
            if discovery_ok:
                self._discovery_ready = True
            self._self_metrics.set_last_discovery_duration_seconds(
                time.perf_counter() - started
            )
            if not discovery_ok:
                self._self_metrics.record_discovery_failure()
            self._refresh_redis_up()
            self._self_metrics.set_redis_up(self.redis_up)
            self._self_metrics.set_discovered_targets(self.discovered_targets)
            self._self_metrics.set_stale_nodes(self._current_stale_nodes())
            self._log_discovery_state(
                discovery_ok,
                self.self_metrics_snapshot.last_discovery_duration_seconds,
            )
            await asyncio.sleep(self._settings.redis_discovery_interval_ms / 1000)

    async def _poll_loop(self) -> None:
        while True:
            poll_ok = await self._poller.poll_once()
            self._last_poll_ok = poll_ok
            if poll_ok:
                self._poll_ready = True
            if not poll_ok:
                self._self_metrics.record_poll_failure()
            self._refresh_redis_up()
            self._self_metrics.set_redis_up(self.redis_up)
            self._self_metrics.set_discovered_targets(self.discovered_targets)
            self._self_metrics.set_stale_nodes(self._current_stale_nodes())
            await asyncio.sleep(self._settings.redis_poll_interval_ms / 1000)

    def _current_stale_nodes(self) -> int:
        return sum(
            1 for node_state in self._store.render_snapshot()[1] if node_state.stale
        )

    def _refresh_redis_up(self) -> None:
        discovery_component = self._last_discovery_ok is not False
        poll_component = self._last_poll_ok is not False
        self.redis_up = discovery_component and poll_component

    def _log_discovery_state(self, discovery_ok: bool, duration_seconds: float) -> None:
        state = (discovery_ok, self.discovered_targets)
        if not discovery_ok:
            if self._last_logged_discovery_state != state:
                LOGGER.warning(
                    "Discovery failed: duration_seconds=%s",
                    format(duration_seconds, "g"),
                )
        elif (
            self._last_logged_discovery_state is None
            or self._last_logged_discovery_state[0] is False
        ):
            LOGGER.info(
                "Discovery ready: targets=%d duration_seconds=%s",
                self.discovered_targets,
                format(duration_seconds, "g"),
            )
        elif self._last_logged_discovery_state[1] != self.discovered_targets:
            LOGGER.info(
                "Discovery updated: targets=%d duration_seconds=%s",
                self.discovered_targets,
                format(duration_seconds, "g"),
            )
        self._last_logged_discovery_state = state
