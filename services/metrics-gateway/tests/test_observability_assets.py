from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).parents[1]
DASHBOARD = ROOT / "ops/grafana/dashboards/xcore-overview.json"
PROMETHEUS = ROOT / "ops/prometheus/prometheus.yml"
RULES = ROOT / "ops/prometheus/xcore.rules.yml"


# This is the cross-repository contract from xcore-sentinel's SentinelMetrics.
# Keep this list exact: changing a metric name or its labels is a compatibility change.
SENTINEL_METRICS = {
    "sentinel_nickname_denials_total",
    "sentinel_admission_attempts_total",
    "sentinel_admission_waves_total",
    "sentinel_admission_closes_total",
    "sentinel_subnet_denials_total",
}

XCORE_METRICS = {
    "mindustry_players_online",
    "mindustry_wave",
    "mindustry_tps",
    "mindustry_player_joins_total",
    "mindustry_player_leaves_total",
    "xcore_plugin_uptime_seconds",
    "xcore_ingress_denials_total",
    "xcore_ingress_check_errors_total",
    "xcore_commands_total",
    "xcore_command_duration_seconds",
}

GATEWAY_METRICS = {
    "xcore_node_up",
    "xcore_node_stale",
    "xcore_node_snapshot_age_seconds",
    "xcore_metrics_gateway_redis_up",
    "xcore_metrics_gateway_discovered_targets",
    "xcore_metrics_gateway_stale_nodes",
    "xcore_metrics_gateway_snapshots_total",
    "xcore_metrics_gateway_discovery_failures_total",
    "xcore_metrics_gateway_poll_failures_total",
    "xcore_metrics_gateway_decode_failures_total",
    "xcore_metrics_gateway_validation_failures_total",
    "xcore_metrics_gateway_dropped_series_total",
    "xcore_metrics_gateway_last_discovery_duration_seconds",
    "xcore_metrics_gateway_last_poll_duration_seconds",
}


def test_dashboard_references_every_exported_metric() -> None:
    dashboard = json.loads(DASHBOARD.read_text())
    dashboard_text = json.dumps(dashboard)

    expected = SENTINEL_METRICS | XCORE_METRICS | GATEWAY_METRICS
    missing = sorted(metric for metric in expected if metric not in dashboard_text)
    assert not missing, f"Exported metrics missing from Grafana dashboard: {missing}"


def test_prometheus_loads_xcore_rules() -> None:
    prometheus_config = PROMETHEUS.read_text()
    assert "rule_files:" in prometheus_config
    assert "/etc/prometheus/rules/*.yml" in prometheus_config


def test_sentinel_recording_rules_cover_all_counter_families() -> None:
    rules = RULES.read_text()
    for metric in SENTINEL_METRICS:
        assert metric in rules, (
            f"Sentinel metric is not covered by Prometheus rules: {metric}"
        )


def test_dashboard_has_operational_sections() -> None:
    dashboard = json.loads(DASHBOARD.read_text())
    titles = {panel["title"] for panel in dashboard["panels"] if "title" in panel}

    assert {"Platform health", "Sentinel security", "Gateway internals"} <= titles
