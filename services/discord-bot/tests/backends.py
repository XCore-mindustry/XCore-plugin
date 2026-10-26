"""The bot's real MongoStore and RedisTransport, connected to in-memory backends.

Tests that need a store or a bus use these instead of a class of their own, so a method
renamed or removed in ``src`` fails here too (#32). The store runs over the fake database in
``cases_fakes``; the bus over fakeredis. Both go through their own ``connect()``.
"""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import patch

from bot_fakes import make_bot, make_settings
from cases_fakes import Database
from fakeredis.aioredis import FakeRedis

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.settings import Settings


class _MongoClient:
    def __init__(self, db: Database) -> None:
        self._db = db

    def __getitem__(self, name: str) -> Database:
        return self._db

    async def close(self) -> None:
        pass


async def connected_store(
    db: Database | None = None, settings: Settings | None = None
) -> tuple[MongoStore, Database]:
    db = db if db is not None else Database()
    store = MongoStore(settings or make_settings())
    with patch(
        "xcore_discord_bot.mongo_store.AsyncMongoClient",
        lambda *args, **kwargs: _MongoClient(db),
    ):
        await store.connect()
    return store, db


async def connected_bus(settings: Settings | None = None) -> RedisTransport:
    bus = RedisTransport(settings or make_settings())
    with patch(
        "xcore_discord_bot.bus.transport.Redis.from_url",
        lambda *args, **kwargs: FakeRedis(decode_responses=True),
    ):
        await bus.connect()
    return bus


async def real_bot(
    *, settings: Settings | None = None, db: Database | None = None
) -> tuple[XCoreDiscordBot, Database]:
    """A bot whose store and bus are the real classes over in-memory backends."""
    settings = settings or make_settings()
    store, db = await connected_store(db, settings)
    bot = make_bot(settings=settings, bus=await connected_bus(settings), store=store)
    return bot, db


async def published(bus: RedisTransport, stream: str) -> list[dict[str, Any]]:
    """The payloads published to ``stream``, oldest first."""
    entries = await bus.redis.xrange(stream)
    return [json.loads(fields["payload_json"]) for _, fields in entries]
