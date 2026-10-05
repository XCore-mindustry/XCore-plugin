"""Read access to the rating seasons and standings the game servers keep in Mongo.

The plugin owns these collections: the bot only reads them, plus the little bookkeeping it
needs to behave well (which season announcements it already posted, which account merges
still have to reach the ladders). Everything that changes a season goes through the plugin.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any

from pymongo import ASCENDING, DESCENDING
from pymongo.errors import DuplicateKeyError

from .mongo_store import MongoStore
from .player_pids import NO_PID, or_none

logger = logging.getLogger(__name__)

SEASONS = "rating_seasons"
STANDINGS = "rating_standings"
PLAYERS = "players"
SEASON_POSTS = "discord_season_posts"
# A claim this old with no message recorded belongs to a poster that stopped mid-way.
STALE_POST_CLAIM = timedelta(minutes=5)
PENDING_MERGES = "rating_merge_pending"
PRIZE_GRANTS = "rating_prize_grants"

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
class Prize:
    """What a season gives to a place or a range of places."""

    place_from: int
    place_to: int
    kind: str  # "badge" or "custom"
    value: str
    description: str | None = None

    def covers(self, place: int) -> bool:
        return self.place_from <= place <= self.place_to

    @property
    def places(self) -> str:
        if self.place_from == self.place_to:
            return str(self.place_from)
        return f"{self.place_from}-{self.place_to}"

    @property
    def label(self) -> str:
        return self.description or self.value


@dataclass(frozen=True, slots=True)
class PrizeGrantRecord:
    """A prize owed to one player, and whether it reached them."""

    season_id: str
    place: int
    player_uuid: str
    kind: str
    value: str
    description: str | None
    status: str  # PENDING, GRANTED, DELIVERED or FAILED
    granted_by: str
    note: str | None


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
    prizes: tuple[Prize, ...] = ()


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
    prizes: tuple[Prize, ...] = ()

    def prizes_for(self, place: int) -> tuple[Prize, ...]:
        return tuple(prize for prize in self.prizes if prize.covers(place))

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
    peak_rating: int = 0


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
        prizes=tuple(_prize(entry) for entry in doc.get("prizes") or ()),
    )


def _prize(doc: dict[str, Any]) -> Prize:
    place_from = max(_int(doc.get("place_from"), 1), 1)
    return Prize(
        place_from=place_from,
        place_to=max(_int(doc.get("place_to"), place_from), place_from),
        kind=str(doc.get("kind") or "custom").lower(),
        value=str(doc.get("value") or ""),
        description=str(doc.get("description") or "").strip() or None,
    )


def grant_from_doc(doc: dict[str, Any]) -> PrizeGrantRecord:
    prize = doc.get("prize") or {}
    return PrizeGrantRecord(
        season_id=str(doc.get("season_id") or ""),
        place=_int(doc.get("place")),
        player_uuid=str(doc.get("player_uuid") or ""),
        kind=str(prize.get("kind") or "custom").lower(),
        value=str(prize.get("value") or ""),
        description=str(prize.get("description") or "").strip() or None,
        status=str(doc.get("status") or "PENDING"),
        granted_by=str(doc.get("granted_by") or "system"),
        note=str(doc.get("note") or "").strip() or None,
    )


def _podium_pid(doc: dict[str, Any]) -> int | None:
    """Entries written before PIDs could be negative stored -1 for "no profile"; the ones the
    game servers write since carry ``signed_pid`` and a real PID, or none at all."""
    pid = _int(doc.get("pid"), NO_PID)
    if pid == -1 and doc.get("signed_pid") is not True:
        return None
    return or_none(pid)


def _podium_entry(doc: dict[str, Any]) -> PodiumEntry:
    return PodiumEntry(
        place=_int(doc.get("place")),
        uuid=str(doc.get("uuid") or ""),
        pid=_podium_pid(doc),
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

    async def prize_grants(self, ladder: str, number: int) -> list[PrizeGrantRecord]:
        """The grants of a finished season, by place."""
        cursor = (
            self._collection(PRIZE_GRANTS)
            .find({"season_id": f"{ladder}:{number}"})
            .sort([("place", ASCENDING), ("prize_index", ASCENDING)])
        )
        return [grant_from_doc(doc) async for doc in cursor]

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
                    peak_rating=max(_int(doc.get("peak_rating")), rating),
                )
            )
        return placings

    async def nicknames(self, uuids: list[str]) -> dict[str, str]:
        return {uuid: nickname for uuid, (nickname, _) in (await self._players(uuids)).items()}

    async def _players(self, uuids: list[str]) -> dict[str, tuple[str, int | None]]:
        if not uuids:
            return {}
        cursor = self._collection(PLAYERS).find({"uuid": {"$in": uuids}})
        names: dict[str, tuple[str, int | None]] = {}
        async for doc in cursor:
            names[str(doc.get("uuid"))] = (
                str(doc.get("nickname") or "Unknown"),
                or_none(_int(doc.get("pid"), NO_PID)),
            )
        return names

    # ----------------------------------------------------- announcement bookkeeping

    async def claim_post(self, season_id: str, kind: str) -> bool:
        """True for the one caller that may post this announcement; false if it was posted.

        A claim left without a message for longer than ``STALE_POST_CLAIM`` is taken over:
        its poster was stopped before it could post or hand the claim back.
        """
        key = f"{season_id}:{kind}"
        now = datetime.now(UTC)
        try:
            await self._collection(SEASON_POSTS).insert_one(
                {
                    "_id": key,
                    "season_id": season_id,
                    "kind": kind,
                    "message_id": None,
                    "claimed_at": now,
                }
            )
        except DuplicateKeyError:
            taken = await self._collection(SEASON_POSTS).update_one(
                {
                    "_id": key,
                    "message_id": None,
                    "claimed_at": {"$lt": now - STALE_POST_CLAIM},
                },
                {"$set": {"claimed_at": now}},
            )
            return taken.modified_count == 1
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
                # Queued again by hand after a refusal: it is waiting once more.
                "$unset": {"failed_at": "", "error": ""},
            },
            upsert=True,
        )

    async def pending_merges(self) -> list[tuple[str, str]]:
        """The merges still waiting for a server; refused ones are kept but not retried."""
        cursor = self._collection(PENDING_MERGES).find({"failed_at": None})
        return [(str(doc["source_uuid"]), str(doc["target_uuid"])) async for doc in cursor]

    async def fail_pending_merge(self, source_uuid: str, target_uuid: str, error: str) -> None:
        """Keeps a refused merge on record for an administrator instead of retrying it."""
        await self._collection(PENDING_MERGES).update_one(
            {"_id": f"{source_uuid}>{target_uuid}"},
            {"$set": {"failed_at": datetime.now(UTC), "error": error}},
        )

    async def clear_pending_merge(self, source_uuid: str, target_uuid: str) -> None:
        await self._collection(PENDING_MERGES).delete_one(
            {"_id": f"{source_uuid}>{target_uuid}"}
        )
