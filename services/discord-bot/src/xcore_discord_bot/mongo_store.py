from __future__ import annotations

import re
from datetime import datetime, timezone
from typing import Any

from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase
from pydantic import BaseModel, ConfigDict
from pymongo import DESCENDING

from .settings import Settings


class _MongoDoc(BaseModel):
    model_config = ConfigDict(extra="allow", frozen=True)


class PlayerDoc(_MongoDoc):
    pid: int | None = None
    uuid: str | None = None
    ip: str | None = None
    nickname: str | None = None
    custom_nickname: str | None = None
    hexed_rank: int | None = None
    hexed_points: int | None = None
    total_play_time: int | None = None
    pvp_rating: int | None = None
    is_admin: bool | None = None
    admin_confirmed: bool | None = None
    password_hash: str | None = None
    created_at: int | None = None
    updated_at: int | None = None


class BanDoc(_MongoDoc):
    uuid: str | None = None
    ip: str | None = None
    name: str | None = None
    admin_name: str | None = None
    reason: str | None = None
    expire_date: datetime | None = None


class MuteDoc(_MongoDoc):
    uuid: str
    name: str
    admin_name: str
    reason: str
    expire_date: datetime


class MongoStore:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client: AsyncIOMotorClient | None = None
        self._db: AsyncIOMotorDatabase | None = None

    async def connect(self) -> None:
        if self._db is not None:
            return

        self._client = AsyncIOMotorClient(self._settings.mongo_uri)
        self._db = self._client[self._settings.mongo_db_name]
        await self._db.command("ping")

    async def close(self) -> None:
        if self._client is not None:
            self._client.close()
        self._client = None
        self._db = None

    async def find_player_by_pid(self, pid: int) -> dict[str, Any] | None:
        raw = await self._db_required()["players"].find_one({"pid": pid})
        if raw is None:
            return None
        return PlayerDoc.model_validate(raw).model_dump(mode="python")

    async def find_player_by_uuid(self, uuid: str) -> dict[str, Any] | None:
        raw = await self._db_required()["players"].find_one({"uuid": uuid})
        if raw is None:
            return None
        return PlayerDoc.model_validate(raw).model_dump(mode="python")

    async def search_players(
        self, query: str, limit: int = 6, page: int = 0
    ) -> list[dict[str, Any]]:
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
        return [PlayerDoc.model_validate(row).model_dump(mode="python") for row in rows]

    async def list_bans(
        self, name_filter: str | None = None, limit: int = 6, page: int = 0
    ) -> list[dict[str, Any]]:
        query: dict[str, Any] = {}
        if name_filter:
            query["name"] = {"$regex": re.escape(name_filter), "$options": "i"}

        skip = page * limit
        cursor = (
            self._db_required()["bans"]
            .find(query)
            .sort("expire_date", DESCENDING)
            .skip(skip)
            .limit(limit)
        )
        rows = await cursor.to_list(length=limit)
        return [BanDoc.model_validate(row).model_dump(mode="python") for row in rows]

    async def upsert_ban(
        self,
        *,
        uuid: str,
        ip: str | None,
        name: str,
        admin_name: str,
        reason: str,
        expire_date: datetime,
    ) -> None:
        query: dict[str, Any] = {"uuid": uuid}
        if ip:
            query = {"$or": [{"uuid": uuid}, {"ip": ip}]}

        payload = BanDoc(
            uuid=uuid,
            ip=ip,
            name=name,
            admin_name=admin_name,
            reason=reason,
            expire_date=expire_date,
        ).model_dump(mode="python")
        await self._db_required()["bans"].replace_one(query, payload, upsert=True)

    async def delete_ban(self, *, uuid: str, ip: str | None) -> int:
        query: dict[str, Any] = {"uuid": uuid}
        if ip:
            query = {"$or": [{"uuid": uuid}, {"ip": ip}]}

        result = await self._db_required()["bans"].delete_many(query)
        return result.deleted_count

    async def upsert_mute(
        self,
        *,
        uuid: str,
        name: str,
        admin_name: str,
        reason: str,
        expire_date: datetime,
    ) -> None:
        payload = MuteDoc(
            uuid=uuid,
            name=name,
            admin_name=admin_name,
            reason=reason,
            expire_date=expire_date,
        ).model_dump(mode="python")
        await self._db_required()["mutes"].replace_one(
            {"uuid": uuid}, payload, upsert=True
        )

    async def delete_mute(self, *, uuid: str) -> int:
        result = await self._db_required()["mutes"].delete_one({"uuid": uuid})
        return result.deleted_count

    async def remove_admin(self, *, uuid: str) -> bool:
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            {"$set": {"is_admin": False, "admin_confirmed": False}},
        )
        return result.modified_count > 0

    async def reset_password(self, *, uuid: str) -> bool:
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            {"$set": {"password_hash": ""}},
        )
        return result.modified_count > 0

    async def mark_admin_confirmed(self, *, uuid: str) -> bool:
        result = await self._db_required()["players"].update_one(
            {"uuid": uuid},
            {"$set": {"admin_confirmed": True}},
        )
        return result.modified_count > 0

    @staticmethod
    def now_utc() -> datetime:
        return datetime.now(timezone.utc)

    def _db_required(self) -> AsyncIOMotorDatabase:
        if self._db is None:
            raise RuntimeError("MongoStore is not connected")
        return self._db
