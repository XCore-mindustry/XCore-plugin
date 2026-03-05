from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from datetime import timedelta
from datetime import datetime, timezone
from types import SimpleNamespace
from typing import Any

import pytest
from bson.datetime_ms import DatetimeMS
from discord import app_commands

from xcore_discord_bot.bot import (
    XCoreDiscordBot,
    parse_duration,
    strip_mindustry_colors,
)
from xcore_discord_bot.registry import ServerInfo, server_registry


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


def test_build_stats_title_truncates_to_discord_limit() -> None:
    nickname = "n" * 240
    custom_nickname = "c" * 240

    title = XCoreDiscordBot._build_stats_title(nickname, custom_nickname)

    assert len(title) == 256
    assert title.endswith("...")


def test_build_stats_title_without_custom_nickname() -> None:
    title = XCoreDiscordBot._build_stats_title("PlayerOne", "")
    assert title == "Player Stats • PlayerOne"


def test_format_ban_expire_date_for_datetime() -> None:
    expire = datetime(2026, 3, 4, 12, 0, tzinfo=timezone.utc)

    formatted = XCoreDiscordBot._format_ban_expire_date(expire)

    assert "<t:" in formatted


def test_format_ban_expire_date_for_invalid_value() -> None:
    assert XCoreDiscordBot._format_ban_expire_date({"bad": "value"}) == "Unknown"


def test_format_ban_expire_date_for_datetime_ms() -> None:
    dtms = DatetimeMS(1762296000000)
    formatted = XCoreDiscordBot._format_ban_expire_date(dtms)
    assert "<t:" in formatted


def test_format_ban_expire_date_for_far_future_millis() -> None:
    formatted = XCoreDiscordBot._format_ban_expire_date(315537897600000)
    assert formatted == "After year 9999"


def test_sort_live_servers_by_players_then_name() -> None:
    servers = [
        ServerInfo("b", 1, 5, 30, "1", None, None, 0.0),
        ServerInfo("a", 2, 5, 30, "1", None, None, 0.0),
        ServerInfo("c", 3, 2, 30, "1", None, None, 0.0),
    ]

    sorted_servers = XCoreDiscordBot._sort_live_servers(servers, "players")

    assert [srv.name for srv in sorted_servers] == ["a", "b", "c"]


def test_sort_live_servers_by_name() -> None:
    servers = [
        ServerInfo("beta", 1, 5, 30, "1", None, None, 0.0),
        ServerInfo("Alpha", 2, 20, 30, "1", None, None, 0.0),
    ]

    sorted_servers = XCoreDiscordBot._sort_live_servers(servers, "name")

    assert [srv.name for srv in sorted_servers] == ["Alpha", "beta"]


def test_build_servers_embed_includes_sort_mode_footer() -> None:
    servers = [
        ServerInfo("alpha", 123, 3, 30, "155.4", None, None, 0.0),
    ]

    embed = XCoreDiscordBot._build_servers_embed(servers, sort_mode="players")

    assert embed.footer.text == "Sort: players • Servers: 1 • Players online: 3"


def test_build_servers_embed_includes_address_when_available() -> None:
    servers = [
        ServerInfo("alpha", 123, 3, 30, "155.4", "play.example.com", 6567, 0.0),
    ]

    embed = XCoreDiscordBot._build_servers_embed(servers, sort_mode="players")

    assert len(embed.fields) == 1
    assert "Address: `play.example.com:6567`" in (embed.fields[0].value or "")


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
    def response(self) -> "_FakeInteraction":
        return self


class _FakeBus:
    def __init__(self, should_claim: bool) -> None:
        self.should_claim = should_claim

    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:
        return self.should_claim


@pytest.mark.asyncio
async def test_claim_mutation_success() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot._bus = _FakeBus(should_claim=True)
    interaction = _FakeInteraction()

    claimed = await XCoreDiscordBot._claim_mutation(
        bot,
        interaction,
        operation="ban",
        scope="1:1d:test",
    )

    assert claimed is True
    assert interaction.replies == []
    assert interaction.ephemeral_replies == []


