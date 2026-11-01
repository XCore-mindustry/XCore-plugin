from __future__ import annotations

import asyncio
from datetime import UTC, datetime, timedelta
from typing import Any
from unittest.mock import AsyncMock, patch

import pytest
from backends import connected_store
from bot_fakes import make_settings
from mongo_fakes import Database
from pymongo.errors import DuplicateKeyError

from xcore_discord_bot.audit_store import AuditStore
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.punishment_store import PunishmentStore
from xcore_discord_bot.store_mappers import ban_record_from_doc, mute_record_from_doc

NOW = datetime(2026, 1, 1, tzinfo=UTC)


async def _punishments(*bans: dict[str, Any]) -> tuple[PunishmentStore, Database]:
    store, db = await connected_store()
    punishments = PunishmentStore(store)
    await punishments.ensure_indexes()
    for ban in bans:
        await db["bans"].insert_one(ban)
    return punishments, db


async def _rows(db: Database, name: str) -> list[dict[str, Any]]:
    return await db[name].find({}).to_list()


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


async def test_concurrent_counter_allocations_are_unique() -> None:
    store, db = await _punishments()
    ids = await asyncio.gather(*(store.next_ban_id() for _ in range(100)))
    assert sorted(ids) == list(range(1, 101))
    assert await _rows(db, "counters") == [{"_id": "ban_id", "seq": 100}]


async def test_ensure_ban_id_is_idempotent_and_preserves_existing_id() -> None:
    store, db = await _punishments(_ban())
    first = await store.ensure_ban_id(uuid="uuid-42", ip=None)
    second = await store.ensure_ban_id(uuid="uuid-42", ip=None)
    assert first == second
    assert first.ban_id == 1
    assert (await _rows(db, "bans"))[0]["ban_id"] == 1
    assert (await _rows(db, "counters"))[0]["seq"] == 1
    await db["bans"].update_one({"_id": 1}, {"$set": {"ban_id": 81}})
    assert (await store.ensure_ban_id(uuid="uuid-42", ip=None)).ban_id == 81
    assert (await _rows(db, "counters"))[0]["seq"] == 1


async def test_concurrent_lazy_assignment_returns_the_winning_id() -> None:
    store, db = await _punishments(_ban())
    bans = db["bans"]
    read, readers, total = bans.find_one, 0, 20
    everyone_read = asyncio.Event()

    async def find_one(query, **kwargs):
        # Every lazy reader sees the row without an id before any can assign one.
        nonlocal readers
        found = await read(query, **kwargs)
        if "_id" not in query:
            readers += 1
            if readers == total:
                everyone_read.set()
            await everyone_read.wait()
        return found

    bans.find_one = find_one
    records = await asyncio.gather(
        *(store.ensure_ban_id(uuid="uuid-42", ip=None) for _ in range(total))
    )
    assert len({record.ban_id for record in records}) == 1
    assert records[0].ban_id == (await _rows(db, "bans"))[0]["ban_id"]
    # losing allocations leave allowed gaps
    assert (await _rows(db, "counters"))[0]["seq"] == total


async def test_lazy_assignment_does_not_overwrite_an_id_written_during_allocation() -> (
    None
):
    store, db = await _punishments(_ban())

    async def competing_writer():
        await db["bans"].update_one({"_id": 1}, {"$set": {"ban_id": 80}})
        return 1

    store.next_ban_id = competing_writer
    assert (await store.ensure_ban_id(uuid="uuid-42", ip=None)).ban_id == 80
    assert (await _rows(db, "bans"))[0]["ban_id"] == 80


async def test_lazy_assignment_cannot_assign_to_a_replacement_row() -> None:
    store, db = await _punishments(_ban())

    async def replace_during_allocation():
        await db["bans"].delete_many({})
        await db["bans"].insert_one(_ban(_id=2))
        return 1

    store.next_ban_id = replace_during_allocation
    assert await store.ensure_ban_id(uuid="uuid-42", ip=None) is None
    assert await _rows(db, "bans") == [_ban(_id=2)]


async def test_identity_lookup_and_missing_lazy_assignment() -> None:
    store, db = await _punishments(_ban(ban_id=7, case_number=57))
    record = await store.find_ban_by_id(7)
    assert record.ban_id == 7 and record.case_number == 57
    assert (await store.find_ban(uuid="uuid-42", ip=None)) == record
    assert await store.find_ban_by_id(8) is None
    assert await store.ensure_ban_id(uuid="missing", ip=None) is None
    assert not await _rows(db, "counters")


async def test_update_ban_expiry_keeps_id_and_case_and_refuses_lengthening() -> None:
    store, db = await _punishments(_ban(ban_id=7, case_number=57))
    earlier = NOW + timedelta(days=2)
    assert await store.update_ban_expiry(ban_id=7, new_expiry=earlier)
    assert not await store.update_ban_expiry(ban_id=7, new_expiry=earlier)
    assert not await store.update_ban_expiry(
        ban_id=7, new_expiry=NOW + timedelta(days=3)
    )
    assert not await store.update_ban_expiry(ban_id=8, new_expiry=NOW)
    assert await _rows(db, "bans") == [
        _ban(ban_id=7, case_number=57, expire_date=earlier)
    ]


