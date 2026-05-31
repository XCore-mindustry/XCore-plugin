from __future__ import annotations

from xcore_protocol.generated import (
    MetricSampleV1,
    MetricSampleV1Type,
    MetricsSnapshotV1,
)

from xcore_metrics_gateway.prometheus import render_metrics
from xcore_metrics_gateway.self_metrics import GatewaySelfMetricsSnapshot
from xcore_metrics_gateway.store import NodeState


def test_render_metrics_renders_histogram_without_explicit_timestamps() -> None:
    snapshot = MetricsSnapshotV1(
        server="mini-hexed",
        nodeId="mini-hexed-01",
        producer="xcore-plugin",
        createdAtUnixMs=1000,
        startTimeUnixMs=0,
        sequence=1,
        intervalMs=15000,
        samples=(
            MetricSampleV1(
                name="xcore_command_duration_seconds",
                type=MetricSampleV1Type.HISTOGRAM,
                labels={"command": "maps"},
                help="Command duration",
                unit="seconds",
                value=None,
                buckets=(0.1, 0.5, 1.0),
                counts=(1, 2, 3),
                count=4,
                sum=5.45,
            ),
        ),
    )

    rendered = render_metrics(
        {"mini-hexed": snapshot},
        (NodeState(server="mini-hexed", up=True, snapshot_age_seconds=1.5),),
        GatewaySelfMetricsSnapshot(
            redis_up=True,
            discovered_targets=1,
            snapshots_total=4,
            discovery_failures_total=1,
            poll_failures_total=2,
            decode_failures_total=2,
            validation_failures_total=1,
            dropped_series_total=(("label_value_length_limit", 3),),
            last_poll_duration_seconds=0.25,
        ),
    )

    assert (
        'xcore_command_duration_seconds_bucket{command="maps",le="0.1",server="mini-hexed"} 1'
        in rendered
    )
    assert (
        'xcore_command_duration_seconds_bucket{command="maps",le="+Inf",server="mini-hexed"} 4'
        in rendered
    )
    assert (
        'xcore_command_duration_seconds_sum{command="maps",server="mini-hexed"} 5.45'
        in rendered
    )
    assert 'xcore_node_up{server="mini-hexed"} 1' in rendered
    assert 'xcore_node_snapshot_age_seconds{server="mini-hexed"} 1.5' in rendered
    assert "xcore_metrics_gateway_redis_up 1" in rendered
    assert "xcore_metrics_gateway_discovered_targets 1" in rendered
    assert "xcore_metrics_gateway_snapshots_total 4" in rendered
    assert "xcore_metrics_gateway_discovery_failures_total 1" in rendered
    assert "xcore_metrics_gateway_poll_failures_total 2" in rendered
    assert "xcore_metrics_gateway_decode_failures_total 2" in rendered
    assert "xcore_metrics_gateway_validation_failures_total 1" in rendered
    assert (
        'xcore_metrics_gateway_dropped_series_total{reason="label_value_length_limit"} 3'
        in rendered
    )
    assert "xcore_metrics_gateway_last_poll_duration_seconds 0.25" in rendered
    assert " 1790789112000" not in rendered
