"""The bot's real MongoStore and RedisTransport, connected to in-memory backends.

Tests that need a store or a bus use these instead of a class of their own, so a method
renamed or removed in ``src`` fails here too (#32). The store runs over the fake database in
``cases_fakes``; the bus over fakeredis. Both go through their own ``connect()``.
"""

from __future__ import annotations

import asyncio
import json
import time
import uuid
from collections.abc import Awaitable
from contextlib import suppress
from typing import Any
from unittest.mock import patch

from bot_fakes import make_bot, make_settings
from cases_fakes import Database
from fakeredis.aioredis import FakeRedis

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.settings import Settings


class _Redis(FakeRedis):
    """fakeredis serves a blocking read without ever yielding to the event loop, so a
    consumer waiting on an empty stream would starve the test. Real Redis waits on the
    socket instead; this reads without blocking and yields in place of the wait."""

    async def xreadgroup(self, *args: Any, block: int | None = None, **kwargs: Any):
        await asyncio.sleep(0.01 if block else 0)
        return await super().xreadgroup(*args, **kwargs)

    async def xread(self, *args: Any, block: int | None = None, **kwargs: Any):
        await asyncio.sleep(0.01 if block else 0)
        return await super().xread(*args, **kwargs)


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
        lambda *args, **kwargs: _Redis(decode_responses=True),
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


async def emit(
    bus: RedisTransport, stream: str, event: Any, *, server: str = "mini-pvp"
) -> None:
    """Put a generated ``event`` on ``stream`` the way a game server does."""
    payload = event.to_payload()
    await bus.redis.xadd(
        stream,
        {
            "schema_version": "1",
            "event_type": str(payload.get("messageType", "")),
            "event_id": str(uuid.uuid4()),
            "producer": server,
            "created_at": str(int(time.time() * 1000)),
            "server": server,
            "payload_json": json.dumps(payload),
        },
    )


async def _handled(bus: RedisTransport, stream: str) -> bool:
    if not await bus.redis.exists(stream):
        return False
    groups = await bus.redis.xinfo_groups(stream)
    last = (await bus.redis.xinfo_stream(stream))["last-generated-id"]
    return bool(groups) and all(
        group["pending"] == 0 and group["last-delivered-id"] == last for group in groups
    )


async def consumed(
    bus: RedisTransport, consumer: Awaitable[None], *streams: str, timeout: float = 5
) -> None:
    """Run ``consumer`` until it has read and acknowledged everything on ``streams``."""
    task = asyncio.ensure_future(consumer)
    try:
        async with asyncio.timeout(timeout):
            while not all([await _handled(bus, stream) for stream in streams]):
                if task.done():
                    task.result()
                    raise AssertionError(
                        "The consumer stopped before reading its stream"
                    )
                await asyncio.sleep(0.01)
    finally:
        task.cancel()
        with suppress(asyncio.CancelledError):
            await task
