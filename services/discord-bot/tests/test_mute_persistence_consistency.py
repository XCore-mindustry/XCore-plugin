from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

import pytest
from backends import real_bot

from xcore_discord_bot.handlers_moderation import cmd_mute
from xcore_discord_bot.mongo_store import MuteDoc


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
    bot, db = await real_bot()
    await db["players"].insert_one({"pid": 55, "uuid": "uuid-55", "nickname": "Target"})
    interaction = _Interaction(id=101, user=_User(id=999, display_name="Moderator"))

    await cmd_mute(bot, interaction, 55, "1d", "spam")

    (mute,) = await db["mutes"].find({}).to_list()
    assert mute["uuid"] == "uuid-55"
    assert mute["pid"] == 55
    assert mute["name"] == "Target"
    assert mute["admin_name"] == "Moderator"
    assert mute["admin_discord_id"] == "999"
    assert mute["reason"] == "spam"
    (audit,) = await db["moderation_audit"].find({}).to_list()
    assert audit["action"] == "MUTE"
    assert audit["target"]["pid"] == 55


def test_mute_doc_accepts_optional_pid() -> None:
    doc = MuteDoc.model_validate(
        {
            "uuid": "u-1",
            "pid": 77,
            "name": "Nick",
            "admin_name": "mod",
            "reason": "spam",
            "expire_date": datetime(2026, 3, 1, tzinfo=UTC),
        }
    )

    assert doc.pid == 77
