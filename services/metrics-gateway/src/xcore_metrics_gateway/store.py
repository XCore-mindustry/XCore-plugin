from __future__ import annotations

from dataclasses import dataclass
from threading import RLock

from xcore_protocol.generated import MetricsSnapshotV1


@dataclass(frozen=True, slots=True)
class NodeState:
    server: str
    up: bool
    stale: bool
    snapshot_age_seconds: float | None


class SeriesStore:
    def __init__(self) -> None:
        self._lock = RLock()
        self._snapshots: dict[str, MetricsSnapshotV1] = {}
        self._node_states: dict[str, NodeState] = {}

    def tracked_servers(self) -> int:
        with self._lock:
            return len(self._node_states)

    def tracked_total_series(self) -> int:
        with self._lock:
            return sum(len(snapshot.samples) for snapshot in self._snapshots.values())

    def replace_server_snapshot(
        self,
        server: str,
        snapshot: MetricsSnapshotV1,
        *,
        snapshot_age_seconds: float | None,
    ) -> None:
        with self._lock:
            self._snapshots[server] = snapshot
            self._node_states[server] = NodeState(
                server=server,
                up=True,
                stale=False,
                snapshot_age_seconds=snapshot_age_seconds,
            )

    def mark_stale(self, server: str, *, snapshot_age_seconds: float) -> None:
        with self._lock:
            self._snapshots.pop(server, None)
            self._node_states[server] = NodeState(
                server=server,
                up=False,
                stale=True,
                snapshot_age_seconds=snapshot_age_seconds,
            )

    def mark_missing(self, server: str) -> None:
        with self._lock:
            self._snapshots.pop(server, None)
            self._node_states[server] = NodeState(
                server=server,
                up=False,
                stale=False,
                snapshot_age_seconds=None,
            )

    def render_snapshot(
        self,
    ) -> tuple[dict[str, MetricsSnapshotV1], tuple[NodeState, ...]]:
        with self._lock:
            return dict(self._snapshots), tuple(
                sorted(self._node_states.values(), key=lambda item: item.server)
            )
