from __future__ import annotations

from dataclasses import dataclass
from threading import Lock


@dataclass(frozen=True, slots=True)
class GatewaySelfMetricsSnapshot:
    redis_up: bool
    discovered_targets: int
    stale_nodes: int
    snapshots_total: int
    discovery_failures_total: int
    poll_failures_total: int
    decode_failures_total: int
    validation_failures_total: int
    dropped_series_total: tuple[tuple[str, int], ...]
    last_poll_duration_seconds: float


class GatewaySelfMetrics:
    def __init__(self) -> None:
        self._lock = Lock()
        self._redis_up = False
        self._discovered_targets = 0
        self._stale_nodes = 0
        self._snapshots_total = 0
        self._discovery_failures_total = 0
        self._poll_failures_total = 0
        self._decode_failures_total = 0
        self._validation_failures_total = 0
        self._dropped_series_total: dict[str, int] = {}
        self._last_poll_duration_seconds = 0.0

    def set_redis_up(self, redis_up: bool) -> None:
        with self._lock:
            self._redis_up = redis_up

    def set_discovered_targets(self, discovered_targets: int) -> None:
        with self._lock:
            self._discovered_targets = discovered_targets

    def record_snapshot_applied(self) -> None:
        with self._lock:
            self._snapshots_total += 1

    def set_stale_nodes(self, stale_nodes: int) -> None:
        with self._lock:
            self._stale_nodes = max(0, stale_nodes)

    def record_discovery_failure(self) -> None:
        with self._lock:
            self._discovery_failures_total += 1

    def record_poll_failure(self) -> None:
        with self._lock:
            self._poll_failures_total += 1

    def record_decode_failure(self) -> None:
        with self._lock:
            self._decode_failures_total += 1

    def record_validation_failure(self) -> None:
        with self._lock:
            self._validation_failures_total += 1

    def record_drop(self, reason: str, count: int = 1) -> None:
        with self._lock:
            self._dropped_series_total[reason] = (
                self._dropped_series_total.get(reason, 0) + count
            )

    def set_last_poll_duration_seconds(self, duration_seconds: float) -> None:
        with self._lock:
            self._last_poll_duration_seconds = max(0.0, duration_seconds)

    def snapshot(self) -> GatewaySelfMetricsSnapshot:
        with self._lock:
            return GatewaySelfMetricsSnapshot(
                redis_up=self._redis_up,
                discovered_targets=self._discovered_targets,
                stale_nodes=self._stale_nodes,
                snapshots_total=self._snapshots_total,
                discovery_failures_total=self._discovery_failures_total,
                poll_failures_total=self._poll_failures_total,
                decode_failures_total=self._decode_failures_total,
                validation_failures_total=self._validation_failures_total,
                dropped_series_total=tuple(sorted(self._dropped_series_total.items())),
                last_poll_duration_seconds=self._last_poll_duration_seconds,
            )
