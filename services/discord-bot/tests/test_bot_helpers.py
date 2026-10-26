from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import Any

import pytest
from backends import GameServer, connected_bus, published, real_bot, redis_connections
from bot_fakes import make_bot, make_settings
from bson.datetime_ms import DatetimeMS
from discord import app_commands
from test_ban_identity import NOW
from test_staff_actions import PLAYER, _action_bot, _assert_audit

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.command_helpers import claim_mutation
from xcore_discord_bot.handlers_moderation import (
    cmd_pardon,
    perform_ban,
)
from xcore_discord_bot.presentation import (
    build_servers_embed,
    format_ban_expire_date,
    sort_live_servers,
)
from xcore_discord_bot.registry import ServerInfo
from xcore_discord_bot.runtime_consumers import run_consumer_forever
from xcore_discord_bot.utils.duration import parse_duration
from xcore_discord_bot.utils.mindustry_colors import strip_mindustry_colors


def test_parse_duration() -> None:
    assert parse_duration("10m") == timedelta(minutes=10)
    assert parse_duration("1h") == timedelta(hours=1)
    assert parse_duration("1h30m") == timedelta(hours=1, minutes=30)
    assert parse_duration("90") == timedelta(days=90)
    assert parse_duration("15", default_unit="m") == timedelta(minutes=15)


def test_parse_duration_invalid() -> None:
    with pytest.raises(ValueError, match="Invalid period format"):
        parse_duration("abc")
    with pytest.raises(ValueError, match="Invalid period format"):
        parse_duration("1h-30m")
    with pytest.raises(ValueError, match="Invalid period format"):
        parse_duration("10x")


def test_strip_mindustry_colors() -> None:
    assert strip_mindustry_colors("[#FFA108FF]OSPx") == "OSPx"
    assert strip_mindustry_colors("[accent]Hello[]") == "Hello"
    assert strip_mindustry_colors("[scarlet]A[white]B[]") == "AB"
    assert strip_mindustry_colors("[not-a-color]text") == "[not-a-color]text"


def test_format_ban_expire_date_for_datetime() -> None:
    expire = datetime(2026, 3, 4, 12, 0, tzinfo=UTC)

    formatted = format_ban_expire_date(expire)

    assert "<t:" in formatted


def test_format_ban_expire_date_for_invalid_value() -> None:
    assert format_ban_expire_date({"bad": "value"}) == "Unknown"


def test_format_ban_expire_date_for_datetime_ms() -> None:
    dtms = DatetimeMS(1762296000000)
    formatted = format_ban_expire_date(dtms)
    assert "<t:" in formatted


def test_format_ban_expire_date_for_far_future_millis() -> None:
    formatted = format_ban_expire_date(315537897600000)
    assert formatted == "After year 9999"


def test_sort_live_servers_by_players_then_name() -> None:
    servers = [
        ServerInfo("b", 1, 5, 30, "1", None, None, 0.0),
        ServerInfo("a", 2, 5, 30, "1", None, None, 0.0),
        ServerInfo("c", 3, 2, 30, "1", None, None, 0.0),
    ]

    sorted_servers = sort_live_servers(servers, "players")

    assert [srv.name for srv in sorted_servers] == ["a", "b", "c"]


def test_sort_live_servers_by_name() -> None:
    servers = [
        ServerInfo("beta", 1, 5, 30, "1", None, None, 0.0),
        ServerInfo("Alpha", 2, 20, 30, "1", None, None, 0.0),
    ]

    sorted_servers = sort_live_servers(servers, "name")

    assert [srv.name for srv in sorted_servers] == ["Alpha", "beta"]


def test_build_servers_embed_includes_sort_mode_footer() -> None:
    servers = [
        ServerInfo("alpha", 123, 3, 30, "155.4", None, None, 0.0),
    ]

    embed = build_servers_embed(servers, sort_mode="players")

    assert embed.footer.text == "Sort: players • Servers: 1 • Players online: 3"


def test_build_servers_embed_includes_address_when_available() -> None:
    servers = [
        ServerInfo("alpha", 123, 3, 30, "155.4", "play.example.com", 6567, 0.0),
    ]

    embed = build_servers_embed(servers, sort_mode="players")

    assert len(embed.fields) == 1
    assert "Address: `play.example.com:6567`" in (embed.fields[0].value or "")


