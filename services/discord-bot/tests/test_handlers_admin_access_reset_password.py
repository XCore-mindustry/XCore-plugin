from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest
from backends import GameServer, real_bot
from xcore_protocol.generated.security import (
    SecurityStaffResetPasswordRequestV1,
    SecurityStaffResetPasswordResponseV1,
)

from xcore_discord_bot.handlers_admin_access import cmd_reset_password

RESET = SecurityStaffResetPasswordRequestV1.MESSAGE_TYPE


@dataclass
class _User:
    display_name: str = "admin"


@dataclass
class _Response:
    deferred: bool = False

    async def defer(self, *, thinking: bool = False) -> None:
        self.deferred = True


@dataclass
class _Followup:
    sent: list[str] = field(default_factory=list)

    async def send(self, text: str) -> None:
        self.sent.append(text)


@dataclass
class _Interaction:
    id: int
    user: _User = field(default_factory=_User)
    response: _Response = field(default_factory=_Response)
    followup: _Followup = field(default_factory=_Followup)


async def _bot(*, changed: bool):
    bot, db = await real_bot()
    game = GameServer(bot)
    game.answers[RESET] = lambda payload: SecurityStaffResetPasswordResponseV1(
        payload["server"], payload["operationId"], changed
    ).to_payload()
    await db["players"].insert_one(
        {"_id": 1, "pid": 7, "uuid": "uuid-7", "nickname": "Target"}
    )
    return bot, game


@pytest.mark.asyncio
async def test_cmd_reset_password_asks_the_game_server() -> None:
    bot, game = await _bot(changed=True)
    interaction = _Interaction(id=21)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert await bot.bus.redis.exists("xcore:idmp:cmd:reset-password:21:7")
    [request] = game.sent(RESET)
    assert (request["server"], request["playerUuid"]) == ("mini-pvp", "uuid-7")
    assert interaction.response.deferred
    assert interaction.followup.sent == ["Password reset for `Target`"]


@pytest.mark.asyncio
async def test_cmd_reset_password_reports_when_nothing_changed() -> None:
    bot, _ = await _bot(changed=False)
    interaction = _Interaction(id=22)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert interaction.followup.sent == ["Password reset did not update any row"]
