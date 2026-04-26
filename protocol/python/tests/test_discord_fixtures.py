from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "discord" / "discord.link.confirm.command.v1.json",
        fixtures_root() / "valid" / "discord" / "discord.link.confirm.command.v1.json",
    ),
    (
        spec_root() / "messages" / "discord" / "discord.unlink.command.v1.json",
        fixtures_root() / "valid" / "discord" / "discord.unlink.command.v1.json",
    ),
    (
        spec_root() / "messages" / "discord" / "discord.link.status-changed.v1.json",
        fixtures_root() / "valid" / "discord" / "discord.link.status-changed.v1.json",
    ),
    (
        spec_root()
        / "messages"
        / "discord"
        / "discord.admin-access.changed.command.v1.json",
        fixtures_root()
        / "valid"
        / "discord"
        / "discord.admin-access.changed.command.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "discord" / "discord.link.confirm.command.v1.json",
        fixtures_root()
        / "invalid"
        / "discord"
        / "discord.link.confirm.command.v1.bad-time.json",
    ),
    (
        spec_root() / "messages" / "discord" / "discord.unlink.command.v1.json",
        fixtures_root()
        / "invalid"
        / "discord"
        / "discord.unlink.command.v1.missing-discord.json",
    ),
    (
        spec_root()
        / "messages"
        / "discord"
        / "discord.admin-access.changed.command.v1.json",
        fixtures_root()
        / "invalid"
        / "discord"
        / "discord.admin-access.changed.command.v1.missing-reason.json",
    ),
]


def test_valid_discord_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_discord_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_discord_fixture_inventory_exists() -> None:
    discord_valid_dir = fixtures_root() / "valid" / "discord"
    discord_invalid_dir = fixtures_root() / "invalid" / "discord"

    assert discord_valid_dir.exists()
    assert discord_invalid_dir.exists()
    assert any(discord_valid_dir.iterdir())
    assert any(discord_invalid_dir.iterdir())
