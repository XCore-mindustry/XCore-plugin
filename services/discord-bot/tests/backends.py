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
from collections.abc import Awaitable, Callable, Iterator
from contextlib import contextmanager, suppress
from dataclasses import dataclass
from typing import Any, cast
from unittest.mock import create_autospec, patch

from bot_fakes import make_bot, make_settings
from cases_fakes import Database
from fakeredis import FakeServer
from fakeredis.aioredis import FakeRedis
from xcore_protocol.generated.maps import MapsUploadResponseV1

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.settings import Settings

RPC_REQUESTS = "xcore:rpc:req:"


class _Redis(FakeRedis):
    """fakeredis serves a blocking read without ever yielding to the event loop, so a
    consumer waiting on an empty stream would starve the test. Real Redis waits on the
    socket instead; this reads without blocking and yields in place of the wait.

    A ``GameServer`` on it answers each RPC request as it is published, on the request's
    ``reply_to`` stream, the way the plugin does.
    """

    def __init__(self) -> None:
        self.server = FakeServer()
        self.game_servers: dict[str, GameServer] = {}
        super().__init__(server=self.server, decode_responses=True)

    async def xadd(self, name: Any, fields: Any, *args: Any, **kwargs: Any) -> Any:
        entry = await super().xadd(name, fields, *args, **kwargs)
        server = self.game_servers.get(str(name).removeprefix(RPC_REQUESTS))
        if str(name).startswith(RPC_REQUESTS) and server is not None:
            reply = server.reply(fields)
            if reply is not None:
                reply["correlation_id"] = fields["correlation_id"]
                await super().xadd(fields["reply_to"], reply)
        return entry

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


@contextmanager
def redis_connections(*, down: int = 0) -> Iterator[list[_Redis]]:
    """While this lasts, each connection a bus opens is to a new in-memory Redis, the
    first ``down`` of them unreachable. Yields the connections opened, oldest first."""
    opened: list[_Redis] = []

    def from_url(*args: Any, **kwargs: Any) -> _Redis:
        redis = _Redis()
        redis.server.connected = len(opened) >= down
        opened.append(redis)
        return redis

    with patch("xcore_discord_bot.bus.transport.Redis.from_url", from_url):
        yield opened


async def connected_bus(settings: Settings | None = None) -> RedisTransport:
    bus = RedisTransport(settings or make_settings())
    with redis_connections():
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


def unplug(bus: RedisTransport) -> None:
    """Takes Redis down: from now on every call on ``bus`` raises a connection error."""
    cast(_Redis, bus.redis).server.connected = False


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


@dataclass(frozen=True)
class RpcError:
    """An error reply to an RPC; ``code`` is None for a server that sends none."""

    code: str | None
    message: str


def refuse(message: str) -> RpcError:
    return RpcError("REJECTED", message)


def fail(message: str) -> RpcError:
    return RpcError("FAILED", message)


# What a game server makes of an RPC request's payload: the response payload, an error
# reply, or None to stay silent.
RpcAnswer = Callable[[dict[str, Any]], dict[str, Any] | RpcError | None]


class GameServer:
    """A game server on the bot's bus: it answers RPCs and map uploads and reads its
    commands.

    It registers with the bot, so commands published to every server reach it. Put an
    ``RpcAnswer`` in ``answers`` for each RPC type it should answer; ``requests`` holds
    the fields of every request it was sent. Set ``statuses`` for the upload results,
    ``upload_error`` to fail the upload, or ``answer`` to return any other response.
    """

    def __init__(self, bot: XCoreDiscordBot, name: str = "mini-pvp") -> None:
        self.bus, self.name = bot.bus, name
        self.answers: dict[str, RpcAnswer] = {}
        self.requests: list[dict[str, str]] = []
        self.statuses = ["installed"]
        self.upload_error: BaseException | None = None
        self.upload_calls: list[tuple[str, list[dict[str, Any]], int]] = []
        self.answer: Callable[[str, list[dict[str, Any]]], object] = self._installed
        bot.container.servers.update_server(name, 1, 0, 10, "v1")
        game_servers = cast(_Redis, self.bus.redis).game_servers
        game_servers[name] = self
        if len(game_servers) == 1:  # the first server patches the uploads for all
            maps = bot.container.maps_bus

            async def upload(*, server: str, **kwargs: Any) -> object:
                return await game_servers[server].rpc_upload_maps(
                    server=server, **kwargs
                )

            maps.rpc_upload_maps = create_autospec(  # type: ignore[method-assign]
                maps.rpc_upload_maps, side_effect=upload
            )

    def reply(self, fields: dict[str, str]) -> dict[str, str] | None:
        self.requests.append(dict(fields))
        rpc_type, payload = fields["rpc_type"], json.loads(fields["payload_json"])
        if rpc_type not in self.answers:
            raise AssertionError(f"{self.name} has no answer for {rpc_type}")
        answer = self.answers[rpc_type](payload)
        if answer is None:
            return None
        if isinstance(answer, RpcError):
            reply = {"status": "error", "error_message": answer.message}
            if answer.code is not None:
                reply["error_code"] = answer.code
            return reply
        return {"status": "ok", "payload_json": json.dumps(answer)}

    def sent(self, rpc_type: str) -> list[dict[str, Any]]:
        """The payloads of the ``rpc_type`` requests this server was sent."""
        return [
            json.loads(fields["payload_json"])
            for fields in self.requests
            if fields["rpc_type"] == rpc_type
        ]

    def _installed(self, server: str, files: list[dict[str, Any]]) -> object:
        return MapsUploadResponseV1.from_payload(
            {
                "messageType": "maps.upload.response",
                "messageVersion": 1,
                "server": server,
                "results": [
                    {"fileName": file["filename"], "status": status}
                    for file, status in zip(files, self.statuses, strict=True)
                ],
            }
        )

    async def rpc_upload_maps(
        self, *, server: str, files: list[dict[str, Any]], timeout_ms: int
    ) -> object:
        self.upload_calls.append((server, files, timeout_ms))
        if self.upload_error is not None:
            raise self.upload_error
        return self.answer(server, files)

    async def commands(self, kind: str) -> list[dict[str, Any]]:
        return await published(self.bus, f"xcore:cmd:{kind}:{self.name}")

    async def kicks(self) -> list[tuple[str | None, str | None]]:
        return [
            (command["target"].get("playerUuid"), command["target"].get("ip"))
            for command in await self.commands("kick-banned")
        ]

    async def pardons(self) -> list[str]:
        return [
            command["target"]["playerUuid"]
            for command in await self.commands("pardon-player")
        ]

    async def inventory_events(self) -> list[tuple[str, str, list[str]]]:
        return [
            (command["playerUuid"], command["activeBadge"], command["unlockedBadges"])
            for command in await self.commands("player-badge-inventory")
        ]
