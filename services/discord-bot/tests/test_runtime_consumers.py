from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import pytest

from xcore_discord_bot.contracts import (
    BanEvent,
    GameChatMessage,
    MuteEvent,
    VoteKickEvent,
    VoteKickParticipant,
)
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.runtime_consumers import (
    consume_bans,
    consume_game_chat,
    consume_mutes,
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


class _GameChatBot:
    def __init__(self, *, channel_id: int | None, channel: _Channel | None) -> None:
        self.channel_id = channel_id
        self.channel = channel

    def _channel_id_for_server(self, server: str, *, context: str) -> int | None:
        assert server == "mini-pvp"
        assert context == "game chat"
        return self.channel_id

    async def _resolve_messageable_channel(
        self,
        channel_id: int,
        *,
        context: str,
    ) -> _Channel | None:
        assert channel_id == 555
        assert context == "game chat"
        return self.channel

    async def consume_game_chat_events(self, callback) -> None:
        await callback(
            GameChatMessage(
                author_name="mod`name",
                message="hello `world`",
                server="mini-pvp",
            )
        )
        raise asyncio.CancelledError()

    async def reconnect_bus(self) -> None:
        raise AssertionError("reconnect should not be called")


class _BanBot:
    def __init__(self, *, bans_channel_id: int, player: PlayerRecord | None) -> None:
        self.bans_channel_id = bans_channel_id
        self.player = player
        self.now_calls = 0

    async def consume_bans_stream(self, callback) -> None:
        await callback(
            BanEvent(
                uuid="uuid-1",
                name="Target",
                admin_name="Admin",
                admin_discord_id="123",
                reason="Rule 1",
                expire_date="bad-date",
            )
        )
        raise asyncio.CancelledError()

    async def reconnect_bus(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        assert uuid == "uuid-1"
        return self.player

    async def now_utc(self) -> datetime:
        self.now_calls += 1
        return datetime(2026, 1, 1, tzinfo=timezone.utc)

    @staticmethod
    def _parse_iso_datetime(raw: str | None) -> datetime | None:
        del raw
        return None


class _MuteBot:
    def __init__(self, *, mutes_channel_id: int, player: PlayerRecord | None) -> None:
        self.mutes_channel_id = mutes_channel_id
        self.player = player
        self.now_calls = 0

    async def consume_mutes_stream(self, callback) -> None:
        await callback(
            MuteEvent(
                uuid="uuid-1",
                name="Target",
                admin_name="Admin",
                admin_discord_id="456",
                reason="Rule 1",
                expire_date="bad-date",
            )
        )
        raise asyncio.CancelledError()

    async def reconnect_bus(self) -> None:
        raise AssertionError("reconnect should not be called")

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        assert uuid == "uuid-1"
        return self.player

    async def now_utc(self) -> datetime:
        self.now_calls += 1
        return datetime(2026, 1, 1, tzinfo=timezone.utc)

    @staticmethod
    def _parse_iso_datetime(raw: str | None) -> datetime | None:
        del raw
        return None


class _VoteKickBot:
    def __init__(self, *, votekicks_channel_id: int) -> None:
        self.votekicks_channel_id = votekicks_channel_id

    async def consume_vote_kicks_stream(self, callback) -> None:
        await callback(
            VoteKickEvent(
                target_name="Target",
                target_pid=42,
                target_uuid="uuid-target",
                starter_name="Starter",
                starter_pid=7,
                starter_discord_id="123456",
                reason="griefing",
                votes_for=[
                    VoteKickParticipant(name="Starter", pid=7, discord_id="123456")
                ],
                votes_against=[
                    VoteKickParticipant(name="Voter2", pid=8, discord_id="654321")
                ],
            )
        )
        raise asyncio.CancelledError()

    async def reconnect_bus(self) -> None:
        raise AssertionError("reconnect should not be called")


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

    assert bot.now_calls == 1
    assert captured["pid"] == 42
    assert captured["name"] == "Target"
    assert captured["admin_name"] == "Admin"
    assert captured["admin_discord_id"] == "123"
    assert captured["reason"] == "Rule 1"
    assert captured["expire"] == datetime(2026, 1, 1, tzinfo=timezone.utc)


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

    assert bot.now_calls == 0
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

    assert bot.now_calls == 1
    assert captured["pid"] == 42
    assert captured["name"] == "Target"
    assert captured["admin_name"] == "Admin"
    assert captured["admin_discord_id"] == "456"
    assert captured["reason"] == "Rule 1"
    assert captured["expire"] == datetime(2026, 1, 1, tzinfo=timezone.utc)


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

    assert bot.now_calls == 0
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
    assert [item.name for item in captured["votes_for"]] == ["Starter"]
    assert [item.name for item in captured["votes_against"]] == ["Voter2"]


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
