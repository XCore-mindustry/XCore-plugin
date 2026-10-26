"""The moderation audit: the ``moderation_audit`` collection the bot and the servers write."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime
from typing import Any

from pymongo import DESCENDING
from pymongo.asynchronous.client_session import AsyncClientSession
from pymongo.errors import PyMongoError

from .dto import AuditRecordSummary
from .mongo_store import AuditDoc, MongoStore, to_int


class AuditStore:
    def __init__(self, store: MongoStore) -> None:
        self._store = store

    async def ensure_indexes(self) -> None:
        await self._store.database["moderation_audit"].create_index(
            "details.extra.ban_id", sparse=True
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
            self._store.database["moderation_audit"]
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
                await self._store.database["moderation_audit"].count_documents(
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
            self._store.database["moderation_audit"]
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
                await self._store.database["moderation_audit"].count_documents(
                    actor_filter
                )
            )
        except PyMongoError:
            return 0

    async def find_audit_by_id(self, *, audit_id: str) -> AuditRecordSummary | None:
        if not audit_id.strip():
            return None
        raw = await self._store.database["moderation_audit"].find_one(
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
        await self._store.database["moderation_audit"].insert_one(
            document, session=session
        )
        return audit_id

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
