from __future__ import annotations

import asyncio
import logging

from aiohttp import web
from dotenv import load_dotenv

from .http import create_app
from .redis_client import RedisMetricsClient
from .runtime import GatewayRuntime
from .settings import Settings
from .store import SeriesStore


def setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


async def run() -> None:
    settings = Settings.from_env()
    store = SeriesStore()
    redis_client = RedisMetricsClient(settings)
    runtime = GatewayRuntime(settings, store, redis_client)
    app = create_app(store, runtime)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, settings.gateway_http_host, settings.gateway_http_port)
    await site.start()
    logging.getLogger(__name__).info(
        "xcore-metrics-gateway listening on %s:%s",
        settings.gateway_http_host,
        settings.gateway_http_port,
    )
    await asyncio.Event().wait()


def main() -> None:
    load_dotenv()
    setup_logging()
    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
