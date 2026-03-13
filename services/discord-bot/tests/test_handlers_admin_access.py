from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest
import discord

from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.handlers_moderation import (
    cmd_add_admin,
    cmd_list_admins,
    cmd_remove_admin,
    cmd_sync_admins,
)


@dataclass
class _User:
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


class _Bot:
    def __init__(
        self, *, changed: bool = True, player: PlayerRecord | None = None
    ) -> None:
        self.changed = changed
        self.player = player or PlayerRecord(
            pid=7,
            uuid="uuid-7",
            nickname="Target",
            discord_id="123",
            discord_username="discord-user",
            is_admin=False,
            admin_source="NONE",
        )
        self.claims: list[tuple[str, str]] = []
        self.role_calls: list[tuple[str, bool, str]] = []
        self.set_admin_calls: list[tuple[str, bool, str]] = []
        self.published: list[dict[str, Any]] = []
        self.list_players: list[PlayerRecord] = []
        self.reconcile_result = {
            "applied": 0,
            "revoked": 0,
            "discord_admins": 0,
            "applied_players": [],
            "revoked_players": [],
            "skipped": [],
        }

    async def _get_player_or_reply(
        self, interaction: _Interaction, player_id: int
    ) -> PlayerRecord | None:
        del interaction
        assert player_id == 7
        return self.player

    async def _claim_mutation(
        self, interaction: _Interaction, *, operation: str, scope: str
    ) -> bool:
        del interaction
        self.claims.append((operation, scope))
        return True

    async def _require_player_uuid(
        self, interaction: _Interaction, player: PlayerRecord, *, action: str
    ) -> str | None:
        del interaction, action
        return player.uuid

    async def set_admin_access(
        self, *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        self.set_admin_calls.append((uuid, is_admin, admin_source))
        return True, self.changed

    async def set_discord_admin_role(
        self,
        *,
        discord_id: str,
        should_have_role: bool,
        reason: str,
    ) -> bool:
        self.role_calls.append((discord_id, should_have_role, reason))
        return True

    async def publish_discord_admin_access_changed(self, **payload: Any) -> None:
        self.published.append(payload)

    async def find_discord_admin_players(self) -> list[PlayerRecord]:
        return self.list_players

    async def find_players_by_discord_id(self, discord_id: str) -> list[PlayerRecord]:
        if self.player.discord_id == discord_id:
            return [self.player]
        return []

    async def reconcile_discord_admin_access(self) -> dict[str, int]:
        return self.reconcile_result

    async def _send_paginated(
        self,
        interaction: _Interaction,
        fetch_page,
        *,
        ephemeral: bool = False,
        allowed_mentions=None,
    ) -> None:
        embed, has_next = await fetch_page(0)
        await interaction.response.send_message(
            embed=embed,
            view={"has_next": has_next},
            ephemeral=ephemeral,
            allowed_mentions=allowed_mentions,
        )

    @staticmethod
    def _player_name(player: PlayerRecord) -> str:
        return player.nickname


@pytest.mark.asyncio
async def test_cmd_add_admin_sets_admin_access_and_publishes_event() -> None:
    bot = _Bot(changed=True)
    interaction = _Interaction(id=30)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.claims == [("add-admin", "7")]
    assert bot.role_calls == [("123", True, "/admin add by boss")]
    assert bot.set_admin_calls == [("uuid-7", True, "DISCORD_ROLE")]
    assert bot.published[0]["player_uuid"] == "uuid-7"
    assert bot.published[0]["admin"] is True
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
    bot = _Bot(changed=True)
    interaction = _Interaction(id=31)

    await cmd_remove_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.claims == [("remove-admin", "7")]
    assert bot.role_calls == [("123", False, "/admin remove by boss")]
    assert bot.set_admin_calls == [("uuid-7", False, "NONE")]
    assert bot.published[0]["admin"] is False
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
    bot = _Bot(
        player=PlayerRecord(pid=7, uuid="uuid-7", nickname="Target", discord_id=None)
    )
    interaction = _Interaction(id=32)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.set_admin_calls == []
    assert bot.published == []
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
    bot = _Bot()
    other = PlayerRecord(pid=9, uuid="uuid-9", nickname="Other", discord_id="123")

    async def duplicate_find(discord_id: str) -> list[PlayerRecord]:
        assert discord_id == "123"
        return [bot.player, other]

    bot.find_players_by_discord_id = duplicate_find  # type: ignore[attr-defined]
    interaction = _Interaction(id=35)

    await cmd_add_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.set_admin_calls == [
        ("uuid-7", True, "DISCORD_ROLE"),
        ("uuid-9", True, "DISCORD_ROLE"),
    ]
    assert len(bot.published) == 2
    assert interaction.response.sent == [
        {
            "text": "Granted admin for `Target`, `Other`",
            "ephemeral": False,
            "embed": None,
            "view": None,
            "allowed_mentions": None,
        }
    ]


@pytest.mark.asyncio
async def test_cmd_list_admins_returns_paginated_embed_with_mentions() -> None:
    bot = _Bot()
    bot.list_players = [
        PlayerRecord(
            pid=7,
            uuid="uuid-7",
            nickname="Target",
            is_admin=True,
            admin_source="DISCORD_ROLE",
            discord_id="123",
            discord_username="discord-user",
        )
    ]
    interaction = _Interaction(id=33)

    await cmd_list_admins(cast(Any, bot), cast(Any, interaction))

    assert len(interaction.response.sent) == 1
    sent = interaction.response.sent[0]
    assert sent["text"] is None
    assert sent["ephemeral"] is False
    assert isinstance(sent["embed"], discord.Embed)
    assert sent["embed"].title == "Discord Admin Access"
    assert sent["embed"].fields[0].name == "Target"
    field_value = sent["embed"].fields[0].value or ""
    assert "PID: `7`" in field_value
    assert "Source: `DISCORD_ROLE`" in field_value
    assert "Discord: <@123>" in field_value
    assert sent["allowed_mentions"] is None


@pytest.mark.asyncio
async def test_cmd_sync_admins_reports_reconcile_summary() -> None:
    bot = _Bot()
    bot.reconcile_result = {
        "applied": 2,
        "revoked": 1,
        "discord_admins": 4,
        "applied_players": [
            {"nickname": "Target", "pid": 7, "discord_id": "123"},
            {"nickname": "Other", "pid": 9, "discord_id": "123"},
        ],
        "revoked_players": [{"nickname": "Former", "pid": 11, "discord_id": "555"}],
        "skipped": [
            {
                "discord_id": "999",
                "player": "-",
                "reason": "no linked Mindustry accounts",
            }
        ],
    }
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


@pytest.mark.asyncio
async def test_cmd_remove_admin_clears_all_linked_accounts_for_same_discord() -> None:
    bot = _Bot()
    other = PlayerRecord(pid=9, uuid="uuid-9", nickname="Other", discord_id="123")

    async def duplicate_find(discord_id: str) -> list[PlayerRecord]:
        assert discord_id == "123"
        return [bot.player, other]

    bot.find_players_by_discord_id = duplicate_find  # type: ignore[attr-defined]
    interaction = _Interaction(id=36)

    await cmd_remove_admin(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.set_admin_calls == [
        ("uuid-7", False, "NONE"),
        ("uuid-9", False, "NONE"),
    ]
    assert len(bot.published) == 2
    assert interaction.response.sent == [
        {
            "text": "Removed admin for `Target`, `Other`",
            "ephemeral": False,
            "embed": None,
            "view": None,
            "allowed_mentions": None,
        }
    ]
