from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


def _schema(message: str):
    return spec_root() / "messages" / "security" / f"{message}.v1.json"


VALID_CASES = [
    (_schema(message), fixtures_root() / "valid" / "security" / f"{fixture}.json")
    for message, fixture in (
        ("player.password-reset.command", "player.password-reset.command.v1"),
        ("security.permissions.changed", "security.permissions.changed.v1"),
        ("security.permissions.changed", "security.permissions.changed.v1.minimal"),
        ("security.staff.sync.request", "security.staff.sync.request.v1"),
        ("security.staff.sync.request", "security.staff.sync.request.v1.left-guild"),
        ("security.staff.sync.response", "security.staff.sync.response.v1"),
        ("security.staff.reset-password.request", "security.staff.reset-password.request.v1"),
        ("security.staff.reset-password.response", "security.staff.reset-password.response.v1"),
    )
]


INVALID_CASES = [
    (_schema(message), fixtures_root() / "invalid" / "security" / f"{message}.v1.{case}.json")
    for message, case in (
        ("player.password-reset.command", "legacy-uuid"),
        ("security.permissions.changed", "negative-revision"),
        ("security.permissions.changed", "legacy-uuid"),
        ("security.staff.sync.request", "missing-complete"),
        ("security.staff.sync.request", "duplicate-role"),
        ("security.staff.sync.response", "missing-revision"),
        ("security.staff.reset-password.request", "missing-operation-id"),
    )
]


def test_valid_security_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_security_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_every_security_fixture_is_covered() -> None:
    valid_dir = fixtures_root() / "valid" / "security"
    invalid_dir = fixtures_root() / "invalid" / "security"

    assert {path for _, path in VALID_CASES} == set(valid_dir.iterdir())
    assert {path for _, path in INVALID_CASES} == set(invalid_dir.iterdir())
