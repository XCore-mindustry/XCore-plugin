from __future__ import annotations

from types import SimpleNamespace
from typing import Any

import pytest

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
    assert (stats.blocks_built, stats.blocks_destroyed, stats.units_produced) == (150, 9, 7)
    assert (stats.pvp.games, stats.pvp.wins, stats.pvp.win_rate) == (10, 6, 60)
    assert (stats.survival.best_wave, stats.survival.average_wave) == (120, 45)
    assert (stats.hexed.best_placement, stats.hexed.top3) == (2, 2)


def test_no_rows_is_a_player_without_games() -> None:
    assert game_stats_from_rows([]) == GameStats()
    assert GameStats().win_rate == 0


class _Games:
    def __init__(self, rows: list[dict[str, Any]]) -> None:
        self.rows = rows
        self.pipelines: list[list[dict[str, Any]]] = []

    def aggregate(self, pipeline: list[dict[str, Any]]):
        self.pipelines.append(pipeline)

        async def generate():
            for row in self.rows:
                yield row

        return generate()


@pytest.mark.asyncio
async def test_overview_counts_only_the_games_that_count_for_this_player() -> None:
    games = _Games([{"_id": "PVP", "games": 2, "wins": 1}])
    store = GameStatsStore(SimpleNamespace(database={"games_v2": games}))

    stats = await store.overview("uuid-1")

    assert (stats.games, stats.pvp.wins) == (2, 1)
    match, unwind, own, group = games.pipelines[0]
    assert match == {"$match": {"player_stats.uuid": "uuid-1", "counted_in_stats": True}}
    assert unwind == {"$unwind": "$player_stats"}
    # the other players of the same games are dropped before anything is summed
    assert own == {"$match": {"player_stats.uuid": "uuid-1"}}
    assert group["$group"]["_id"] == "$stats_category"

    # nothing is read for a player without a UUID
    assert await store.overview("") == GameStats()
    assert len(games.pipelines) == 1
