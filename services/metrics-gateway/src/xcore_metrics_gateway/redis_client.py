from __future__ import annotations

import asyncio
from collections.abc import Sequence

from redis.asyncio import Redis

from .settings import Settings

SNAPSHOT_KEY_PREFIX = "xcore:metrics:snapshot:"
SNAPSHOT_KEY_MATCH = f"{SNAPSHOT_KEY_PREFIX}*"


class RedisMetricsClient:
    def __init__(self, settings: Settings, redis: Redis | None = None) -> None:
        self._settings = settings
        self._redis = redis or Redis.from_url(
            settings.redis_url, decode_responses=False
        )
        self._owns_client = redis is None

    async def close(self) -> None:
        if self._owns_client:
            await self._redis.aclose()

    async def scan_snapshot_keys(self, cursor: int) -> tuple[int, list[str]]:
        next_cursor, keys = await asyncio.wait_for(
            self._redis.scan(
                cursor=cursor,
                match=SNAPSHOT_KEY_MATCH,
                count=self._settings.redis_scan_count,
            ),
            timeout=self._settings.redis_command_timeout_ms / 1000,
        )
        return int(next_cursor), [self._decode_key(key) for key in keys]

    async def mget_bytes(self, keys: Sequence[str]) -> list[bytes | None]:
        if not keys:
            return []
        values = await asyncio.wait_for(
            self._redis.mget(list(keys)),
            timeout=self._settings.redis_command_timeout_ms / 1000,
        )
        return [value if isinstance(value, bytes) else None for value in values]

    @staticmethod
    def server_from_key(key: str) -> str:
        if not key.startswith(SNAPSHOT_KEY_PREFIX):
            msg = f"unexpected snapshot key: {key}"
            raise ValueError(msg)
        return key.removeprefix(SNAPSHOT_KEY_PREFIX)

    @staticmethod
    def _decode_key(key: bytes | str) -> str:
        if isinstance(key, bytes):
            return key.decode("utf-8")
        return key
