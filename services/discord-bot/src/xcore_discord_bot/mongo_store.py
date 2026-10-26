from __future__ import annotations

import logging
import re
import uuid
from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel, ConfigDict
from pymongo import DESCENDING, AsyncMongoClient
from pymongo.asynchronous.client_session import AsyncClientSession
from pymongo.asynchronous.database import AsyncDatabase
from pymongo.errors import PyMongoError

from .dto import (
    AuditRecordSummary,
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
        await self._db["moderation_audit"].create_index(
            "details.extra.ban_id", sparse=True
        )

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

    async def list_audit_for_player(
        self,
        *,
        uuid: str,
        limit: int = 6,
        page: int = 0,
    ) -> list[AuditRecordSummary]:
        if not uuid.strip():
            return []

        skip = page * limit
        cursor = (
            self._db_required()["moderation_audit"]
            .find(
                {"target.uuid": uuid},
                {
                    "_id": 0,
                    "audit_id": 1,
                    "action": 1,
                    "target": 1,
                    "actor": 1,
                    "reason": 1,
                    "details": 1,
                    "occurred_at": 1,
                    "created_at_epoch_ms": 1,
                },
            )
            .sort("created_at_epoch_ms", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        rows = await cursor.to_list(length=limit)
        return [self._audit_record_from_doc(row) for row in rows]

    async def count_audit_for_player(self, *, uuid: str) -> int:
        if not uuid.strip():
            return 0
        try:
            return to_int(
                await self._db_required()["moderation_audit"].count_documents(
                    {"target.uuid": uuid}
                )
            )
        except PyMongoError:
            return 0

    async def list_audit_for_actor(
        self,
        *,
        actor_id: str,
        actor_discord_id: str | None = None,
        limit: int = 6,
        page: int = 0,
    ) -> list[AuditRecordSummary]:
        actor_filter = self._audit_actor_lookup_filter(
            actor_id=actor_id,
            actor_discord_id=actor_discord_id,
        )
        if actor_filter is None:
            return []

        skip = page * limit
        cursor = (
            self._db_required()["moderation_audit"]
            .find(
                actor_filter,
                {
                    "_id": 0,
                    "audit_id": 1,
                    "action": 1,
                    "target": 1,
                    "actor": 1,
                    "reason": 1,
                    "details": 1,
                    "occurred_at": 1,
                    "created_at_epoch_ms": 1,
                },
            )
            .sort("created_at_epoch_ms", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        rows = await cursor.to_list(length=limit)
        return [self._audit_record_from_doc(row) for row in rows]

    async def count_audit_for_actor(
        self,
        *,
        actor_id: str,
        actor_discord_id: str | None = None,
    ) -> int:
        actor_filter = self._audit_actor_lookup_filter(
            actor_id=actor_id,
            actor_discord_id=actor_discord_id,
        )
        if actor_filter is None:
            return 0
        try:
            return to_int(
                await self._db_required()["moderation_audit"].count_documents(
                    actor_filter
                )
            )
        except PyMongoError:
            return 0

    async def find_audit_by_id(self, *, audit_id: str) -> AuditRecordSummary | None:
        if not audit_id.strip():
            return None
        raw = await self._db_required()["moderation_audit"].find_one(
            {"audit_id": audit_id},
            {
                "_id": 0,
                "audit_id": 1,
                "action": 1,
                "target": 1,
                "actor": 1,
                "reason": 1,
                "details": 1,
                "occurred_at": 1,
                "created_at_epoch_ms": 1,
            },
        )
        if raw is None:
            return None
        return self._audit_record_from_doc(raw)

    async def append_moderation_audit(
        self,
        *,
        action: str,
        target_uuid: str,
        target_pid: int | None,
        target_name: str,
        target_ip: str | None,
        actor_discord_id: str | None,
        actor_name: str,
        reason: str,
        occurred_at: datetime,
        duration_ms: int | None = None,
        expires_at: datetime | None = None,
        related_audit_id: str | None = None,
        supersedes_audit_id: str | None = None,
        request_id: str | None = None,
        extra: dict[str, Any] | None = None,
        session: AsyncClientSession | None = None,
    ) -> str:
        audit_id = str(uuid.uuid4())
        normalized_reason = str(reason or "Not Specified").strip() or "Not Specified"
        normalized_actor_name = str(actor_name or "Unknown").strip() or "Unknown"
        normalized_target_name = str(target_name or "Unknown").strip() or "Unknown"
        normalized_target_uuid = str(target_uuid or "").strip()
        actor_id = str(actor_discord_id or normalized_actor_name).strip()
        occurred = (
            occurred_at.replace(tzinfo=UTC)
            if occurred_at.tzinfo is None
            else occurred_at.astimezone(UTC)
        )
        created_at_epoch_ms = to_int(occurred.timestamp() * 1000)

        document = {
            "audit_id": audit_id,
            "schema_version": 1,
            "action": str(action or "NOTE").strip().upper() or "NOTE",
            "category": "NOTE"
            if str(action or "NOTE").strip().upper() == "NOTE"
            else "SANCTION",
            "target": {
                "uuid": normalized_target_uuid,
                "pid": target_pid,
                "name_snapshot": normalized_target_name,
                "ip_snapshot": target_ip,
            },
            "actor": {
                "type": "DISCORD_USER",
                "id": actor_id,
                "name_snapshot": normalized_actor_name,
                "display_name_snapshot": normalized_actor_name,
                "discord_id": str(actor_discord_id or "").strip() or None,
                "pid": None,
                "player_uuid": None,
                "server_id": None,
            },
            "origin": {
                "channel": "DISCORD",
                "source": "xcore-discord-bot",
                "server_id": "discord-bot",
                "request_id": str(request_id or uuid.uuid4()).strip(),
            },
            "reason": normalized_reason,
            "details": {
                "duration_ms": duration_ms,
                "expires_at": expires_at,
                "visibility": None,
                "tags": [],
                "extra": dict(extra) if extra else {},
            },
            "related_audit_id": related_audit_id,
            "supersedes_audit_id": supersedes_audit_id,
            "occurred_at": occurred,
            "created_at_ts": occurred,
            "created_at_epoch_ms": created_at_epoch_ms,
            "integrity": {
                "dedupeKey": f"{str(action or 'NOTE').strip().upper()}:{actor_id}:{normalized_target_uuid}:{normalized_reason}",
                "hash": None,
            },
        }
        await self._db_required()["moderation_audit"].insert_one(
            document, session=session
        )
        return audit_id

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

    @staticmethod
    def _audit_record_from_doc(raw: dict[str, Any]) -> AuditRecordSummary:
        validated = AuditDoc.model_validate(raw).model_dump(mode="python")
        target = validated.get("target") or {}
        actor = validated.get("actor") or {}
        details = validated.get("details") or {}
        return AuditRecordSummary(
            audit_id=str(validated.get("audit_id") or ""),
            action=str(validated.get("action") or "NOTE"),
            target_uuid=target.get("uuid"),
            target_name=target.get("name_snapshot"),
            actor_type=actor.get("type"),
            actor_id=actor.get("id"),
            actor_name=actor.get("name_snapshot"),
            reason=validated.get("reason"),
            duration_ms=details.get("duration_ms"),
            expires_at=details.get("expires_at"),
            occurred_at=validated.get("occurred_at"),
            created_at_epoch_ms=to_int(validated.get("created_at_epoch_ms")),
        )

    @staticmethod
    def _audit_actor_lookup_filter(
        *,
        actor_id: str,
        actor_discord_id: str | None,
    ) -> dict[str, Any] | None:
        normalized_actor_id = str(actor_id or "").strip()
        normalized_discord_id = str(actor_discord_id or "").strip()

        filters: list[dict[str, Any]] = []
        if normalized_discord_id:
            filters.append({"actor.id": normalized_discord_id})
            filters.append({"actor.discord_id": normalized_discord_id})
        if normalized_actor_id:
            filters.append({"actor.id": normalized_actor_id})

        if not filters:
            return None
        if len(filters) == 1:
            return filters[0]
        return {"$or": filters}
