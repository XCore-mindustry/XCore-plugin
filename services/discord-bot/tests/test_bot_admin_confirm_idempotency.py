from __future__ import annotations

from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any

import discord
import pytest

from xcore_discord_bot.bot import XCoreDiscordBot


@dataclass
class _Role:
    id: int


@dataclass
class _User:
    roles: list[_Role]
    display_name: str = "moderator"


@dataclass
class _Response:
    replies: list[tuple[str, bool]] = field(default_factory=list)
    edits: list[tuple[str | None, Any]] = field(default_factory=list)

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        self.replies.append((text, ephemeral))

    async def edit_message(self, *, content: str | None = None, view: Any = None) -> None:
        self.edits.append((content, view))


@dataclass
class _Interaction:
    id: int
    user: _User
    custom_id: str
    response: _Response = field(default_factory=_Response)
    message: Any = None

    type: discord.InteractionType = discord.InteractionType.component

    @property
    def data(self) -> dict[str, str]:
        return {"custom_id": self.custom_id}


@dataclass
class _Message:
    content: str


class _Store:
    def __init__(self) -> None:
        self.marked: list[str] = []

    async def find_player_by_pid(self, pid: int) -> dict[str, object] | None:
        if pid != 1:
            return None
        return {"pid": 1, "uuid": "uuid-1", "nickname": "Nick"}

    async def mark_admin_confirmed(self, uuid: str) -> None:
        self.marked.append(uuid)


class _Bus:
    def __init__(self) -> None:
        self.claimed: set[str] = set()
        self.confirmed: list[tuple[str, str]] = []

    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:
        if key in self.claimed:
            return False
        self.claimed.add(key)
        return True

    async def publish_admin_confirm(self, uuid_value: str, server: str) -> None:
        self.confirmed.append((uuid_value, server))


@pytest.mark.asyncio
async def test_admin_confirm_idempotency_not_tied_to_interaction_id() -> None:
    bot = object.__new__(XCoreDiscordBot)
    settings = SimpleNamespace(
        discord_admin_role_id=5,
        discord_token="token",
        discord_interaction_hmac_secret="secret",
    )
    store = _Store()
    bus = _Bus()
    bot.__dict__["_settings"] = settings
    bot.__dict__["_store"] = store
    bot.__dict__["_bus"] = bus

    custom_id = XCoreDiscordBot._build_admin_interaction_custom_id(
        bot,
        "mini-pvp",
        1,
        "confirm",
        "req-1",
    )
    initial_text = "Admin request: **Nick** (`pid=1`) on `mini-pvp`"
    first = _Interaction(
        id=1001,
        user=_User(roles=[_Role(5)]),
        custom_id=custom_id,
        message=_Message(content=initial_text),
    )
    second = _Interaction(
        id=1002,
        user=_User(roles=[_Role(5)]),
        custom_id=custom_id,
        message=_Message(content=initial_text),
    )

    await XCoreDiscordBot.on_interaction(bot, first)
    await XCoreDiscordBot.on_interaction(bot, second)

    assert bus.confirmed == [("uuid-1", "mini-pvp")]
    assert store.marked == ["uuid-1"]
    assert len(first.response.edits) == 1
    assert "✅ Confirmed admin request" in (first.response.edits[0][0] or "")
    assert second.response.replies[0][0] == "This admin confirmation was already processed."
    assert second.response.replies[0][1] is True
