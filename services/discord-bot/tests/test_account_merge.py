from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import discord
import pytest

from xcore_discord_bot.dto import (
    MERGE_KEEP_PID_SOURCE,
    MERGE_KEEP_PID_TARGET,
    AccountMergeResult,
    PlayerRecord,
)
from xcore_discord_bot.handlers_moderation import cmd_merge_player
from xcore_discord_bot.moderation_views import AccountMergeConfirmView
from xcore_discord_bot.mongo_store import MongoStore


@dataclass
class _User:
    id: int = 12345
    display_name: str = "TestAdmin"


@dataclass
class _Response:
    sent_embeds: list[Any] = field(default_factory=list)
    sent_views: list[Any] = field(default_factory=list)
    sent_texts: list[str] = field(default_factory=list)
    edited_embeds: list[Any] = field(default_factory=list)

    async def send_message(
        self,
        content: str | None = None,
        *,
        embed: Any | None = None,
        view: Any | None = None,
        ephemeral: bool = False,
    ) -> None:
        if content:
            self.sent_texts.append(content)
        if embed:
            self.sent_embeds.append(embed)
        if view:
            self.sent_views.append(view)

    async def edit_message(
        self,
        *,
        content: str | None = None,
        embed: Any | None = None,
        view: Any | None = None,
    ) -> None:
        if embed:
            self.edited_embeds.append(embed)


class _Interaction:
    def __init__(self, user: _User | None = None) -> None:
        self.id = 999
        self.user = user or _User()
        self.response = _Response()

    async def original_response(self) -> MagicMock:
        return MagicMock()


class _MockBot:
    def __init__(self, players: dict[int, PlayerRecord]) -> None:
        self._players = players
        self.merge_calls: list[dict[str, Any]] = []

    async def _get_player_or_reply(
        self, interaction: _Interaction, player_id: int
    ) -> PlayerRecord | None:
        p = self._players.get(player_id)
        if p is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return None
        return p

    def _player_name(self, player: PlayerRecord) -> str:
        return player.nickname

    async def merge_player_accounts(self, **kwargs: Any) -> AccountMergeResult:
        self.merge_calls.append(kwargs)
        s = self._players.get(kwargs["source_pid"])
        t = self._players.get(kwargs["target_pid"])
        return AccountMergeResult(
            success=True,
            source_before=s,
            target_before=t,
            target_after=t,
            games_transferred=5,
            audit_id="audit-test-123",
        )


async def _run_merge(
    bot: _MockBot,
    interaction: _Interaction,
    source_pid: int,
    target_pid: int,
    reason: str,
    keep_pid: str = MERGE_KEEP_PID_TARGET,
) -> None:
    # Test doubles intentionally stand in for the bot and the Discord interaction.
    await cmd_merge_player(bot, interaction, source_pid, target_pid, reason, keep_pid)  # type: ignore[arg-type]


async def _click(view: discord.ui.View, index: int, interaction: _Interaction) -> None:
    callback = view.children[index].callback
    assert callback is not None
    await callback(interaction)  # type: ignore[arg-type]


def _source_player(**overrides: Any) -> PlayerRecord:
    base: dict[str, Any] = {
        "pid": 10,
        "nickname": "OldAcc",
        "uuid": "uuid-source",
        "total_play_time": 120,
        "hexed_points": 10,
        "unlocked_badges": ("badge-1",),
    }
    base.update(overrides)
    return PlayerRecord(**base)


def _target_player(**overrides: Any) -> PlayerRecord:
    base: dict[str, Any] = {
        "pid": 20,
        "nickname": "NewAcc",
        "uuid": "uuid-target",
        "total_play_time": 30,
        "hexed_points": 5,
        "unlocked_badges": ("badge-2",),
    }
    base.update(overrides)
    return PlayerRecord(**base)


@pytest.mark.asyncio
async def test_cmd_merge_player_self_merge_rejected() -> None:
    bot = _MockBot({})
    interaction = _Interaction()
    await _run_merge(bot, interaction, 10, 10, "Test")

    assert len(interaction.response.sent_texts) == 1
    assert "Cannot merge an account into itself" in interaction.response.sent_texts[0]


@pytest.mark.asyncio
async def test_cmd_merge_player_shows_confirm_view() -> None:
    bot = _MockBot({10: _source_player(), 20: _target_player()})
    interaction = _Interaction()

    await _run_merge(bot, interaction, 10, 20, "Reinstalled phone")

    assert len(interaction.response.sent_embeds) == 1
    embed = interaction.response.sent_embeds[0]
    assert embed.title == "🔄 Confirm account merge"
    assert len(interaction.response.sent_views) == 1
    view = interaction.response.sent_views[0]
    assert isinstance(view, AccountMergeConfirmView)
    assert view._keep_pid == MERGE_KEEP_PID_TARGET


