from __future__ import annotations

import logging
import re
from datetime import datetime, timezone
from typing import Any

from bson.errors import InvalidBSON
from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase
from pymongo.errors import PyMongoError
from pydantic import BaseModel, ConfigDict
from pymongo import DESCENDING

from .dto import BanRecord, MuteRecord, PlayerRecord
from .settings import Settings
from .store_mappers import (
    ban_record_from_doc,
    mute_record_from_doc,
    player_record_from_doc,
)

logger = logging.getLogger(__name__)


class _MongoDoc(BaseModel):
    model_config = ConfigDict(extra="allow", frozen=True, arbitrary_types_allowed=True)


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
    expire_date: Any | None = None


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
        if normalized.isdigit():
            rows = await players.aggregate(
                [
                    {
                        "$match": {
                            "$expr": {
                                "$regexMatch": {
                                    "input": {"$toString": {"$ifNull": ["$pid", ""]}},
                                    "regex": f"^{re.escape(normalized)}",
                                }
                            }
                        }
                    },
                    {"$sort": {"pid": -1}},
                    {"$limit": limit},
                    {"$project": {"_id": 0, "pid": 1, "nickname": 1}},
                ]
            ).to_list(length=limit)
        else:
            cursor = (
                players.find(
                    {"nickname": {"$regex": re.escape(normalized), "$options": "i"}},
                    {"_id": 0, "pid": 1, "nickname": 1},
                )
                .sort("pid", DESCENDING)
                .limit(limit)
            )
            rows = await cursor.to_list(length=limit)

        records = [player_record_from_doc(row) for row in rows]
        return [record for record in records if record.pid >= 0]

    async def count_players_by_name(self, query: str) -> int:
        return int(
            await self._db_required()["players"].count_documents(
                {"nickname": {"$regex": re.escape(query), "$options": "i"}}
            )
        )

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
        return int(await self._db_required()["bans"].count_documents(query))

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
        query = self._ban_lookup_query(uuid=uuid, ip=ip)

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

    async def find_mute(self, *, uuid: str) -> MuteRecord | None:
        raw = await self._db_required()["mutes"].find_one({"uuid": uuid})
        if raw is None:
            return None
        return mute_record_from_doc(
            MuteDoc.model_validate(raw).model_dump(mode="python")
        )

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

    @staticmethod
    def _ban_lookup_query(uuid: str, ip: str | None) -> dict[str, Any]:
        if ip:
            return {"$or": [{"uuid": uuid}, {"ip": ip}]}
        return {"uuid": uuid}
