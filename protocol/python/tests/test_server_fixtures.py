from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "server" / "server.action.v1.json",
        fixtures_root() / "valid" / "server" / "server.action.v1.json",
    ),
    (
        spec_root() / "messages" / "server" / "server.heartbeat.v1.json",
        fixtures_root() / "valid" / "server" / "server.heartbeat.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "server" / "server.heartbeat.v1.json",
        fixtures_root()
        / "invalid"
        / "server"
        / "server.heartbeat.v1.server-host-alias.json",
    ),
    (
        spec_root() / "messages" / "server" / "server.heartbeat.v1.json",
        fixtures_root()
        / "invalid"
        / "server"
        / "server.heartbeat.v1.missing-channel.json",
    ),
]


def test_valid_server_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_server_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_server_fixture_inventory_exists() -> None:
    valid_dir = fixtures_root() / "valid" / "server"
    invalid_dir = fixtures_root() / "invalid" / "server"

    assert valid_dir.exists()
    assert invalid_dir.exists()
    assert any(valid_dir.iterdir())
    assert any(invalid_dir.iterdir())
