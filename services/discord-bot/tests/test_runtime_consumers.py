from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import pytest

from xcore_discord_bot.contracts import BanEvent, GameChatMessage
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.runtime_consumers import consume_bans, consume_game_chat


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
        bot, *, pid: int, name: str, admin_name: str, reason: str, expire
    ):
        captured.update(
            {
                "bot": bot,
                "pid": pid,
                "name": name,
                "admin_name": admin_name,
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
