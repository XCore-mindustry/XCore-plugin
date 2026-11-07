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
        return (
            value
            if isinstance(value, int)
            else (0 if value is None else round(float(value)))
        )
    except (TypeError, ValueError, OverflowError):
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
    left: int = 0
    streak: int = 0
    best_streak: int = 0
    play_ms: int = 0
    completed_only: bool = False

    @property
    def win_rate(self) -> int:
        return win_rate(
            self.wins, self.games - self.left if self.completed_only else self.games
        )


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
    left: int = 0
    streak: int = 0
    status: str = "ready"
    updating: bool = False
    # Left, streaks and play time exist only in the plugin's projection, not in the legacy rows.
    detailed: bool = False

    @property
    def win_rate(self) -> int:
        return win_rate(self.wins, self.games)


def win_rate(wins: int, games: int) -> int:
    return (200 * wins + games) // (2 * games) if games > 0 else 0


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


def game_stats_from_totals(doc: dict[str, Any] | None) -> GameStats:
    """Read the plugin's projection; no participation policy is duplicated here."""
    # Each stored game upserts its owners' totals, so a missing document means no games yet.
    if doc is None:
        return GameStats(detailed=True)
    if doc.get("status") != "ready":
        return GameStats(status="pending", updating=True, detailed=True)
    if doc.get("schema_version") != 1:
        raise ValueError("Unsupported player_totals schema")
    overall = doc.get("all") or {}
    modes = doc.get("modes") or {}

    def mode(category: str) -> ModeStats:
        row = modes.get(category) or {}
        n = _int(row.get("wave_n"))
        return ModeStats(
            games=_int(row.get("games")),
            wins=_int(row.get("wins")),
            best_wave=_int(row.get("best_wave")),
            average_wave=(2 * _int(row.get("wave_sum")) + n) // (2 * n) if n else 0,
            best_placement=_int(row.get("best_place")),
            top3=_int(row.get("top3")),
            left=_int(row.get("left")),
            streak=_int(row.get("streak")),
            best_streak=_int(row.get("best_streak")),
            play_ms=_int(row.get("play_ms")),
            completed_only=category == SURVIVAL,
        )

    return GameStats(
        games=_int(overall.get("games")),
        wins=_int(overall.get("wins")),
        blocks_built=_int(overall.get("blocks_built")),
        blocks_deconstructed=_int(overall.get("blocks_deconstructed")),
        pvp=mode(PVP),
        survival=mode(SURVIVAL),
        hexed=mode(HEXED),
        left=_int(overall.get("left")),
        streak=_int(overall.get("streak")),
        updating=bool((doc.get("rebuild") or {}).get("dirty")),
        detailed=True,
    )


class GameStatsStore:
    def __init__(self, store: MongoStore, *, materialized: bool | None = None) -> None:
        self._store = store
        self._materialized = (
            store.stats_materialized if materialized is None else materialized
        )

    async def overview(self, uuid: str) -> GameStats:
        if not uuid:
            return GameStats()
        if self._materialized:
            doc = await self._store.database["player_totals"].find_one({"_id": uuid})
            return game_stats_from_totals(doc)
        cursor = await self._store.database[GAMES].aggregate(_pipeline(uuid))
        return game_stats_from_rows([row async for row in cursor])
