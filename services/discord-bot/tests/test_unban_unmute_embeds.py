from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

import pytest
from backends import published, real_bot

from xcore_discord_bot.handlers_moderation import (
    MSG_INVALID_BAN_ID,
    MSG_NO_ACTIVE_BAN,
    MSG_NO_ACTIVE_MUTE,
    cmd_unban,
    cmd_unban_ip,
    cmd_unmute,
    parse_ban_id,
)


@dataclass
class _User:
    id: int
    display_name: str


@dataclass
class _Response:
    calls: list[dict[str, Any]] = field(default_factory=list)

    async def send_message(
        self,
        content: str | None = None,
        *,
        embed: Any = None,
        ephemeral: bool = False,
    ) -> None:
        self.calls.append({"content": content, "embed": embed, "ephemeral": ephemeral})


@dataclass
class _Interaction:
    id: int
    user: _User
    response: _Response = field(default_factory=_Response)


PLAYER = {"pid": 123, "uuid": "uuid-123", "last_ip": "1.2.3.4", "nickname": "Vortex"}
# Written before bans had ids, so the unban assigns one first.
BAN = {
    "uuid": "uuid-123",
    "ip": "1.2.3.4",
    "name": "Vortex",
    "admin_name": "mod-1",
    "admin_discord_id": "111",
    "reason": "griefing",
    "expire_date": datetime(2026, 12, 31, 15, 0, tzinfo=UTC),
}
MUTE = {
    "uuid": "uuid-123",
    "pid": 123,
    "name": "Vortex",
    "admin_name": "mod-2",
    "admin_discord_id": "222",
    "reason": "spam",
    "expire_date": datetime(2026, 12, 31, 16, 0, tzinfo=UTC),
}
PARDON = "xcore:cmd:pardon-player:mini-pvp"


async def _bot(*, ban: bool = True, mute: bool = True):
    bot, db = await real_bot()
    bot.container.servers.update_server("mini-pvp", 1, 0, 10, "v1")
    await db["players"].insert_one(dict(PLAYER))
    if ban:
        await db["bans"].insert_one(dict(BAN))
    if mute:
        await db["mutes"].insert_one(dict(MUTE))
    return bot, db


@pytest.mark.asyncio
async def test_cmd_unban_sends_rich_embed() -> None:
    bot, db = await _bot()

    interaction = _Interaction(id=1, user=_User(id=7, display_name="admin-x"))
    await cmd_unban(bot, interaction, 123)

    assert await db["bans"].count_documents({}) == 0
    (pardon,) = await published(bot.bus, PARDON)
    assert pardon["target"]["playerUuid"] == "uuid-123"
    assert len(interaction.response.calls) == 1
    call = interaction.response.calls[0]
    assert call["content"] is None
    assert call["ephemeral"] is False

    embed = call["embed"]
    assert embed is not None
    assert embed.title == "Unbanned Vortex"
    fields = {field.name: field.value for field in embed.fields}
    assert fields["PID"] == "123"
    assert fields["Admin who banned"] == "mod-1 (<@111>)"
    assert fields["Reason"] == "griefing"
    assert "<t:" in fields["Was set to expire"]
    assert fields["Unbanned by"] == "admin-x"
    (audit,) = await db["moderation_audit"].find({}).to_list()
    assert audit["action"] == "UNBAN"
    assert audit["reason"] == "griefing"
    assert audit["actor"]["discord_id"] == "7"
    assert audit["origin"]["request_id"] == "1"
    assert audit["details"]["extra"]["ban_id"] == "1"


