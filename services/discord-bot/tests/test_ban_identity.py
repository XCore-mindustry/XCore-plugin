from __future__ import annotations

import asyncio
from copy import deepcopy
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import Any
from unittest.mock import AsyncMock, patch

import pytest
from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError
from test_rating_store import _Collection, _Database, _matches

from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.punishment_store import PunishmentStore
from xcore_discord_bot.store_mappers import ban_record_from_doc, mute_record_from_doc

NOW = datetime(2026, 1, 1, tzinfo=UTC)


def _identity_matches(doc: dict[str, Any], query: dict[str, Any]) -> bool:
    for key, value in query.items():
        if key == "$or":
            if not any(_identity_matches(doc, option) for option in value):
                return False
        elif isinstance(value, dict) and "$exists" in value:
            if (key in doc) != value["$exists"]:
                return False
        elif not _matches(doc, {key: value}):
            return False
    return True


class _IdentityCollection(_Collection):
    def __init__(self) -> None:
        super().__init__()
        self.lock = asyncio.Lock()
        self.updates: list[tuple[dict, dict, bool, object]] = []
        self.indexes: list[tuple[str, dict]] = []
        self.read_gate: asyncio.Event | None = None
        self.read_count = 0
        self.read_total = 0

    async def create_index(self, key, **kwargs):
        self.indexes.append((key, kwargs))

    async def find_one(self, query):
        doc = next((doc for doc in self.docs if _identity_matches(doc, query)), None)
        snapshot = deepcopy(doc)
        # Make concurrent lazy readers all see the same row before any can assign it.
        if self.read_gate is not None and "_id" not in query:
            self.read_count += 1
            if self.read_count == self.read_total:
                self.read_gate.set()
            await self.read_gate.wait()
        return snapshot

    @staticmethod
    def _apply(target, update):
        target.update(deepcopy(update.get("$set", {})))
        for key, amount in update.get("$inc", {}).items():
            target[key] = target.get(key, 0) + amount
        for key, value in update.get("$addToSet", {}).items():
            values = target.setdefault(key, [])
            if value not in values:
                values.append(value)

    def _check_unique(self, row):
        if isinstance(row.get("ban_id"), int) and any(
            doc["_id"] != row["_id"] and doc.get("ban_id") == row["ban_id"]
            for doc in self.docs
        ):
            raise DuplicateKeyError("duplicate ban id")

    async def find_one_and_update(
        self,
        query,
        update,
        *,
        upsert=False,
        return_document=ReturnDocument.BEFORE,
        session=None,
    ):
        async with self.lock:
            self.updates.append(
                (deepcopy(query), deepcopy(update), upsert, return_document)
            )
            target = next(
                (doc for doc in self.docs if _identity_matches(doc, query)), None
            )
            if target is None:
                if not upsert:
                    return None
                target = {"_id": query["_id"]}
                self.docs.append(target)
            before = deepcopy(target)
            changed = deepcopy(target)
            self._apply(changed, update)
            self._check_unique(changed)
            target.update(changed)
            return (
                deepcopy(target) if return_document == ReturnDocument.AFTER else before
            )

    async def update_one(self, query, update, upsert=False):
        async with self.lock:
            self.updates.append((deepcopy(query), deepcopy(update), upsert, None))
            target = next(
                (doc for doc in self.docs if _identity_matches(doc, query)), None
            )
            if target is None:
                return SimpleNamespace(modified_count=0)
            before = deepcopy(target)
            self._apply(target, update)
            return SimpleNamespace(modified_count=int(before != target))

    async def replace_one(self, query, payload, upsert=False):
        async with self.lock:
            target = next(
                (doc for doc in self.docs if _identity_matches(doc, query)), None
            )
            if target is None:
                assert upsert
                target = {"_id": len(self.docs) + 1}
                self.docs.append(target)
            row = {"_id": target["_id"], **deepcopy(payload)}
            self._check_unique(row)
            target.clear()
            target.update(row)

    async def delete_many(self, query):
        async with self.lock:
            before = len(self.docs)
            self.docs = [doc for doc in self.docs if not _identity_matches(doc, query)]
            return SimpleNamespace(deleted_count=before - len(self.docs))

    async def insert_one(self, doc, *, session=None):
        self.docs.append(deepcopy(doc))


class _IdentityDatabase(_Database):
    def __getitem__(self, name: str) -> _IdentityCollection:
        return self.collections.setdefault(name, _IdentityCollection())


def _store() -> tuple[MongoStore, _IdentityDatabase]:
    db = _IdentityDatabase()
    store = MongoStore(SimpleNamespace())
    store._db = db
    return store, db


