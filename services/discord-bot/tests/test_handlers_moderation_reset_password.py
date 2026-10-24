from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest
from bot_fakes import make_bot, make_settings

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


class _Backend:
    def __init__(self, *, changed: bool) -> None:
        self.changed = changed
        self.claims: list[tuple[str, str]] = []
        self.password_reset_events: list[str] = []

    async def reset_password(self, *, uuid: str) -> bool:
        assert uuid == "uuid-7"
        return self.changed

    async def publish_player_password_reset(self, *, uuid_value: str) -> None:
        self.password_reset_events.append(uuid_value)

    async def find_player_by_pid(self, player_id):
        assert player_id == 7
        return PlayerRecord(pid=7, uuid="uuid-7", nickname="Target")

    async def claim_idempotency(self, key, *, ttl_seconds=600):
        _, operation, _, scope = key.split(":", 3)
        self.claims.append((operation, scope))
        return True

    async def find_player_by_uuid(self, uuid):
        return None


def _bot(*args, **kwargs):
    backend = _Backend(*args, **kwargs)
    bot = make_bot(store=backend, bus=backend, settings=make_settings())
    return bot


@pytest.mark.asyncio
async def test_cmd_reset_password_publishes_player_password_reset() -> None:
    bot = _bot(changed=True)
    interaction = _Interaction(id=21)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.store.claims == [("reset-password", "7")]
    assert bot.store.password_reset_events == ["uuid-7"]
    assert interaction.response.sent == [("Password reset for `Target`", False)]


@pytest.mark.asyncio
async def test_cmd_reset_password_skips_publish_when_nothing_changed() -> None:
    bot = _bot(changed=False)
    interaction = _Interaction(id=22)

    await cmd_reset_password(cast(Any, bot), cast(Any, interaction), 7)

    assert bot.store.password_reset_events == []
    assert interaction.response.sent == [
        ("Password reset did not update any row", False)
    ]
