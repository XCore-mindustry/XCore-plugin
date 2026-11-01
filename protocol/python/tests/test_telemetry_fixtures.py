from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "telemetry" / "metrics.snapshot.v1.json",
        fixtures_root() / "valid" / "telemetry" / "metrics.snapshot.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "telemetry" / "metrics.snapshot.v1.json",
        fixtures_root() / "invalid" / "telemetry" / "metrics.snapshot.v1.samples-not-array.json",
    ),
    (
        spec_root() / "messages" / "telemetry" / "metrics.snapshot.v1.json",
        fixtures_root() / "invalid" / "telemetry" / "metrics.snapshot.v1.histogram-missing-counts.json",
    ),
    (
        spec_root() / "messages" / "telemetry" / "metrics.snapshot.v1.json",
        fixtures_root() / "invalid" / "telemetry" / "metrics.snapshot.v1.sample-labels-include-server.json",
    ),
]


def test_valid_telemetry_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_telemetry_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_telemetry_fixture_inventory_exists() -> None:
    telemetry_valid_dir = fixtures_root() / "valid" / "telemetry"
    telemetry_invalid_dir = fixtures_root() / "invalid" / "telemetry"

    assert telemetry_valid_dir.exists()
    assert telemetry_invalid_dir.exists()
    assert any(telemetry_valid_dir.iterdir())
    assert any(telemetry_invalid_dir.iterdir())
