from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest
from backends import published, real_bot

from xcore_discord_bot.handlers_moderation import cmd_reset_password


@dataclass
class _User:
    display_name: str = "admin"


@dataclass
class _Response:
    sent: list[tuple[str, bool]] = field(default_factory=list)

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        self.sent.append((text, ephemeral))


@dataclass
class _Interaction:
    id: int
    user: _User = field(default_factory=_User)
    response: _Response = field(default_factory=_Response)


RESETS = "xcore:cmd:player-password-reset:mini-pvp"


async def _bot(password_hash: str):
    bot, db = await real_bot()
    bot.container.servers.update_server("mini-pvp", 1, 0, 10, "v1")
    await db["players"].insert_one(
        {
            "_id": 1,
            "pid": 7,
            "uuid": "uuid-7",
            "nickname": "Target",
            "password_hash": password_hash,
        }
    )
    return bot


@pytest.mark.asyncio
async def test_cmd_reset_password_publishes_player_password_reset() -> None:
    bot = await _bot("hash")
    interaction = _Interaction(id=21)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert await bot.bus.redis.exists("xcore:idmp:cmd:reset-password:21:7")
    (player,) = await bot.store.database["players"].find({}).to_list()
    assert player["password_hash"] == ""
    assert [event["playerUuid"] for event in await published(bot.bus, RESETS)] == [
        "uuid-7"
    ]
    assert interaction.response.sent == [("Password reset for `Target`", False)]


@pytest.mark.asyncio
async def test_cmd_reset_password_skips_publish_when_nothing_changed() -> None:
    bot = await _bot("")
    interaction = _Interaction(id=22)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert await published(bot.bus, RESETS) == []
    assert interaction.response.sent == [
        ("Password reset did not update any row", False)
    ]