@pytest.mark.asyncio
async def test_cmd_merge_player_keeps_source_pid_in_preview() -> None:
    bot = _MockBot({10: _source_player(), 20: _target_player()})
    interaction = _Interaction()

    await _run_merge(
        bot, interaction, 10, 20, "Reinstalled phone", MERGE_KEEP_PID_SOURCE
    )

    embed = interaction.response.sent_embeds[0]
    identity_field = next(f for f in embed.fields if f.name == "🆔 Surviving identity")
    assert "PID: `#10` (kept from source, was #20)" in identity_field.value
    assert "UUID: from #20 (always)" in identity_field.value
    assert interaction.response.sent_views[0]._keep_pid == MERGE_KEEP_PID_SOURCE


@pytest.mark.asyncio
async def test_cmd_merge_player_blocks_online_account() -> None:
    bot = _MockBot(
        {
            10: _source_player(),
            20: _target_player(online=True, online_since=1, online_server="mini-pvp"),
        }
    )
    interaction = _Interaction()

    await _run_merge(bot, interaction, 10, 20, "Reinstalled phone")

    assert interaction.response.sent_embeds == []
    assert len(interaction.response.sent_texts) == 1
    assert "#20 (NewAcc)" in interaction.response.sent_texts[0]
    assert "disconnect" in interaction.response.sent_texts[0]


@pytest.mark.asyncio
async def test_cmd_merge_player_rejects_closed_target() -> None:
    bot = _MockBot({10: _source_player(), 20: _target_player(uuid="merged:uuid-other")})
    interaction = _Interaction()

    await _run_merge(bot, interaction, 10, 20, "Reinstalled phone")

    assert interaction.response.sent_embeds == []
    assert "closed" in interaction.response.sent_texts[0]


async def _unused_merge(**kwargs: Any) -> AccountMergeResult:
    return AccountMergeResult(success=True, audit_id="unused")


@pytest.mark.asyncio
async def test_merge_view_swap_pid_button_toggles_keep_pid() -> None:
    calls: list[dict[str, Any]] = []

    async def perform_merge(**kwargs: Any) -> AccountMergeResult:
        calls.append(kwargs)
        return AccountMergeResult(
            success=True,
            source_before=_source_player(),
            target_before=_target_player(),
            target_after=_target_player(),
            audit_id="audit-1",
        )

    view = AccountMergeConfirmView(
        requester_id=7,
        source_pid=10,
        target_pid=20,
        source_player=_source_player(),
        target_player=_target_player(),
        reason="Reinstalled phone",
        perform_merge=perform_merge,
    )
    interaction = _Interaction(user=_User(id=7))

    assert getattr(view.children[1], "label", None) == "Swap PID"

    await _click(view, 1, interaction)
    assert view._keep_pid == MERGE_KEEP_PID_SOURCE
    assert (
        "#10` (kept from source, was #20)"
        in interaction.response.edited_embeds[0].fields[2].value
    )

    await _click(view, 1, interaction)
    assert view._keep_pid == MERGE_KEEP_PID_TARGET
    assert (
        "#20` (kept from target, was #10)"
        in interaction.response.edited_embeds[1].fields[2].value
    )

    await _click(view, 0, interaction)

    assert calls[0]["keep_pid"] == MERGE_KEEP_PID_TARGET


@pytest.mark.asyncio
async def test_merge_view_cancel_reports_english_message() -> None:
    view = AccountMergeConfirmView(
        requester_id=7,
        source_pid=10,
        target_pid=20,
        source_player=_source_player(),
        target_player=_target_player(),
        reason="Reinstalled phone",
        perform_merge=_unused_merge,
    )
    interaction = _Interaction(user=_User(id=7))

    await _click(view, 2, interaction)

    assert interaction.response.edited_embeds[0].title == "Merge cancelled"
    assert all(getattr(child, "disabled", False) for child in view.children)


def _merge_store() -> tuple[MongoStore, dict[str, MagicMock]]:
    store = MongoStore(MagicMock())
    db = MagicMock()
    store._db = db

    players_col = MagicMock()
    games_col = MagicMock()
    bans_col = MagicMock()
    mutes_col = MagicMock()
    audit_col = MagicMock()

    cols = {
        "players": players_col,
        "games_v2": games_col,
        "bans": bans_col,
        "mutes": mutes_col,
        "moderation_audit": audit_col,
    }
    db.__getitem__.side_effect = lambda k: cols[k]

    return store, cols


