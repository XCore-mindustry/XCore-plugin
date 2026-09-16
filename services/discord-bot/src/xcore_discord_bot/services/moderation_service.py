from __future__ import annotations

from datetime import datetime

from ..dto import AuditRecordSummary, BanRecord, MuteRecord
from ..mongo_store import MongoStore
from ..redis_bus import RedisBus


class ModerationService:
    def __init__(self, store: MongoStore, bus: RedisBus) -> None:
        self._store = store
        self._bus = bus

    def now_utc(self) -> datetime:
        return self._store.now_utc()

    async def count_bans(self, *, name_filter: str | None = None) -> int:
        return await self._store.count_bans(name_filter)

    async def list_bans(
        self,
        *,
        name_filter: str | None = None,
        limit: int = 10,
        page: int = 1,
    ) -> list[BanRecord]:
        return await self._store.list_bans(
            name_filter=name_filter, limit=limit, page=page
        )

    async def find_ban(
        self,
        *,
        uuid: str,
        ip: str | None = None,
    ) -> BanRecord | None:
        return await self._store.find_ban(uuid=uuid, ip=ip)

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
        await self._store.upsert_ban(
            uuid=uuid,
            ip=ip,
            pid=pid,
            name=name,
            admin_name=admin_name,
            admin_discord_id=admin_discord_id,
            reason=reason,
            expire_date=expire_date,
        )

    async def delete_ban(self, *, uuid: str, ip: str | None = None) -> int:
        return await self._store.delete_ban(uuid=uuid, ip=ip)

    async def publish_kick_banned(self, *, uuid_value: str, ip: str | None) -> None:
        await self._bus.publish_kick_banned(uuid_value=uuid_value, ip=ip)

    async def publish_pardon_player(self, *, uuid_value: str) -> None:
        await self._bus.publish_pardon_player(uuid_value=uuid_value)

    async def find_mute(self, *, uuid: str) -> MuteRecord | None:
        return await self._store.find_mute(uuid=uuid)

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
        await self._store.upsert_mute(
            uuid=uuid,
            pid=pid,
            name=name,
            admin_name=admin_name,
            admin_discord_id=admin_discord_id,
            reason=reason,
            expire_date=expire_date,
        )

    async def delete_mute(self, *, uuid: str) -> int:
        return await self._store.delete_mute(uuid=uuid)

    async def list_audit_for_player(
        self, *, uuid: str, limit: int, page: int
    ) -> list[AuditRecordSummary]:
        return await self._store.list_audit_for_player(
            uuid=uuid, limit=limit, page=page
        )

    async def count_audit_for_player(self, *, uuid: str) -> int:
        return await self._store.count_audit_for_player(uuid=uuid)

    async def list_audit_for_actor(
        self,
        *,
        actor_id: str,
        actor_discord_id: str | None,
        limit: int,
        page: int,
    ) -> list[AuditRecordSummary]:
        return await self._store.list_audit_for_actor(
            actor_id=actor_id,
            actor_discord_id=actor_discord_id,
            limit=limit,
            page=page,
        )

    async def count_audit_for_actor(
        self,
        *,
        actor_id: str,
        actor_discord_id: str | None,
    ) -> int:
        return await self._store.count_audit_for_actor(
            actor_id=actor_id,
            actor_discord_id=actor_discord_id,
        )

    async def find_audit_by_id(self, *, audit_id: str) -> AuditRecordSummary | None:
        return await self._store.find_audit_by_id(audit_id=audit_id)

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
    ) -> str:
        return await self._store.append_moderation_audit(
            action=action,
            target_uuid=target_uuid,
            target_pid=target_pid,
            target_name=target_name,
            target_ip=target_ip,
            actor_discord_id=actor_discord_id,
            actor_name=actor_name,
            reason=reason,
            occurred_at=occurred_at,
            duration_ms=duration_ms,
            expires_at=expires_at,
            related_audit_id=related_audit_id,
            supersedes_audit_id=supersedes_audit_id,
            request_id=request_id,
        )
