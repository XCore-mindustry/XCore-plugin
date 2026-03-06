from __future__ import annotations

from dataclasses import dataclass, field
from datetime import timedelta
from types import SimpleNamespace
from typing import Any

import pytest

from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.moderation_views import (
    AdminRequestView,
    BanConfirmView,
    MapRemoveConfirmView,
    MuteUndoView,
)


@dataclass
class _Role:
    id: int


@dataclass
class _User:
    id: int
    display_name: str = "moderator"
    roles: list[_Role] = field(default_factory=list)
    mention: str = "@moderator"


@dataclass
class _Message:
    edits: list[dict[str, Any]] = field(default_factory=list)

    async def edit(self, *, content: str | None = None, view: Any = None) -> None:
        self.edits.append({"content": content, "view": view})


@dataclass
class _Response:
    replies: list[tuple[str, bool]] = field(default_factory=list)
    edits: list[dict[str, Any]] = field(default_factory=list)
    deferred: int = 0

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        self.replies.append((text, ephemeral))

    async def edit_message(
        self, *, content: str | None = None, view: Any = None
    ) -> None:
        self.edits.append({"content": content, "view": view})

    async def defer(self) -> None:
        self.deferred += 1


@dataclass
class _Interaction:
    user: _User
    response: _Response = field(default_factory=_Response)
    message: Any = None


@pytest.mark.asyncio
async def test_ban_confirm_view_cancel_disables_buttons() -> None:
    view = BanConfirmView(
        requester_id=7,
        player_id=12,
        player={"nickname": "Target"},
        period="1d",
        reason="reason",
        duration=timedelta(days=1),
        perform_ban=lambda **kwargs: None,
    )
    interaction = _Interaction(user=_User(id=7))

    callback = view.children[1].callback
    assert callback is not None
    await callback(interaction)

    assert interaction.response.edits[0]["content"] == "Ban cancelled."
    assert all(getattr(child, "disabled", False) for child in view.children)


@pytest.mark.asyncio
async def test_map_remove_confirm_view_cancel_edits_message() -> None:
    view = MapRemoveConfirmView(
        requester_id=7,
        server="mini-pvp",
        file_name="arena.msav",
        request_nonce="nonce-1",
        perform_remove_map=lambda **kwargs: None,
    )
    interaction = _Interaction(user=_User(id=7))

    callback = view.children[1].callback
    assert callback is not None
    await callback(interaction)

    assert interaction.response.edits[0]["content"] == "Map removal cancelled."
    assert all(getattr(child, "disabled", False) for child in view.children)


@pytest.mark.asyncio
async def test_map_remove_confirm_view_confirm_without_message_only_defers() -> None:
    calls: list[dict[str, str]] = []

    async def perform_remove_map(**kwargs: str) -> str:
        calls.append(kwargs)
        return "removed"

    view = MapRemoveConfirmView(
        requester_id=7,
        server="mini-pvp",
        file_name="arena.msav",
        request_nonce="nonce-2",
        perform_remove_map=perform_remove_map,
    )
    interaction = _Interaction(user=_User(id=7), message=None)

    callback = view.children[0].callback
    assert callback is not None
    await callback(interaction)

    assert interaction.response.deferred == 1
    assert calls == [
        {
            "server": "mini-pvp",
            "file_name": "arena.msav",
            "request_nonce": "nonce-2",
        }
    ]


@pytest.mark.asyncio
async def test_mute_undo_view_reports_already_inactive_mute() -> None:
    async def delete_mute(*, uuid: str) -> int:
        assert uuid == "uuid-1"
        return 0

    view = MuteUndoView(
        requester_id=7,
        uuid="uuid-1",
        player_name="Target",
        delete_mute=delete_mute,
    )
    interaction = _Interaction(user=_User(id=7))

    callback = view.children[0].callback
    assert callback is not None
    await callback(interaction)

    assert interaction.response.edits == [
        {"content": "Mute was already inactive for Target.", "view": None}
    ]


@pytest.mark.asyncio
async def test_admin_request_view_confirm_rejects_missing_player() -> None:
    async def find_player_by_pid(pid: int) -> None:
        assert pid == 5
        return None

    async def no_op(*args, **kwargs):
        return None

    view = AdminRequestView(
        settings=SimpleNamespace(discord_admin_role_id=10),
        server="mini-pvp",
        pid=5,
        request_nonce="nonce-1",
        find_player_by_pid=find_player_by_pid,
        claim_idempotency=no_op,
        mark_admin_confirmed=no_op,
        publish_admin_confirm=no_op,
        finalize_message=no_op,
    )
    interaction = _Interaction(user=_User(id=1, roles=[_Role(10)]))

    callback = view.children[0].callback
    assert callback is not None
    await callback(interaction)

    assert interaction.response.replies == [("Player not found", True)]


@pytest.mark.asyncio
async def test_admin_request_view_confirm_rejects_missing_uuid() -> None:
    async def find_player_by_pid(pid: int) -> PlayerRecord:
        assert pid == 5
        return PlayerRecord(pid=5, nickname="Nick", uuid="")

    async def no_op(*args, **kwargs):
        return None

    view = AdminRequestView(
        settings=SimpleNamespace(discord_admin_role_id=10),
        server="mini-pvp",
        pid=5,
        request_nonce="nonce-2",
        find_player_by_pid=find_player_by_pid,
        claim_idempotency=no_op,
        mark_admin_confirmed=no_op,
        publish_admin_confirm=no_op,
        finalize_message=no_op,
    )
    interaction = _Interaction(user=_User(id=1, roles=[_Role(10)]))

    callback = view.children[0].callback
    assert callback is not None
    await callback(interaction)

    assert interaction.response.replies == [("Player UUID is missing", True)]


@pytest.mark.asyncio
async def test_admin_request_view_decline_finalizes_message() -> None:
    finalized: list[str] = []

    async def find_player_by_pid(pid: int) -> PlayerRecord:
        assert pid == 5
        return PlayerRecord(pid=5, nickname="Nick", uuid="uuid-1")

    async def finalize_message(_interaction: _Interaction, status: str) -> None:
        finalized.append(status)

    async def no_op(*args, **kwargs):
        return None

    view = AdminRequestView(
        settings=SimpleNamespace(discord_admin_role_id=10),
        server="mini-pvp",
        pid=5,
        request_nonce="nonce-3",
        find_player_by_pid=find_player_by_pid,
        claim_idempotency=no_op,
        mark_admin_confirmed=no_op,
        publish_admin_confirm=no_op,
        finalize_message=finalize_message,
    )
    interaction = _Interaction(
        user=_User(id=1, display_name="boss", roles=[_Role(10)], mention="@boss")
    )

    callback = view.children[1].callback
    assert callback is not None
    await callback(interaction)

    assert finalized == ["❌ Declined admin request for `Nick` on `mini-pvp` by @boss"]
