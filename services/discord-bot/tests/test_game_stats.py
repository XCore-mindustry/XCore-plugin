from __future__ import annotations

from typing import Any

import pytest
from backends import connected_store

from xcore_discord_bot.game_stats import (
    GameStats,
    GameStatsStore,
    game_stats_from_rows,
)


def test_rows_fold_into_totals_and_modes() -> None:
    stats = game_stats_from_rows(
        [
            {"_id": "PVP", "games": 10, "wins": 6, "blocks_built": 100},
            {
                "_id": "SURVIVAL",
                "games": 4,
                "wins": 1,
                "best_wave": 120,
                "average_wave": 44.6,
                "blocks_built": 50,
                "units_produced": 7,
            },
            {"_id": "HEXED", "games": 3, "wins": 1, "best_placement": 2, "top3": 2},
            # an event has no section of its own, but its games still count
            {"_id": "EVENT", "games": 3, "wins": 2, "blocks_destroyed": 9},
            {"_id": None, "games": 1, "wins": 0, "best_placement": None},
        ]
    )

    assert (stats.games, stats.wins, stats.win_rate) == (21, 10, 48)
    assert (stats.blocks_built, stats.blocks_destroyed, stats.units_produced) == (
        150,
        9,
        7,
    )
    assert (stats.pvp.games, stats.pvp.wins, stats.pvp.win_rate) == (10, 6, 60)
    assert (stats.survival.best_wave, stats.survival.average_wave) == (120, 45)
    assert (stats.hexed.best_placement, stats.hexed.top3) == (2, 2)


def test_no_rows_is_a_player_without_games() -> None:
    assert game_stats_from_rows([]) == GameStats()
    assert GameStats().win_rate == 0


def _game(category: str, *players: dict[str, Any], **game: Any) -> dict[str, Any]:
    return {
        "stats_category": category,
        "counted_in_stats": True,
        "player_stats": list(players),
        **game,
    }


def _me(**stats: Any) -> dict[str, Any]:
    return {"uuid": "uuid-1", **stats}


@pytest.mark.asyncio
async def test_overview_counts_only_the_games_that_count_for_this_player() -> None:
    store, db = await connected_store()
    other = {"uuid": "uuid-2", "isWinner": True, "blocks_built": 100, "placement": 1}
    for game in [
        _game("PVP", _me(isWinner=True, blocks_built=5), other),
        _game("PVP", _me(isWinner=False, blocks_built=3)),
        _game("SURVIVAL", _me(units_produced=4), waves_reached=10),
        _game("SURVIVAL", _me(), waves_reached=20),
        _game("HEXED", _me(placement=2), other),
        _game("HEXED", _me(placement=5)),
        # no placement is not a top-3 finish
        _game("HEXED", _me()),
        # neither a game that does not count nor someone else's game
        _game("PVP", _me(isWinner=True), counted_in_stats=False),
        _game("PVP", other),
    ]:
        await db["games_v2"].insert_one(game)

    stats = await GameStatsStore(store).overview("uuid-1")

    assert (stats.games, stats.wins, stats.win_rate) == (7, 1, 14)
    # the other players' stats in the same games are not summed in
    assert (stats.blocks_built, stats.units_produced) == (8, 4)
    assert (stats.pvp.games, stats.pvp.wins, stats.pvp.win_rate) == (2, 1, 50)
    assert (stats.survival.best_wave, stats.survival.average_wave) == (20, 15)
    assert (stats.hexed.games, stats.hexed.best_placement, stats.hexed.top3) == (
        3,
        2,
        1,
    )


@pytest.mark.asyncio
async def test_overview_of_a_player_without_a_uuid_is_empty() -> None:
    store, db = await connected_store()
    await db["games_v2"].insert_one(_game("PVP", {"uuid": "", "isWinner": True}))

    assert await GameStatsStore(store).overview("") == GameStats()


def test_shared_materialized_fixture_and_states() -> None:
    import json
    from pathlib import Path

    from xcore_discord_bot.game_stats import game_stats_from_totals

    path = (
        Path(__file__).resolve().parents[3]
        / "plugins/xcore/src/test/resources/fixtures/player-totals.v1.json"
    )
    doc = json.loads(path.read_text())
    stats = game_stats_from_totals(doc)
    assert stats.games == 8
    assert stats.blocks_built == 3_000_000_000
    assert stats.survival.win_rate == 100
    assert stats.hexed.best_placement == 1
    newcomer = game_stats_from_totals(None)
    assert (newcomer.status, newcomer.updating, newcomer.games) == ("ready", False, 0)
    pending = game_stats_from_totals({"schema_version": 1, "status": "pending"})
    assert (pending.status, pending.updating) == ("pending", True)
    assert game_stats_from_totals({"schema_version": 1, "status": "ready"}).games == 0
    with pytest.raises(ValueError):
        game_stats_from_totals({**doc, "schema_version": 99})


@pytest.mark.asyncio
async def test_materialized_store_reads_the_projection_instead_of_legacy_games() -> (
    None
):
    store, db = await connected_store()
    await db["player_totals"].insert_one(
        {
            "_id": "uuid-1",
            "schema_version": 1,
            "status": "ready",
            "all": {"games": 7, "wins": 4},
        }
    )
    stats = await GameStatsStore(store, materialized=True).overview("uuid-1")
    assert (stats.games, stats.wins) == (7, 4)
    newcomer = await GameStatsStore(store, materialized=True).overview("new-player")
    assert (newcomer.status, newcomer.games) == ("ready", 0)


def test_percentage_rounding_matches_java_and_typescript() -> None:
    from xcore_discord_bot.game_stats import win_rate

    assert win_rate(1, 8) == 13
