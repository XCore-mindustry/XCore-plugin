from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest

from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.handlers_badges import cmd_badge_grant, cmd_badge_revoke


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


class _Bot:
    def __init__(
        self, *, player: PlayerRecord | None, grant_result: bool, revoke_result: bool
    ) -> None:
        self.player = player
        self.grant_result = grant_result
        self.revoke_result = revoke_result
        self.claims: list[tuple[str, str]] = []
        self.grants: list[tuple[str, str]] = []
        self.revokes: list[tuple[str, str]] = []
        self.reload_calls = 0

    async def _get_player_or_reply(
        self, interaction: _Interaction, player_id: int
    ) -> PlayerRecord | None:
        assert player_id == 7
        if self.player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return None
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

    async def grant_badge(self, *, uuid: str, badge_id: str) -> bool:
        self.grants.append((uuid, badge_id))
        return self.grant_result

    async def revoke_badge(self, *, uuid: str, badge_id: str) -> bool:
        self.revokes.append((uuid, badge_id))
        return self.revoke_result

    async def publish_reload_player_data_cache(self) -> None:
        self.reload_calls += 1

    @staticmethod
    def _player_name(player: PlayerRecord) -> str:
        return player.nickname


@pytest.mark.asyncio
async def test_cmd_badge_grant_grants_badge() -> None:
    bot = _Bot(
        player=PlayerRecord(pid=7, uuid="uuid-7", nickname="Target"),
        grant_result=True,
        revoke_result=False,
    )
    interaction = _Interaction(id=11)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert bot.claims == [("badge-grant", "7:translator")]
    assert bot.grants == [("uuid-7", "translator")]
    assert bot.reload_calls == 1
    assert interaction.response.sent == [
        ("Granted badge `translator` to `Target`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_grant_reports_existing_badge() -> None:
    bot = _Bot(
        player=PlayerRecord(pid=7, uuid="uuid-7", nickname="Target"),
        grant_result=False,
        revoke_result=False,
    )
    interaction = _Interaction(id=12)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert bot.reload_calls == 0
    assert interaction.response.sent == [
        ("Player already has badge `translator`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_revoke_revokes_badge() -> None:
    bot = _Bot(
        player=PlayerRecord(pid=7, uuid="uuid-7", nickname="Target"),
        grant_result=False,
        revoke_result=True,
    )
    interaction = _Interaction(id=13)

    await cmd_badge_revoke(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert bot.claims == [("badge-revoke", "7:translator")]
    assert bot.revokes == [("uuid-7", "translator")]
    assert bot.reload_calls == 1
    assert interaction.response.sent == [
        ("Revoked badge `translator` from `Target`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_revoke_reports_missing_badge() -> None:
    bot = _Bot(
        player=PlayerRecord(pid=7, uuid="uuid-7", nickname="Target"),
        grant_result=False,
        revoke_result=False,
    )
    interaction = _Interaction(id=14)

    await cmd_badge_revoke(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert bot.reload_calls == 0
    assert interaction.response.sent == [
        ("Player does not have badge `translator`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_change_rejects_unknown_badge() -> None:
    bot = _Bot(
        player=PlayerRecord(pid=7, uuid="uuid-7", nickname="Target"),
        grant_result=False,
        revoke_result=False,
    )
    interaction = _Interaction(id=15)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "unknown")

    assert interaction.response.sent == [("Badge `unknown` was not found.", True)]


@pytest.mark.asyncio
async def test_cmd_badge_change_rejects_system_badge() -> None:
    bot = _Bot(
        player=PlayerRecord(pid=7, uuid="uuid-7", nickname="Target"),
        grant_result=False,
        revoke_result=False,
    )
    interaction = _Interaction(id=16)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "admin")

    assert interaction.response.sent == [
        ("Badge `admin` cannot be granted or revoked manually.", True)
    ]
