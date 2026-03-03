from __future__ import annotations

from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any

import pytest

from xcore_discord_bot.bot import _AdminRequestView


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

    async def edit_message(
        self, *, content: str | None = None, view: Any = None
    ) -> None:
        self.edits.append((content, view))


@dataclass
class _Interaction:
    user: _User
    response: _Response = field(default_factory=_Response)
    message: Any = None


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
async def test_admin_request_view_confirm_idempotent() -> None:
    bot = SimpleNamespace(
        _settings=SimpleNamespace(discord_admin_role_id=5),
        _store=_Store(),
        _bus=_Bus(),
    )

    async def _finalize_admin_request_message(
        interaction: _Interaction, status: str
    ) -> None:
        await interaction.response.edit_message(content=status, view=None)

    bot._finalize_admin_request_message = _finalize_admin_request_message

    view = _AdminRequestView(bot=bot, server="mini-pvp", pid=1, request_nonce="req-1")
    first = _Interaction(user=_User(roles=[_Role(5)]))
    second = _Interaction(user=_User(roles=[_Role(5)]))

    confirm_button = view.children[0]
    callback = confirm_button.callback
    assert callback is not None
    await callback(first)
    await callback(second)

    assert bot._bus.confirmed == [("uuid-1", "mini-pvp")]
    assert bot._store.marked == ["uuid-1"]
    assert len(first.response.edits) == 1
    assert "✅ Confirmed admin request" in (first.response.edits[0][0] or "")
    assert (
        second.response.replies[0][0]
        == "This admin confirmation was already processed."
    )
    assert second.response.replies[0][1] is True
