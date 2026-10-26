from __future__ import annotations

import logging
import re
from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel, ConfigDict
from pymongo import DESCENDING, AsyncMongoClient
from pymongo.asynchronous.database import AsyncDatabase
from pymongo.errors import PyMongoError

from .dto import (
    PlayerRecord,
)
from .player_pids import is_assigned
from .settings import Settings
from .store_mappers import (
    player_record_from_doc,
)
from .utils.mindustry_colors import strip_mindustry_colors

logger = logging.getLogger(__name__)
# Rows read to find two players with a name: a bracketed group that is not a colour tag
# also matches the query, and the check after it drops those.
EXACT_NAME_CANDIDATES = 10


def to_int(value: Any, default: int = 0) -> int:
    try:
        if value is None:
            return default
        return int(value)
    except (ValueError, TypeError):
        return default


class _MongoDoc(BaseModel):
    model_config = ConfigDict(extra="allow", frozen=True, arbitrary_types_allowed=True)


class PlayerDoc(_MongoDoc):
    pid: int | None = None
    uuid: str | None = None
    ip: str | None = None
    nickname: str | None = None
    username: str | None = None
    custom_nickname: str | None = None
    description: str | None = None
    local_language: str | None = None
    translator_language: str | None = None
    hexed_rank: int | None = None
    hexed_points: int | None = None
    total_play_time: int | None = None
    leaderboard: bool | None = None
    unlocked_badges: list[str] | None = None
    active_badge: str | None = None
    blocked_private_uuids: list[str] | None = None
    is_admin: bool | None = None
    admin_source: str | None = None
    discord_id: str | None = None
    discord_username: str | None = None
    discord_linked_at: int | None = None
    online: bool | None = None
    online_since: int | None = None
    online_server: str | None = None
    password_hash: str | None = None
    created_at: int | None = None
    updated_at: int | None = None


class BanDoc(_MongoDoc):
    ban_id: int | None = None
    case_number: int | None = None
    uuid: str | None = None
    ip: str | None = None
    pid: int | None = None
    name: str | None = None
    admin_name: str | None = None
    admin_discord_id: str | None = None
    reason: str | None = None
    expire_date: Any | None = None


class MuteDoc(_MongoDoc):
    case_number: int | None = None
    uuid: str
    pid: int | None = None
    name: str
    admin_name: str
    admin_discord_id: str | None = None
    reason: str
    expire_date: datetime


class AuditActorDoc(_MongoDoc):
    type: str | None = None
    id: str | None = None
    name_snapshot: str | None = None


class AuditTargetDoc(_MongoDoc):
    uuid: str | None = None
    name_snapshot: str | None = None


class AuditDetailsDoc(_MongoDoc):
    duration_ms: int | None = None
    expires_at: Any | None = None


class AuditDoc(_MongoDoc):
    audit_id: str | None = None
    action: str | None = None
    target: AuditTargetDoc | dict[str, Any] | None = None
    actor: AuditActorDoc | dict[str, Any] | None = None
    reason: str | None = None
    details: AuditDetailsDoc | dict[str, Any] | None = None
    extra: dict[str, Any] | None = None
    occurred_at: Any | None = None
    created_at_epoch_ms: int | None = None


