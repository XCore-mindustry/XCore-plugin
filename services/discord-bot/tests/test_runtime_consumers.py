from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any
from unittest.mock import AsyncMock

import pytest
from backends import consumed, emit, real_bot
from bot_fakes import make_settings
from xcore_protocol.generated.shared import (
    ActorRefV1,
    ExpirationInfoV1,
    PlayerRefV1,
    VoteKickParticipantV1,
)

from xcore_discord_bot.contracts import (
    ChatMessageV1,
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
    ModerationVoteKickCreatedV1,
    PlayerJoinLeaveV1,
    ServerActionV1,
)
from xcore_discord_bot.runtime_consumers import (
    consume_bans,
    consume_game_chat,
    consume_join_leave,
    consume_mutes,
    consume_server_actions,
    consume_vote_kicks,
)


@dataclass
class _Message:
    content: str | None = None
    view: Any = None


@dataclass
class _Channel:
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send(self, content: str | None = None, *, view: Any = None) -> _Message:
        self.sent.append({"content": content, "view": view})
        return _Message(content=content, view=view)


CHAT = "xcore:evt:chat:message"
JOIN_LEAVE = "xcore:evt:player:joinleave"
SERVER_ACTION = "xcore:evt:server:action"
BANS = "xcore:evt:moderation:ban"
MUTES = "xcore:evt:moderation:mute"
VOTE_KICKS = "xcore:evt:moderation:votekick"
BAN = ModerationBanCreatedV1(
    target=PlayerRefV1(playerUuid="uuid-1", playerName="Target"),
    actor=ActorRefV1(actorName="Admin", actorDiscordId="123"),
    reason="Rule 1",
    expiration=ExpirationInfoV1(expiresAt="bad-date"),
)
MUTE = ModerationMuteCreatedV1(
    target=PlayerRefV1(playerUuid="uuid-1", playerName="Target"),
    actor=ActorRefV1(actorName="Admin", actorDiscordId="456"),
    reason="Rule 1",
    expiration=ExpirationInfoV1(expiresAt="bad-date"),
)
NOW = datetime(2026, 1, 1, tzinfo=UTC)
TARGET = {"_id": 1, "pid": 42, "uuid": "uuid-1", "nickname": "Target"}


async def _server_bot(channel_id: int | None):
    """A bot with mini-pvp bound to ``channel_id``; the Discord channel is stubbed."""
    bot, _ = await real_bot()
    if channel_id is not None:
        bot.container.servers.update_server("mini-pvp", channel_id, 0, 20, "test")
    channel = _Channel()
    bot.resolve_channel = AsyncMock(return_value=channel)
    return bot, channel


async def _log_bot(**channels: int):
    bot, db = await real_bot(settings=make_settings(**channels))
    await db["players"].insert_one(dict(TARGET))
    bot.store.now_utc = lambda: NOW
    return bot


def _vote_kick(*, starter_discord_id: str | None) -> ModerationVoteKickCreatedV1:
    return ModerationVoteKickCreatedV1(
        target=PlayerRefV1(playerUuid="uuid-target", playerPid=42, playerName="Target"),
        actor=ActorRefV1(actorName="Starter", actorDiscordId="123456"),
        reason="griefing",
        votesFor=(
            VoteKickParticipantV1(
                playerName="Starter", playerPid=7, discordId=starter_discord_id
            ),
        ),
        votesAgainst=(
            VoteKickParticipantV1(playerName="Voter2", playerPid=8, discordId="654321"),
        ),
    )


@pytest.mark.asyncio
async def test_consume_game_chat_sanitizes_message() -> None:
    bot, channel = await _server_bot(555)
    await emit(
        bot.bus,
        CHAT,
        ChatMessageV1(
            authorName="[#ecececff]mod`name",
            message="hello `[red]world`",
            server="mini-pvp",
        ),
    )

    await consumed(bot.bus, consume_game_chat(bot), CHAT)

    bot.resolve_channel.assert_awaited_once_with(555, context="game chat")
    assert channel.sent == [{"content": "`modname: hello world`", "view": None}]


