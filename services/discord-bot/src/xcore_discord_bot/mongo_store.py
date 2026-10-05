from __future__ import annotations

import logging
import re
import uuid
from datetime import UTC, datetime
from typing import Any

from bson.errors import InvalidBSON
from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase
from pydantic import BaseModel, ConfigDict
from pymongo import DESCENDING
from pymongo.errors import PyMongoError

from .dto import (
    MERGE_KEEP_PID_CHOICES,
    MERGE_KEEP_PID_SOURCE,
    MERGE_KEEP_PID_TARGET,
    AccountMergeResult,
    AuditRecordSummary,
    BanRecord,
    MuteRecord,
    PlayerRecord,
)
from .player_pids import is_assigned
from .settings import Settings
from .store_mappers import (
    ban_record_from_doc,
    mute_record_from_doc,
    player_record_from_doc,
)

logger = logging.getLogger(__name__)


def _to_int(value: Any, default: int = 0) -> int:
    try:
        if value is None:
            return default
        return int(value)
    except (ValueError, TypeError):
        return default


def _to_bool(value: Any, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        return value.strip().lower() in {"1", "true", "yes", "online"}
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
    uuid: str | None = None
    ip: str | None = None
    pid: int | None = None
    name: str | None = None
    admin_name: str | None = None
    admin_discord_id: str | None = None
    reason: str | None = None
    expire_date: Any | None = None


class MuteDoc(_MongoDoc):
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
        self._client: AsyncIOMotorClient | None = None
        self._db: AsyncIOMotorDatabase | None = None

    async def connect(self) -> None:
        if self._db is not None:
            return

        self._client = AsyncIOMotorClient(
            self._settings.mongo_uri,
            datetime_conversion="DATETIME_AUTO",
        )
        self._db = self._client[self._settings.mongo_db_name]
        await self._db.command("ping")

    async def close(self) -> None:
        if self._client is not None:
            self._client.close()
        self._client = None
        self._db = None

    async def find_player_by_pid(self, pid: int) -> PlayerRecord | None:
        raw = await self._db_required()["players"].find_one({"pid": pid})
        if raw is None:
            return None
        return player_record_from_doc(
            PlayerDoc.model_validate(raw).model_dump(mode="python")
        )

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        raw = await self._db_required()["players"].find_one({"uuid": uuid})
        if raw is None:
            return None
        return player_record_from_doc(
            PlayerDoc.model_validate(raw).model_dump(mode="python")
        )

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
            rows = await players.aggregate(
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
            ).to_list(length=limit)
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
            return _to_int(
                await self._db_required()["players"].count_documents(
                    {"nickname": {"$regex": re.escape(query), "$options": "i"}}
                )
            )
        except PyMongoError:
            return 0

    async def list_bans(
        self, name_filter: str | None = None, limit: int = 6, page: int = 0
    ) -> list[BanRecord]:
        query: dict[str, Any] = {}
        if name_filter:
            query["name"] = {"$regex": re.escape(name_filter), "$options": "i"}

        skip = page * limit
        bans = self._db_required()["bans"]
        cursor = (
            bans.find(query).sort("expire_date", DESCENDING).skip(skip).limit(limit)
        )

        try:
            rows = await cursor.to_list(length=limit)
        except InvalidBSON:
            logger.warning(
                "Encountered out-of-range BSON datetime in bans; retrying without expire_date"
            )
            rows = await bans.aggregate(
                [
                    {"$match": query},
                    {"$sort": {"_id": -1}},
                    {"$skip": skip},
                    {"$limit": limit},
                    {
                        "$project": {
                            "uuid": 1,
                            "ip": 1,
                            "name": 1,
                            "admin_name": 1,
                            "reason": 1,
                            "expire_date": {
                                "$convert": {
                                    "input": "$expire_date",
                                    "to": "long",
                                    "onError": None,
                                    "onNull": None,
                                }
                            },
                        }
                    },
                ]
            ).to_list(length=limit)

        sanitized: list[BanRecord] = []
        for row in rows:
            try:
                sanitized.append(
                    ban_record_from_doc(
                        BanDoc.model_validate(row).model_dump(mode="python")
                    )
                )
            except (ValueError, TypeError, PyMongoError) as error:
                logger.warning("Skipping malformed ban row: %s", error)
                sanitized.append(
                    ban_record_from_doc(
                        {
                            "uuid": row.get("uuid"),
                            "ip": row.get("ip"),
                            "name": row.get("name"),
                            "admin_name": row.get("admin_name"),
                            "reason": row.get("reason"),
                            "expire_date": None,
                        }
                    )
                )
        return sanitized

    async def count_bans(self, name_filter: str | None = None) -> int:
        query: dict[str, Any] = {}
        if name_filter:
            query["name"] = {"$regex": re.escape(name_filter), "$options": "i"}
        try:
            return _to_int(await self._db_required()["bans"].count_documents(query))
        except PyMongoError:
            return 0

    async def upsert_ban(
        self,
        *,
        uuid: str,
        ip: str | None,
        pid: int | None,
        name: str,
        admin_name: str,
        admin_discord_id: str | None,
        reason: str,
        expire_date: datetime,
    ) -> None:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)

        payload = BanDoc(
            uuid=uuid,
            ip=ip,
            pid=pid,
            name=name,
            admin_name=admin_name,
            admin_discord_id=admin_discord_id,
            reason=reason,
            expire_date=expire_date,
        ).model_dump(mode="python")
        await self._db_required()["bans"].replace_one(query, payload, upsert=True)

    async def delete_ban(self, *, uuid: str, ip: str | None) -> int:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)

        result = await self._db_required()["bans"].delete_many(query)
        return result.deleted_count

    async def find_ban(self, *, uuid: str, ip: str | None) -> BanRecord | None:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)

        raw = await self._db_required()["bans"].find_one(query)
        if raw is None:
            return None
        return ban_record_from_doc(BanDoc.model_validate(raw).model_dump(mode="python"))

    async def upsert_mute(
        self,
        *,
        uuid: str,
        pid: int | None,
        name: str,
        admin_name: str,
        admin_discord_id: str | None,
        reason: str,
        expire_date: datetime,
    ) -> None:
        payload = MuteDoc(
            uuid=uuid,
            pid=pid,
            name=name,
            admin_name=admin_name,
            admin_discord_id=admin_discord_id,
            reason=reason,
            expire_date=expire_date,
        ).model_dump(mode="python")
        await self._db_required()["mutes"].replace_one(
            {"uuid": uuid}, payload, upsert=True
        )

    async def delete_mute(self, *, uuid: str) -> int:
        result = await self._db_required()["mutes"].delete_one({"uuid": uuid})
        return result.deleted_count

    async def find_mute(self, *, uuid: str) -> MuteRecord | None:
        raw = await self._db_required()["mutes"].find_one({"uuid": uuid})
        if raw is None:
            return None
        return mute_record_from_doc(
            MuteDoc.model_validate(raw).model_dump(mode="python")
        )

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
            return _to_int(
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
            return _to_int(
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
        created_at_epoch_ms = _to_int(occurred.timestamp() * 1000)

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
        await self._db_required()["moderation_audit"].insert_one(document)
        return audit_id

    async def set_admin_access(
        self, *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            {"$set": {"is_admin": is_admin, "admin_source": admin_source}},
        )
        return result.matched_count > 0, result.modified_count > 0

    async def reset_password(self, *, uuid: str) -> bool:
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
    def database(self) -> AsyncIOMotorDatabase:
        """The connected database, for stores that read collections this one does not own."""
        return self._db_required()

    def _db_required(self) -> AsyncIOMotorDatabase:
        if self._db is None:
            raise RuntimeError("MongoStore is not connected")
        return self._db

    async def merge_player_accounts(
        self,
        *,
        source_pid: int,
        target_pid: int,
        actor_name: str,
        actor_discord_id: str | None,
        reason: str,
        keep_pid: str = MERGE_KEEP_PID_TARGET,
    ) -> AccountMergeResult:
        normalized_keep_pid = str(keep_pid or MERGE_KEEP_PID_TARGET).strip().lower()
        if normalized_keep_pid not in MERGE_KEEP_PID_CHOICES:
            return AccountMergeResult(
                success=False,
                error=(
                    f"Unknown keep_pid value '{keep_pid}'. "
                    f"Expected one of: {', '.join(MERGE_KEEP_PID_CHOICES)}."
                ),
            )

        if source_pid == target_pid:
            return AccountMergeResult(
                success=False,
                error=f"Cannot merge account into itself (PID #{source_pid})",
            )

        db = self._db_required()
        source_raw = await db["players"].find_one({"pid": source_pid})
        if source_raw is None:
            return AccountMergeResult(
                success=False,
                error=f"Source player #{source_pid} not found in database",
            )

        target_raw = await db["players"].find_one({"pid": target_pid})
        if target_raw is None:
            return AccountMergeResult(
                success=False,
                error=f"Target player #{target_pid} not found in database",
            )

        source_uuid = str(source_raw.get("uuid") or "").strip()
        target_uuid = str(target_raw.get("uuid") or "").strip()

        if source_uuid and target_uuid and source_uuid == target_uuid:
            return AccountMergeResult(
                success=False,
                error="Source and target have the same UUID.",
            )

        if source_uuid.startswith("merged:"):
            return AccountMergeResult(
                success=False,
                error=f"Source player #{source_pid} was already merged into another account.",
            )

        if target_uuid.startswith("merged:"):
            return AccountMergeResult(
                success=False,
                error=(
                    f"Target player #{target_pid} is a closed account "
                    "and cannot receive a merge."
                ),
            )

        online_pids = [
            pid
            for pid, doc in ((source_pid, source_raw), (target_pid, target_raw))
            if _to_bool(doc.get("online"))
        ]
        if online_pids:
            online_list = ", ".join(f"#{pid}" for pid in online_pids)
            return AccountMergeResult(
                success=False,
                error=(
                    f"Cannot merge while {online_list} is online. "
                    "Ask the player to disconnect, then retry."
                ),
            )

        # The UUID always comes from the merge target; only the PID is selectable.
        if normalized_keep_pid == MERGE_KEEP_PID_SOURCE:
            surviving_pid = source_pid
            tombstone_pid = target_pid
        else:
            surviving_pid = target_pid
            tombstone_pid = source_pid

        source_before = player_record_from_doc(
            PlayerDoc.model_validate(source_raw).model_dump(mode="python")
        )
        target_before = player_record_from_doc(
            PlayerDoc.model_validate(target_raw).model_dump(mode="python")
        )

        # Consolidate stats
        total_play_time = _to_int(target_raw.get("total_play_time")) + _to_int(
            source_raw.get("total_play_time")
        )
        hexed_points = _to_int(target_raw.get("hexed_points")) + _to_int(
            source_raw.get("hexed_points")
        )

        hexed_rank_reqs = [0, 3, 10, 20, 25, 30]
        hexed_rank = 0
        for idx, req in enumerate(hexed_rank_reqs):
            if hexed_points >= req:
                hexed_rank = idx

        # Badges
        unlocked_badges = sorted(
            set(
                (target_raw.get("unlocked_badges") or [])
                + (source_raw.get("unlocked_badges") or [])
            )
        )
        active_badge = (
            target_raw.get("active_badge") or source_raw.get("active_badge") or ""
        )

        # Profile fields
        custom_nickname = (
            target_raw.get("custom_nickname") or source_raw.get("custom_nickname") or ""
        )
        description = (
            target_raw.get("description") or source_raw.get("description") or ""
        )

        # Discord linkage
        source_discord_id = str(source_raw.get("discord_id") or "").strip()
        target_discord_id = str(target_raw.get("discord_id") or "").strip()
        discord_id = target_discord_id or source_discord_id
        discord_link_moved = bool(source_discord_id) and not target_discord_id
        discord_link_conflict = (
            bool(source_discord_id)
            and bool(target_discord_id)
            and source_discord_id != target_discord_id
        )
        discord_username = (
            target_raw.get("discord_username")
            or source_raw.get("discord_username")
            or ""
        )
        discord_linked_at = (
            target_raw.get("discord_linked_at")
            or source_raw.get("discord_linked_at")
            or 0
        )

        # Device tokens & hashes
        device_tokens = {
            **(source_raw.get("device_tokens") or {}),
            **(target_raw.get("device_tokens") or {}),
        }
        device_token_hashes = sorted(
            set(
                (target_raw.get("device_token_hashes") or [])
                + (source_raw.get("device_token_hashes") or [])
            )
        )

        # Blocked UUIDs
        blocked_private_uuids = sorted(
            set(
                (target_raw.get("blocked_private_uuids") or [])
                + (source_raw.get("blocked_private_uuids") or [])
            )
        )

        # Admin
        is_admin = bool(target_raw.get("is_admin") or source_raw.get("is_admin"))
        admin_source = (
            target_raw.get("admin_source") or source_raw.get("admin_source") or "NONE"
        )

        target_updates: dict[str, Any] = {
            "pid": surviving_pid,
            "total_play_time": total_play_time,
            "hexed_points": hexed_points,
            "hexed_rank": hexed_rank,
            "unlocked_badges": unlocked_badges,
            "active_badge": active_badge,
            "custom_nickname": custom_nickname,
            "description": description,
            "discord_id": discord_id,
            "discord_username": discord_username,
            "discord_linked_at": discord_linked_at,
            "device_tokens": device_tokens,
            "device_token_hashes": device_token_hashes,
            "blocked_private_uuids": blocked_private_uuids,
            "is_admin": is_admin,
            "admin_source": admin_source,
        }

        # Update target
        await db["players"].update_one(
            {"_id": target_raw["_id"]}, {"$set": target_updates}
        )

        # Update source (mark as merged)
        source_updates: dict[str, Any] = {
            "pid": tombstone_pid,
            "uuid": f"merged:{source_uuid}",
            "total_play_time": 0,
            "description": f"Merged into PID #{surviving_pid} ({target_raw.get('nickname')})",
        }
        if source_discord_id:
            # The closed account must not keep a live Discord link, otherwise admin
            # sync and link lookups would resolve to a dead player.
            source_updates["discord_id"] = ""
            source_updates["discord_username"] = ""
            source_updates["discord_linked_at"] = 0
        await db["players"].update_one(
            {"_id": source_raw["_id"]}, {"$set": source_updates}
        )

        # Reassign games in games_v2
        games_transferred = 0
        if source_uuid and target_uuid:
            match_res = await db["games_v2"].update_many(
                {"player_stats.uuid": source_uuid},
                {"$set": {"player_stats.$[elem].uuid": target_uuid}},
                array_filters=[{"elem.uuid": source_uuid}],
            )
            games_transferred = _to_int(match_res.modified_count)

        # Punishments transfer
        punishment_transfers: dict[str, bool] = {"bans": False, "mutes": False}
        if source_uuid and target_uuid:
            for collection in ("bans", "mutes"):
                source_punishment = await db[collection].find_one({"uuid": source_uuid})
                if source_punishment is not None:
                    await db[collection].update_one(
                        {"_id": source_punishment["_id"]},
                        {"$set": {"pid": tombstone_pid}},
                    )

                target_punishment = await db[collection].find_one({"uuid": target_uuid})
                if target_punishment is not None:
                    await db[collection].update_one(
                        {"_id": target_punishment["_id"]},
                        {"$set": {"pid": surviving_pid}},
                    )
                    continue

                if source_punishment is None:
                    continue

                transferred = dict(source_punishment)
                transferred.pop("_id", None)
                transferred["uuid"] = target_uuid
                transferred["pid"] = surviving_pid
                transferred["name"] = target_raw.get("nickname") or "Unknown"
                transferred["reason"] = (
                    f"[Merged from #{source_pid}] {source_punishment.get('reason', '')}"
                )
                await db[collection].insert_one(transferred)
                punishment_transfers[collection] = True

        ban_transferred = punishment_transfers["bans"]
        mute_transferred = punishment_transfers["mutes"]

        # Audit record
        audit_id = await self.append_moderation_audit(
            action="MERGE",
            target_uuid=target_uuid,
            target_pid=surviving_pid,
            target_name=str(target_raw.get("nickname") or "Unknown"),
            target_ip=str(target_raw.get("ip") or ""),
            actor_discord_id=actor_discord_id,
            actor_name=actor_name,
            reason=reason,
            occurred_at=datetime.now(UTC),
            extra={
                "merge": {
                    "keep_pid": normalized_keep_pid,
                    "source_pid": source_pid,
                    "source_uuid": source_uuid,
                    "source_name": str(source_raw.get("nickname") or "Unknown"),
                    "tombstone_pid": tombstone_pid,
                    "surviving_pid": surviving_pid,
                    "games_transferred": games_transferred,
                    "ban_transferred": ban_transferred,
                    "mute_transferred": mute_transferred,
                    "discord_link_moved": discord_link_moved,
                    "discord_link_conflict": discord_link_conflict,
                }
            },
        )

        updated_target_raw = await db["players"].find_one({"_id": target_raw["_id"]})
        target_after = player_record_from_doc(
            PlayerDoc.model_validate(updated_target_raw).model_dump(mode="python")
        )

        return AccountMergeResult(
            success=True,
            source_before=source_before,
            target_before=target_before,
            target_after=target_after,
            games_transferred=games_transferred,
            ban_transferred=ban_transferred,
            mute_transferred=mute_transferred,
            audit_id=audit_id,
            keep_pid=normalized_keep_pid,
            surviving_pid=surviving_pid,
            tombstone_pid=tombstone_pid,
            discord_link_moved=discord_link_moved,
            discord_link_conflict=discord_link_conflict,
        )

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
            created_at_epoch_ms=_to_int(validated.get("created_at_epoch_ms")),
        )

    @staticmethod
    def _ban_lookup_query(uuid: str, ip: str | None) -> dict[str, Any]:
        if ip:
            return {"$or": [{"uuid": uuid}, {"ip": ip}]}
        return {"uuid": uuid}

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
