from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest

from xcore_discord_bot.bot import XCoreDiscordBot, _MuteUndoView


@dataclass
class _User:
    id: int
    display_name: str


@dataclass
class _Message:
    edits: list[dict[str, Any]] = field(default_factory=list)

    async def edit(self, *, content: str | None = None, view: Any = None) -> None:
        self.edits.append({"content": content, "view": view})


@dataclass
class _Response:
    sent: list[tuple[str, bool]] = field(default_factory=list)
    edited: list[dict[str, Any]] = field(default_factory=list)
    command_messages: list[dict[str, Any]] = field(default_factory=list)

    async def send_message(
        self,
        content: str,
        *,
        ephemeral: bool = False,
        view: Any = None,
    ) -> None:
        if view is None:
            self.sent.append((content, ephemeral))
            return
        self.command_messages.append(
            {"content": content, "ephemeral": ephemeral, "view": view}
        )

    async def edit_message(
        self, *, content: str | None = None, view: Any = None
    ) -> None:
        self.edited.append({"content": content, "view": view})


@dataclass
class _Interaction:
    id: int
    user: _User
    response: _Response = field(default_factory=_Response)
    _original_message: _Message = field(default_factory=_Message)

    async def original_response(self) -> _Message:
        return self._original_message


class _Store:
    def __init__(self) -> None:
        self.deleted: list[str] = []
        self.upserts: list[dict[str, Any]] = []

    async def delete_mute(self, *, uuid: str) -> int:
        self.deleted.append(uuid)
        return 1

    async def find_player_by_pid(self, pid: int) -> dict[str, object] | None:
        if pid != 123:
            return None
        return {"pid": 123, "uuid": "uuid-123", "nickname": "Vortex"}

    def now_utc(self):
        from datetime import datetime, timezone

        return datetime(2026, 1, 1, tzinfo=timezone.utc)

    async def upsert_mute(
        self,
        *,
        uuid: str,
        name: str,
        admin_name: str,
        reason: str,
        expire_date: Any,
    ) -> None:
        self.upserts.append(
            {
                "uuid": uuid,
                "name": name,
                "admin_name": admin_name,
                "reason": reason,
                "expire_date": expire_date,
            }
        )


class _Bus:
    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:  # noqa: ARG002
        return True


@pytest.mark.asyncio
async def test_mute_undo_view_blocks_other_users() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()

    view = _MuteUndoView(
        bot=bot,
        requester_id=10,
        uuid="uuid-123",
        player_name="Vortex",
    )
    interaction = _Interaction(id=1, user=_User(id=11, display_name="other"))

    allowed = await view.interaction_check(interaction)

    assert allowed is False
    assert interaction.response.sent == [
        ("Only the moderator who started this action can undo it.", True)
    ]


@pytest.mark.asyncio
async def test_mute_undo_view_removes_mute_and_edits_message() -> None:
    bot = object.__new__(XCoreDiscordBot)
    store = _Store()
    bot.__dict__["_store"] = store

    view = _MuteUndoView(
        bot=bot,
        requester_id=10,
        uuid="uuid-123",
        player_name="Vortex",
    )
    interaction = _Interaction(id=2, user=_User(id=10, display_name="moderator"))

    undo_button = view.children[0]
    callback = undo_button.callback
    assert callback is not None
    await callback(interaction)

    assert store.deleted == ["uuid-123"]
    assert interaction.response.edited == [
        {"content": "Mute undone for Vortex.", "view": None}
    ]


@pytest.mark.asyncio
async def test_mute_undo_view_timeout_hides_button() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    view = _MuteUndoView(
        bot=bot,
        requester_id=10,
        uuid="uuid-123",
        player_name="Vortex",
    )
    message = _Message()
    view.message = message

    await view.on_timeout()

    assert message.edits == [{"content": None, "view": None}]


@pytest.mark.asyncio
async def test_cmd_mute_attaches_undo_view() -> None:
    bot = object.__new__(XCoreDiscordBot)
    store = _Store()
    bot.__dict__["_store"] = store
    bot.__dict__["_bus"] = _Bus()

    interaction = _Interaction(id=99, user=_User(id=10, display_name="moderator"))
    await XCoreDiscordBot._cmd_mute(bot, interaction, 123, "10m", "spam")

    assert len(interaction.response.command_messages) == 1
    sent = interaction.response.command_messages[0]
    assert sent["content"].startswith("Muted `Vortex` until")
    assert isinstance(sent["view"], _MuteUndoView)
    assert sent["view"].message is interaction._original_message
