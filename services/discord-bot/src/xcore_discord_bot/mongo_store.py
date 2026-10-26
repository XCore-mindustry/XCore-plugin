from __future__ import annotations

import logging
import re
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from bson.errors import InvalidBSON
from pydantic import BaseModel, ConfigDict
from pymongo import DESCENDING, AsyncMongoClient, ReturnDocument
from pymongo.asynchronous.client_session import AsyncClientSession
from pymongo.asynchronous.database import AsyncDatabase
from pymongo.errors import OperationFailure, PyMongoError

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
from .utils.mindustry_colors import strip_mindustry_colors

logger = logging.getLogger(__name__)
# Rows read to find two players with a name: a bracketed group that is not a colour tag
# also matches the query, and the check after it drops those.
EXACT_NAME_CANDIDATES = 10
# The server error a standalone MongoDB returns for a transaction.
_ILLEGAL_OPERATION = 20


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


@dataclass(frozen=True)
class _MergePlan:
    source_raw: dict[str, Any]
    target_raw: dict[str, Any]
    source_uuid: str
    target_uuid: str
    keep_pid: str
    surviving_pid: int
    tombstone_pid: int
    target_updates: dict[str, Any]
    source_updates: dict[str, Any]
    discord_link_moved: bool
    discord_link_conflict: bool


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
        await self._db["bans"].create_index(
            "ban_id", unique=True, partialFilterExpression={"ban_id": {"$type": "int"}}
        )
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
            fallback_cursor = await bans.aggregate(
                [
                    {"$match": query},
                    {"$sort": {"_id": -1}},
                    {"$skip": skip},
                    {"$limit": limit},
                    {
                        "$project": {
                            "uuid": 1,
                            "ip": 1,
                            "pid": 1,
                            "ban_id": 1,
                            "case_number": 1,
                            "admin_discord_id": 1,
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
            )
            rows = await fallback_cursor.to_list(length=limit)

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
                            "ban_id": row.get("ban_id"),
                            "case_number": row.get("case_number"),
                            "pid": row.get("pid"),
                            "admin_discord_id": row.get("admin_discord_id"),
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
        case_number: int | None = None,
        ban_id: int | None = None,
    ) -> None:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)

        payload = BanDoc(
            ban_id=ban_id if ban_id is not None else await self.next_ban_id(),
            case_number=case_number,
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

    async def delete_ban(
        self, *, uuid: str, ip: str | None, ban_id: int | None = None
    ) -> int:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)
        if ban_id is not None:
            query["ban_id"] = ban_id

        result = await self._db_required()["bans"].delete_many(query)
        return result.deleted_count

    async def find_ban(self, *, uuid: str, ip: str | None) -> BanRecord | None:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)

        raw = await self._db_required()["bans"].find_one(query)
        if raw is None:
            return None
        return ban_record_from_doc(BanDoc.model_validate(raw).model_dump(mode="python"))

    async def next_ban_id(self, *, session: AsyncClientSession | None = None) -> int:
        row = await self._db_required()["counters"].find_one_and_update(
            {"_id": "ban_id"},
            {"$inc": {"seq": 1}},
            upsert=True,
            return_document=ReturnDocument.AFTER,
            session=session,
        )
        return int(row["seq"])

    async def find_ban_by_id(self, ban_id: int) -> BanRecord | None:
        raw = await self._db_required()["bans"].find_one({"ban_id": ban_id})
        if raw is None:
            return None
        return ban_record_from_doc(BanDoc.model_validate(raw).model_dump(mode="python"))

    async def ensure_ban_id(self, *, uuid: str, ip: str | None) -> BanRecord | None:
        bans = self._db_required()["bans"]
        raw = await bans.find_one(self._ban_lookup_query(uuid=uuid, ip=ip))
        if raw is None:
            return None
        if raw.get("ban_id") is None:
            ban_id = await self.next_ban_id()
            missing_id = {"$exists": False} if "ban_id" not in raw else None
            updated = await bans.find_one_and_update(
                {"_id": raw["_id"], "ban_id": missing_id},
                {"$set": {"ban_id": ban_id}},
                return_document=ReturnDocument.AFTER,
            )
            # Another reader may have won the assignment; return its id.
            raw = updated or await bans.find_one({"_id": raw["_id"]})
            if raw is None:
                return None
        return ban_record_from_doc(BanDoc.model_validate(raw).model_dump(mode="python"))

    async def update_ban_expiry(self, *, ban_id: int, new_expiry: datetime) -> bool:
        result = await self._db_required()["bans"].update_one(
            {
                "ban_id": ban_id,
                "$or": [
                    {"expire_date": None},
                    {"expire_date": {"$gt": new_expiry}},
                ],
            },
            {"$set": {"expire_date": new_expiry}},
        )
        return result.modified_count > 0

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
        case_number: int | None = None,
    ) -> None:
        payload = MuteDoc(
            case_number=case_number,
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

        # with_transaction retries the whole callback on a transient error such as a write
        # conflict, so the players are read inside it: a retry sees what the other writer did.
        async def merge(session: AsyncClientSession) -> AccountMergeResult:
            loaded = await self._load_merge_pair(
                db, session, source_pid=source_pid, target_pid=target_pid
            )
            if isinstance(loaded, str):
                return AccountMergeResult(success=False, error=loaded)
            plan = self._plan_merge(*loaded, keep_pid=normalized_keep_pid)
            return await self._apply_merge(
                db,
                session,
                plan,
                actor_name=actor_name,
                actor_discord_id=actor_discord_id,
                reason=reason,
            )

        try:
            async with db.client.start_session() as session:
                return await session.with_transaction(merge)
        except OperationFailure as error:
            if error.code != _ILLEGAL_OPERATION:
                raise
            # A standalone server refuses the first operation of a transaction.
            logger.warning("Account merge needs a MongoDB replica set: %s", error)
            return AccountMergeResult(
                success=False,
                error=(
                    "Account merge needs MongoDB to run as a replica set. "
                    "Nothing was changed."
                ),
            )

    @staticmethod
    async def _load_merge_pair(
        db: AsyncDatabase,
        session: AsyncClientSession,
        *,
        source_pid: int,
        target_pid: int,
    ) -> tuple[dict[str, Any], dict[str, Any]] | str:
        """Both players of a merge, or why they cannot be merged."""
        source_raw = await db["players"].find_one({"pid": source_pid}, session=session)
        if source_raw is None:
            return f"Source player #{source_pid} not found in database"

        target_raw = await db["players"].find_one({"pid": target_pid}, session=session)
        if target_raw is None:
            return f"Target player #{target_pid} not found in database"

        source_uuid = str(source_raw.get("uuid") or "").strip()
        target_uuid = str(target_raw.get("uuid") or "").strip()

        if source_uuid and target_uuid and source_uuid == target_uuid:
            return "Source and target have the same UUID."

        if source_uuid.startswith("merged:"):
            return (
                f"Source player #{source_pid} was already merged into another account."
            )

        if target_uuid.startswith("merged:"):
            return (
                f"Target player #{target_pid} is a closed account "
                "and cannot receive a merge."
            )

        online_pids = [
            pid
            for pid, doc in ((source_pid, source_raw), (target_pid, target_raw))
            if _to_bool(doc.get("online"))
        ]
        if online_pids:
            online_list = ", ".join(f"#{pid}" for pid in online_pids)
            return (
                f"Cannot merge while {online_list} is online. "
                "Ask the player to disconnect, then retry."
            )

        return source_raw, target_raw

    def _plan_merge(
        self, source_raw: dict[str, Any], target_raw: dict[str, Any], *, keep_pid: str
    ) -> _MergePlan:
        """The documents a merge writes, built from both players without touching the database."""
        source_pid = _to_int(source_raw.get("pid"))
        target_pid = _to_int(target_raw.get("pid"))
        source_uuid = str(source_raw.get("uuid") or "").strip()

        # The UUID always comes from the merge target; only the PID is selectable.
        if keep_pid == MERGE_KEEP_PID_SOURCE:
            surviving_pid = source_pid
            tombstone_pid = target_pid
        else:
            surviving_pid = target_pid
            tombstone_pid = source_pid

        source_discord_id = str(source_raw.get("discord_id") or "").strip()
        target_discord_id = str(target_raw.get("discord_id") or "").strip()

        target_updates = self._merged_target_fields(source_raw, target_raw)
        target_updates["pid"] = surviving_pid

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

        return _MergePlan(
            source_raw=source_raw,
            target_raw=target_raw,
            source_uuid=source_uuid,
            target_uuid=str(target_raw.get("uuid") or "").strip(),
            keep_pid=keep_pid,
            surviving_pid=surviving_pid,
            tombstone_pid=tombstone_pid,
            target_updates=target_updates,
            source_updates=source_updates,
            discord_link_moved=bool(source_discord_id) and not target_discord_id,
            discord_link_conflict=(
                bool(source_discord_id)
                and bool(target_discord_id)
                and source_discord_id != target_discord_id
            ),
        )

    def _merged_target_fields(
        self, source_raw: dict[str, Any], target_raw: dict[str, Any]
    ) -> dict[str, Any]:
        """The surviving account's fields: the target's values, filled in from the source."""

        def first(key: str, default: Any) -> Any:
            return target_raw.get(key) or source_raw.get(key) or default

        def union(key: str) -> list[Any]:
            return sorted(
                set((target_raw.get(key) or []) + (source_raw.get(key) or []))
            )

        hexed_points = _to_int(target_raw.get("hexed_points")) + _to_int(
            source_raw.get("hexed_points")
        )
        hexed_rank_reqs = [0, 3, 10, 20, 25, 30]
        hexed_rank = 0
        for idx, req in enumerate(hexed_rank_reqs):
            if hexed_points >= req:
                hexed_rank = idx

        fields: dict[str, Any] = {
            "total_play_time": _to_int(target_raw.get("total_play_time"))
            + _to_int(source_raw.get("total_play_time")),
            "hexed_points": hexed_points,
            "hexed_rank": hexed_rank,
            "unlocked_badges": union("unlocked_badges"),
            "active_badge": first("active_badge", ""),
            "custom_nickname": first("custom_nickname", ""),
            "description": first("description", ""),
            "discord_id": str(target_raw.get("discord_id") or "").strip()
            or str(source_raw.get("discord_id") or "").strip(),
            "discord_username": first("discord_username", ""),
            "discord_linked_at": first("discord_linked_at", 0),
            "device_tokens": {
                **(source_raw.get("device_tokens") or {}),
                **(target_raw.get("device_tokens") or {}),
            },
            "device_token_hashes": union("device_token_hashes"),
            "blocked_private_uuids": union("blocked_private_uuids"),
        }
        if self._settings.permissions_mode != "roles":
            fields["is_admin"] = bool(
                target_raw.get("is_admin") or source_raw.get("is_admin")
            )
            fields["admin_source"] = first("admin_source", "NONE")
        return fields

    async def _apply_merge(
        self,
        db: AsyncDatabase,
        session: AsyncClientSession,
        plan: _MergePlan,
        *,
        actor_name: str,
        actor_discord_id: str | None,
        reason: str,
    ) -> AccountMergeResult:
        source_raw = plan.source_raw
        target_raw = plan.target_raw

        await db["players"].update_one(
            {"_id": target_raw["_id"]}, {"$set": plan.target_updates}, session=session
        )
        await db["players"].update_one(
            {"_id": source_raw["_id"]}, {"$set": plan.source_updates}, session=session
        )

        games_transferred = 0
        punishment_transfers = {"bans": False, "mutes": False}
        if plan.source_uuid and plan.target_uuid:
            match_res = await db["games_v2"].update_many(
                {"player_stats.uuid": plan.source_uuid},
                {"$set": {"player_stats.$[elem].uuid": plan.target_uuid}},
                array_filters=[{"elem.uuid": plan.source_uuid}],
                session=session,
            )
            games_transferred = _to_int(match_res.modified_count)
            for collection in ("bans", "mutes"):
                punishment_transfers[collection] = await self._transfer_punishment(
                    db, session, plan, collection
                )

        audit_id = await self.append_moderation_audit(
            action="MERGE",
            target_uuid=plan.target_uuid,
            target_pid=plan.surviving_pid,
            target_name=str(target_raw.get("nickname") or "Unknown"),
            target_ip=str(target_raw.get("ip") or ""),
            actor_discord_id=actor_discord_id,
            actor_name=actor_name,
            reason=reason,
            occurred_at=datetime.now(UTC),
            extra={
                "merge": {
                    "keep_pid": plan.keep_pid,
                    "source_pid": source_raw.get("pid"),
                    "source_uuid": plan.source_uuid,
                    "source_name": str(source_raw.get("nickname") or "Unknown"),
                    "tombstone_pid": plan.tombstone_pid,
                    "surviving_pid": plan.surviving_pid,
                    "games_transferred": games_transferred,
                    "ban_transferred": punishment_transfers["bans"],
                    "mute_transferred": punishment_transfers["mutes"],
                    "discord_link_moved": plan.discord_link_moved,
                    "discord_link_conflict": plan.discord_link_conflict,
                }
            },
            session=session,
        )

        updated_target_raw = await db["players"].find_one(
            {"_id": target_raw["_id"]}, session=session
        )
        return AccountMergeResult(
            success=True,
            source_before=self._player_record(source_raw),
            target_before=self._player_record(target_raw),
            target_after=self._player_record(updated_target_raw),
            games_transferred=games_transferred,
            ban_transferred=punishment_transfers["bans"],
            mute_transferred=punishment_transfers["mutes"],
            audit_id=audit_id,
            keep_pid=plan.keep_pid,
            surviving_pid=plan.surviving_pid,
            tombstone_pid=plan.tombstone_pid,
            discord_link_moved=plan.discord_link_moved,
            discord_link_conflict=plan.discord_link_conflict,
        )

    async def _transfer_punishment(
        self,
        db: AsyncDatabase,
        session: AsyncClientSession,
        plan: _MergePlan,
        collection: str,
    ) -> bool:
        """Copy the closed account's ban or mute to the surviving one unless it has its own.

        Returns whether a copy was made. Both documents get the PID their account now has.
        """
        tombstone: dict[str, Any] = {"$set": {"pid": plan.tombstone_pid}}
        if collection == "bans":
            # Bans match by UUID or IP and unban deletes one ban_id, so a kept IP would
            # keep banning the surviving account after its own ban is lifted.
            tombstone["$unset"] = {"ip": ""}
        source_punishment = await db[collection].find_one(
            {"uuid": plan.source_uuid}, session=session
        )
        if source_punishment is not None:
            await db[collection].update_one(
                {"_id": source_punishment["_id"]}, tombstone, session=session
            )

        target_punishment = await db[collection].find_one(
            {"uuid": plan.target_uuid}, session=session
        )
        if target_punishment is not None:
            await db[collection].update_one(
                {"_id": target_punishment["_id"]},
                {"$set": {"pid": plan.surviving_pid}},
                session=session,
            )
            return False

        if source_punishment is None:
            return False

        transferred = dict(source_punishment)
        transferred.pop("_id", None)
        transferred["uuid"] = plan.target_uuid
        transferred["pid"] = plan.surviving_pid
        transferred["name"] = plan.target_raw.get("nickname") or "Unknown"
        transferred["reason"] = (
            f"[Merged from #{plan.source_raw.get('pid')}] "
            f"{source_punishment.get('reason', '')}"
        )
        if collection == "bans":
            # ban_id is unique and the closed account keeps its ban, so the copy needs its own.
            transferred["ban_id"] = await self.next_ban_id(session=session)
        await db[collection].insert_one(transferred, session=session)
        return True

    @staticmethod
    def _player_record(raw: dict[str, Any] | None) -> PlayerRecord:
        return player_record_from_doc(
            PlayerDoc.model_validate(raw).model_dump(mode="python")
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