class _FakeTree:
    def __init__(self) -> None:
        self.calls: list[tuple[str, int | None]] = []

    def clear_commands(self, *, guild=None) -> None:
        self.calls.append(("clear", getattr(guild, "id", None)))

    def copy_global_to(self, *, guild) -> None:
        self.calls.append(("copy", getattr(guild, "id", None)))

    async def sync(self, *, guild=None):
        self.calls.append(("sync", getattr(guild, "id", None)))
        return []


def _record_command_sync(bot):
    tree = _FakeTree()
    bot.tree.clear_commands = tree.clear_commands
    bot.tree.copy_global_to = tree.copy_global_to
    bot.tree.sync = tree.sync
    return tree


@pytest.mark.asyncio
async def test_sync_application_commands_clears_global_and_guild_when_enabled() -> None:
    bot = make_bot(
        settings=make_settings(
            discord_guild_id=123,
            discord_clear_stale_commands=True,
        )
    )
    tree = _record_command_sync(bot)

    await XCoreDiscordBot._sync_application_commands(bot)

    assert tree.calls == [
        ("clear", None),
        ("sync", None),
        ("clear", 123),
        ("copy", 123),
        ("sync", 123),
    ]


@pytest.mark.asyncio
async def test_sync_application_commands_only_syncs_guild_by_default() -> None:
    bot = make_bot(
        settings=make_settings(
            discord_guild_id=123,
            discord_clear_stale_commands=False,
        )
    )
    tree = _record_command_sync(bot)

    await XCoreDiscordBot._sync_application_commands(bot)

    assert tree.calls == [
        ("copy", 123),
        ("sync", 123),
    ]


@pytest.mark.asyncio
async def test_sync_application_commands_clears_global_only_without_guild() -> None:
    bot = make_bot(
        settings=make_settings(
            discord_guild_id=0,
            discord_clear_stale_commands=True,
        )
    )
    tree = _record_command_sync(bot)

    await XCoreDiscordBot._sync_application_commands(bot)

    assert tree.calls == [
        ("clear", None),
        ("sync", None),
    ]


# ── _claim_mutation tests ─────────────────────────────────────────────────────


@dataclass
class _FakeInteraction:
    id: int = 1
    replies: list[str] = field(default_factory=list)
    ephemeral_replies: list[str] = field(default_factory=list)

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        if ephemeral:
            self.ephemeral_replies.append(text)
        else:
            self.replies.append(text)

    @property
    def response(self) -> _FakeInteraction:
        return self


@pytest.mark.asyncio
async def test_claim_mutation_success() -> None:
    bot, _ = await real_bot()
    interaction = _FakeInteraction()

    claimed = await claim_mutation(
        bot,
        interaction,
        operation="ban",
        scope="1:1d:test",
    )

    assert claimed is True
    assert interaction.replies == []
    assert interaction.ephemeral_replies == []
    assert 590 < await bot.bus.redis.ttl("xcore:idmp:cmd:ban:1:1:1d:test") <= 600


@pytest.mark.asyncio
async def test_claim_mutation_duplicate() -> None:
    bot, _ = await real_bot()
    await claim_mutation(bot, _FakeInteraction(), operation="ban", scope="1:1d:test")
    # The same interaction delivered again.
    interaction = _FakeInteraction()

    claimed = await claim_mutation(
        bot,
        interaction,
        operation="ban",
        scope="1:1d:test",
    )

    assert claimed is False
    assert len(interaction.ephemeral_replies) == 1
    assert "Duplicate command ignored" in interaction.ephemeral_replies[0]
    # Another command for the same player is not a duplicate.
    assert await claim_mutation(
        bot, _FakeInteraction(id=2), operation="ban", scope="1:1d:test"
    )