def _wire_store(cols: dict[str, MagicMock], *, online_source: bool = False) -> None:
    source_doc = {
        "_id": "s-id",
        "pid": 10,
        "uuid": "source-uuid",
        "nickname": "SourceUser",
        "total_play_time": 100,
        "pvp_rating": 1400,
        "hexed_points": 15,
        "unlocked_badges": ["b1"],
        "discord_id": "disc-1",
        "discord_username": "user1",
        "online": online_source,
    }
    target_doc = {
        "_id": "t-id",
        "pid": 20,
        "uuid": "target-uuid",
        "nickname": "TargetUser",
        "total_play_time": 50,
        "pvp_rating": 1600,
        "hexed_points": 5,
        "unlocked_badges": ["b2"],
        "discord_id": None,
        "online": False,
    }

    async def mock_find_one(query: dict[str, Any]) -> dict[str, Any] | None:
        if query.get("pid") == 10:
            return dict(source_doc)
        if query.get("pid") == 20 or query.get("_id") == "t-id":
            return dict(target_doc)
        return None

    cols["players"].find_one = AsyncMock(side_effect=mock_find_one)
    cols["players"].update_one = AsyncMock(return_value=MagicMock(modified_count=1))
    cols["games_v2"].update_many = AsyncMock(return_value=MagicMock(modified_count=3))
    for name in ("bans", "mutes"):
        cols[name].find_one = AsyncMock(return_value=None)
        cols[name].update_one = AsyncMock(return_value=MagicMock(modified_count=1))
        cols[name].insert_one = AsyncMock()
    cols["moderation_audit"].insert_one = AsyncMock()


def _set_fields(cols: dict[str, MagicMock], doc_id: str) -> dict[str, Any]:
    update_call = next(
        c
        for c in cols["players"].update_one.call_args_list
        if c[0][0] == {"_id": doc_id}
    )
    return update_call[0][1]["$set"]


@pytest.mark.asyncio
async def test_mongo_store_merge_player_accounts_logic() -> None:
    store, cols = _merge_store()
    _wire_store(cols)

    result = await store.merge_player_accounts(
        source_pid=10,
        target_pid=20,
        actor_name="Admin",
        actor_discord_id="999",
        reason="Device migration",
    )

    assert result.success is True
    assert result.games_transferred == 3
    assert result.source_before is not None and result.source_before.pid == 10
    assert result.target_before is not None and result.target_before.pid == 20

    # Default keeps the target PID on the surviving account.
    assert result.keep_pid == MERGE_KEEP_PID_TARGET
    assert result.surviving_pid == 20
    assert result.tombstone_pid == 10

    target_set = _set_fields(cols, "t-id")
    assert target_set["pid"] == 20
    assert target_set["total_play_time"] == 150
    assert "pvp_rating" not in target_set
    assert target_set["hexed_points"] == 20
    assert (
        "b1" in target_set["unlocked_badges"] and "b2" in target_set["unlocked_badges"]
    )
    assert target_set["discord_id"] == "disc-1"

    source_set = _set_fields(cols, "s-id")
    assert source_set["uuid"] == "merged:source-uuid"
    assert source_set["total_play_time"] == 0
    assert source_set["pid"] == 10
    assert "#20" in source_set["description"]

    audit_doc = cols["moderation_audit"].insert_one.call_args[0][0]
    assert audit_doc["action"] == "MERGE"
    assert audit_doc["target"]["pid"] == 20
    merge_extra = audit_doc["details"]["extra"]["merge"]
    assert merge_extra["source_pid"] == 10
    assert merge_extra["surviving_pid"] == 20
    assert merge_extra["tombstone_pid"] == 10
    assert merge_extra["source_uuid"] == "source-uuid"