@pytest.mark.asyncio
async def test_cmd_unmute_sends_rich_embed() -> None:
    bot, db = await _bot()

    interaction = _Interaction(id=2, user=_User(id=7, display_name="admin-y"))
    await cmd_unmute(bot, interaction, 123)

    assert len(interaction.response.calls) == 1
    call = interaction.response.calls[0]
    embed = call["embed"]
    assert embed is not None
    assert embed.title == "Unmuted Vortex"
    fields = {field.name: field.value for field in embed.fields}
    assert fields["PID"] == "123"
    assert fields["Admin who muted"] == "mod-2 (<@222>)"
    assert fields["Reason"] == "spam"
    assert "<t:" in fields["Was set to expire"]
    assert fields["Unmuted by"] == "admin-y"
    assert await db["mutes"].count_documents({}) == 0
    (audit,) = await db["moderation_audit"].find({}).to_list()
    assert audit["action"] == "UNMUTE"
    assert audit["reason"] == "spam"
    assert audit["actor"]["discord_id"] == "7"
    assert audit["origin"]["request_id"] == "2"


@pytest.mark.asyncio
async def test_cmd_unban_no_active_ban_message() -> None:
    bot, db = await _bot(ban=False)

    interaction = _Interaction(id=3, user=_User(id=7, display_name="admin"))
    await cmd_unban(bot, interaction, 123)

    assert interaction.response.calls == [
        {"content": MSG_NO_ACTIVE_BAN, "embed": None, "ephemeral": True}
    ]
    assert await published(bot.bus, PARDON) == []
    assert await db["moderation_audit"].count_documents({}) == 0


@pytest.mark.asyncio
async def test_cmd_unmute_no_active_mute_message() -> None:
    bot, db = await _bot(mute=False)

    interaction = _Interaction(id=4, user=_User(id=7, display_name="admin"))
    await cmd_unmute(bot, interaction, 123)

    assert interaction.response.calls == [
        {"content": MSG_NO_ACTIVE_MUTE, "embed": None, "ephemeral": True}
    ]
    assert await db["moderation_audit"].count_documents({}) == 0


@pytest.mark.parametrize(
    "value,expected",
    [
        ("B-123", 123),
        ("b123", 123),
        (" #123 ", 123),
        ("123", 123),
        ("B-0", None),
        ("-5", None),
        ("abc", None),
        ("99999999999", None),
    ],
)
def test_parse_ban_id(value: str, expected: int | None) -> None:
    assert parse_ban_id(value) == expected


@pytest.mark.asyncio
async def test_cmd_unban_ip_keeps_the_uuid_banned() -> None:
    bot, db = await _bot(mute=False)
    await db["bans"].update_one({}, {"$set": {"ban_id": 7, "pid": 123}})
    interaction = _Interaction(id=42, user=_User(id=999, display_name="Admin"))

    await cmd_unban_ip(bot, interaction, "B-7")

    row = db["bans"].docs[0]
    assert row["uuid"] == "uuid-123" and row["ip"] is None
    embed = interaction.response.calls[0]["embed"]
    assert embed.title == "IP released from ban B-7"
    fields = {item.name: item.value for item in embed.fields}
    assert fields["PID"] == "123" and fields["Released by"] == "Admin"
    assert "1.2.3.4" not in str(embed.to_dict())
    audit = db["moderation_audit"].docs[0]
    assert audit["action"] == "NOTE"
    assert audit["details"]["extra"] == {"ban_id": "7", "kind": "ban_ip_released"}
    assert audit["target"]["ip_snapshot"] == "1.2.3.4"
    assert not await published(bot.bus, PARDON)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "value,message", [("nope", MSG_INVALID_BAN_ID), ("B-8", MSG_NO_ACTIVE_BAN)]
)
async def test_cmd_unban_ip_refusals_are_ephemeral(value: str, message: str) -> None:
    bot, db = await _bot(mute=False)
    await db["bans"].update_one({}, {"$set": {"ban_id": 7}})
    interaction = _Interaction(id=43, user=_User(id=999, display_name="Admin"))

    await cmd_unban_ip(bot, interaction, value)

    assert interaction.response.calls == [
        {"content": message, "embed": None, "ephemeral": True}
    ]
    assert db["bans"].docs[0]["ip"] == "1.2.3.4"
    assert not db["moderation_audit"].docs