class MongoStore:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client: AsyncMongoClient | None = None
        self._db: AsyncDatabase | None = None

    async def connect(self) -> None:
        if self._db is not None:
            return

        self._client = AsyncMongoClient(
            self._settings.mongo_uri,
            datetime_conversion="DATETIME_AUTO",
        )
        self._db = self._client[self._settings.mongo_db_name]
        await self._db.command("ping")

    async def close(self) -> None:
        if self._client is not None:
            await self._client.close()
        self._client = None
        self._db = None

    async def find_player_by_pid(self, pid: int) -> PlayerRecord | None:
        raw = await self._db_required()["players"].find_one({"pid": pid})
        if raw is None:
            return None
        return player_record_from_doc(
            PlayerDoc.model_validate(raw).model_dump(mode="python")
        )

    async def find_player_by_username(self, username: str) -> PlayerRecord | None:
        raw = await self._db_required()["players"].find_one(
            {"username": {"$regex": f"^{re.escape(username)}$", "$options": "i"}}
        )
        if raw is None:
            return None
        return player_record_from_doc(
            PlayerDoc.model_validate(raw).model_dump(mode="python")
        )

    async def find_players_by_exact_name(self, name: str) -> list[PlayerRecord]:
        """Players whose nickname, custom nickname or username is ``name``, ignoring case
        and colour tags (the game stores the coloured nickname).

        At most two: the caller only needs to know whether the name is unambiguous.
        """
        wanted = name.strip().casefold()
        if not wanted:
            return []
        # Any bracketed group may sit between the letters; the check below keeps only names
        # that read the same once their colour tags are removed.
        tags = r"(?:\[[^\[\]]*\])*"
        pattern = {
            "$regex": "^"
            + tags
            + tags.join(re.escape(c) for c in name.strip())
            + tags
            + "$",
            "$options": "i",
        }
        cursor = (
            self._db_required()["players"]
            .find(
                {
                    "$or": [
                        {field: pattern}
                        for field in ("nickname", "custom_nickname", "username")
                    ]
                }
            )
            .limit(EXACT_NAME_CANDIDATES)
        )
        candidates = [row async for row in cursor]
        rows = [
            row
            for row in candidates
            if any(
                strip_mindustry_colors(str(row.get(field) or "")).strip().casefold()
                == wanted
                for field in ("nickname", "custom_nickname", "username")
            )
        ]
        # A bounded raw regex scan can be saturated by non-colour tags such as [VIP].
        # One filtered match does not prove uniqueness when a later real match may exist.
        if len(candidates) == EXACT_NAME_CANDIDATES and len(rows) < 2:
            return []
        return [
            player_record_from_doc(
                PlayerDoc.model_validate(row).model_dump(mode="python")
            )
            for row in rows[:2]
        ]

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        raw = await self._db_required()["players"].find_one({"uuid": uuid})
        if raw is None:
            return None
        return player_record_from_doc(
            PlayerDoc.model_validate(raw).model_dump(mode="python")
        )

    async def find_linked_players(self) -> list[PlayerRecord]:
        cursor = self._db_required()["players"].find(
            {"discord_id": {"$type": "string", "$nin": ["", None]}}
        )
        return [
            player_record_from_doc(
                PlayerDoc.model_validate(row).model_dump(mode="python")
            )
            for row in await cursor.to_list(length=None)
        ]

    async def find_players_by_discord_id(self, discord_id: str) -> list[PlayerRecord]:
        cursor = (
            self._db_required()["players"]
            .find({"discord_id": discord_id})
            .sort("pid", DESCENDING)
        )
        rows = await cursor.to_list(length=None)
        return [
            player_record_from_doc(
                PlayerDoc.model_validate(row).model_dump(mode="python")
            )
            for row in rows
        ]

    async def find_discord_admin_players(self) -> list[PlayerRecord]:
        cursor = (
            self._db_required()["players"]
            .find({"is_admin": True, "admin_source": "DISCORD_ROLE"})
            .sort("pid", DESCENDING)
        )
        rows = await cursor.to_list(length=None)
        return [
            player_record_from_doc(
                PlayerDoc.model_validate(row).model_dump(mode="python")
            )
            for row in rows
        ]

    async def search_players(
        self, query: str, limit: int = 6, page: int = 0
    ) -> list[PlayerRecord]:
        regex = re.escape(query)
        skip = page * limit
        cursor = (
            self._db_required()["players"]
            .find({"nickname": {"$regex": regex, "$options": "i"}})
            .sort("pid", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        rows = await cursor.to_list(length=limit)
        return [
            player_record_from_doc(
                PlayerDoc.model_validate(row).model_dump(mode="python")
            )
            for row in rows
        ]

    async def autocomplete_players(
        self, query: str, limit: int = 25
    ) -> list[PlayerRecord]:
        normalized = query.strip()
        if not normalized:
            return []

        players = self._db_required()["players"]
        # A PID as it is typed in the game: 12, -12, #12 or #-12.
        typed_pid = normalized.removeprefix("#")
        if re.fullmatch(r"-?\d+|-", typed_pid):
            pid_cursor = await players.aggregate(
                [
                    {
                        "$match": {
                            "$expr": {
                                "$regexMatch": {
                                    "input": {"$toString": {"$ifNull": ["$pid", ""]}},
                                    "regex": f"^{re.escape(typed_pid)}",
                                }
                            }
                        }
                    },
                    {"$sort": {"pid": -1}},
                    {"$limit": limit},
                    {"$project": {"_id": 0, "pid": 1, "nickname": 1, "username": 1}},
                ]
            )
            rows = await pid_cursor.to_list(length=limit)
        else:
            # A name as it is typed in the game: part of a nickname, or a @username.
            typed_username = re.escape(normalized.removeprefix("@"))
            by_username = {
                "username": {"$regex": f"^{typed_username}", "$options": "i"}
            }
            by_nickname = {
                "nickname": {"$regex": re.escape(normalized), "$options": "i"}
            }
            cursor = (
                players.find(
                    by_username
                    if normalized.startswith("@")
                    else {"$or": [by_nickname, by_username]},
                    {"_id": 0, "pid": 1, "nickname": 1, "username": 1},
                )
                .sort("pid", DESCENDING)
                .limit(limit)
            )
            rows = await cursor.to_list(length=limit)

        records = [player_record_from_doc(row) for row in rows]
        return [record for record in records if is_assigned(record.pid)]

    async def count_players_by_name(self, query: str) -> int:
        try:
            return to_int(
                await self._db_required()["players"].count_documents(
                    {"nickname": {"$regex": re.escape(query), "$options": "i"}}
                )
            )
        except PyMongoError:
            return 0

    async def set_admin_access(
        self, *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        if self._settings.permissions_mode == "roles":
            raise RuntimeError("Direct admin writes are disabled in roles mode")
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            {"$set": {"is_admin": is_admin, "admin_source": admin_source}},
        )
        return result.matched_count > 0, result.modified_count > 0

    async def reset_password(self, *, uuid: str) -> bool:
        if self._settings.permissions_mode == "roles":
            raise RuntimeError("Direct password writes are disabled in roles mode")
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            {"$set": {"password_hash": ""}},
        )
        return result.modified_count > 0

    async def grant_badge(self, *, uuid: str, badge_id: str) -> bool:
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            {"$addToSet": {"unlocked_badges": badge_id}},
        )
        return result.modified_count > 0

    async def revoke_badge(self, *, uuid: str, badge_id: str) -> bool:
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            [
                {
                    "$set": {
                        "unlocked_badges": {
                            "$filter": {
                                "input": {"$ifNull": ["$unlocked_badges", []]},
                                "as": "badge",
                                "cond": {"$ne": ["$$badge", badge_id]},
                            }
                        },
                        "active_badge": {
                            "$cond": [
                                {"$eq": [{"$ifNull": ["$active_badge", ""]}, badge_id]},
                                "",
                                {"$ifNull": ["$active_badge", ""]},
                            ]
                        },
                    }
                }
            ],
        )
        return result.modified_count > 0

    @staticmethod
    def now_utc() -> datetime:
        return datetime.now(UTC)

    @property
    def database(self) -> AsyncDatabase:
        """The connected database, for stores that read collections this one does not own."""
        return self._db_required()

    def _db_required(self) -> AsyncDatabase:
        if self._db is None:
            raise RuntimeError("MongoStore is not connected")
        return self._db