@pytest.mark.asyncio
async def test_claim_mutation_duplicate() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot._bus = _FakeBus(should_claim=False)
    interaction = _FakeInteraction()

    claimed = await XCoreDiscordBot._claim_mutation(
        bot,
        interaction,
        operation="ban",
        scope="1:1d:test",
    )

    assert claimed is False
    assert len(interaction.ephemeral_replies) == 1
    assert "Duplicate command ignored" in interaction.ephemeral_replies[0]


class _BanStore:
    def __init__(self) -> None:
        self.bans: list[dict[str, object]] = []

    def now_utc(self) -> datetime:
        return datetime(2026, 1, 1, tzinfo=timezone.utc)

    async def upsert_ban(
        self,
        *,
        uuid: str,
        ip: str | None,
        name: str,
        admin_name: str,
        reason: str,
        expire_date: datetime,
    ) -> None:
        self.bans.append(
            {
                "uuid": uuid,
                "ip": ip,
                "name": name,
                "admin_name": admin_name,
                "reason": reason,
                "expire_date": expire_date,
            }
        )


class _BanBus:
    def __init__(self) -> None:
        self._keys: set[str] = set()
        self.kicks: list[tuple[str, str | None]] = []

    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:
        if key in self._keys:
            return False
        self._keys.add(key)
        return True

    async def publish_kick_banned(self, uuid_value: str, ip: str | None) -> None:
        self.kicks.append((uuid_value, ip))


@pytest.mark.asyncio
async def test_perform_ban_idempotent_by_entity_key() -> None:
    bot = object.__new__(XCoreDiscordBot)
    store = _BanStore()
    bus = _BanBus()
    bot.__dict__["_store"] = store
    bot.__dict__["_bus"] = bus

    calls: list[dict[str, object]] = []

    async def fake_post_ban_log(
        *,
        pid: int,
        name: str,
        admin_name: str,
        reason: str,
        expire: datetime,
    ) -> None:
        calls.append(
            {
                "pid": pid,
                "name": name,
                "admin_name": admin_name,
                "reason": reason,
                "expire": expire,
            }
        )

    bot.__dict__["_post_ban_log"] = fake_post_ban_log

    player = {"uuid": "u-1", "ip": "1.2.3.4", "nickname": "Nick"}

    first = await XCoreDiscordBot._perform_ban(
        bot,
        actor_name="admin",
        player_id=10,
        period="1d",
        reason="r",
        duration=timedelta(days=1),
        player=player,
    )
    second = await XCoreDiscordBot._perform_ban(
        bot,
        actor_name="admin",
        player_id=10,
        period="1d",
        reason="r",
        duration=timedelta(days=1),
        player=player,
    )

    assert first.startswith("Banned `Nick`")
    assert second == "This ban was already processed recently."
    assert len(store.bans) == 1
    assert bus.kicks == [("u-1", "1.2.3.4")]
    assert len(calls) == 1


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


class _ResetPasswordStore:
    async def find_player_by_pid(self, pid: int) -> dict[str, object] | None:
        if pid != 7:
            return None
        return {"pid": 7, "uuid": "u-7", "nickname": "Target"}

    async def reset_password(self, uuid: str) -> bool:
        return uuid == "u-7"


class _ResetPasswordBus:
    def __init__(self) -> None:
        self.reload_calls = 0
        self.pardon_calls: list[str] = []

    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:  # noqa: ARG002
        return True

    async def publish_reload_player_data_cache(self) -> None:
        self.reload_calls += 1

    async def publish_pardon_player(self, uuid_value: str) -> None:
        self.pardon_calls.append(uuid_value)


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
async def test_handle_app_command_error_sends_check_failure_message() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(discord_error_log_channel_id=None)
    interaction = _ErrorInteraction()

    await XCoreDiscordBot._handle_app_command_error(
        bot,
        interaction,
        app_commands.CheckFailure("Missing role"),
    )

    assert interaction.response.sent == [("Missing role", True)]
    assert interaction.followup.sent == []


