from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "security" / "player.password-reset.command.v1.json",
        fixtures_root() / "valid" / "security" / "player.password-reset.command.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "security" / "player.password-reset.command.v1.json",
        fixtures_root()
        / "invalid"
        / "security"
        / "player.password-reset.command.v1.legacy-uuid.json",
    ),
]


def test_valid_security_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_security_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_security_fixture_inventory_exists() -> None:
    valid_dir = fixtures_root() / "valid" / "security"
    invalid_dir = fixtures_root() / "invalid" / "security"

    assert valid_dir.exists()
    assert invalid_dir.exists()
    assert any(valid_dir.iterdir())
    assert any(invalid_dir.iterdir())
