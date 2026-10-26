"""Bans and mutes: the ``bans`` and ``mutes`` collections and the ban id counter."""

from __future__ import annotations

import logging
import re
from datetime import datetime
from typing import Any

from bson.errors import InvalidBSON
from pymongo import DESCENDING, ReturnDocument
from pymongo.asynchronous.client_session import AsyncClientSession
from pymongo.errors import PyMongoError

from .dto import BanRecord, MuteRecord
from .mongo_store import BanDoc, MongoStore, MuteDoc, to_int
from .store_mappers import ban_record_from_doc, mute_record_from_doc

logger = logging.getLogger(__name__)


class PunishmentStore:
    def __init__(self, store: MongoStore) -> None:
        self._store = store

    async def ensure_indexes(self) -> None:
        await self._store.database["bans"].create_index(
            "ban_id", unique=True, partialFilterExpression={"ban_id": {"$type": "int"}}
        )

    async def list_bans(
        self, name_filter: str | None = None, limit: int = 6, page: int = 0
    ) -> list[BanRecord]:
        query: dict[str, Any] = {}
        if name_filter:
            query["name"] = {"$regex": re.escape(name_filter), "$options": "i"}

        skip = page * limit
        bans = self._store.database["bans"]
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
            return to_int(await self._store.database["bans"].count_documents(query))
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
        await self._store.database["bans"].replace_one(query, payload, upsert=True)

    async def delete_ban(
        self, *, uuid: str, ip: str | None, ban_id: int | None = None
    ) -> int:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)
        if ban_id is not None:
            query["ban_id"] = ban_id

        result = await self._store.database["bans"].delete_many(query)
        return result.deleted_count

    async def find_ban(self, *, uuid: str, ip: str | None) -> BanRecord | None:
        query = self._ban_lookup_query(uuid=uuid, ip=ip)

        raw = await self._store.database["bans"].find_one(query)
        if raw is None:
            return None
        return ban_record_from_doc(BanDoc.model_validate(raw).model_dump(mode="python"))

    async def next_ban_id(self, *, session: AsyncClientSession | None = None) -> int:
        row = await self._store.database["counters"].find_one_and_update(
            {"_id": "ban_id"},
            {"$inc": {"seq": 1}},
            upsert=True,
            return_document=ReturnDocument.AFTER,
            session=session,
        )
        return int(row["seq"])

    async def find_ban_by_id(self, ban_id: int) -> BanRecord | None:
        raw = await self._store.database["bans"].find_one({"ban_id": ban_id})
        if raw is None:
            return None
        return ban_record_from_doc(BanDoc.model_validate(raw).model_dump(mode="python"))

    async def ensure_ban_id(self, *, uuid: str, ip: str | None) -> BanRecord | None:
        bans = self._store.database["bans"]
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
        result = await self._store.database["bans"].update_one(
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
        await self._store.database["mutes"].replace_one(
            {"uuid": uuid}, payload, upsert=True
        )

    async def delete_mute(self, *, uuid: str) -> int:
        result = await self._store.database["mutes"].delete_one({"uuid": uuid})
        return result.deleted_count

    async def find_mute(self, *, uuid: str) -> MuteRecord | None:
        raw = await self._store.database["mutes"].find_one({"uuid": uuid})
        if raw is None:
            return None
        return mute_record_from_doc(
            MuteDoc.model_validate(raw).model_dump(mode="python")
        )

    @staticmethod
    def _ban_lookup_query(uuid: str, ip: str | None) -> dict[str, Any]:
        if ip:
            return {"$or": [{"uuid": uuid}, {"ip": ip}]}
        return {"uuid": uuid}
