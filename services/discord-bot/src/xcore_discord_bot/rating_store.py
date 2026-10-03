"""Read access to the rating seasons and standings the game servers keep in Mongo.

The plugin owns these collections: the bot only reads them, plus the little bookkeeping it
needs to behave well (which season announcements it already posted, which account merges
still have to reach the ladders). Everything that changes a season goes through the plugin.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from pymongo import ASCENDING, DESCENDING
from pymongo.errors import DuplicateKeyError

from .mongo_store import MongoStore

logger = logging.getLogger(__name__)

SEASONS = "rating_seasons"
STANDINGS = "rating_standings"
PLAYERS = "players"
SEASON_POSTS = "discord_season_posts"
PENDING_MERGES = "rating_merge_pending"

ACTIVE = "ACTIVE"
CLOSING = "CLOSING"
ARCHIVED = "ARCHIVED"


def _int(value: Any, default: int = 0) -> int:
    try:
        return default if value is None else int(value)
    except (TypeError, ValueError):
        return default


def _utc(value: Any) -> datetime:
    if isinstance(value, datetime):
        return value if value.tzinfo is not None else value.replace(tzinfo=UTC)
    return datetime.fromtimestamp(0, UTC)


@dataclass(frozen=True, slots=True)
class PodiumEntry:
    place: int
    uuid: str
    pid: int | None
    nickname: str
    rating: int
    league: str
    matches: int
    wins: int
    discord_id: str | None
    discord_username: str | None


@dataclass(frozen=True, slots=True)
class Season:
    ladder: str
    number: int
    name: str
    status: str
    starts_at: datetime
    ends_at: datetime
    matches: int
    participants: int | None
    podium: tuple[PodiumEntry, ...]

    @property
    def id(self) -> str:
        return f"{self.ladder}:{self.number}"

    @property
    def title(self) -> str:
        return self.name or f"Season {self.number}"

    @property
    def running(self) -> bool:
        return self.status == ACTIVE


@dataclass(frozen=True, slots=True)
class RankedStanding:
    rank: int
    uuid: str
    nickname: str
    pid: int | None
    rating: int
    matches: int
    wins: int


@dataclass(frozen=True, slots=True)
class Placing:
    """Where a player stands on one ladder in the season that is running now."""

    season: Season
    rating: int
    matches: int
    wins: int
    rank: int
    participants: int


def season_from_doc(doc: dict[str, Any]) -> Season:
    summary = doc.get("summary") or {}
    return Season(
        ladder=str(doc.get("ladder") or ""),
        number=_int(doc.get("number"), 1),
        name=str(doc.get("name") or "").strip(),
        status=str(doc.get("status") or ACTIVE),
        starts_at=_utc(doc.get("starts_at")),
        ends_at=_utc(doc.get("ends_at")),
        matches=_int(doc.get("matches")),
        participants=_int(summary["participants"]) if "participants" in summary else None,
        podium=tuple(_podium_entry(entry) for entry in doc.get("podium") or ()),
    )


def _podium_entry(doc: dict[str, Any]) -> PodiumEntry:
    pid = _int(doc.get("pid"), -1)
    return PodiumEntry(
        place=_int(doc.get("place")),
        uuid=str(doc.get("uuid") or ""),
        pid=pid if pid > 0 else None,
        nickname=str(doc.get("nickname") or "Unknown"),
        rating=_int(doc.get("rating")),
        league=str(doc.get("league") or ""),
        matches=_int(doc.get("matches")),
        wins=_int(doc.get("wins")),
        discord_id=str(doc.get("discord_id") or "").strip() or None,
        discord_username=str(doc.get("discord_username") or "").strip() or None,
    )


class RatingStore:
    def __init__(self, store: MongoStore) -> None:
        self._store = store

    def _collection(self, name: str):
        return self._store.database[name]

    # ------------------------------------------------------------------ seasons

    async def ladders(self) -> list[str]:
        found = await self._collection(SEASONS).distinct("ladder")
        return sorted(str(ladder) for ladder in found)

    async def list_seasons(self, ladder: str) -> list[Season]:
        """Newest first."""
        cursor = self._collection(SEASONS).find({"ladder": ladder}).sort(
            "number", DESCENDING
        )
        return [season_from_doc(doc) async for doc in cursor]

    async def find_season(self, ladder: str, number: int) -> Season | None:
        doc = await self._collection(SEASONS).find_one({"_id": f"{ladder}:{number}"})
        return season_from_doc(doc) if doc else None

    async def current_season(self, ladder: str) -> Season | None:
        """The running season, or the newest one while the next is still being opened."""
        seasons = await self.list_seasons(ladder)
        return next((season for season in seasons if season.running), None) or (
            seasons[0] if seasons else None
        )

    # ---------------------------------------------------------------- standings

    async def count(self, ladder: str, season: int) -> int:
        return _int(
            await self._collection(STANDINGS).count_documents(
                {"ladder": ladder, "season": season}
            )
        )

    async def top(self, ladder: str, season: int, limit: int = 10) -> list[RankedStanding]:
        cursor = (
            self._collection(STANDINGS)
            .find({"ladder": ladder, "season": season})
            .sort([("rating", DESCENDING), ("player_uuid", ASCENDING)])
            .limit(limit)
        )
        docs = [doc async for doc in cursor]
        names = await self._players([str(doc.get("player_uuid") or "") for doc in docs])
        ranked: list[RankedStanding] = []
        for index, doc in enumerate(docs, start=1):
            uuid = str(doc.get("player_uuid") or "")
            nickname, pid = names.get(uuid, ("Unknown", None))
            ranked.append(
                RankedStanding(
                    rank=index,
                    uuid=uuid,
                    nickname=nickname,
                    pid=pid,
                    rating=_int(doc.get("rating")),
                    matches=_int(doc.get("matches")),
                    wins=_int(doc.get("wins")),
                )
            )
        return ranked

    async def placings(self, uuid: str) -> list[Placing]:
        """The player's standing on every ladder, in the season each one is running now."""
        placings: list[Placing] = []
        for ladder in await self.ladders():
            season = await self.current_season(ladder)
            if season is None:
                continue
            doc = await self._collection(STANDINGS).find_one(
                {"ladder": ladder, "season": season.number, "player_uuid": uuid}
            )
            if doc is None:
                continue
            rating = _int(doc.get("rating"))
            ahead = await self._collection(STANDINGS).count_documents(
                {
                    "ladder": ladder,
                    "season": season.number,
                    "$or": [
                        {"rating": {"$gt": rating}},
                        {"rating": rating, "player_uuid": {"$lt": uuid}},
                    ],
                }
            )
            placings.append(
                Placing(
                    season=season,
                    rating=rating,
                    matches=_int(doc.get("matches")),
                    wins=_int(doc.get("wins")),
                    rank=_int(ahead) + 1,
                    participants=await self.count(ladder, season.number),
                )
            )
        return placings

    async def _players(self, uuids: list[str]) -> dict[str, tuple[str, int | None]]:
        if not uuids:
            return {}
        cursor = self._collection(PLAYERS).find({"uuid": {"$in": uuids}})
        names: dict[str, tuple[str, int | None]] = {}
        async for doc in cursor:
            pid = _int(doc.get("pid"), -1)
            names[str(doc.get("uuid"))] = (
                str(doc.get("nickname") or "Unknown"),
                pid if pid > 0 else None,
            )
        return names

    # ----------------------------------------------------- announcement bookkeeping

    async def claim_post(self, season_id: str, kind: str) -> bool:
        """True for the one caller that may post this announcement; false if it was posted."""
        try:
            await self._collection(SEASON_POSTS).insert_one(
                {
                    "_id": f"{season_id}:{kind}",
                    "season_id": season_id,
                    "kind": kind,
                    "message_id": None,
                    "claimed_at": datetime.now(UTC),
                }
            )
        except DuplicateKeyError:
            return False
        return True

    async def record_post(self, season_id: str, kind: str, message_id: int) -> None:
        await self._collection(SEASON_POSTS).update_one(
            {"_id": f"{season_id}:{kind}"}, {"$set": {"message_id": message_id}}
        )

    async def release_post(self, season_id: str, kind: str) -> None:
        """Gives the announcement back when posting it failed, so a replay can try again."""
        await self._collection(SEASON_POSTS).delete_one({"_id": f"{season_id}:{kind}"})

    # --------------------------------------------------------- pending merges

    async def add_pending_merge(self, source_uuid: str, target_uuid: str) -> None:
        await self._collection(PENDING_MERGES).update_one(
            {"_id": f"{source_uuid}>{target_uuid}"},
            {
                "$set": {"source_uuid": source_uuid, "target_uuid": target_uuid},
                "$setOnInsert": {"created_at": datetime.now(UTC)},
            },
            upsert=True,
        )

    async def pending_merges(self) -> list[tuple[str, str]]:
        cursor = self._collection(PENDING_MERGES).find({})
        return [(str(doc["source_uuid"]), str(doc["target_uuid"])) async for doc in cursor]

    async def clear_pending_merge(self, source_uuid: str, target_uuid: str) -> None:
        await self._collection(PENDING_MERGES).delete_one(
            {"_id": f"{source_uuid}>{target_uuid}"}
        )
