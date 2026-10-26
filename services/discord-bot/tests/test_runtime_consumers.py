from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

import pytest
from bot_fakes import make_bot, make_settings
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
from xcore_discord_bot.dto import PlayerRecord
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


class _GameChatBus:
    def __init__(self, *, channel_id: int | None, channel: _Channel | None) -> None:
        self.channel_id = channel_id
        self.channel = channel

    async def resolve_channel(
        self,
        channel_id: int,
        *,
        context: str,
    ) -> _Channel | None:
        assert channel_id == 555
        assert context == "game chat"
        return self.channel

    async def consume_game_chat(self, callback) -> None:
        await callback(
            ChatMessageV1(
                authorName="[#ecececff]mod`name",
                message="hello `[red]world`",
                server="mini-pvp",
            )
        )
        raise asyncio.CancelledError()

    async def reconnect(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid):
        return None


def _GameChatBot(*, channel_id, channel):
    backend = _GameChatBus(channel_id=channel_id, channel=channel)
    bot = make_bot(store=backend, bus=backend)
    if channel_id is not None:
        bot.container.servers.update_server("mini-pvp", channel_id, 0, 20, "test")
    bot.container.chat_bus = backend
    bot.resolve_channel = backend.resolve_channel
    return bot


class _JoinLeaveBus:
    def __init__(self, *, channel_id: int | None, channel: _Channel | None) -> None:
        self.channel_id = channel_id
        self.channel = channel

    async def resolve_channel(
        self,
        channel_id: int,
        *,
        context: str,
    ) -> _Channel | None:
        assert channel_id == 556
        assert context == "join/leave"
        return self.channel

    async def consume_player_join_leave(self, callback) -> None:
        await callback(
            PlayerJoinLeaveV1(
                playerName="mod`name",
                server="mini-pvp",
                joined=True,
            )
        )
        raise asyncio.CancelledError()

    async def reconnect(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid):
        return None


def _JoinLeaveBot(*, channel_id, channel):
    backend = _JoinLeaveBus(channel_id=channel_id, channel=channel)
    bot = make_bot(store=backend, bus=backend)
    if channel_id is not None:
        bot.container.servers.update_server("mini-pvp", channel_id, 0, 20, "test")
    bot.container.chat_bus = backend
    bot.resolve_channel = backend.resolve_channel
    return bot


class _ServerActionBus:
    def __init__(self, *, channel_id: int | None, channel: _Channel | None) -> None:
        self.channel_id = channel_id
        self.channel = channel

    async def resolve_channel(
        self,
        channel_id: int,
        *,
        context: str,
    ) -> _Channel | None:
        assert channel_id == 557
        assert context == "server action"
        return self.channel

    async def consume_server_actions(self, callback) -> None:
        await callback(
            ServerActionV1(
                message="Server `started`",
                server="mini-pvp",
            )
        )
        raise asyncio.CancelledError()

    async def reconnect(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid):
        return None


def _ServerActionBot(*, channel_id, channel):
    backend = _ServerActionBus(channel_id=channel_id, channel=channel)
    bot = make_bot(store=backend, bus=backend)
    if channel_id is not None:
        bot.container.servers.update_server("mini-pvp", channel_id, 0, 20, "test")
    bot.container.chat_bus = backend
    bot.resolve_channel = backend.resolve_channel
    return bot