@pytest.mark.asyncio
async def test_perform_ban_reports_a_repeated_ban_instead_of_banning_twice() -> None:
    bot, db, game, channel = await _action_bot()
    ban: dict[str, Any] = {
        "actor_name": "Moderator",
        "actor_discord_id": "123",
        "player_id": PLAYER.pid,
        "period": "1d",
        "reason": "griefing",
        "duration": timedelta(days=1),
        "player": PLAYER,
    }

    first = await perform_ban(bot, **ban)
    second = await perform_ban(bot, **ban)

    assert first.startswith("Banned `Target`")
    assert second == "This ban was already processed recently."
    (row,) = db["bans"].docs
    assert (row["pid"], row["admin_name"], row["admin_discord_id"]) == (
        42,
        "Moderator",
        "123",
    )
    (audit,) = db["moderation_audit"].docs
    _assert_audit(
        audit,
        action="BAN",
        extra={"ban_id": "1"},
        expires_at=NOW + timedelta(days=1),
        duration_ms=86_400_000,
        request_id=audit["origin"]["request_id"],  # generated per action
    )
    assert await game.kicks() == [("uuid-42", "1.2.3.4")]
    assert len(channel.embeds) == 1


@dataclass
class _Role:
    id: int


@dataclass
class _User:
    id: int
    display_name: str
    roles: list[_Role]


@dataclass
class _ResponseCollector:
    messages: list[tuple[str, bool]] = field(default_factory=list)

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        self.messages.append((text, ephemeral))


@dataclass
class _CmdInteraction:
    id: int
    user: _User
    response: _ResponseCollector = field(default_factory=_ResponseCollector)


@dataclass
class _ErrorResponse:
    done: bool = False
    sent: list[tuple[str, bool]] = field(default_factory=list)

    def is_done(self) -> bool:
        return self.done

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        self.sent.append((text, ephemeral))


@dataclass
class _ErrorFollowup:
    sent: list[tuple[str, bool]] = field(default_factory=list)

    async def send(self, text: str, *, ephemeral: bool = False) -> None:
        self.sent.append((text, ephemeral))


@dataclass
class _ErrorInteraction:
    response: _ErrorResponse = field(default_factory=_ErrorResponse)
    followup: _ErrorFollowup = field(default_factory=_ErrorFollowup)
    user: Any = field(default_factory=lambda: SimpleNamespace(id=1, display_name="mod"))
    command: Any = field(default_factory=lambda: SimpleNamespace(name="ban"))
    namespace: Any = field(default_factory=lambda: SimpleNamespace(player_id=1))


@pytest.mark.asyncio
async def testhandle_app_command_error_sends_check_failure_message() -> None:
    bot = make_bot(settings=make_settings(discord_error_log_channel_id=None))
    interaction = _ErrorInteraction()

    await XCoreDiscordBot.handle_app_command_error(
        bot,
        interaction,
        app_commands.CheckFailure("Missing role"),
    )

    assert interaction.response.sent == [("Missing role", True)]
    assert interaction.followup.sent == []


@pytest.mark.asyncio
async def testhandle_app_command_error_sends_generic_message_on_unexpected_error() -> (
    None
):
    bot = make_bot(settings=make_settings(discord_error_log_channel_id=None))
    interaction = _ErrorInteraction(response=_ErrorResponse(done=True))

    class _UnexpectedError(app_commands.AppCommandError):
        pass

    await XCoreDiscordBot.handle_app_command_error(
        bot,
        interaction,
        _UnexpectedError("boom"),
    )

    assert interaction.response.sent == []
    assert len(interaction.followup.sent) == 1
    sent_text, is_ephemeral = interaction.followup.sent[0]
    assert is_ephemeral is True
    assert sent_text.startswith("Command failed (ref: ")
    assert sent_text.endswith("). Report this to an admin if the issue persists.")


@pytest.mark.asyncio
async def testhandle_app_command_error_posts_embed_to_error_channel() -> None:
    class _Channel:
        def __init__(self) -> None:
            self.embeds: list[Any] = []

        async def send(self, *, embed: Any) -> None:
            self.embeds.append(embed)

    class _UnexpectedError(app_commands.AppCommandError):
        pass

    bot = make_bot(settings=make_settings(discord_error_log_channel_id=999))
    channel = _Channel()

    async def _resolve(channel_id: int, *, context: str):
        assert channel_id == 999
        assert context == "error logs"
        return channel

    bot.resolve_channel = _resolve

    interaction = _ErrorInteraction(
        command=SimpleNamespace(name="mute"),
        namespace=SimpleNamespace(player_id=123, reason="spam"),
    )

    await XCoreDiscordBot.handle_app_command_error(
        bot,
        interaction,
        _UnexpectedError("boom"),
    )

    assert len(channel.embeds) == 1
    embed = channel.embeds[0]
    assert embed.title == "Application Command Error"
    fields = {field.name: field.value for field in embed.fields}
    assert len(fields["Error ID"]) == 8
    assert fields["Command"].startswith("/mute")
    assert "player_id=123" in fields["Command"]
    assert "reason=spam" in fields["Command"]
    assert fields["User"] == "mod (id=1)"


