from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import TypeVar

from tenacity import (
    AsyncRetrying,
    retry_if_exception_type,
    stop_after_attempt,
    wait_fixed,
)

try:
    from redis.exceptions import ConnectionError as RedisConnectionError
    from redis.exceptions import TimeoutError as RedisTimeoutError
except Exception:  # pragma: no cover
    RedisConnectionError = ConnectionError
    RedisTimeoutError = TimeoutError

TRANSIENT_EXCEPTIONS = (
    ConnectionError,
    OSError,
    TimeoutError,
    RedisConnectionError,
    RedisTimeoutError,
)

T = TypeVar("T")


async def retry_reconnect_bus(
    reconnect: Callable[[], Awaitable[None]],
    *,
    attempts: int = 2,
    wait_seconds: float = 2.0,
) -> None:
    async for attempt in AsyncRetrying(
        retry=retry_if_exception_type(TRANSIENT_EXCEPTIONS),
        stop=stop_after_attempt(attempts),
        wait=wait_fixed(wait_seconds),
        sleep=asyncio.sleep,
        reraise=True,
    ):
        with attempt:
            await reconnect()


async def retry_read_rpc(
    call: Callable[[], Awaitable[T]],
    *,
    attempts: int = 2,
    wait_seconds: float = 0.5,
) -> T:
    async for attempt in AsyncRetrying(
        retry=retry_if_exception_type(TRANSIENT_EXCEPTIONS),
        stop=stop_after_attempt(attempts),
        wait=wait_fixed(wait_seconds),
        sleep=asyncio.sleep,
        reraise=True,
    ):
        with attempt:
            return await call()

    raise AssertionError("retry_read_rpc exhausted without returning or raising")