@pytest.mark.asyncio
async def test_consume_game_chat_skips_when_channel_missing() -> None:
    bot, channel = await _server_bot(None)
    await emit(
        bot.bus, CHAT, ChatMessageV1(authorName="a", message="b", server="mini-pvp")
    )

    await consumed(bot.bus, consume_game_chat(bot), CHAT)

    bot.resolve_channel.assert_not_awaited()
    assert channel.sent == []


@pytest.mark.asyncio
async def test_consume_join_leave_formats_generated_payload() -> None:
    bot, channel = await _server_bot(556)
    await emit(
        bot.bus,
        JOIN_LEAVE,
        PlayerJoinLeaveV1(playerName="mod`name", server="mini-pvp", joined=True),
    )

    await consumed(bot.bus, consume_join_leave(bot), JOIN_LEAVE)

    bot.resolve_channel.assert_awaited_once_with(556, context="join/leave")
    assert channel.sent == [{"content": "`modname` joined", "view": None}]


@pytest.mark.asyncio
async def test_consume_join_leave_skips_when_channel_missing() -> None:
    bot, channel = await _server_bot(None)
    await emit(
        bot.bus,
        JOIN_LEAVE,
        PlayerJoinLeaveV1(playerName="a", server="mini-pvp", joined=False),
    )

    await consumed(bot.bus, consume_join_leave(bot), JOIN_LEAVE)

    bot.resolve_channel.assert_not_awaited()
    assert channel.sent == []


@pytest.mark.asyncio
async def test_consume_server_actions_formats_generated_payload() -> None:
    bot, channel = await _server_bot(557)
    await emit(
        bot.bus,
        SERVER_ACTION,
        ServerActionV1(message="Server `started`", server="mini-pvp"),
    )

    await consumed(bot.bus, consume_server_actions(bot), SERVER_ACTION)

    bot.resolve_channel.assert_awaited_once_with(557, context="server action")
    assert channel.sent == [{"content": "Server started", "view": None}]


@pytest.mark.asyncio
async def test_consume_server_actions_skips_when_channel_missing() -> None:
    bot, channel = await _server_bot(None)
    await emit(
        bot.bus, SERVER_ACTION, ServerActionV1(message="started", server="mini-pvp")
    )

    await consumed(bot.bus, consume_server_actions(bot), SERVER_ACTION)

    bot.resolve_channel.assert_not_awaited()
    assert channel.sent == []


@pytest.mark.asyncio
async def test_consume_bans_uses_now_when_expire_date_invalid(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}

    async def fake_post_ban_log(
        bot,
        *,
        pid: int,
        name: str,
        admin_name: str,
        admin_discord_id: str | None,
        reason: str,
        expire,
    ):
        captured.update(
            {
                "bot": bot,
                "pid": pid,
                "name": name,
                "admin_name": admin_name,
                "admin_discord_id": admin_discord_id,
                "reason": reason,
                "expire": expire,
            }
        )

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_ban_log", fake_post_ban_log
    )

    bot = await _log_bot(discord_bans_channel_id=777)
    await emit(bot.bus, BANS, BAN)

    await consumed(bot.bus, consume_bans(bot), BANS)

    assert captured["pid"] == 42
    assert captured["name"] == "Target"
    assert captured["admin_name"] == "Admin"
    assert captured["admin_discord_id"] == "123"
    assert captured["reason"] == "Rule 1"
    assert captured["expire"] == datetime(2026, 1, 1, tzinfo=UTC)


@pytest.mark.asyncio
async def test_consume_bans_returns_early_when_ban_logs_disabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    called = False

    async def fake_post_ban_log(*args, **kwargs):
        nonlocal called
        called = True

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_ban_log", fake_post_ban_log
    )

    bot = await _log_bot(discord_bans_channel_id=0)
    await emit(bot.bus, BANS, BAN)

    await consumed(bot.bus, consume_bans(bot), BANS)

    assert called is False


