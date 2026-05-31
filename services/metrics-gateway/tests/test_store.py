from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor

from xcore_protocol.generated import (
    MetricSampleV1,
    MetricSampleV1Type,
    MetricsSnapshotV1,
)

from xcore_metrics_gateway.store import SeriesStore


def _snapshot(server: str, sequence: int, *sample_names: str) -> MetricsSnapshotV1:
    return MetricsSnapshotV1(
        server=server,
        nodeId=f"{server}-01",
        producer="xcore-plugin",
        createdAtUnixMs=1_000 + sequence,
        startTimeUnixMs=0,
        sequence=sequence,
        intervalMs=15_000,
        samples=tuple(
            MetricSampleV1(
                name=name,
                type=MetricSampleV1Type.GAUGE,
                labels={},
                value=float(index),
            )
            for index, name in enumerate(sample_names, start=1)
        ),
    )


def test_render_snapshot_returns_atomic_read_views_during_replacement() -> None:
    store = SeriesStore()
    first = _snapshot("mini-hexed", 1, "mindustry_players_online")
    second = _snapshot(
        "mini-hexed",
        2,
        "mindustry_players_online",
        "mindustry_player_joins_total",
    )
    store.replace_server_snapshot("mini-hexed", first, snapshot_age_seconds=1.0)

    def read_view() -> tuple[int, tuple[str, ...]]:
        snapshots, _ = store.render_snapshot()
        snapshot = snapshots["mini-hexed"]
        return snapshot.sequence, tuple(sample.name for sample in snapshot.samples)

    with ThreadPoolExecutor(max_workers=8) as executor:
        before_futures = [executor.submit(read_view) for _ in range(50)]
        store.replace_server_snapshot("mini-hexed", second, snapshot_age_seconds=2.0)
        after_futures = [executor.submit(read_view) for _ in range(50)]

    observed = {future.result() for future in before_futures + after_futures}
    assert observed <= {
        (1, ("mindustry_players_online",)),
        (2, ("mindustry_players_online", "mindustry_player_joins_total")),
    }
    assert (2, ("mindustry_players_online", "mindustry_player_joins_total")) in observed