@pytest.mark.asyncio
async def test_mongo_store_merge_keeps_source_pid_when_requested() -> None:
    store, cols = _merge_store()
    _wire_store(cols)

    result = await store.merge_player_accounts(
        source_pid=10,
        target_pid=20,
        actor_name="Admin",
        actor_discord_id="999",
        reason="Device migration",
        keep_pid=MERGE_KEEP_PID_SOURCE,
    )

    assert result.success is True
    assert result.keep_pid == MERGE_KEEP_PID_SOURCE
    assert result.surviving_pid == 10
    assert result.tombstone_pid == 20

    # UUID always comes from the target, only the PID moves.
    target_set = _set_fields(cols, "t-id")
    assert target_set["pid"] == 10

    source_set = _set_fields(cols, "s-id")
    assert source_set["pid"] == 20
    assert source_set["uuid"] == "merged:source-uuid"
    assert "Merged into PID #10" in source_set["description"]

    audit_doc = cols["moderation_audit"].insert_one.call_args[0][0]
    assert audit_doc["target"]["pid"] == 10
    assert audit_doc["details"]["extra"]["merge"]["keep_pid"] == MERGE_KEEP_PID_SOURCE


@pytest.mark.asyncio
async def test_mongo_store_merge_clears_source_discord_link() -> None:
    store, cols = _merge_store()
    _wire_store(cols)

    result = await store.merge_player_accounts(
        source_pid=10,
        target_pid=20,
        actor_name="Admin",
        actor_discord_id="999",
        reason="Device migration",
    )

    assert result.discord_link_moved is True
    assert result.discord_link_conflict is False
    source_set = _set_fields(cols, "s-id")
    assert source_set["discord_id"] == ""
    assert source_set["discord_linked_at"] == 0


@pytest.mark.asyncio
async def test_mongo_store_merge_rejects_online_player() -> None:
    store, cols = _merge_store()
    _wire_store(cols, online_source=True)

    result = await store.merge_player_accounts(
        source_pid=10,
        target_pid=20,
        actor_name="Admin",
        actor_discord_id="999",
        reason="Device migration",
    )

    assert result.success is False
    assert "#10" in (result.error or "")
    assert cols["players"].update_one.await_count == 0


@pytest.mark.asyncio
async def test_mongo_store_merge_rejects_closed_target() -> None:
    store, cols = _merge_store()
    _wire_store(cols)

    async def mock_find_one(query: dict[str, Any]) -> dict[str, Any] | None:
        if query.get("pid") == 10:
            return {
                "_id": "s-id",
                "pid": 10,
                "uuid": "source-uuid",
                "nickname": "SourceUser",
            }
        if query.get("pid") == 20:
            return {
                "_id": "t-id",
                "pid": 20,
                "uuid": "merged:old-target",
                "nickname": "TargetUser",
            }
        return None

    cols["players"].find_one = AsyncMock(side_effect=mock_find_one)

    result = await store.merge_player_accounts(
        source_pid=10,
        target_pid=20,
        actor_name="Admin",
        actor_discord_id="999",
        reason="Device migration",
    )

    assert result.success is False
    assert "closed account" in (result.error or "")


@pytest.mark.asyncio
async def test_mongo_store_merge_rejects_unknown_keep_pid() -> None:
    store, cols = _merge_store()
    _wire_store(cols)

    result = await store.merge_player_accounts(
        source_pid=10,
        target_pid=20,
        actor_name="Admin",
        actor_discord_id="999",
        reason="Device migration",
        keep_pid="whatever",
    )

    assert result.success is False
    assert "keep_pid" in (result.error or "")
    assert cols["players"].update_one.await_count == 0


@pytest.mark.asyncio
async def test_mongo_store_merge_rewrites_punishment_pids() -> None:
    store, cols = _merge_store()
    _wire_store(cols)

    source_ban = {
        "_id": "ban-1",
        "uuid": "source-uuid",
        "pid": 10,
        "name": "SourceUser",
        "reason": "Cheating",
    }

    async def ban_find_one(query: dict[str, Any]) -> dict[str, Any] | None:
        if query.get("uuid") == "source-uuid":
            return dict(source_ban)
        return None

    cols["bans"].find_one = AsyncMock(side_effect=ban_find_one)

    result = await store.merge_player_accounts(
        source_pid=10,
        target_pid=20,
        actor_name="Admin",
        actor_discord_id="999",
        reason="Device migration",
        keep_pid=MERGE_KEEP_PID_SOURCE,
    )

    assert result.success is True
    assert result.ban_transferred is True

    # Closed account keeps its own punishment with the tombstone PID.
    cols["bans"].update_one.assert_awaited_once_with(
        {"_id": "ban-1"}, {"$set": {"pid": 20}}
    )
    # Transferred ban belongs to the surviving account (PID 10 in this scenario).
    transferred = cols["bans"].insert_one.call_args[0][0]
    assert transferred["uuid"] == "target-uuid"
    assert transferred["pid"] == 10
