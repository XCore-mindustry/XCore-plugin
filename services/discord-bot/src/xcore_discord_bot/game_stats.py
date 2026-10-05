"""What a player has done in the games the servers recorded.

The plugin owns `games_v2` and shows the same figures in the in-game profile; the bot only
reads them.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .mongo_store import MongoStore

GAMES = "games_v2"

PVP = "PVP"
SURVIVAL = "SURVIVAL"
HEXED = "HEXED"


def _int(value: Any) -> int:
    try:
        return 0 if value is None else round(float(value))
    except (TypeError, ValueError):
        return 0


@dataclass(frozen=True, slots=True)
class ModeStats:
    """One mode's games; the waves matter in survival, the placements in Hexed."""

    games: int = 0
    wins: int = 0
    best_wave: int = 0
    average_wave: int = 0
    best_placement: int = 0
    top3: int = 0

    @property
    def win_rate(self) -> int:
        return win_rate(self.wins, self.games)


@dataclass(frozen=True, slots=True)
class GameStats:
    """Every counted game of a player, in total and mode by mode."""

    games: int = 0
    wins: int = 0
    blocks_built: int = 0
    blocks_deconstructed: int = 0
    blocks_destroyed: int = 0
    units_produced: int = 0
    units_destroyed: int = 0
    pvp: ModeStats = ModeStats()
    survival: ModeStats = ModeStats()
    hexed: ModeStats = ModeStats()

    @property
    def win_rate(self) -> int:
        return win_rate(self.wins, self.games)


def win_rate(wins: int, games: int) -> int:
    return round(wins * 100 / games) if games > 0 else 0


def _mode(row: dict[str, Any] | None) -> ModeStats:
    if row is None:
        return ModeStats()
    return ModeStats(
        games=_int(row.get("games")),
        wins=_int(row.get("wins")),
        best_wave=_int(row.get("best_wave")),
        average_wave=_int(row.get("average_wave")),
        best_placement=_int(row.get("best_placement")),
        top3=_int(row.get("top3")),
    )


def game_stats_from_rows(rows: list[dict[str, Any]]) -> GameStats:
    """Folds the per-category rows of the aggregation; the totals cover every category."""
    by_category = {str(row.get("_id") or ""): row for row in rows}

    def total(field: str) -> int:
        return sum(_int(row.get(field)) for row in rows)

    return GameStats(
        games=total("games"),
        wins=total("wins"),
        blocks_built=total("blocks_built"),
        blocks_deconstructed=total("blocks_deconstructed"),
        blocks_destroyed=total("blocks_destroyed"),
        units_produced=total("units_produced"),
        units_destroyed=total("units_destroyed"),
        pvp=_mode(by_category.get(PVP)),
        survival=_mode(by_category.get(SURVIVAL)),
        hexed=_mode(by_category.get(HEXED)),
    )


def _pipeline(uuid: str) -> list[dict[str, Any]]:
    placed_in_top3 = {
        "$and": [
            {"$ne": [{"$ifNull": ["$player_stats.placement", None]}, None]},
            {"$lte": ["$player_stats.placement", 3]},
        ]
    }
    return [
        {"$match": {"player_stats.uuid": uuid, "counted_in_stats": True}},
        {"$unwind": "$player_stats"},
        {"$match": {"player_stats.uuid": uuid}},
        {
            "$group": {
                "_id": "$stats_category",
                "games": {"$sum": 1},
                "wins": {"$sum": {"$cond": ["$player_stats.isWinner", 1, 0]}},
                "best_wave": {"$max": "$waves_reached"},
                "average_wave": {"$avg": "$waves_reached"},
                "best_placement": {"$min": "$player_stats.placement"},
                "top3": {"$sum": {"$cond": [placed_in_top3, 1, 0]}},
                "blocks_built": {"$sum": "$player_stats.blocks_built"},
                "blocks_deconstructed": {"$sum": "$player_stats.blocks_deconstructed"},
                "blocks_destroyed": {"$sum": "$player_stats.blocks_destroyed"},
                "units_produced": {"$sum": "$player_stats.units_produced"},
                "units_destroyed": {"$sum": "$player_stats.units_destroyed"},
            }
        },
    ]


class GameStatsStore:
    def __init__(self, store: MongoStore) -> None:
        self._store = store

    async def overview(self, uuid: str) -> GameStats:
        if not uuid:
            return GameStats()
        cursor = self._store.database[GAMES].aggregate(_pipeline(uuid))
        return game_stats_from_rows([row async for row in cursor])