@pytest.mark.asyncio
async def test_consume_mutes_uses_now_when_expire_date_invalid(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}

    async def fake_post_mute_log(
        bot,
        *,
        pid: int,
        name: str,
        admin_name: str,
        admin_discord_id: str | None,
        reason: str,
        expire,
    ):
        captured.update(
            {
                "bot": bot,
                "pid": pid,
                "name": name,
                "admin_name": admin_name,
                "admin_discord_id": admin_discord_id,
                "reason": reason,
                "expire": expire,
            }
        )

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_mute_log", fake_post_mute_log
    )

    bot = await _log_bot(discord_mutes_channel_id=778)
    await emit(bot.bus, MUTES, MUTE)

    await consumed(bot.bus, consume_mutes(bot), MUTES)

    assert captured["pid"] == 42
    assert captured["name"] == "Target"
    assert captured["admin_name"] == "Admin"
    assert captured["admin_discord_id"] == "456"
    assert captured["reason"] == "Rule 1"
    assert captured["expire"] == datetime(2026, 1, 1, tzinfo=UTC)


@pytest.mark.asyncio
async def test_consume_mutes_returns_early_when_mute_logs_disabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    called = False

    async def fake_post_mute_log(*args, **kwargs):
        nonlocal called
        called = True

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_mute_log", fake_post_mute_log
    )

    bot = await _log_bot(discord_mutes_channel_id=0)
    await emit(bot.bus, MUTES, MUTE)

    await consumed(bot.bus, consume_mutes(bot), MUTES)

    assert called is False


@pytest.mark.asyncio
async def test_consume_vote_kicks_dispatches_vote_payload_to_log_handler(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}

    async def fake_post_vote_kick_log(
        bot,
        *,
        target_name: str,
        target_pid: int | None,
        starter_name: str,
        starter_pid: int | None,
        starter_discord_id: str | None,
        reason: str,
        votes_for,
        votes_against,
    ):
        captured.update(
            {
                "bot": bot,
                "target_name": target_name,
                "target_pid": target_pid,
                "starter_name": starter_name,
                "starter_pid": starter_pid,
                "starter_discord_id": starter_discord_id,
                "reason": reason,
                "votes_for": votes_for,
                "votes_against": votes_against,
            }
        )

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_vote_kick_log",
        fake_post_vote_kick_log,
    )

    bot = await _log_bot(discord_votekicks_channel_id=779)
    await emit(bot.bus, VOTE_KICKS, _vote_kick(starter_discord_id="123456"))

    await consumed(bot.bus, consume_vote_kicks(bot), VOTE_KICKS)

    assert captured["target_name"] == "Target"
    assert captured["target_pid"] == 42
    assert captured["starter_name"] == "Starter"
    assert captured["starter_pid"] == 7
    assert captured["starter_discord_id"] == "123456"
    assert captured["reason"] == "griefing"
    assert [item.playerName for item in captured["votes_for"]] == ["Starter"]
    assert [item.playerName for item in captured["votes_against"]] == ["Voter2"]


@pytest.mark.asyncio
async def test_consume_vote_kicks_uses_participant_name_fallback_for_starter_pid(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}

    async def fake_post_vote_kick_log(bot, **kwargs):
        del bot
        captured.update(kwargs)

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_vote_kick_log",
        fake_post_vote_kick_log,
    )

    bot = await _log_bot(discord_votekicks_channel_id=779)
    # The starter's vote carries no Discord id, so it is matched by name.
    await emit(bot.bus, VOTE_KICKS, _vote_kick(starter_discord_id=None))

    await consumed(bot.bus, consume_vote_kicks(bot), VOTE_KICKS)

    assert captured["starter_pid"] == 7


@pytest.mark.asyncio
async def test_consume_vote_kicks_returns_early_when_logs_disabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    called = False

    async def fake_post_vote_kick_log(*args, **kwargs):
        nonlocal called
        called = True

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_vote_kick_log",
        fake_post_vote_kick_log,
    )

    bot = await _log_bot(discord_votekicks_channel_id=0)
    await emit(bot.bus, VOTE_KICKS, _vote_kick(starter_discord_id="123456"))

    await consumed(bot.bus, consume_vote_kicks(bot), VOTE_KICKS)

    assert called is False
