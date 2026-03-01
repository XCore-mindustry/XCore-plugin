from __future__ import annotations

import json
import logging
import hashlib
import asyncio
import time
import uuid
from typing import Any, Awaitable, Callable, Mapping

from redis.asyncio import Redis
from redis.exceptions import ResponseError

from .contracts import (
    BanEvent,
    EventType,
    GameChatMessage,
    GlobalChatEvent,
    PlayerJoinLeaveEvent,
    RawEvent,
    ServerHeartbeatEvent,
    ServerActionEvent,
)
from .registry import server_registry
from .settings import Settings


logger = logging.getLogger(__name__)

MAXLEN_EVT = 50_000
MAXLEN_CMD = 10_000
MAXLEN_RPC_REQ = 5_000
MAXLEN_RPC_RESP = 20_000
MAXLEN_DLQ = 100_000
RECLAIM_INTERVAL_SEC = 10
RECLAIM_MIN_IDLE_MS = 15_000
MAX_ATTEMPTS_EVT = 5


class RedisBus:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._redis: Redis | None = None
        self._conn_lock = asyncio.Lock()
        self._last_reclaim_at: dict[str, float] = {}

    async def connect(self) -> None:
        async with self._conn_lock:
            if self._redis is not None:
                return

            redis = Redis.from_url(self._settings.redis_url, decode_responses=True)
            await redis.ping()
            self._redis = redis

    async def close(self) -> None:
        async with self._conn_lock:
            if self._redis is None:
                return

            await self._redis.aclose()
            self._redis = None

    async def reconnect(self) -> None:
        async with self._conn_lock:
            old = self._redis
            self._redis = None

            if old is not None:
                try:
                    await old.aclose()
                except Exception:
                    pass

            redis = Redis.from_url(self._settings.redis_url, decode_responses=True)
            await redis.ping()
            self._redis = redis

    async def publish_discord_message(
        self,
        server: str | None,
        author_name: str,
        message: str,
        source_message_id: str | None = None,
    ) -> None:
        redis = self._require_redis()
        now = int(time.time() * 1000)
        target_servers = (
            [server]
            if server is not None
            else [srv.name for srv in server_registry.get_all_servers()]
        )
        for target_server in target_servers:
            event_id = str(uuid.uuid4())
            stream = f"xcore:cmd:discord-message:{target_server}"
            payload = {
                "authorName": author_name,
                "message": message,
                "server": target_server,
            }

            fields = {
                "schema_version": "1",
                "event_type": "chat.discord_ingress",
                "event_id": event_id,
                "idempotency_key": self._build_idempotency_key(
                    prefix="discord.message",
                    server=target_server,
                    payload_json=fields_payload_json(payload),
                    now_ms=now,
                    ttl_ms=60_000,
                    explicit_scope=source_message_id,
                ),
                "producer": "discord-bot",
                "created_at": str(now),
                "expires_at": str(now + 60_000),
                "server": target_server,
                "payload_json": json.dumps(payload, ensure_ascii=False),
            }
            await redis.xadd(
                stream,
                fields,
                maxlen=self._stream_maxlen(stream),
                approximate=True,
            )

    async def consume_game_chat(
        self, callback: Callable[[GameChatMessage], Awaitable[None]]
    ) -> None:
        await self._consume_events(
            stream="xcore:evt:chat:message",
            group_suffix="discord-chat",
            parse_payload=GameChatMessage.from_payload,
            callback=callback,
            skip_discord_producer=True,
        )

    async def consume_global_chat(
        self, callback: Callable[[GlobalChatEvent], Awaitable[None]]
    ) -> None:
        await self._consume_events(
            stream="xcore:evt:chat:global",
            group_suffix="discord-global-chat",
            parse_payload=GlobalChatEvent.from_payload,
            callback=callback,
        )

    async def consume_raw_events(
        self, callback: Callable[[RawEvent], Awaitable[None]]
    ) -> None:
        async def wrapped(event: RawEvent) -> None:
            if event.event_type in {
                EventType.HEARTBEAT,
                "org.xcore.plugin.event.SocketEvents$ServerHeartbeatEvent",
            }:
                heartbeat = ServerHeartbeatEvent.from_payload(event.payload)
                server_registry.update_server(
                    heartbeat.server_name,
                    heartbeat.discord_channel_id,
                    heartbeat.players,
                    heartbeat.max_players,
                    heartbeat.version,
                )
            await callback(event)

        await self._consume_events(
            stream="xcore:evt:raw",
            group_suffix="discord-raw",
            parse_fields=RawEvent.from_fields,
            callback=wrapped,
        )

    async def consume_server_heartbeats(
        self, callback: Callable[[ServerHeartbeatEvent], Awaitable[None]]
    ) -> None:
        async def wrapped(event: ServerHeartbeatEvent) -> None:
            server_registry.update_server(
                event.server_name,
                event.discord_channel_id,
                event.players,
                event.max_players,
                event.version,
            )
            await callback(event)

        await self._consume_events(
            stream="xcore:evt:server:heartbeat",
            group_suffix="discord-server-heartbeat",
            parse_payload=ServerHeartbeatEvent.from_payload,
            callback=wrapped,
        )

    async def consume_admin_requests(
        self, callback: Callable[[int, str], Awaitable[None]]
    ) -> None:
        def parse(payload: dict[str, Any]) -> tuple[int, str]:
            return int(payload["pid"]), str(payload["server"])

        async def wrapped(parsed: tuple[int, str]) -> None:
            pid, server = parsed
            await callback(pid, server)

        await self._consume_events(
            stream="xcore:evt:admin:request",
            group_suffix="discord-admin-request",
            parse_payload=parse,
            callback=wrapped,
        )

    async def consume_player_join_leave(
        self, callback: Callable[[PlayerJoinLeaveEvent], Awaitable[None]]
    ) -> None:
        await self._consume_events(
            stream="xcore:evt:player:joinleave",
            group_suffix="discord-join-leave",
            parse_payload=PlayerJoinLeaveEvent.from_payload,
            callback=callback,
        )

    async def consume_server_actions(
        self, callback: Callable[[ServerActionEvent], Awaitable[None]]
    ) -> None:
        await self._consume_events(
            stream="xcore:evt:server:action",
            group_suffix="discord-server-action",
            parse_payload=ServerActionEvent.from_payload,
            callback=callback,
        )

    async def consume_bans(
        self, callback: Callable[[BanEvent], Awaitable[None]]
    ) -> None:
        await self._consume_events(
            stream="xcore:evt:moderation:ban",
            group_suffix="discord-ban",
            parse_payload=BanEvent.from_payload,
            callback=callback,
        )

    async def _consume_events(
        self,
        *,
        stream: str,
        group_suffix: str,
        callback: Callable[[Any], Awaitable[None]],
        parse_payload: Callable[[dict[str, Any]], Any] | None = None,
        parse_fields: Callable[[dict[str, Any]], Any] | None = None,
        skip_discord_producer: bool = False,
        max_attempts: int = MAX_ATTEMPTS_EVT,
    ) -> None:
        redis = self._require_redis()
        group = f"{self._settings.redis_group_prefix}:{group_suffix}"
        consumer = self._settings.redis_consumer_name
        await self._ensure_group(stream=stream, group=group)

        while True:
            now = time.monotonic()
            last_reclaim = self._last_reclaim_at.get(group, 0.0)
            if now - last_reclaim >= RECLAIM_INTERVAL_SEC:
                await self._reclaim_pending(
                    stream=stream,
                    group=group,
                    consumer=consumer,
                    callback=callback,
                    parse_payload=parse_payload,
                    parse_fields=parse_fields,
                    skip_discord_producer=skip_discord_producer,
                    max_attempts=max_attempts,
                )
                self._last_reclaim_at[group] = now

            data = await redis.xreadgroup(
                groupname=group,
                consumername=consumer,
                streams={stream: ">"},
                count=50,
                block=1000,
            )

            if not data:
                continue

            for found_stream, messages in data:
                for message_id, fields in messages:
                    await self._process_event_message(
                        stream=found_stream,
                        group=group,
                        message_id=message_id,
                        fields=fields,
                        callback=callback,
                        parse_payload=parse_payload,
                        parse_fields=parse_fields,
                        skip_discord_producer=skip_discord_producer,
                        max_attempts=max_attempts,
                        source="live",
                    )

    async def _reclaim_pending(
        self,
        *,
        stream: str,
        group: str,
        consumer: str,
        callback: Callable[[Any], Awaitable[None]],
        parse_payload: Callable[[dict[str, Any]], Any] | None,
        parse_fields: Callable[[dict[str, Any]], Any] | None,
        skip_discord_producer: bool,
        max_attempts: int,
    ) -> None:
        redis = self._require_redis()
        cursor = "0-0"

        while True:
            claimed = await redis.xautoclaim(
                name=stream,
                groupname=group,
                consumername=consumer,
                min_idle_time=RECLAIM_MIN_IDLE_MS,
                start_id=cursor,
                count=50,
            )

            if not isinstance(claimed, (list, tuple)) or len(claimed) < 2:
                return

            cursor = str(claimed[0])
            messages = claimed[1] if isinstance(claimed[1], list) else []
            if not messages:
                return

            for message_id, fields in messages:
                await self._process_event_message(
                    stream=stream,
                    group=group,
                    message_id=message_id,
                    fields=fields,
                    callback=callback,
                    parse_payload=parse_payload,
                    parse_fields=parse_fields,
                    skip_discord_producer=skip_discord_producer,
                    max_attempts=max_attempts,
                    source="reclaim",
                )

            if cursor == "0-0":
                return

    async def _process_event_message(
        self,
        *,
        stream: str,
        group: str,
        message_id: str,
        fields: dict[str, Any],
        callback: Callable[[Any], Awaitable[None]],
        parse_payload: Callable[[dict[str, Any]], Any] | None,
        parse_fields: Callable[[dict[str, Any]], Any] | None,
        skip_discord_producer: bool,
        max_attempts: int,
        source: str,
    ) -> None:
        redis = self._require_redis()
        producer = self._field_str(fields, "producer")
        if skip_discord_producer and producer == "discord-bot":
            await redis.xack(stream, group, message_id)
            await self._clear_failure_counter(
                stream=stream, group=group, message_id=message_id
            )
            return

        try:
            if parse_fields is not None:
                parsed = parse_fields(self._stringify_field_map(fields))
            elif parse_payload is not None:
                payload = json.loads(self._field_str(fields, "payload_json", "{}"))
                parsed = parse_payload(payload)
            else:
                raise RuntimeError("No parser configured for stream consumer")
        except Exception as error:
            attempts = await self._increment_failure_counter(
                stream=stream, group=group, message_id=message_id
            )
            await self._route_to_dlq(
                source_stream=stream,
                source_group=group,
                message_id=message_id,
                fields=fields,
                attempts=attempts,
                failure_stage="validation",
                error=error,
            )
            await redis.xack(stream, group, message_id)
            await self._clear_failure_counter(
                stream=stream, group=group, message_id=message_id
            )
            return

        try:
            await callback(parsed)
        except Exception as error:
            attempts = await self._increment_failure_counter(
                stream=stream, group=group, message_id=message_id
            )
            logger.warning(
                "Failed to process stream %s id=%s (%s attempt %s/%s): %s",
                stream,
                message_id,
                source,
                attempts,
                max_attempts,
                error,
            )
            if attempts >= max_attempts:
                await self._route_to_dlq(
                    source_stream=stream,
                    source_group=group,
                    message_id=message_id,
                    fields=fields,
                    attempts=attempts,
                    failure_stage="handler",
                    error=error,
                )
                await redis.xack(stream, group, message_id)
                await self._clear_failure_counter(
                    stream=stream, group=group, message_id=message_id
                )
            return

        await redis.xack(stream, group, message_id)
        await self._clear_failure_counter(
            stream=stream, group=group, message_id=message_id
        )

    async def _route_to_dlq(
        self,
        *,
        source_stream: str,
        source_group: str,
        message_id: str,
        fields: dict[str, Any],
        attempts: int,
        failure_stage: str,
        error: Exception,
    ) -> None:
        redis = self._require_redis()
        now = int(time.time() * 1000)
        dlq_stream = "xcore:dlq:evt"

        source_stream_s = self._to_text(source_stream)
        source_group_s = self._to_text(source_group)
        message_id_s = self._to_text(message_id)

        payload = {
            "source_stream": source_stream_s,
            "source_group": source_group_s,
            "source_message_id": message_id_s,
            "attempts": attempts,
            "failure_stage": failure_stage,
            "error_code": type(error).__name__,
            "error_message": str(error),
            "failed_at": now,
            "envelope": self._stringify_field_map(fields),
        }

        await redis.xadd(
            dlq_stream,
            {
                "schema_version": "1",
                "failure_stage": failure_stage,
                "source_stream": source_stream_s,
                "source_group": source_group_s,
                "message_id": message_id_s,
                "attempts": str(attempts),
                "error_code": type(error).__name__,
                "error_message": str(error),
                "failed_at": str(now),
                "payload_json": json.dumps(payload, ensure_ascii=False),
            },
            maxlen=self._stream_maxlen(dlq_stream),
            approximate=True,
        )

    async def _increment_failure_counter(
        self, *, stream: str, group: str, message_id: str
    ) -> int:
        redis = self._require_redis()
        key = self._failure_counter_key(
            stream=stream, group=group, message_id=message_id
        )
        attempts = await redis.incr(key)
        await redis.expire(key, 86_400)
        return int(attempts)

    async def _clear_failure_counter(
        self, *, stream: str, group: str, message_id: str
    ) -> None:
        redis = self._require_redis()
        key = self._failure_counter_key(
            stream=stream, group=group, message_id=message_id
        )
        await redis.delete(key)

    @staticmethod
    def _failure_counter_key(*, stream: str, group: str, message_id: str) -> str:
        digest = hashlib.sha1(
            f"{stream}|{group}|{message_id}".encode("utf-8")
        ).hexdigest()
        return f"xcore:retries:{digest}"

    @staticmethod
    def _field_str(
        fields: Mapping[str | bytes, Any], key: str, default: str = ""
    ) -> str:
        value = fields.get(key, default)
        if value == default:
            for raw_key, raw_value in fields.items():
                if (
                    isinstance(raw_key, bytes)
                    and raw_key.decode("utf-8", errors="replace") == key
                ):
                    value = raw_value
                    break
        if isinstance(value, bytes):
            return value.decode("utf-8", errors="replace")
        return str(value)

    @classmethod
    def _stringify_field_map(cls, fields: Mapping[str | bytes, Any]) -> dict[str, str]:
        normalized: dict[str, str] = {}
        for k, v in fields.items():
            key = (
                k.decode("utf-8", errors="replace") if isinstance(k, bytes) else str(k)
            )
            if isinstance(v, bytes):
                normalized[key] = v.decode("utf-8", errors="replace")
            else:
                normalized[key] = str(v)
        return normalized

    @staticmethod
    def _to_text(value: Any) -> str:
        if isinstance(value, bytes):
            return value.decode("utf-8", errors="replace")
        return str(value)

    async def publish_admin_confirm(self, uuid_value: str, server: str) -> None:
        await self._publish_event(
            stream=f"xcore:cmd:admin-confirm:{server}",
            event_type="admin.confirm",
            ttl_ms=120_000,
            server=server,
            payload={"uuid": uuid_value, "server": server},
            idempotency_prefix="admin.confirm",
        )

    async def publish_remove_admin(self, uuid_value: str) -> None:
        for server in [srv.name for srv in server_registry.get_all_servers()]:
            await self._publish_event(
                stream=f"xcore:cmd:remove-admin:{server}",
                event_type="admin.remove",
                ttl_ms=120_000,
                server=server,
                payload={"uuid": uuid_value, "server": server},
                idempotency_prefix="admin.remove",
            )

    async def publish_kick_banned(self, uuid_value: str, ip: str | None) -> None:
        for server in [srv.name for srv in server_registry.get_all_servers()]:
            await self._publish_event(
                stream=f"xcore:cmd:kick-banned:{server}",
                event_type="moderation.kick_banned",
                ttl_ms=120_000,
                server=server,
                payload={"uuid": uuid_value, "ip": ip, "server": server},
                idempotency_prefix="moderation.kick_banned",
            )

    async def publish_pardon_player(self, uuid_value: str) -> None:
        for server in [srv.name for srv in server_registry.get_all_servers()]:
            await self._publish_event(
                stream=f"xcore:cmd:pardon-player:{server}",
                event_type="moderation.pardon",
                ttl_ms=120_000,
                server=server,
                payload={"uuid": uuid_value, "server": server},
                idempotency_prefix="moderation.pardon",
            )

    async def publish_maps_load(self, server: str, files: list[dict[str, str]]) -> None:
        await self._publish_event(
            stream=f"xcore:cmd:maps-load:{server}",
            event_type="maps.load",
            ttl_ms=300_000,
            server=server,
            payload={"urls": files, "server": server},
            idempotency_prefix="maps.load",
        )

    async def publish_reload_player_data_cache(self) -> None:
        for server in [srv.name for srv in server_registry.get_all_servers()]:
            await self._publish_event(
                stream=f"xcore:cmd:reload-player-data-cache:{server}",
                event_type="player.reload_cache",
                ttl_ms=120_000,
                server=server,
                payload={"server": server},
                idempotency_prefix="player.reload_cache",
            )

    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:
        redis = self._require_redis()
        claimed = await redis.set(
            name=f"xcore:idmp:{key}",
            value="1",
            ex=max(1, ttl_seconds),
            nx=True,
        )
        return bool(claimed)

    async def rpc_maps_list(self, server: str, timeout_ms: int) -> list[dict[str, str]]:
        body = await self._rpc_request(
            server=server,
            rpc_type="maps.list",
            payload={"server": server},
            timeout_ms=timeout_ms,
        )

        payload_json = body.get("payload_json", "{}")
        payload = json.loads(payload_json)
        maps = payload.get("maps")
        if isinstance(maps, list):
            normalized: list[dict[str, str]] = []
            for value in maps:
                if isinstance(value, dict):
                    normalized.append(
                        {
                            "name": str(value.get("name", "Unknown")),
                            "file_name": str(
                                value.get("fileName", value.get("file_name", ""))
                            ),
                            "author": str(value.get("author", "Unknown")),
                            "width": str(value.get("width", "")),
                            "height": str(value.get("height", "")),
                            "file_size_bytes": str(
                                value.get(
                                    "fileSizeBytes", value.get("file_size_bytes", "")
                                )
                            ),
                        }
                    )
                else:
                    normalized.append(
                        {
                            "name": str(value),
                            "file_name": "",
                            "author": "Unknown",
                            "width": "",
                            "height": "",
                            "file_size_bytes": "",
                        }
                    )
            return normalized
        return []

    async def rpc_remove_map(self, server: str, file_name: str, timeout_ms: int) -> str:
        body = await self._rpc_request(
            server=server,
            rpc_type="maps.remove",
            payload={"server": server, "fileName": file_name, "file_name": file_name},
            timeout_ms=timeout_ms,
        )

        payload_json = body.get("payload_json", "{}")
        payload = json.loads(payload_json)
        result = payload.get("result")
        return str(result) if result is not None else "No result"

    async def _rpc_request(
        self, server: str, rpc_type: str, payload: dict[str, Any], timeout_ms: int
    ) -> dict[str, str]:
        redis = self._require_redis()
        correlation_id = str(uuid.uuid4())
        reply_stream = "xcore:rpc:resp:discord"
        request_stream = f"xcore:rpc:req:{server}"
        now = int(time.time() * 1000)
        timeout_ms = max(1, timeout_ms)

        fields = {
            "schema_version": "1",
            "rpc_type": rpc_type,
            "correlation_id": correlation_id,
            "request_id": str(uuid.uuid4()),
            "idempotency_key": self._build_idempotency_key(
                prefix=f"rpc.{rpc_type}",
                server=server,
                payload_json=json.dumps(payload, ensure_ascii=False, sort_keys=True),
                now_ms=now,
                ttl_ms=timeout_ms,
            ),
            "reply_to": reply_stream,
            "requested_by": "discord-bot",
            "server": server,
            "timeout_ms": str(timeout_ms),
            "created_at": str(now),
            "expires_at": str(now + timeout_ms),
            "payload_json": json.dumps(payload, ensure_ascii=False),
        }

        tail = await redis.xrevrange(reply_stream, count=1)
        cursor = tail[0][0] if tail else "0-0"
        await redis.xadd(
            request_stream,
            fields,
            maxlen=self._stream_maxlen(request_stream),
            approximate=True,
        )

        deadline = time.monotonic() + timeout_ms / 1000.0
        while True:
            remaining_ms = int((deadline - time.monotonic()) * 1000)
            if remaining_ms <= 0:
                raise TimeoutError(f"RPC timeout for {rpc_type} ({server})")

            response = await redis.xread(
                streams={reply_stream: cursor},
                count=100,
                block=min(1000, remaining_ms),
            )
            if not response:
                continue

            for _stream_name, messages in response:
                for message_id, body in messages:
                    cursor = message_id
                    if body.get("correlation_id") != correlation_id:
                        continue

                    status = body.get("status", "ok")
                    if status != "ok":
                        error_code = body.get("error_code", "UNKNOWN")
                        error_message = body.get("error_message", "unknown rpc error")
                        raise RuntimeError(
                            f"RPC {rpc_type} failed [{error_code}]: {error_message}"
                        )
                    return body

    async def _ensure_group(self, stream: str, group: str) -> None:
        redis = self._require_redis()
        try:
            await redis.xgroup_create(
                name=stream, groupname=group, id="0-0", mkstream=True
            )
        except ResponseError as error:
            if "BUSYGROUP" not in str(error).upper():
                raise

    def _require_redis(self) -> Redis:
        if self._redis is None:
            raise RuntimeError("RedisBus is not connected")
        return self._redis

    async def _publish_event(
        self,
        *,
        stream: str,
        event_type: str,
        ttl_ms: int,
        server: str,
        payload: dict[str, Any],
        idempotency_prefix: str,
    ) -> None:
        redis = self._require_redis()
        now = int(time.time() * 1000)
        event_id = str(uuid.uuid4())
        fields = {
            "schema_version": "1",
            "event_type": event_type,
            "event_id": event_id,
            "idempotency_key": self._build_idempotency_key(
                prefix=idempotency_prefix,
                server=server,
                payload_json=json.dumps(payload, ensure_ascii=False, sort_keys=True),
                now_ms=now,
                ttl_ms=ttl_ms,
            ),
            "producer": "discord-bot",
            "created_at": str(now),
            "expires_at": str(now + ttl_ms),
            "server": server,
            "payload_json": json.dumps(payload, ensure_ascii=False),
        }
        await redis.xadd(
            stream,
            fields,
            maxlen=self._stream_maxlen(stream),
            approximate=True,
        )

    @staticmethod
    def _stream_maxlen(stream: str) -> int:
        if stream.startswith("xcore:evt:"):
            return MAXLEN_EVT
        if stream.startswith("xcore:cmd:"):
            return MAXLEN_CMD
        if stream.startswith("xcore:rpc:req:"):
            return MAXLEN_RPC_REQ
        if stream.startswith("xcore:rpc:resp:"):
            return MAXLEN_RPC_RESP
        if stream.startswith("xcore:dlq:"):
            return MAXLEN_DLQ
        return MAXLEN_EVT

    @staticmethod
    def _build_idempotency_key(
        *,
        prefix: str,
        server: str,
        payload_json: str,
        now_ms: int,
        ttl_ms: int,
        explicit_scope: str | None = None,
    ) -> str:
        if explicit_scope is not None and explicit_scope.strip():
            return f"{prefix}:{explicit_scope.strip()}"

        window_ms = max(60_000, min(ttl_ms, 600_000))
        window = now_ms // window_ms
        digest = hashlib.sha256(
            f"{prefix}|{server}|{payload_json}|{window}".encode("utf-8")
        ).hexdigest()[:24]
        return f"{prefix}:{digest}"


def fields_payload_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, sort_keys=True)
