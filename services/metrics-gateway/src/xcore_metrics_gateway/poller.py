from __future__ import annotations

import logging
import time

from .guard import CardinalityGuard
from .discovery import SnapshotDiscovery
from .redis_client import RedisMetricsClient
from .settings import Settings
from .self_metrics import GatewaySelfMetrics
from .snapshot import SnapshotDecodeError, SnapshotValidationError, decode_snapshot
from .store import SeriesStore


LOGGER = logging.getLogger(__name__)


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
        self._last_logged_issue_signature: tuple[object, ...] | None = None

    async def poll_once(self) -> bool:
        keys = self._discovery.current_keys()
        if not keys:
            self._self_metrics.set_last_poll_duration_seconds(0.0)
            return True

        started = time.perf_counter()
        now_ms = int(time.time() * 1000)
        applied_count = 0
        missing_count = 0
        stale_count = 0
        decode_failures = 0
        validation_failures = 0
        dropped: dict[str, int] = {}
        try:
            for start in range(0, len(keys), self._settings.redis_mget_batch_size):
                batch = keys[start : start + self._settings.redis_mget_batch_size]
                values = await self._redis_client.mget_bytes(batch)
                for key, compressed in zip(batch, values, strict=True):
                    server = self._redis_client.server_from_key(key)
                    if compressed is None:
                        self._store.mark_missing(server)
                        missing_count += 1
                        continue

                    try:
                        decoded = decode_snapshot(
                            compressed,
                            max_compressed_snapshot_bytes=self._settings.max_compressed_snapshot_bytes,
                            max_uncompressed_snapshot_bytes=self._settings.max_uncompressed_snapshot_bytes,
                        )
                    except SnapshotValidationError:
                        self._self_metrics.record_validation_failure()
                        validation_failures += 1
                        continue
                    except SnapshotDecodeError:
                        self._self_metrics.record_decode_failure()
                        decode_failures += 1
                        continue

                    if decoded.snapshot.server != server:
                        self._self_metrics.record_validation_failure()
                        validation_failures += 1
                        continue

                    guard_result = self._guard.apply(
                        decoded.snapshot,
                        tracked_servers=self._store.tracked_servers(),
                        tracked_total_series=self._store.tracked_total_series(),
                    )
                    for reason, count in guard_result.dropped_reasons:
                        self._self_metrics.record_drop(reason, count)
                        dropped[reason] = dropped.get(reason, 0) + count
                    if guard_result.snapshot is None:
                        self._self_metrics.record_validation_failure()
                        validation_failures += 1
                        continue

                    snapshot_age_seconds = max(
                        0.0,
                        (now_ms - guard_result.snapshot.createdAtUnixMs) / 1000,
                    )
                    if snapshot_age_seconds > self._settings.stale_snapshot_age_seconds:
                        self._store.mark_stale(
                            server,
                            snapshot_age_seconds=snapshot_age_seconds,
                        )
                        stale_count += 1
                        continue
                    self._store.replace_server_snapshot(
                        server,
                        guard_result.snapshot,
                        snapshot_age_seconds=snapshot_age_seconds,
                    )
                    self._self_metrics.record_snapshot_applied()
                    applied_count += 1
        except Exception as error:
            self._self_metrics.set_last_poll_duration_seconds(
                time.perf_counter() - started
            )
            signature = ("exception", type(error).__name__, str(error))
            if self._last_logged_issue_signature != signature:
                LOGGER.warning(
                    "Poll failed unexpectedly: %s: %s",
                    type(error).__name__,
                    error,
                )
                self._last_logged_issue_signature = signature
            return False

        self._self_metrics.set_last_poll_duration_seconds(time.perf_counter() - started)
        self._log_summary(
            applied_count=applied_count,
            missing_count=missing_count,
            stale_count=stale_count,
            decode_failures=decode_failures,
            validation_failures=validation_failures,
            dropped=dropped,
        )
        return True

    def _log_summary(
        self,
        *,
        applied_count: int,
        missing_count: int,
        stale_count: int,
        decode_failures: int,
        validation_failures: int,
        dropped: dict[str, int],
    ) -> None:
        issue_signature = (
            decode_failures,
            validation_failures,
            missing_count,
            stale_count,
            tuple(sorted(dropped.items())),
        )
        has_issues = any(
            (
                decode_failures,
                validation_failures,
                missing_count,
                stale_count,
                dropped,
            )
        )
        if has_issues:
            if self._last_logged_issue_signature != issue_signature:
                LOGGER.warning(
                    "Poll summary: applied=%d missing=%d stale=%d decode_failures=%d validation_failures=%d dropped=%s",
                    applied_count,
                    missing_count,
                    stale_count,
                    decode_failures,
                    validation_failures,
                    dict(sorted(dropped.items())),
                )
                self._last_logged_issue_signature = issue_signature
            return

        if self._last_logged_issue_signature is not None:
            LOGGER.info("Poll recovered: applied=%d", applied_count)
            self._last_logged_issue_signature = None
