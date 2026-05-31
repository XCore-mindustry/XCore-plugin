from __future__ import annotations

from xcore_protocol.generated import MetricSampleV1Type, MetricsSnapshotV1

from .self_metrics import GatewaySelfMetricsSnapshot
from .store import NodeState


def render_metrics(
    snapshots_by_server: dict[str, MetricsSnapshotV1],
    node_states: tuple[NodeState, ...],
    self_metrics: GatewaySelfMetricsSnapshot,
) -> str:
    lines: list[str] = []
    emitted_help: set[str] = set()
    emitted_type: set[str] = set()

    for server, snapshot in sorted(snapshots_by_server.items()):
        for sample in snapshot.samples:
            base_name = sample.name
            if base_name not in emitted_help and sample.help:
                lines.append(f"# HELP {base_name} {sample.help}")
                emitted_help.add(base_name)
            if base_name not in emitted_type:
                lines.append(f"# TYPE {base_name} {_prometheus_type(sample.type)}")
                emitted_type.add(base_name)

            labels = {"server": server} | {
                key: str(value) for key, value in sample.labels.items()
            }
            if sample.type in (
                MetricSampleV1Type.COUNTER,
                MetricSampleV1Type.GAUGE,
                MetricSampleV1Type.INFO,
            ):
                lines.append(
                    f"{base_name}{_format_labels(labels)} {_format_number(sample.value or 0.0)}"
                )
                continue

            if sample.type is MetricSampleV1Type.HISTOGRAM:
                assert sample.buckets is not None
                assert sample.counts is not None
                assert sample.count is not None
                assert sample.sum is not None
                for bucket, count in zip(sample.buckets, sample.counts, strict=True):
                    bucket_labels = labels | {"le": _format_number(bucket)}
                    lines.append(
                        f"{base_name}_bucket{_format_labels(bucket_labels)} {count}"
                    )
                inf_labels = labels | {"le": "+Inf"}
                lines.append(
                    f"{base_name}_bucket{_format_labels(inf_labels)} {sample.count}"
                )
                lines.append(
                    f"{base_name}_sum{_format_labels(labels)} {_format_number(sample.sum)}"
                )
                lines.append(
                    f"{base_name}_count{_format_labels(labels)} {sample.count}"
                )

    lines.append("# TYPE xcore_node_up gauge")
    for node_state in node_states:
        lines.append(
            f'xcore_node_up{{server="{_escape_label_value(node_state.server)}"}} {1 if node_state.up else 0}'
        )

    lines.append("# TYPE xcore_node_stale gauge")
    for node_state in node_states:
        lines.append(
            f'xcore_node_stale{{server="{_escape_label_value(node_state.server)}"}} {1 if node_state.stale else 0}'
        )

    lines.append("# TYPE xcore_node_snapshot_age_seconds gauge")
    for node_state in node_states:
        if node_state.snapshot_age_seconds is None:
            continue
        lines.append(
            f'xcore_node_snapshot_age_seconds{{server="{_escape_label_value(node_state.server)}"}} '
            f"{_format_number(node_state.snapshot_age_seconds)}"
        )

    lines.append("# TYPE xcore_metrics_gateway_redis_up gauge")
    lines.append(f"xcore_metrics_gateway_redis_up {1 if self_metrics.redis_up else 0}")
    lines.append("# TYPE xcore_metrics_gateway_discovered_targets gauge")
    lines.append(
        f"xcore_metrics_gateway_discovered_targets {self_metrics.discovered_targets}"
    )
    lines.append("# TYPE xcore_metrics_gateway_stale_nodes gauge")
    lines.append(f"xcore_metrics_gateway_stale_nodes {self_metrics.stale_nodes}")
    lines.append("# TYPE xcore_metrics_gateway_snapshots_total counter")
    lines.append(
        f"xcore_metrics_gateway_snapshots_total {self_metrics.snapshots_total}"
    )
    lines.append("# TYPE xcore_metrics_gateway_discovery_failures_total counter")
    lines.append(
        "xcore_metrics_gateway_discovery_failures_total "
        f"{self_metrics.discovery_failures_total}"
    )
    lines.append("# TYPE xcore_metrics_gateway_poll_failures_total counter")
    lines.append(
        f"xcore_metrics_gateway_poll_failures_total {self_metrics.poll_failures_total}"
    )
    lines.append("# TYPE xcore_metrics_gateway_decode_failures_total counter")
    lines.append(
        f"xcore_metrics_gateway_decode_failures_total {self_metrics.decode_failures_total}"
    )
    lines.append("# TYPE xcore_metrics_gateway_validation_failures_total counter")
    lines.append(
        "xcore_metrics_gateway_validation_failures_total "
        f"{self_metrics.validation_failures_total}"
    )
    lines.append("# TYPE xcore_metrics_gateway_dropped_series_total counter")
    for reason, count in self_metrics.dropped_series_total:
        lines.append(
            "xcore_metrics_gateway_dropped_series_total"
            f'{{reason="{_escape_label_value(reason)}"}} {count}'
        )
    lines.append("# TYPE xcore_metrics_gateway_last_discovery_duration_seconds gauge")
    lines.append(
        "xcore_metrics_gateway_last_discovery_duration_seconds "
        f"{_format_number(self_metrics.last_discovery_duration_seconds)}"
    )
    lines.append("# TYPE xcore_metrics_gateway_last_poll_duration_seconds gauge")
    lines.append(
        "xcore_metrics_gateway_last_poll_duration_seconds "
        f"{_format_number(self_metrics.last_poll_duration_seconds)}"
    )

    return "\n".join(lines) + "\n"


def _prometheus_type(sample_type: MetricSampleV1Type) -> str:
    if sample_type is MetricSampleV1Type.COUNTER:
        return "counter"
    if sample_type is MetricSampleV1Type.HISTOGRAM:
        return "histogram"
    return "gauge"


def _format_labels(labels: dict[str, str]) -> str:
    if not labels:
        return ""
    pairs = ",".join(
        f'{key}="{_escape_label_value(value)}"' for key, value in sorted(labels.items())
    )
    return "{" + pairs + "}"


def _escape_label_value(value: str) -> str:
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace('"', '\\"')


def _format_number(value: float) -> str:
    return format(value, "g")
