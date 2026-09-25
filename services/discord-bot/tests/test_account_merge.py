from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from xcore_discord_bot.dto import AccountMergeResult, PlayerRecord
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


@pytest.mark.asyncio
async def test_cmd_merge_player_self_merge_rejected() -> None:
    bot = _MockBot({})
    interaction = _Interaction()
    await cmd_merge_player(bot, interaction, 10, 10, "Test")  # type: ignore[arg-type]

    assert len(interaction.response.sent_texts) == 1
    assert "Нельзя объединить аккаунт сам с собой" in interaction.response.sent_texts[0]


@pytest.mark.asyncio
async def test_cmd_merge_player_shows_confirm_view() -> None:
    source = PlayerRecord(
        pid=10,
        nickname="OldAcc",
        uuid="uuid-source",
        total_play_time=120,
        pvp_rating=1500,
        hexed_points=10,
        unlocked_badges=("badge-1",),
    )
    target = PlayerRecord(
        pid=20,
        nickname="NewAcc",
        uuid="uuid-target",
        total_play_time=30,
        pvp_rating=1600,
        hexed_points=5,
        unlocked_badges=("badge-2",),
    )
    bot = _MockBot({10: source, 20: target})
    interaction = _Interaction()

    await cmd_merge_player(bot, interaction, 10, 20, "Reinstalled phone")  # type: ignore[arg-type]

    assert len(interaction.response.sent_embeds) == 1
    embed = interaction.response.sent_embeds[0]
    assert "Подтверждение слияния аккаунтов" in embed.title
    assert len(interaction.response.sent_views) == 1
    assert isinstance(interaction.response.sent_views[0], AccountMergeConfirmView)


@pytest.mark.asyncio
async def test_mongo_store_merge_player_accounts_logic() -> None:
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
    }

    async def mock_find_one(query: dict[str, Any]) -> dict[str, Any] | None:
        if query.get("pid") == 10:
            return dict(source_doc)
        if query.get("pid") == 20 or query.get("_id") == "t-id":
            return dict(target_doc)
        return None

    players_col.find_one = AsyncMock(side_effect=mock_find_one)
    players_col.update_one = AsyncMock(return_value=MagicMock(modified_count=1))
    games_col.update_many = AsyncMock(return_value=MagicMock(modified_count=3))
    bans_col.find_one = AsyncMock(return_value=None)
    mutes_col.find_one = AsyncMock(return_value=None)
    audit_col.insert_one = AsyncMock()

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

    # Verify update to target
    target_update_call = next(
        c for c in players_col.update_one.call_args_list if c[0][0] == {"_id": "t-id"}
    )
    set_fields = target_update_call[0][1]["$set"]
    assert set_fields["total_play_time"] == 150
    assert set_fields["pvp_rating"] == 1600
    assert set_fields["hexed_points"] == 20
    assert "b1" in set_fields["unlocked_badges"] and "b2" in set_fields["unlocked_badges"]
    assert set_fields["discord_id"] == "disc-1"

    # Verify source closed
    source_update_call = next(
        c for c in players_col.update_one.call_args_list if c[0][0] == {"_id": "s-id"}
    )
    source_set_fields = source_update_call[0][1]["$set"]
    assert source_set_fields["uuid"] == "merged:source-uuid"
    assert source_set_fields["total_play_time"] == 0

    # Verify audit record
    assert audit_col.insert_one.called
    audit_doc = audit_col.insert_one.call_args[0][0]
    assert audit_doc["action"] == "MERGE"
    assert audit_doc["target"]["pid"] == 20
