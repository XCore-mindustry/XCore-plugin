from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import pytest

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.handlers_moderation import cmd_mute
from xcore_discord_bot.mongo_store import MuteDoc


class _Store:
    def __init__(self) -> None:
        self.upserts: list[dict[str, Any]] = []

    async def find_player_by_pid(self, pid: int) -> PlayerRecord | None:
        if pid != 55:
            return None
        return PlayerRecord(pid=55, uuid="uuid-55", nickname="Target")

    def now_utc(self) -> datetime:
        return datetime(2026, 1, 1, tzinfo=timezone.utc)

    async def upsert_mute(
        self,
        *,
        uuid: str,
        pid: int | None,
        name: str,
        admin_name: str,
        admin_discord_id: str | None,
        reason: str,
        expire_date: datetime,
    ) -> None:
        self.upserts.append(
            {
                "uuid": uuid,
                "pid": pid,
                "name": name,
                "admin_name": admin_name,
                "admin_discord_id": admin_discord_id,
                "reason": reason,
                "expire_date": expire_date,
            }
        )


class _Bus:
    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:  # noqa: ARG002
        return True


@dataclass
class _Message:
    edits: list[dict[str, Any]] = field(default_factory=list)

    async def edit(self, *, content: str | None = None, view: Any = None) -> None:
        self.edits.append({"content": content, "view": view})


@dataclass
class _Response:
    command_messages: list[dict[str, Any]] = field(default_factory=list)

    async def send_message(
        self,
        content: str,
        *,
        ephemeral: bool = False,
        view: Any = None,
    ) -> None:
        self.command_messages.append(
            {"content": content, "ephemeral": ephemeral, "view": view}
        )


@dataclass
class _User:
    id: int
    display_name: str


@dataclass
class _Interaction:
    id: int
    user: _User
    response: _Response = field(default_factory=_Response)
    _original_message: _Message = field(default_factory=_Message)

    async def original_response(self) -> _Message:
        return self._original_message


@pytest.mark.asyncio
async def test_cmd_mute_persists_pid() -> None:
    bot = object.__new__(XCoreDiscordBot)
    store = _Store()
    bot.__dict__["_store"] = store
    bot.__dict__["_bus"] = _Bus()

    interaction = _Interaction(id=101, user=_User(id=999, display_name="Moderator"))

    import xcore_discord_bot.handlers_moderation as handlers_moderation

    original_post_mute_log = handlers_moderation.post_mute_log

    async def fake_post_mute_log(*args, **kwargs):
        return None

    handlers_moderation.post_mute_log = fake_post_mute_log
    try:
        await cmd_mute(bot, interaction, 55, "1d", "spam")
    finally:
        handlers_moderation.post_mute_log = original_post_mute_log

    assert len(store.upserts) == 1
    assert store.upserts[0]["pid"] == 55


def test_mute_doc_accepts_optional_pid() -> None:
    doc = MuteDoc.model_validate(
        {
            "uuid": "u-1",
            "pid": 77,
            "name": "Nick",
            "admin_name": "mod",
            "reason": "spam",
            "expire_date": datetime(2026, 3, 1, tzinfo=timezone.utc),
        }
    )

    assert doc.pid == 77
