from __future__ import annotations

import asyncio
import logging

from dotenv import load_dotenv

from .bot import XCoreDiscordBot
from .mongo_store import MongoStore
from .redis_bus import RedisBus
from .settings import Settings


def setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


async def run() -> None:
    settings = Settings.from_env()
    bus = RedisBus(settings)
    store = MongoStore(settings)
    bot = XCoreDiscordBot(settings=settings, bus=bus, store=store)
    await bot.start(settings.discord_token)


def main() -> None:
    load_dotenv()
    setup_logging()
    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
