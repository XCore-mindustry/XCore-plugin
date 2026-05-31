from __future__ import annotations

from xcore_protocol.generated import MetricSampleV1Type, MetricsSnapshotV1
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_metrics_snapshot_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "telemetry" / "metrics.snapshot.v1.json")

    model = MetricsSnapshotV1.from_payload(payload)

    assert model.SCHEMA_VERSION == "metrics.snapshot.v1"
    assert model.server == payload["server"]
    assert model.samples[0].type is MetricSampleV1Type.GAUGE
    assert model.samples[1].type is MetricSampleV1Type.COUNTER
    assert model.samples[2].type is MetricSampleV1Type.HISTOGRAM
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "telemetry" / "metrics.snapshot.v1.json", model.to_payload())


def test_generated_metrics_snapshot_remains_strict() -> None:
    invalid_payload = load_json(
        fixtures_root() / "invalid" / "telemetry" / "metrics.snapshot.v1.samples-not-array.json"
    )

    try:
        MetricsSnapshotV1.from_payload(invalid_payload)
    except (TypeError, ValueError) as error:
        assert "samples" in str(error)
    else:
        raise AssertionError("Expected strict generated model parsing to reject non-array samples")
