from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast
from unittest.mock import AsyncMock

import discord
import pytest
from backends import real_bot

from xcore_discord_bot.daemons.staff_sync_daemon import StaffSyncResult
from xcore_discord_bot.handlers_admin_access import (
    cmd_add_admin,
    cmd_remove_admin,
    cmd_sync_admins,
)


@dataclass
class _User:
    id: int = 1
    display_name: str = "boss"


@dataclass
class _Response:
    sent: list[tuple[str, bool]] = field(default_factory=list)
    deferred: bool = False

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        self.sent.append((text, ephemeral))

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


TARGET = {
    "_id": 1,
    "pid": 7,
    "uuid": "uuid-7",
    "nickname": "Target",
    "discord_id": "123",
    "discord_username": "discord-user",
}
SYNCED = "Game role sync: 1 synced, 0 skipped or failed."


async def _bot(*players: dict[str, Any]):
    bot, db = await real_bot()
    for player in players or (TARGET,):
        await db["players"].insert_one(dict(player))
    # Discord itself, and the game servers the staff sync talks to.
    bot.set_discord_admin_role = AsyncMock(return_value=True)
    bot.container.staff_sync_daemon.sync_member = AsyncMock(
        return_value=StaffSyncResult(1, 0)
    )
    bot.container.staff_sync_daemon.reconcile = AsyncMock(
        return_value=StaffSyncResult(1, 0)
    )
    return bot


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "command,should_have_role,reason",
    [
        (cmd_add_admin, True, "/admin add by boss"),
        (cmd_remove_admin, False, "/admin remove by boss"),
    ],
)
async def test_cmd_admin_changes_the_role_and_syncs_the_member(
    command, should_have_role, reason
) -> None:
    bot = await _bot()
    interaction = _Interaction(id=30)

    await command(cast(Any, bot), cast(Any, interaction), 7)

    operation = "add-admin" if should_have_role else "remove-admin"
    assert await bot.bus.redis.exists(f"xcore:idmp:cmd:{operation}:30:7")
    bot.set_discord_admin_role.assert_awaited_once_with(
        discord_id="123", should_have_role=should_have_role, reason=reason
    )
    bot.container.staff_sync_daemon.sync_member.assert_awaited_once_with("123")
    assert interaction.response.deferred
    assert interaction.followup.sent == [SYNCED]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "player,message",
    [
        (dict(TARGET, discord_id=None), "Discord account is not linked."),
        (dict(TARGET, uuid=""), "No linked Mindustry account has a UUID."),
    ],
)
async def test_cmd_add_admin_needs_a_linked_account(player, message) -> None:
    bot = await _bot(player)
    interaction = _Interaction(id=32)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    bot.set_discord_admin_role.assert_not_awaited()
    bot.container.staff_sync_daemon.sync_member.assert_not_awaited()
    assert interaction.response.sent == [(message, True)]


@pytest.mark.asyncio
async def test_cmd_remove_admin_takes_the_role_without_a_game_account() -> None:
    bot = await _bot(dict(TARGET, uuid=""))
    interaction = _Interaction(id=33)

    await cmd_remove_admin(cast(Any, bot), cast(Any, interaction), 7)

    bot.set_discord_admin_role.assert_awaited_once_with(
        discord_id="123", should_have_role=False, reason="/admin remove by boss"
    )


@pytest.mark.asyncio
async def test_cmd_add_admin_reports_a_discord_failure() -> None:
    bot = await _bot()
    bot.set_discord_admin_role.side_effect = discord.HTTPException(
        AsyncMock(status=500, reason="boom"), "boom"
    )
    interaction = _Interaction(id=33)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    bot.container.staff_sync_daemon.sync_member.assert_not_awaited()
    [message] = interaction.followup.sent
    assert message.startswith("Game role sync failed: Discord could not update")


@pytest.mark.asyncio
async def test_cmd_sync_admins_reports_the_pass() -> None:
    bot = await _bot()
    interaction = _Interaction(id=34)

    await cmd_sync_admins(cast(Any, bot), cast(Any, interaction))

    bot.container.staff_sync_daemon.reconcile.assert_awaited_once_with()
    assert interaction.followup.sent == [SYNCED]


def test_add_embed_section_splits_long_values() -> None:
    from xcore_discord_bot.moderation_logs import add_embed_section

    embed = discord.Embed(title="Test")
    items = ["x" * 600, "y" * 600, "z" * 10]

    add_embed_section(embed, name="Added", items=items)

    assert [field.name for field in embed.fields] == ["Added", "Added (cont. 2)"]
    assert len(embed.fields[0].value or "") <= 1024
    assert len(embed.fields[1].value or "") <= 1024
