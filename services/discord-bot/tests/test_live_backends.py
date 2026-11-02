"""Migration checks against explicitly supplied, disposable test backends."""

from __future__ import annotations

import json
import os
from pathlib import Path
from uuid import uuid4

import pytest
from bot_fakes import make_settings
from pymongo import AsyncMongoClient

from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.store_mappers import player_record_from_doc


@pytest.mark.skipif(not os.getenv("XCORE_TEST_MONGO_URL"), reason="No test MongoDB")
async def test_player_contract_roundtrips_through_mongo() -> None:
    uri = os.environ["XCORE_TEST_MONGO_URL"]
    database = f"xcore_migration_test_{uuid4().hex}"
    document = json.loads(
        (Path(__file__).parent / "fixtures/player_document.json").read_text()
    )
    client = AsyncMongoClient(uri, serverSelectionTimeoutMS=5000)
    store = MongoStore(make_settings(mongo_uri=uri, mongo_db_name=database))
    try:
        await client[database]["players"].insert_one(dict(document))
        await store.connect()
        assert await store.find_player_by_pid(
            document["pid"]
        ) == player_record_from_doc(document)
    finally:
        await store.close()
        await client.drop_database(database)
        await client.close()


@pytest.mark.skipif(not os.getenv("XCORE_TEST_REDIS_URL"), reason="No test Redis")
async def test_idempotency_survives_a_real_redis_reconnection() -> None:
    key = f"migration-test:{uuid4().hex}"
    transport = RedisTransport(
        make_settings(redis_url=os.environ["XCORE_TEST_REDIS_URL"])
    )
    await transport.connect()
    try:
        assert await transport.claim_idempotency(key)
        await transport.reconnect()
        assert not await transport.claim_idempotency(key)
    finally:
        await transport.redis.delete(f"xcore:idmp:{key}")
        await transport.close()
