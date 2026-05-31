from __future__ import annotations

from xcore_protocol.generated import (
    MetricSampleV1,
    MetricSampleV1Type,
    MetricsSnapshotV1,
)

from xcore_metrics_gateway.guard import CardinalityGuard
from xcore_metrics_gateway.settings import Settings


def _settings() -> Settings:
    return Settings(
        max_servers=10,
        max_series_per_server=2,
        max_total_series=3,
        max_labels_per_metric=2,
        max_label_value_length=5,
    )


def _snapshot(*samples: MetricSampleV1) -> MetricsSnapshotV1:
    return MetricsSnapshotV1(
        server="mini-hexed",
        nodeId="mini-hexed-01",
        producer="xcore-plugin",
        createdAtUnixMs=1_000,
        startTimeUnixMs=0,
        sequence=1,
        intervalMs=15_000,
        samples=samples,
    )


def test_guard_drops_series_over_per_server_limit() -> None:
    guard = CardinalityGuard(_settings())
    snapshot = _snapshot(
        MetricSampleV1("a_total", MetricSampleV1Type.COUNTER, {}, value=1.0),
        MetricSampleV1("b_total", MetricSampleV1Type.COUNTER, {}, value=1.0),
        MetricSampleV1("c_total", MetricSampleV1Type.COUNTER, {}, value=1.0),
    )

    result = guard.apply(snapshot, tracked_servers=1, tracked_total_series=0)

    assert result.snapshot is not None
    assert len(result.snapshot.samples) == 2
    assert result.dropped_reasons == (("series_per_server_limit", 1),)


def test_guard_drops_series_with_too_many_labels() -> None:
    guard = CardinalityGuard(_settings())
    snapshot = _snapshot(
        MetricSampleV1(
            "mindustry_players_online",
            MetricSampleV1Type.GAUGE,
            {"mode": "pvp", "map": "hex", "team": "blue"},
            value=5.0,
        )
    )

    result = guard.apply(snapshot, tracked_servers=1, tracked_total_series=0)

    assert result.snapshot is None
    assert result.dropped_reasons == (("labels_per_metric_limit", 1),)


def test_guard_drops_series_with_long_label_value() -> None:
    guard = CardinalityGuard(_settings())
    snapshot = _snapshot(
        MetricSampleV1(
            "mindustry_players_online",
            MetricSampleV1Type.GAUGE,
            {"mode": "very-long-value"},
            value=5.0,
        )
    )

    result = guard.apply(snapshot, tracked_servers=1, tracked_total_series=0)

    assert result.snapshot is None
    assert result.dropped_reasons == (("label_value_length_limit", 1),)


def test_guard_drops_all_when_total_series_limit_is_exhausted() -> None:
    guard = CardinalityGuard(_settings())
    snapshot = _snapshot(
        MetricSampleV1("a_total", MetricSampleV1Type.COUNTER, {}, value=1.0),
        MetricSampleV1("b_total", MetricSampleV1Type.COUNTER, {}, value=1.0),
    )

    result = guard.apply(snapshot, tracked_servers=1, tracked_total_series=3)

    assert result.snapshot is None
    assert result.dropped_reasons == (("total_series_limit", 2),)
