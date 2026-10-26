from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast
from unittest.mock import AsyncMock

import discord
import pytest
from backends import published, real_bot
from xcore_protocol.generated.shared import ActorRefV1ActorType

from xcore_discord_bot.daemons.admin_sync_daemon import (
    AdminReconcileResult,
    ReconciledAdmin,
    SkippedAdmin,
)
from xcore_discord_bot.handlers_moderation import (
    cmd_add_admin,
    cmd_list_admins,
    cmd_remove_admin,
    cmd_sync_admins,
)


@dataclass
class _User:
    id: int = 1
    display_name: str = "boss"


@dataclass
class _Response:
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send_message(
        self,
        text: str | None = None,
        *,
        ephemeral: bool = False,
        embed: Any = None,
        view: Any = None,
        allowed_mentions: Any = None,
    ) -> None:
        self.sent.append(
            {
                "text": text,
                "ephemeral": ephemeral,
                "embed": embed,
                "view": view,
                "allowed_mentions": allowed_mentions,
            }
        )


@dataclass
class _Interaction:
    id: int
    user: _User = field(default_factory=_User)
    response: _Response = field(default_factory=_Response)

    async def original_response(self) -> object:
        return object()


TARGET = {
    "_id": 1,
    "pid": 7,
    "uuid": "uuid-7",
    "nickname": "Target",
    "discord_id": "123",
    "discord_username": "discord-user",
    "is_admin": False,
    "admin_source": "NONE",
}
OTHER = {"_id": 2, "pid": 9, "uuid": "uuid-9", "nickname": "Other", "discord_id": "123"}
ACCESS = "xcore:cmd:discord-admin-access:mini-pvp"


async def _bot(*players: dict[str, Any]):
    bot, db = await real_bot()
    bot.container.servers.update_server("mini-pvp", 1, 0, 10, "v1")
    for player in players or (TARGET,):
        await db["players"].insert_one(dict(player))
    # Discord itself: the guild role and the role members the reconcile reads.
    bot.set_discord_admin_role = AsyncMock(return_value=True)
    bot.container.admin_sync_daemon.reconcile = AsyncMock(
        return_value=AdminReconcileResult(discord_admins=0)
    )
    return bot


async def _admins(bot) -> list[tuple[str, bool, str]]:
    return [
        (player["uuid"], player.get("is_admin"), player.get("admin_source"))
        for player in await bot.store.database["players"].find({}).to_list()
    ]


@pytest.mark.asyncio
async def test_cmd_add_admin_sets_admin_access_and_publishes_event() -> None:
    bot = await _bot()
    interaction = _Interaction(id=30)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert await bot.bus.redis.exists("xcore:idmp:cmd:add-admin:30:7")
    bot.set_discord_admin_role.assert_awaited_once_with(
        discord_id="123", should_have_role=True, reason="/admin add by boss"
    )
    assert await _admins(bot) == [("uuid-7", True, "DISCORD_ROLE")]
    (event,) = await published(bot.bus, ACCESS)
    assert event["player"]["playerUuid"] == "uuid-7"
    assert event["admin"] is True
    assert event["source"]["actorType"] == ActorRefV1ActorType.SYSTEM.value
    assert event["source"]["actorName"] == "DISCORD_ROLE"
    assert event["actor"]["actorType"] == ActorRefV1ActorType.DISCORD.value
    assert interaction.response.sent == [
        {
            "text": "Granted admin for `Target`",
            "ephemeral": False,
            "embed": None,
            "view": None,
            "allowed_mentions": None,
        }
    ]


@pytest.mark.asyncio
async def test_cmd_remove_admin_clears_admin_access_and_publishes_event() -> None:
    bot = await _bot(dict(TARGET, is_admin=True, admin_source="DISCORD_ROLE"))
    interaction = _Interaction(id=31)

    await cmd_remove_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert await bot.bus.redis.exists("xcore:idmp:cmd:remove-admin:31:7")
    bot.set_discord_admin_role.assert_awaited_once_with(
        discord_id="123", should_have_role=False, reason="/admin remove by boss"
    )
    assert await _admins(bot) == [("uuid-7", False, "NONE")]
    (event,) = await published(bot.bus, ACCESS)
    assert event["admin"] is False
    assert event["source"]["actorType"] == ActorRefV1ActorType.SYSTEM.value
    assert event["source"]["actorName"] == "NONE"
    assert event["actor"]["actorType"] == ActorRefV1ActorType.DISCORD.value
    assert interaction.response.sent == [
        {
            "text": "Removed admin for `Target`",
            "ephemeral": False,
            "embed": None,
            "view": None,
            "allowed_mentions": None,
        }
    ]


