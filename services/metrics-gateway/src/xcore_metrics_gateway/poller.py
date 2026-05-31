from __future__ import annotations

import time

from .guard import CardinalityGuard
from .discovery import SnapshotDiscovery
from .redis_client import RedisMetricsClient
from .settings import Settings
from .self_metrics import GatewaySelfMetrics
from .snapshot import SnapshotDecodeError, decode_snapshot
from .store import SeriesStore


class SnapshotPoller:
    def __init__(
        self,
        settings: Settings,
        redis_client: RedisMetricsClient,
        discovery: SnapshotDiscovery,
        store: SeriesStore,
        self_metrics: GatewaySelfMetrics,
    ) -> None:
        self._settings = settings
        self._redis_client = redis_client
        self._discovery = discovery
        self._store = store
        self._self_metrics = self_metrics
        self._guard = CardinalityGuard(settings)

    async def poll_once(self) -> bool:
        keys = self._discovery.current_keys()
        if not keys:
            self._self_metrics.set_last_poll_duration_seconds(0.0)
            return True

        started = time.perf_counter()
        now_ms = int(time.time() * 1000)
        try:
            for start in range(0, len(keys), self._settings.redis_mget_batch_size):
                batch = keys[start : start + self._settings.redis_mget_batch_size]
                values = await self._redis_client.mget_bytes(batch)
                for key, compressed in zip(batch, values, strict=True):
                    server = self._redis_client.server_from_key(key)
                    if compressed is None:
                        self._store.mark_missing(server)
                        continue

                    try:
                        decoded = decode_snapshot(
                            compressed,
                            max_compressed_snapshot_bytes=self._settings.max_compressed_snapshot_bytes,
                            max_uncompressed_snapshot_bytes=self._settings.max_uncompressed_snapshot_bytes,
                        )
                    except SnapshotDecodeError:
                        self._self_metrics.record_decode_failure()
                        continue

                    guard_result = self._guard.apply(
                        decoded.snapshot,
                        tracked_servers=self._store.tracked_servers(),
                        tracked_total_series=self._store.tracked_total_series(),
                    )
                    for reason, count in guard_result.dropped_reasons:
                        self._self_metrics.record_drop(reason, count)
                    if guard_result.snapshot is None:
                        self._self_metrics.record_validation_failure()
                        continue

                    snapshot_age_seconds = max(
                        0.0,
                        (now_ms - guard_result.snapshot.createdAtUnixMs) / 1000,
                    )
                    self._store.replace_server_snapshot(
                        server,
                        guard_result.snapshot,
                        snapshot_age_seconds=snapshot_age_seconds,
                    )
                    self._self_metrics.record_snapshot_applied()
        except Exception:
            self._self_metrics.set_last_poll_duration_seconds(
                time.perf_counter() - started
            )
            return False

        self._self_metrics.set_last_poll_duration_seconds(time.perf_counter() - started)
        return True
