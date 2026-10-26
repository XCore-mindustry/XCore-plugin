from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
from typing import Any
from unittest.mock import MagicMock

import discord
import pytest
from bot_fakes import make_bot, make_settings
from cases_fakes import Collection, Database
from pymongo.errors import OperationFailure, PyMongoError

from xcore_discord_bot.account_merge_store import AccountMergeStore
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


class _MockBackend:
    def __init__(self, players: dict[int, PlayerRecord]) -> None:
        self._players = players
        self.merge_calls: list[dict[str, Any]] = []

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

    async def find_player_by_pid(self, player_id):
        p = self._players.get(player_id)
        return p

    async def find_player_by_uuid(self, uuid):
        return None


def _Mockbot(*args, **kwargs):
    backend = _MockBackend(*args, **kwargs)
    bot = make_bot(store=backend, bus=backend, settings=make_settings())
    bot.container.players.merge_player_accounts = backend.merge_player_accounts
    return bot


async def _run_merge(
    bot: _Mockbot,
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
    bot = _Mockbot({})
    interaction = _Interaction()
    await _run_merge(bot, interaction, 10, 10, "Test")

    assert len(interaction.response.sent_texts) == 1
    assert "Cannot merge an account into itself" in interaction.response.sent_texts[0]


@pytest.mark.asyncio
async def test_cmd_merge_player_shows_confirm_view() -> None:
    bot = _Mockbot({10: _source_player(), 20: _target_player()})
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
    bot = _Mockbot({10: _source_player(), 20: _target_player()})
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
    bot = _Mockbot(
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
    bot = _Mockbot({10: _source_player(), 20: _target_player(uuid="merged:uuid-other")})
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


SOURCE_DOC: dict[str, Any] = {
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
    "online": False,
}
TARGET_DOC: dict[str, Any] = {
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
SOURCE_BAN: dict[str, Any] = {
    "_id": "ban-1",
    "ban_id": 7,
    "uuid": "source-uuid",
    "ip": "1.2.3.4",
    "pid": 10,
    "name": "SourceUser",
    "admin_name": "Moderator",
    "reason": "Cheating",
}
SOURCE_MUTE: dict[str, Any] = {
    "_id": "mute-1",
    "uuid": "source-uuid",
    "pid": 10,
    "name": "SourceUser",
    "admin_name": "Moderator",
    "reason": "Spam",
}


async def _merge_store(
    *,
    source: dict[str, Any] | None = None,
    target: dict[str, Any] | None = None,
    bans: tuple[dict[str, Any], ...] = (),
    mutes: tuple[dict[str, Any], ...] = (),
) -> tuple[MongoStore, Database]:
    db = Database()
    store = MongoStore(make_settings())
    store._db = db  # type: ignore[assignment]
    await db["bans"].create_index(
        "ban_id", unique=True, partialFilterExpression={"ban_id": {"$type": "int"}}
    )
    await db["counters"].insert_one({"_id": "ban_id", "seq": 7})
    await db["players"].insert_one(dict(SOURCE_DOC, **(source or {})))
    await db["players"].insert_one(dict(TARGET_DOC, **(target or {})))
    await db["games_v2"].insert_one(
        {
            "_id": "game-1",
            "player_stats": [{"uuid": "source-uuid"}, {"uuid": "someone-else"}],
        }
    )
    for ban in bans:
        await db["bans"].insert_one(ban)
    for mute in mutes:
        await db["mutes"].insert_one(mute)
    return store, db


async def _merge(store: MongoStore, **overrides: Any) -> AccountMergeResult:
    kwargs: dict[str, Any] = {
        "source_pid": 10,
        "target_pid": 20,
        "actor_name": "Admin",
        "actor_discord_id": "999",
        "reason": "Device migration",
    }
    kwargs.update(overrides)
    return await AccountMergeStore(store, make_settings()).merge_player_accounts(
        **kwargs
    )


def _player(db: Database, doc_id: str) -> dict[str, Any]:
    return next(doc for doc in db["players"].docs if doc["_id"] == doc_id)


def _contents(db: Database) -> dict[str, list[dict[str, Any]]]:
    return {name: deepcopy(c.docs) for name, c in db.collections.items() if c.docs}


@pytest.mark.asyncio
async def test_merge_player_accounts_logic() -> None:
    store, db = await _merge_store()

    result = await _merge(store)

    assert result.success is True
    assert result.games_transferred == 1
    assert result.source_before is not None and result.source_before.pid == 10
    assert result.target_before is not None and result.target_before.pid == 20
    assert result.target_after is not None
    assert result.target_after.total_play_time == 150

    # Default keeps the target PID on the surviving account.
    assert result.keep_pid == MERGE_KEEP_PID_TARGET
    assert result.surviving_pid == 20
    assert result.tombstone_pid == 10

    target = _player(db, "t-id")
    assert target["pid"] == 20
    assert target["total_play_time"] == 150
    assert target["pvp_rating"] == 1600
    assert target["hexed_points"] == 20
    assert target["unlocked_badges"] == ["b1", "b2"]
    assert target["discord_id"] == "disc-1"

    source = _player(db, "s-id")
    assert source["uuid"] == "merged:source-uuid"
    assert source["total_play_time"] == 0
    assert source["pid"] == 10
    assert "#20" in source["description"]

    assert db["games_v2"].docs[0]["player_stats"] == [
        {"uuid": "target-uuid"},
        {"uuid": "someone-else"},
    ]

    (audit_doc,) = db["moderation_audit"].docs
    assert audit_doc["audit_id"] == result.audit_id
    assert audit_doc["action"] == "MERGE"
    assert audit_doc["target"]["pid"] == 20
    merge_extra = audit_doc["details"]["extra"]["merge"]
    assert merge_extra["source_pid"] == 10
    assert merge_extra["surviving_pid"] == 20
    assert merge_extra["tombstone_pid"] == 10
    assert merge_extra["source_uuid"] == "source-uuid"


@pytest.mark.asyncio
async def test_merge_keeps_source_pid_when_requested() -> None:
    store, db = await _merge_store()

    result = await _merge(store, keep_pid=MERGE_KEEP_PID_SOURCE)

    assert result.success is True
    assert result.keep_pid == MERGE_KEEP_PID_SOURCE
    assert result.surviving_pid == 10
    assert result.tombstone_pid == 20

    # UUID always comes from the target, only the PID moves.
    target = _player(db, "t-id")
    assert target["pid"] == 10
    assert target["uuid"] == "target-uuid"

    source = _player(db, "s-id")
    assert source["pid"] == 20
    assert source["uuid"] == "merged:source-uuid"
    assert "Merged into PID #10" in source["description"]

    (audit_doc,) = db["moderation_audit"].docs
    assert audit_doc["target"]["pid"] == 10
    assert audit_doc["details"]["extra"]["merge"]["keep_pid"] == MERGE_KEEP_PID_SOURCE


@pytest.mark.asyncio
async def test_merge_clears_source_discord_link() -> None:
    store, db = await _merge_store()

    result = await _merge(store)

    assert result.discord_link_moved is True
    assert result.discord_link_conflict is False
    source = _player(db, "s-id")
    assert source["discord_id"] == ""
    assert source["discord_linked_at"] == 0


@pytest.mark.asyncio
async def test_merge_rejects_online_player() -> None:
    store, db = await _merge_store(source={"online": True})
    before = _contents(db)

    result = await _merge(store)

    assert result.success is False
    assert "#10" in (result.error or "")
    assert _contents(db) == before


@pytest.mark.asyncio
async def test_merge_rejects_closed_target() -> None:
    store, db = await _merge_store(target={"uuid": "merged:old-target"})
    before = _contents(db)

    result = await _merge(store)

    assert result.success is False
    assert "closed account" in (result.error or "")
    assert _contents(db) == before


@pytest.mark.asyncio
async def test_merge_rejects_unknown_keep_pid() -> None:
    store, db = await _merge_store()
    before = _contents(db)

    result = await _merge(store, keep_pid="whatever")

    assert result.success is False
    assert "keep_pid" in (result.error or "")
    assert _contents(db) == before


@pytest.mark.asyncio
async def test_merge_rewrites_punishment_pids() -> None:
    store, db = await _merge_store(bans=(SOURCE_BAN,))

    result = await _merge(store, keep_pid=MERGE_KEEP_PID_SOURCE)

    assert result.success is True
    assert result.ban_transferred is True

    kept, transferred = db["bans"].docs
    # Closed account keeps its own punishment with the tombstone PID, banning its
    # device UUID only: the IP now belongs to the surviving account's ban.
    expected = dict(SOURCE_BAN, pid=20)
    del expected["ip"]
    assert kept == expected
    assert transferred["ip"] == "1.2.3.4"
    # Transferred ban belongs to the surviving account (PID 10 in this scenario).
    assert transferred["uuid"] == "target-uuid"
    assert transferred["pid"] == 10
    assert transferred["reason"] == "[Merged from #10] Cheating"


@pytest.mark.asyncio
async def test_merge_gives_the_transferred_ban_its_own_id() -> None:
    # ban_id is unique; copying the closed account's id made the insert fail mid-merge.
    store, db = await _merge_store(bans=(SOURCE_BAN,))

    result = await _merge(store)

    assert result.success is True
    assert [ban["ban_id"] for ban in db["bans"].docs] == [7, 8]
    assert db["counters"].docs == [{"_id": "ban_id", "seq": 8}]


@pytest.mark.asyncio
async def test_merge_unban_of_the_surviving_account_lifts_the_ip_ban() -> None:
    store, _ = await _merge_store(bans=(SOURCE_BAN,))
    await _merge(store)

    survivor_ban = await store.find_ban(uuid="target-uuid", ip="1.2.3.4")
    assert survivor_ban is not None and survivor_ban.ban_id == 8
    await store.delete_ban(uuid="target-uuid", ip="1.2.3.4", ban_id=8)

    assert await store.find_ban(uuid="target-uuid", ip="1.2.3.4") is None
    # The closed account's device stays banned.
    assert await store.find_ban(uuid="source-uuid", ip=None) is not None


@pytest.mark.asyncio
async def test_merge_strips_the_ip_when_the_target_has_its_own_ban() -> None:
    target_ban = dict(SOURCE_BAN, _id="ban-2", ban_id=3, uuid="target-uuid", pid=20)
    store, db = await _merge_store(bans=(SOURCE_BAN, target_ban))

    result = await _merge(store)

    assert result.ban_transferred is False
    kept, own = db["bans"].docs
    assert "ip" not in kept
    assert own["ip"] == "1.2.3.4"


def _fail_call(collection: Collection, method: str, call: int) -> None:
    """Make the ``call``-th call of ``collection.method`` raise, after the earlier ones ran."""
    original = getattr(collection, method)
    calls = 0

    async def failing(*args: Any, **kwargs: Any) -> Any:
        nonlocal calls
        calls += 1
        if calls == call:
            raise PyMongoError(f"Injected {method} failure")
        return await original(*args, **kwargs)

    setattr(collection, method, failing)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("collection", "method", "call"),
    [
        ("players", "update_one", 1),
        ("players", "update_one", 2),
        ("games_v2", "update_many", 1),
        ("bans", "insert_one", 1),
        ("counters", "find_one_and_update", 1),
        ("mutes", "update_one", 1),
        ("mutes", "insert_one", 1),
        ("moderation_audit", "insert_one", 1),
    ],
)
async def test_merge_failure_leaves_both_accounts_unchanged(
    collection: str, method: str, call: int
) -> None:
    store, db = await _merge_store(bans=(SOURCE_BAN,), mutes=(SOURCE_MUTE,))
    before = _contents(db)
    _fail_call(db[collection], method, call)

    with pytest.raises(PyMongoError, match="Injected"):
        await _merge(store)

    assert _contents(db) == before


@pytest.mark.asyncio
async def test_merge_without_replica_set_changes_nothing() -> None:
    store, db = await _merge_store()
    before = _contents(db)

    async def refuse(callback: Any) -> Any:
        raise OperationFailure(
            "Transaction numbers are only allowed on a replica set member or mongos",
            code=20,
        )

    session = db.client.start_session()
    session.with_transaction = refuse  # type: ignore[method-assign]
    db.client.start_session = lambda: session  # type: ignore[method-assign]

    result = await _merge(store)

    assert result.success is False
    assert "replica set" in (result.error or "")
    assert _contents(db) == before