@pytest.mark.asyncio
async def test_cmd_add_admin_requires_discord_link() -> None:
    bot = await _bot(dict(TARGET, discord_id=None))
    interaction = _Interaction(id=32)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert await _admins(bot) == [("uuid-7", False, "NONE")]
    assert await published(bot.bus, ACCESS) == []
    assert interaction.response.sent == [
        {
            "text": "Cannot grant admin: Discord account is not linked.",
            "ephemeral": True,
            "embed": None,
            "view": None,
            "allowed_mentions": None,
        }
    ]


@pytest.mark.asyncio
async def test_cmd_add_admin_grants_all_linked_accounts_for_same_discord() -> None:
    bot = await _bot(TARGET, OTHER)
    interaction = _Interaction(id=35)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert await _admins(bot) == [
        ("uuid-7", True, "DISCORD_ROLE"),
        ("uuid-9", True, "DISCORD_ROLE"),
    ]
    assert len(await published(bot.bus, ACCESS)) == 2
    assert interaction.response.sent == [
        {
            "text": "Granted admin for `Other`, `Target`",
            "ephemeral": False,
            "embed": None,
            "view": None,
            "allowed_mentions": None,
        }
    ]


@pytest.mark.asyncio
async def test_cmd_list_admins_returns_paginated_embed_with_mentions() -> None:
    # Only admins granted through the Discord role are listed.
    bot = await _bot(
        dict(TARGET, is_admin=True, admin_source="DISCORD_ROLE"),
        dict(OTHER, is_admin=True, admin_source="CONSOLE"),
    )
    interaction = _Interaction(id=33)

    await cmd_list_admins(cast(Any, bot), cast(Any, interaction))

    assert len(interaction.response.sent) == 1
    sent = interaction.response.sent[0]
    assert sent["text"] is None
    assert sent["ephemeral"] is False
    assert isinstance(sent["embed"], discord.Embed)
    assert sent["embed"].title == "Discord Admin Access"
    assert [field.name for field in sent["embed"].fields] == ["Target"]
    field_value = sent["embed"].fields[0].value or ""
    assert "PID: `7`" in field_value
    assert "Source: `DISCORD_ROLE`" in field_value
    assert "Discord: <@123>" in field_value
    assert sent["allowed_mentions"] is None


@pytest.mark.asyncio
async def test_cmd_sync_admins_reports_reconcile_summary() -> None:
    bot = await _bot()
    bot.container.admin_sync_daemon.reconcile.return_value = AdminReconcileResult(
        discord_admins=4,
        applied=(
            ReconciledAdmin("123", 7, "Target"),
            ReconciledAdmin("123", 9, "Other"),
        ),
        revoked=(ReconciledAdmin("555", 11, "Former"),),
        skipped=(SkippedAdmin("999", "-", "no linked Mindustry accounts"),),
    )
    interaction = _Interaction(id=34)

    await cmd_sync_admins(cast(Any, bot), cast(Any, interaction))

    assert len(interaction.response.sent) == 1
    sent = interaction.response.sent[0]
    assert sent["text"] is None
    assert sent["ephemeral"] is False
    assert sent["view"] is None
    assert sent["allowed_mentions"] is None
    embed = sent["embed"]
    assert isinstance(embed, discord.Embed)
    assert embed.title == "Admin Reconcile Complete"
    assert embed.description == (
        "Applied: **2**\nRevoked: **1**\nDiscord role members: **4**"
    )
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Added"] == "`Target` (pid=7, <@123>), `Other` (pid=9, <@123>)"
    assert fields["Revoked"] == "`Former` (pid=11, <@555>)"
    assert fields["Skipped"] == "<@999> — - (no linked Mindustry accounts)"


def test_add_embed_section_splits_long_values() -> None:
    from xcore_discord_bot.moderation_logs import add_embed_section

    embed = discord.Embed(title="Test")
    items = ["x" * 600, "y" * 600, "z" * 10]

    add_embed_section(embed, name="Added", items=items)

    assert [field.name for field in embed.fields] == ["Added", "Added (cont. 2)"]
    assert len(embed.fields[0].value or "") <= 1024
    assert len(embed.fields[1].value or "") <= 1024


@pytest.mark.asyncio
async def test_cmd_remove_admin_clears_all_linked_accounts_for_same_discord() -> None:
    admin = {"is_admin": True, "admin_source": "DISCORD_ROLE"}
    bot = await _bot(dict(TARGET, **admin), dict(OTHER, **admin))
    interaction = _Interaction(id=36)

    await cmd_remove_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert await _admins(bot) == [("uuid-7", False, "NONE"), ("uuid-9", False, "NONE")]
    assert len(await published(bot.bus, ACCESS)) == 2
    assert interaction.response.sent == [
        {
            "text": "Removed admin for `Other`, `Target`",
            "ephemeral": False,
            "embed": None,
            "view": None,
            "allowed_mentions": None,
        }
    ]
