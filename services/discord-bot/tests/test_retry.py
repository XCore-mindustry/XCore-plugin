from __future__ import annotations

import asyncio

import pytest

from xcore_discord_bot.retry import retry_reconnect_bus


@pytest.mark.asyncio
async def test_retry_reconnect_bus_retries_transient_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    sleep_calls: list[float] = []

    async def fast_sleep(seconds: float) -> None:
        sleep_calls.append(seconds)

    monkeypatch.setattr("xcore_discord_bot.retry.asyncio.sleep", fast_sleep)

    calls = {"n": 0}

    async def reconnect() -> None:
        calls["n"] += 1
        if calls["n"] == 1:
            raise ConnectionError("redis down")

    await retry_reconnect_bus(reconnect)

    assert calls["n"] == 2
    assert sleep_calls == [2.0]


@pytest.mark.asyncio
async def test_retry_reconnect_bus_preserves_cancellation() -> None:
    async def reconnect() -> None:
        raise asyncio.CancelledError()

    with pytest.raises(asyncio.CancelledError):
        await retry_reconnect_bus(reconnect)