@pytest.mark.asyncio
async def test_handle_app_command_error_sends_generic_message_on_unexpected_error() -> (
    None
):
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(discord_error_log_channel_id=None)
    interaction = _ErrorInteraction(response=_ErrorResponse(done=True))

    class _UnexpectedError(app_commands.AppCommandError):
        pass

    await XCoreDiscordBot._handle_app_command_error(
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
async def test_handle_app_command_error_posts_embed_to_error_channel() -> None:
    class _Channel:
        def __init__(self) -> None:
            self.embeds: list[Any] = []

        async def send(self, *, embed: Any) -> None:
            self.embeds.append(embed)

    class _UnexpectedError(app_commands.AppCommandError):
        pass

    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(discord_error_log_channel_id=999)
    channel = _Channel()

    async def _resolve(channel_id: int, *, context: str):
        assert channel_id == 999
        assert context == "error logs"
        return channel

    bot.__dict__["_resolve_messageable_channel"] = _resolve

    interaction = _ErrorInteraction(
        command=SimpleNamespace(name="mute"),
        namespace=SimpleNamespace(player_id=123, reason="spam"),
    )

    await XCoreDiscordBot._handle_app_command_error(
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
async def test_reset_password_publishes_reload_cache_event() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bus = _ResetPasswordBus()
    bot.__dict__["_bus"] = bus
    bot.__dict__["_store"] = _ResetPasswordStore()
    bot.__dict__["_settings"] = SimpleNamespace(
        discord_general_admin_role_id=10,
        discord_admin_role_id=20,
    )

    interaction = _CmdInteraction(
        id=777,
        user=_User(id=1, display_name="mod", roles=[_Role(id=10)]),
    )

    await XCoreDiscordBot._cmd_reset_password(bot, interaction, 7)

    assert bus.reload_calls == 1
    assert interaction.response.messages
    assert interaction.response.messages[0][0].startswith("Password reset for")


@pytest.mark.asyncio
async def test_pardon_publishes_pardon_event() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bus = _ResetPasswordBus()
    bot.__dict__["_bus"] = bus
    bot.__dict__["_store"] = _ResetPasswordStore()
    bot.__dict__["_settings"] = SimpleNamespace(
        discord_general_admin_role_id=10,
        discord_admin_role_id=20,
    )

    interaction = _CmdInteraction(
        id=778,
        user=_User(id=1, display_name="mod", roles=[_Role(id=10)]),
    )

    await XCoreDiscordBot._cmd_pardon(bot, interaction, 7)

    assert bus.pardon_calls == ["u-7"]
    assert interaction.response.messages
    assert interaction.response.messages[0][0].startswith("Pardoned `Target`")


class _ReconnectBus:
    def __init__(self) -> None:
        self.reconnected = 0

    async def reconnect(self) -> None:
        self.reconnected += 1


@pytest.mark.asyncio
async def test_run_consumer_forever_restarts_after_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bot = object.__new__(XCoreDiscordBot)
    bus = _ReconnectBus()
    bot.__dict__["_bus"] = bus

    async def fast_sleep(_seconds: float) -> None:
        return None

    monkeypatch.setattr("xcore_discord_bot.bot.asyncio.sleep", fast_sleep)

    calls = {"n": 0}

    async def consume(_callback):
        calls["n"] += 1
        if calls["n"] == 1:
            raise RuntimeError("redis down")
        raise asyncio.CancelledError()

    async def callback() -> None:
        return None

    with pytest.raises(asyncio.CancelledError):
        await XCoreDiscordBot._run_consumer_forever(bot, "Test", consume, callback)

    assert calls["n"] == 2
    assert bus.reconnected == 1


class _MessageBus:
    def __init__(self) -> None:
        self.calls: list[dict[str, Any]] = []

    async def publish_discord_message(
        self,
        server: str | None,
        author_name: str,
        message: str,
        source_message_id: str | None = None,
    ) -> None:
        self.calls.append(
            {
                "server": server,
                "author_name": author_name,
                "message": message,
                "source_message_id": source_message_id,
            }
        )


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
    with server_registry._lock:
        server_registry._servers.clear()
    server_registry.update_server("mini-pvp", 333, 1, 10, "v1")

    bot = object.__new__(XCoreDiscordBot)
    bus = _MessageBus()
    bot.__dict__["_bus"] = bus

    message = _DiscordMessage(
        id=44,
        author=_MsgAuthor(display_name="mod", bot=False),
        channel=_MsgChannel(id=333),
        content="hello",
    )
    await XCoreDiscordBot.on_message(bot, message)

    assert bus.calls == [
        {
            "server": "mini-pvp",
            "author_name": "mod",
            "message": "hello",
            "source_message_id": "44",
        }
    ]
