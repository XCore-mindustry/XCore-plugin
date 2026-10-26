"""Construct the Discord client normally, without opening external connections."""

from unittest.mock import AsyncMock, MagicMock

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.transport import RedisTransport
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


FAMILY_BUSES = ("chat_bus", "moderation_bus", "rating_bus")


def make_bot(
    *, settings: Settings | None = None, bus=None, store=None
) -> XCoreDiscordBot:
    if settings is None:
        settings = make_settings()
    default_bus = bus is None
    fake_bus = not default_bus and not isinstance(bus, RedisTransport)
    if bus is None:
        bus = MagicMock(spec=RedisBus)
        bus.claim_idempotency = AsyncMock(return_value=True)
    if store is None:
        store = MagicMock(spec=MongoStore)
        store.find_player_by_pid = AsyncMock(return_value=None)
    bot = XCoreDiscordBot(settings=settings, bus=bus, store=store)
    if fake_bus:
        # A hand-written fake bus implements the family calls a test needs itself, so it
        # stands in for the family buses as well.
        for family in FAMILY_BUSES:
            setattr(bot.container, family, bus)
    # A test may replace ServiceContainer.create with a container of its own.
    if default_bus and hasattr(bot.container, "moderation_bus"):

        async def run_log(event, callback):
            await callback()

        bot.container.moderation_bus.run_ban_log_once = AsyncMock(side_effect=run_log)
    return bot
