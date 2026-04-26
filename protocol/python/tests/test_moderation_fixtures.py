from __future__ import annotations

from pathlib import Path

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "moderation" / "moderation.ban.created.v1.json",
        fixtures_root() / "valid" / "moderation" / "moderation.ban.created.v1.json",
    ),
    (
        spec_root() / "messages" / "moderation" / "moderation.mute.created.v1.json",
        fixtures_root() / "valid" / "moderation" / "moderation.mute.created.v1.json",
    ),
    (
        spec_root() / "messages" / "moderation" / "moderation.vote-kick.created.v1.json",
        fixtures_root() / "valid" / "moderation" / "moderation.vote-kick.created.v1.json",
    ),
    (
        spec_root() / "messages" / "moderation" / "moderation.kick-banned.command.v1.json",
        fixtures_root() / "valid" / "moderation" / "moderation.kick-banned.command.v1.json",
    ),
    (
        spec_root() / "messages" / "moderation" / "moderation.pardon.command.v1.json",
        fixtures_root() / "valid" / "moderation" / "moderation.pardon.command.v1.json",
    ),
    (
        spec_root() / "messages" / "moderation" / "moderation.audit.appended.v1.json",
        fixtures_root() / "valid" / "moderation" / "moderation.audit.appended.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "moderation" / "moderation.ban.created.v1.json",
        fixtures_root()
        / "invalid"
        / "moderation"
        / "moderation.ban.created.v1.missing-reason.json",
    ),
    (
        spec_root() / "messages" / "moderation" / "moderation.mute.created.v1.json",
        fixtures_root()
        / "invalid"
        / "moderation"
        / "moderation.mute.created.v1.bad-expiration-type.json",
    ),
    (
        spec_root() / "messages" / "moderation" / "moderation.vote-kick.created.v1.json",
        fixtures_root()
        / "invalid"
        / "moderation"
        / "moderation.vote-kick.created.v1.bad-votes.json",
    ),
]


def test_valid_moderation_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_moderation_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_moderation_fixture_inventory_exists() -> None:
    moderation_valid_dir = fixtures_root() / "valid" / "moderation"
    moderation_invalid_dir = fixtures_root() / "invalid" / "moderation"

    assert moderation_valid_dir.exists()
    assert moderation_invalid_dir.exists()
    assert any(moderation_valid_dir.iterdir())
    assert any(moderation_invalid_dir.iterdir())
