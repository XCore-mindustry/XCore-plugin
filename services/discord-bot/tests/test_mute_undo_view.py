from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest
from backends import real_bot

from xcore_discord_bot.handlers_moderation import cmd_mute
from xcore_discord_bot.moderation_views import MuteUndoView


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


MUTE = {"uuid": "uuid-123", "pid": 123, "name": "Vortex", "reason": "spam"}


async def _muted_bot():
    bot, db = await real_bot()
    await db["players"].insert_one(
        {"pid": 123, "uuid": "uuid-123", "nickname": "Vortex"}
    )
    await db["mutes"].insert_one(dict(MUTE))
    return bot, db


def _view(bot) -> MuteUndoView:
    return MuteUndoView(
        requester_id=10,
        uuid="uuid-123",
        player_name="Vortex",
        delete_mute=bot.container.punishments.delete_mute,
    )


@pytest.mark.asyncio
async def test_mute_undo_view_blocks_other_users() -> None:
    bot, db = await _muted_bot()
    view = _view(bot)
    interaction = _Interaction(id=1, user=_User(id=11, display_name="other"))

    allowed = await view.interaction_check(interaction)

    assert allowed is False
    assert interaction.response.sent == [
        ("Only the moderator who started this action can undo it.", True)
    ]
    assert await db["mutes"].count_documents({}) == 1


@pytest.mark.asyncio
async def test_mute_undo_view_removes_mute_and_edits_message() -> None:
    bot, db = await _muted_bot()
    view = _view(bot)
    interaction = _Interaction(id=2, user=_User(id=10, display_name="moderator"))

    undo_button = view.children[0]
    callback = undo_button.callback
    assert callback is not None
    await callback(interaction)

    assert await db["mutes"].count_documents({}) == 0
    assert interaction.response.edited == [
        {"content": "Mute undone for Vortex.", "view": None}
    ]


@pytest.mark.asyncio
async def test_mute_undo_view_timeout_hides_button() -> None:
    bot, _ = await _muted_bot()
    view = _view(bot)
    message = _Message()
    view.message = message

    await view.on_timeout()

    assert message.edits == [{"content": None, "view": None}]


@pytest.mark.asyncio
async def test_cmd_mute_attaches_undo_view() -> None:
    bot, db = await real_bot()
    await db["players"].insert_one(
        {"pid": 123, "uuid": "uuid-123", "nickname": "Vortex"}
    )

    interaction = _Interaction(id=99, user=_User(id=10, display_name="moderator"))
    await cmd_mute(bot, interaction, 123, "10m", "spam")

    assert len(interaction.response.command_messages) == 1
    sent = interaction.response.command_messages[0]
    assert sent["content"].startswith("Muted `Vortex` until")
    assert isinstance(sent["view"], MuteUndoView)
    assert sent["view"].message is interaction._original_message


@pytest.mark.asyncio
async def test_cmd_mute_undo_removes_the_stored_mute() -> None:
    bot, db = await real_bot()
    await db["players"].insert_one(
        {"pid": 123, "uuid": "uuid-123", "nickname": "Vortex"}
    )
    interaction = _Interaction(id=98, user=_User(id=10, display_name="moderator"))
    await cmd_mute(bot, interaction, 123, "10m", "spam")
    assert await db["mutes"].count_documents({"uuid": "uuid-123"}) == 1

    view = interaction.response.command_messages[0]["view"]
    callback = view.children[0].callback
    await callback(_Interaction(id=97, user=_User(id=10, display_name="moderator")))

    assert await db["mutes"].count_documents({}) == 0