def _punishments() -> tuple[PunishmentStore, _IdentityDatabase]:
    store, db = _store()
    return PunishmentStore(store), db


def _ban(**extra) -> dict[str, Any]:
    return {
        "_id": 1,
        "uuid": "uuid-42",
        "ip": "1.2.3.4",
        "pid": 42,
        "name": "Target",
        "admin_name": "Moderator",
        "admin_discord_id": "123",
        "reason": "griefing",
        "expire_date": NOW + timedelta(days=7),
        **extra,
    }


async def test_concurrent_counter_allocations_are_unique_and_use_atomic_inc() -> None:
    store, db = _punishments()
    ids = await asyncio.gather(*(store.next_ban_id() for _ in range(100)))
    assert sorted(ids) == list(range(1, 101))
    assert db["counters"].docs == [{"_id": "ban_id", "seq": 100}]
    assert all(
        call == ({"_id": "ban_id"}, {"$inc": {"seq": 1}}, True, ReturnDocument.AFTER)
        for call in db["counters"].updates
    )


async def test_ensure_ban_id_is_idempotent_and_preserves_existing_id() -> None:
    store, db = _punishments()
    db["bans"].docs = [_ban()]
    first = await store.ensure_ban_id(uuid="uuid-42", ip=None)
    second = await store.ensure_ban_id(uuid="uuid-42", ip=None)
    assert first == second
    assert first.ban_id == 1
    assert db["counters"].docs[0]["seq"] == 1
    assert db["bans"].updates == [
        (
            {"_id": 1, "ban_id": {"$exists": False}},
            {"$set": {"ban_id": 1}},
            False,
            ReturnDocument.AFTER,
        )
    ]
    db["bans"].docs[0]["ban_id"] = 81
    assert (await store.ensure_ban_id(uuid="uuid-42", ip=None)).ban_id == 81
    assert db["counters"].docs[0]["seq"] == 1


async def test_concurrent_lazy_assignment_returns_the_winning_id() -> None:
    store, db = _punishments()
    bans = db["bans"]
    bans.docs = [_ban()]
    bans.read_gate = asyncio.Event()
    bans.read_total = 20
    records = await asyncio.gather(
        *(store.ensure_ban_id(uuid="uuid-42", ip=None) for _ in range(bans.read_total))
    )
    assert len({record.ban_id for record in records}) == 1
    assert records[0].ban_id == bans.docs[0]["ban_id"]
    assert db["counters"].docs[0]["seq"] == 20  # losing allocations leave allowed gaps


async def test_lazy_assignment_does_not_overwrite_an_id_written_during_allocation() -> (
    None
):
    store, db = _punishments()
    db["bans"].docs = [_ban()]

    async def competing_writer():
        db["bans"].docs[0]["ban_id"] = 80
        return 1

    store.next_ban_id = competing_writer
    assert (await store.ensure_ban_id(uuid="uuid-42", ip=None)).ban_id == 80
    assert db["bans"].docs[0]["ban_id"] == 80


async def test_lazy_assignment_cannot_assign_to_a_replacement_row() -> None:
    store, db = _punishments()
    db["bans"].docs = [_ban()]

    async def replace_during_allocation():
        db["bans"].docs = [_ban(_id=2)]
        return 1

    store.next_ban_id = replace_during_allocation
    assert await store.ensure_ban_id(uuid="uuid-42", ip=None) is None
    assert "ban_id" not in db["bans"].docs[0]


async def test_identity_lookup_and_missing_lazy_assignment() -> None:
    store, db = _punishments()
    db["bans"].docs = [_ban(ban_id=7, case_number=57)]
    record = await store.find_ban_by_id(7)
    assert record.ban_id == 7 and record.case_number == 57
    assert (await store.find_ban(uuid="uuid-42", ip=None)) == record
    assert await store.find_ban_by_id(8) is None
    assert await store.ensure_ban_id(uuid="missing", ip=None) is None
    assert not db["counters"].docs


async def test_update_ban_expiry_keeps_id_and_case_and_refuses_lengthening() -> None:
    store, db = _punishments()
    db["bans"].docs = [_ban(ban_id=7, case_number=57)]
    earlier = NOW + timedelta(days=2)
    assert await store.update_ban_expiry(ban_id=7, new_expiry=earlier)
    assert not await store.update_ban_expiry(ban_id=7, new_expiry=earlier)
    assert not await store.update_ban_expiry(
        ban_id=7, new_expiry=NOW + timedelta(days=3)
    )
    assert not await store.update_ban_expiry(ban_id=8, new_expiry=NOW)
    assert db["bans"].docs == [_ban(ban_id=7, case_number=57, expire_date=earlier)]
    assert db["bans"].updates[0][:2] == (
        {
            "ban_id": 7,
            "$or": [{"expire_date": None}, {"expire_date": {"$gt": earlier}}],
        },
        {"$set": {"expire_date": earlier}},
    )


