from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "chat" / "chat.message.v1.json",
        fixtures_root() / "valid" / "chat" / "chat.message.v1.json",
    ),
    (
        spec_root() / "messages" / "chat" / "chat.global.v1.json",
        fixtures_root() / "valid" / "chat" / "chat.global.v1.json",
    ),
    (
        spec_root() / "messages" / "chat" / "chat.discord-ingress.command.v1.json",
        fixtures_root() / "valid" / "chat" / "chat.discord-ingress.command.v1.json",
    ),
    (
        spec_root() / "messages" / "chat" / "chat.private.v1.json",
        fixtures_root() / "valid" / "chat" / "chat.private.v1.json",
    ),
    (
        spec_root() / "messages" / "chat" / "server.action.v1.json",
        fixtures_root() / "valid" / "chat" / "server.action.v1.json",
    ),
    (
        spec_root() / "messages" / "chat" / "player.join-leave.v1.json",
        fixtures_root() / "valid" / "chat" / "player.join-leave.v1.json",
    ),
    (
        spec_root() / "messages" / "chat" / "server.heartbeat.v1.json",
        fixtures_root() / "valid" / "chat" / "server.heartbeat.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "chat" / "chat.message.v1.json",
        fixtures_root() / "invalid" / "chat" / "chat.message.v1.snake-author.json",
    ),
    (
        spec_root() / "messages" / "chat" / "chat.private.v1.json",
        fixtures_root() / "invalid" / "chat" / "chat.private.v1.snake-from.json",
    ),
    (
        spec_root() / "messages" / "chat" / "player.join-leave.v1.json",
        fixtures_root()
        / "invalid"
        / "chat"
        / "player.join-leave.v1.legacy-join.json",
    ),
    (
        spec_root() / "messages" / "chat" / "server.heartbeat.v1.json",
        fixtures_root()
        / "invalid"
        / "chat"
        / "server.heartbeat.v1.server-host-alias.json",
    ),
    (
        spec_root() / "messages" / "chat" / "server.heartbeat.v1.json",
        fixtures_root()
        / "invalid"
        / "chat"
        / "server.heartbeat.v1.missing-channel.json",
    ),
]


def test_valid_chat_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_chat_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_chat_fixture_inventory_exists() -> None:
    chat_valid_dir = fixtures_root() / "valid" / "chat"
    chat_invalid_dir = fixtures_root() / "invalid" / "chat"

    assert chat_valid_dir.exists()
    assert chat_invalid_dir.exists()
    assert any(chat_valid_dir.iterdir())
    assert any(chat_invalid_dir.iterdir())
