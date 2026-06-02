from __future__ import annotations

from aiohttp.test_utils import TestClient, TestServer

from xcore_metrics_gateway.http import create_app
from xcore_metrics_gateway.runtime import GatewayRuntime
from xcore_metrics_gateway.settings import Settings
from xcore_metrics_gateway.store import SeriesStore


class _StubRedisClient:
    async def close(self) -> None:
        return None


async def _noop() -> None:
    return None


async def test_ready_returns_503_until_runtime_is_ready() -> None:
    store = SeriesStore()
    runtime = GatewayRuntime(Settings(), store, _StubRedisClient())
    runtime.start = _noop  # type: ignore[method-assign]
    runtime.stop = _noop  # type: ignore[method-assign]
    app = create_app(store, runtime)

    async with TestClient(TestServer(app)) as client:
        response = await client.get("/ready")

    assert response.status == 503


async def test_ready_returns_200_when_runtime_is_ready() -> None:
    store = SeriesStore()
    runtime = GatewayRuntime(Settings(), store, _StubRedisClient())
    runtime.redis_up = True
    runtime._discovery_ready = True
    runtime._poll_ready = True
    runtime._last_discovery_ok = True
    runtime._last_poll_ok = True
    runtime.start = _noop  # type: ignore[method-assign]
    runtime.stop = _noop  # type: ignore[method-assign]

    app = create_app(store, runtime)

    async with TestClient(TestServer(app)) as client:
        response = await client.get("/ready")

    assert response.status == 200