@pytest.mark.parametrize("missing_expiry", [False, True])
async def test_update_permanent_ban_to_finite_keeps_identity_and_concurrent_guard(
    missing_expiry,
) -> None:
    store, db = _punishments()
    row = _ban(ban_id=7, case_number=57, expire_date=None)
    if missing_expiry:
        row.pop("expire_date")
    db["bans"].docs = [row]
    expiry = NOW + timedelta(hours=2)
    assert not await store.update_ban_expiry(ban_id=8, new_expiry=expiry)
    assert await store.update_ban_expiry(ban_id=7, new_expiry=expiry)
    assert db["bans"].docs[0]["ban_id"] == 7
    assert db["bans"].docs[0]["case_number"] == 57
    assert not await store.update_ban_expiry(
        ban_id=7, new_expiry=expiry + timedelta(hours=1)
    )
    assert not await store.update_ban_expiry(ban_id=7, new_expiry=expiry)
    assert db["bans"].docs[0]["expire_date"] == expiry
    assert not db["counters"].docs


async def test_upserts_allocate_new_ids_and_preserve_supplied_identity() -> None:
    store, db = _punishments()
    kwargs = {key: value for key, value in _ban().items() if key != "_id"}
    await store.upsert_ban(**kwargs, case_number=57)
    first = await store.find_ban(uuid="uuid-42", ip=None)
    await store.upsert_ban(**kwargs)
    second = await store.find_ban(uuid="uuid-42", ip=None)
    assert first.ban_id == 1 and first.case_number == 57
    assert second.ban_id == 2 and second.case_number is None
    await store.upsert_ban(**kwargs, ban_id=91)
    assert (await store.find_ban_by_id(91)).ban_id == 91
    assert db["counters"].docs[0]["seq"] == 2


async def test_unban_id_guard_preserves_a_replacement_ban() -> None:
    store, db = _punishments()
    db["bans"].docs = [_ban(ban_id=2)]
    assert await store.delete_ban(uuid="uuid-42", ip=None, ban_id=1) == 0
    assert len(db["bans"].docs) == 1
    assert await store.delete_ban(uuid="uuid-42", ip=None, ban_id=2) == 1


async def test_mute_upsert_carries_case_number() -> None:
    store, db = _punishments()
    kwargs = {key: value for key, value in _ban().items() if key not in {"_id", "ip"}}
    await store.upsert_mute(**kwargs, case_number=57)
    assert (await store.find_mute(uuid="uuid-42")).case_number == 57
    assert db["mutes"].docs[0]["case_number"] == 57


@pytest.mark.parametrize("value", [None, 57, "57"])
def test_mappers_carry_additive_identity_fields(value) -> None:
    ban = ban_record_from_doc(_ban(ban_id=value, case_number=value))
    mute = mute_record_from_doc(_ban(case_number=value))
    expected = int(value) if value is not None else None
    assert ban.ban_id == expected and ban.case_number == expected
    assert mute.case_number == expected


async def test_ensure_indexes_creates_the_partial_unique_ban_id_index() -> None:
    store, db = _punishments()
    await store.ensure_indexes()
    assert db["bans"].indexes == [
        (
            "ban_id",
            {"unique": True, "partialFilterExpression": {"ban_id": {"$type": "int"}}},
        )
    ]


async def test_connect_creates_the_sparse_audit_index() -> None:
    store, db = _store()
    db.command = AsyncMock()
    store._settings = SimpleNamespace(
        mongo_uri="mongodb://unused", mongo_db_name="xcore"
    )
    store._db = None
    with patch("xcore_discord_bot.mongo_store.AsyncMongoClient") as client:
        client.return_value.__getitem__.return_value = db
        await store.connect()
        await store.connect()
    assert db["bans"].indexes == []
    assert db["moderation_audit"].indexes == [
        ("details.extra.ban_id", {"sparse": True})
    ]
    db.command.assert_awaited_once_with("ping")


async def test_ensure_ban_id_also_repairs_explicit_null_without_overwriting_ids() -> (
    None
):
    store, db = _punishments()
    db["bans"].docs = [_ban(ban_id=None)]
    record = await store.ensure_ban_id(uuid="uuid-42", ip=None)
    assert record.ban_id == 1
    assert (await store.ensure_ban_id(uuid="uuid-42", ip=None)) == record
