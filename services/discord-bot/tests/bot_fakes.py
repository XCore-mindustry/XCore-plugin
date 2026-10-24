"""Construct the Discord client normally, without opening external connections."""

from unittest.mock import AsyncMock, MagicMock

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.redis_bus import RedisBus
from xcore_discord_bot.settings import Settings


def make_settings(**overrides) -> Settings:
    return Settings(
        **{
            "discord_bot_token": "test-token",
            "discord_admin_role_id": 5,
            "discord_private_channel_id": 0,
            **overrides,
        }
    )


def make_bot(
    *, settings: Settings | None = None, bus=None, store=None
) -> XCoreDiscordBot:
    if settings is None:
        settings = make_settings()
    if bus is None:
        bus = MagicMock(spec=RedisBus)
        bus.claim_idempotency = AsyncMock(return_value=True)

        async def run_log(event, callback):
            await callback()

        bus.run_ban_log_once = AsyncMock(side_effect=run_log)
    if store is None:
        store = MagicMock(spec=MongoStore)
        store.find_player_by_pid = AsyncMock(return_value=None)
    return XCoreDiscordBot(settings=settings, bus=bus, store=store)