@pytest.mark.asyncio
async def test_pardon_publishes_pardon_event() -> None:
    bot, db = await real_bot()
    game = GameServer(bot)
    await db["players"].insert_one(
        {"_id": 1, "pid": 7, "uuid": "u-7", "nickname": "Target"}
    )
    interaction = _CmdInteraction(
        id=778,
        user=_User(id=1, display_name="mod", roles=[_Role(id=10)]),
    )

    await cmd_pardon(bot, interaction, 7)

    assert await game.pardons() == ["u-7"]
    assert interaction.response.messages == [("Pardoned `Target`", False)]


@pytest.mark.asyncio
async def test_run_consumer_forever_restarts_after_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bus = await connected_bus()

    async def fast_sleep(_seconds: float) -> None:
        return None

    monkeypatch.setattr("xcore_discord_bot.runtime_consumers.asyncio.sleep", fast_sleep)

    calls = {"n": 0}

    async def consume(_callback):
        calls["n"] += 1
        if calls["n"] == 1:
            raise RuntimeError("redis down")
        raise asyncio.CancelledError()

    async def callback() -> None:
        return None

    with redis_connections() as opened, pytest.raises(asyncio.CancelledError):
        await run_consumer_forever(bus, "Test", consume, callback)

    assert calls["n"] == 2
    # The consumer resumed on a new connection.
    assert len(opened) == 1
    assert bus.redis is opened[0]


@pytest.mark.asyncio
async def test_run_consumer_forever_retries_when_reconnect_fails(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bus = await connected_bus()

    sleep_calls: list[float] = []

    async def fast_sleep(seconds: float) -> None:
        sleep_calls.append(seconds)

    monkeypatch.setattr("xcore_discord_bot.runtime_consumers.asyncio.sleep", fast_sleep)
    monkeypatch.setattr("xcore_discord_bot.retry.asyncio.sleep", fast_sleep)

    calls = {"n": 0}

    async def consume(_callback):
        calls["n"] += 1
        if calls["n"] == 1:
            raise RuntimeError("redis down")
        raise asyncio.CancelledError()

    async def callback() -> None:
        return None

    with redis_connections(down=2) as opened, pytest.raises(asyncio.CancelledError):
        await run_consumer_forever(bus, "Test", consume, callback)

    assert calls["n"] == 2
    # Redis stayed down through both attempts; the consumer waited and ran again.
    assert len(opened) == 2
    assert sleep_calls == [2, 2.0, 2]


@dataclass
class _MsgAuthor:
    display_name: str
    bot: bool = False


@dataclass
class _MsgChannel:
    id: int


@dataclass
class _DiscordMessage:
    id: int
    author: _MsgAuthor
    channel: _MsgChannel
    content: str


@pytest.mark.asyncio
async def test_on_message_routes_to_server_registry_mapping() -> None:
    bot, _ = await real_bot()
    bot.container.servers.update_server("mini-pvp", 333, 1, 10, "v1")
    bot.container.servers.update_server("hexed", 444, 1, 10, "v1")

    message = _DiscordMessage(
        id=44,
        author=_MsgAuthor(display_name="mod", bot=False),
        channel=_MsgChannel(id=333),
        content="hello",
    )
    await XCoreDiscordBot.on_message(bot, message)

    (command,) = await published(bot.bus, "xcore:cmd:discord-message:mini-pvp")
    assert (command["authorName"], command["message"]) == ("mod", "hello")
    assert await published(bot.bus, "xcore:cmd:discord-message:hexed") == []
    # Keyed by the Discord message, so the game server drops a redelivery.
    ((_, fields),) = await bot.bus.redis.xrange("xcore:cmd:discord-message:mini-pvp")
    assert fields["idempotency_key"] == "discord.message:44"