@pytest.mark.parametrize("missing_expiry", [False, True])
async def test_update_permanent_ban_to_finite_keeps_identity_and_concurrent_guard(
    missing_expiry,
) -> None:
    row = _ban(ban_id=7, case_number=57, expire_date=None)
    if missing_expiry:
        row.pop("expire_date")
    store, db = await _punishments(row)
    expiry = NOW + timedelta(hours=2)
    assert not await store.update_ban_expiry(ban_id=8, new_expiry=expiry)
    assert await store.update_ban_expiry(ban_id=7, new_expiry=expiry)
    (ban,) = await _rows(db, "bans")
    assert ban["ban_id"] == 7
    assert ban["case_number"] == 57
    assert not await store.update_ban_expiry(
        ban_id=7, new_expiry=expiry + timedelta(hours=1)
    )
    assert not await store.update_ban_expiry(ban_id=7, new_expiry=expiry)
    assert (await _rows(db, "bans"))[0]["expire_date"] == expiry
    assert not await _rows(db, "counters")


async def test_upserts_allocate_new_ids_and_preserve_supplied_identity() -> None:
    store, db = await _punishments()
    kwargs = {key: value for key, value in _ban().items() if key != "_id"}
    await store.upsert_ban(**kwargs, case_number=57)
    first = await store.find_ban(uuid="uuid-42", ip=None)
    await store.upsert_ban(**kwargs)
    second = await store.find_ban(uuid="uuid-42", ip=None)
    assert first.ban_id == 1 and first.case_number == 57
    assert second.ban_id == 2 and second.case_number is None
    await store.upsert_ban(**kwargs, ban_id=91)
    assert (await store.find_ban_by_id(91)).ban_id == 91
    assert len(await _rows(db, "bans")) == 1
    assert (await _rows(db, "counters"))[0]["seq"] == 2


async def test_unban_id_guard_preserves_a_replacement_ban() -> None:
    store, db = await _punishments(_ban(ban_id=2))
    assert await store.delete_ban(uuid="uuid-42", ip=None, ban_id=1) == 0
    assert len(await _rows(db, "bans")) == 1
    assert await store.delete_ban(uuid="uuid-42", ip=None, ban_id=2) == 1


async def test_mute_upsert_carries_case_number() -> None:
    store, db = await _punishments()
    kwargs = {key: value for key, value in _ban().items() if key not in {"_id", "ip"}}
    await store.upsert_mute(**kwargs, case_number=57)
    assert (await store.find_mute(uuid="uuid-42")).case_number == 57
    assert (await _rows(db, "mutes"))[0]["case_number"] == 57


@pytest.mark.parametrize("value", [None, 57, "57"])
def test_mappers_carry_additive_identity_fields(value) -> None:
    ban = ban_record_from_doc(_ban(ban_id=value, case_number=value))
    mute = mute_record_from_doc(_ban(case_number=value))
    expected = int(value) if value is not None else None
    assert ban.ban_id == expected and ban.case_number == expected
    assert mute.case_number == expected


async def test_ensure_indexes_creates_the_partial_unique_ban_id_index() -> None:
    store, db = await connected_store()
    await PunishmentStore(store).ensure_indexes()
    assert db["bans"].indexes == [
        (
            "ban_id",
            {"unique": True, "partialFilterExpression": {"ban_id": {"$type": "int"}}},
        )
    ]
    # Two bans without an id are fine; two with the same id are not.
    await db["bans"].insert_one(_ban(_id=1))
    await db["bans"].insert_one(_ban(_id=2))
    await db["bans"].insert_one(_ban(_id=3, ban_id=5))
    with pytest.raises(DuplicateKeyError):
        await db["bans"].insert_one(_ban(_id=4, ban_id=5))


async def test_audit_ensure_indexes_creates_the_sparse_ban_id_index() -> None:
    store, db = await connected_store()
    await AuditStore(store).ensure_indexes()
    assert db["moderation_audit"].indexes == [
        ("details.extra.ban_id", {"sparse": True})
    ]


async def test_connect_pings_once_and_leaves_the_indexes_to_the_stores() -> None:
    db = Database()
    db.command = AsyncMock()
    store = MongoStore(make_settings())
    with patch("xcore_discord_bot.mongo_store.AsyncMongoClient") as client:
        client.return_value.__getitem__.return_value = db
        await store.connect()
        await store.connect()
    assert db["bans"].indexes == []
    assert db["moderation_audit"].indexes == []
    db.command.assert_awaited_once_with("ping")


async def test_ensure_ban_id_also_repairs_explicit_null_without_overwriting_ids() -> (
    None
):
    store, _ = await _punishments(_ban(ban_id=None))
    record = await store.ensure_ban_id(uuid="uuid-42", ip=None)
    assert record.ban_id == 1
    assert (await store.ensure_ban_id(uuid="uuid-42", ip=None)) == record