class _BanBus:
    def __init__(self, *, bans_channel_id: int, player: PlayerRecord | None) -> None:
        self.bans_channel_id = bans_channel_id
        self.player = player
        self.now_calls = 0

    async def consume_bans(self, callback) -> None:
        await callback(
            ModerationBanCreatedV1(
                target=PlayerRefV1(playerUuid="uuid-1", playerName="Target"),
                actor=ActorRefV1(actorName="Admin", actorDiscordId="123"),
                reason="Rule 1",
                expiration=ExpirationInfoV1(expiresAt="bad-date"),
            )
        )
        raise asyncio.CancelledError()

    async def reconnect(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        assert uuid == "uuid-1"
        return self.player

    def now_utc(self) -> datetime:
        self.now_calls += 1
        return datetime(2026, 1, 1, tzinfo=UTC)

    async def run_ban_log_once(self, event, callback):
        await callback()


def _BanBot(**kwargs):
    backend = _BanBus(**kwargs)
    return make_bot(
        store=backend,
        bus=backend,
        settings=make_settings(discord_bans_channel_id=backend.bans_channel_id),
    )


class _MuteBus:
    def __init__(self, *, mutes_channel_id: int, player: PlayerRecord | None) -> None:
        self.mutes_channel_id = mutes_channel_id
        self.player = player
        self.now_calls = 0

    async def consume_mutes(self, callback) -> None:
        await callback(
            ModerationMuteCreatedV1(
                target=PlayerRefV1(playerUuid="uuid-1", playerName="Target"),
                actor=ActorRefV1(actorName="Admin", actorDiscordId="456"),
                reason="Rule 1",
                expiration=ExpirationInfoV1(expiresAt="bad-date"),
            )
        )
        raise asyncio.CancelledError()

    async def reconnect(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        assert uuid == "uuid-1"
        return self.player

    def now_utc(self) -> datetime:
        self.now_calls += 1
        return datetime(2026, 1, 1, tzinfo=UTC)


def _MuteBot(**kwargs):
    backend = _MuteBus(**kwargs)
    return make_bot(
        store=backend,
        bus=backend,
        settings=make_settings(discord_mutes_channel_id=backend.mutes_channel_id),
    )


class _VoteKickBus:
    def __init__(self, *, votekicks_channel_id: int) -> None:
        self.votekicks_channel_id = votekicks_channel_id

    async def consume_vote_kicks(self, callback) -> None:
        await callback(
            ModerationVoteKickCreatedV1(
                target=PlayerRefV1(
                    playerUuid="uuid-target",
                    playerPid=42,
                    playerName="Target",
                ),
                actor=ActorRefV1(actorName="Starter", actorDiscordId="123456"),
                reason="griefing",
                votesFor=(
                    VoteKickParticipantV1(
                        playerName="Starter", playerPid=7, discordId="123456"
                    ),
                ),
                votesAgainst=(
                    VoteKickParticipantV1(
                        playerName="Voter2", playerPid=8, discordId="654321"
                    ),
                ),
            )
        )
        raise asyncio.CancelledError()

    async def reconnect(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid):
        return None


def _VoteKickBot(**kwargs):
    backend = _VoteKickBus(**kwargs)
    return make_bot(
        store=backend,
        bus=backend,
        settings=make_settings(
            discord_votekicks_channel_id=backend.votekicks_channel_id
        ),
    )


@pytest.mark.asyncio
async def test_consume_game_chat_sanitizes_message() -> None:
    channel = _Channel()
    bot = _GameChatBot(channel_id=555, channel=channel)

    with pytest.raises(asyncio.CancelledError):
        await consume_game_chat(bot)

    assert channel.sent == [{"content": "`modname: hello world`", "view": None}]


@pytest.mark.asyncio
async def test_consume_game_chat_skips_when_channel_missing() -> None:
    bot = _GameChatBot(channel_id=None, channel=None)

    with pytest.raises(asyncio.CancelledError):
        await consume_game_chat(bot)


@pytest.mark.asyncio
async def test_consume_join_leave_formats_generated_payload() -> None:
    channel = _Channel()
    bot = _JoinLeaveBot(channel_id=556, channel=channel)

    with pytest.raises(asyncio.CancelledError):
        await consume_join_leave(bot)

    assert channel.sent == [{"content": "`modname` joined", "view": None}]


@pytest.mark.asyncio
async def test_consume_join_leave_skips_when_channel_missing() -> None:
    bot = _JoinLeaveBot(channel_id=None, channel=None)

    with pytest.raises(asyncio.CancelledError):
        await consume_join_leave(bot)


@pytest.mark.asyncio
async def test_consume_server_actions_formats_generated_payload() -> None:
    channel = _Channel()
    bot = _ServerActionBot(channel_id=557, channel=channel)

    with pytest.raises(asyncio.CancelledError):
        await consume_server_actions(bot)

    assert channel.sent == [{"content": "Server started", "view": None}]


@pytest.mark.asyncio
async def test_consume_server_actions_skips_when_channel_missing() -> None:
    bot = _ServerActionBot(channel_id=None, channel=None)

    with pytest.raises(asyncio.CancelledError):
        await consume_server_actions(bot)


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

    bot = _BanBot(bans_channel_id=777, player=PlayerRecord(pid=42, nickname="Target"))

    with pytest.raises(asyncio.CancelledError):
        await consume_bans(bot)

    assert bot.store.now_calls == 1
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

    bot = _BanBot(bans_channel_id=0, player=PlayerRecord(pid=42, nickname="Target"))

    with pytest.raises(asyncio.CancelledError):
        await consume_bans(bot)

    assert bot.store.now_calls == 0
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

    bot = _MuteBot(
        mutes_channel_id=778,
        player=PlayerRecord(pid=42, nickname="Target"),
    )

    with pytest.raises(asyncio.CancelledError):
        await consume_mutes(bot)

    assert bot.store.now_calls == 1
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

    bot = _MuteBot(mutes_channel_id=0, player=PlayerRecord(pid=42, nickname="Target"))

    with pytest.raises(asyncio.CancelledError):
        await consume_mutes(bot)

    assert bot.store.now_calls == 0
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

    bot = _VoteKickBot(votekicks_channel_id=779)

    with pytest.raises(asyncio.CancelledError):
        await consume_vote_kicks(bot)

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

    class _VoteKickNameFallbackBus(_VoteKickBus):
        async def consume_vote_kicks(self, callback) -> None:
            await callback(
                ModerationVoteKickCreatedV1(
                    target=PlayerRefV1(
                        playerUuid="uuid-target",
                        playerPid=42,
                        playerName="Target",
                    ),
                    actor=ActorRefV1(actorName="Starter", actorDiscordId="999999"),
                    reason="griefing",
                    votesFor=(
                        VoteKickParticipantV1(
                            playerName="Starter",
                            playerPid=7,
                            discordId=None,
                        ),
                    ),
                    votesAgainst=(),
                )
            )
            raise asyncio.CancelledError()

    monkeypatch.setattr(
        "xcore_discord_bot.runtime_consumers.post_vote_kick_log",
        fake_post_vote_kick_log,
    )

    bot = make_bot(
        store=_VoteKickNameFallbackBus(votekicks_channel_id=779),
        bus=_VoteKickNameFallbackBus(votekicks_channel_id=779),
        settings=make_settings(discord_votekicks_channel_id=779),
    )

    with pytest.raises(asyncio.CancelledError):
        await consume_vote_kicks(bot)

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

    bot = _VoteKickBot(votekicks_channel_id=0)

    with pytest.raises(asyncio.CancelledError):
        await consume_vote_kicks(bot)

    assert called is False
