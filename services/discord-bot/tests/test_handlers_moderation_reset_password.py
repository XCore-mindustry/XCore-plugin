from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest

from xcore_discord_bot.dto import PlayerRecord
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


class _Bot:
    def __init__(self, *, changed: bool) -> None:
        self.changed = changed
        self.claims: list[tuple[str, str]] = []
        self.password_reset_events: list[str] = []

    async def _get_player_or_reply(
        self, interaction: _Interaction, player_id: int
    ) -> PlayerRecord | None:
        assert player_id == 7
        return PlayerRecord(pid=7, uuid="uuid-7", nickname="Target")

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

    async def reset_password(self, *, uuid: str) -> bool:
        assert uuid == "uuid-7"
        return self.changed

    async def publish_player_password_reset(self, *, uuid_value: str) -> None:
        self.password_reset_events.append(uuid_value)

    @staticmethod
    def _player_name(player: PlayerRecord) -> str:
        return player.nickname


@pytest.mark.asyncio
async def test_cmd_reset_password_publishes_player_password_reset() -> None:
    bot = _Bot(changed=True)
    interaction = _Interaction(id=21)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.claims == [("reset-password", "7")]
    assert bot.password_reset_events == ["uuid-7"]
    assert interaction.response.sent == [("Password reset for `Target`", False)]


@pytest.mark.asyncio
async def test_cmd_reset_password_skips_publish_when_nothing_changed() -> None:
    bot = _Bot(changed=False)
    interaction = _Interaction(id=22)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.password_reset_events == []
    assert interaction.response.sent == [
        ("Password reset did not update any row", False)
    ]
