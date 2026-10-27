"""Construct the Discord client normally, without opening external connections."""

from pathlib import Path
from unittest.mock import AsyncMock, MagicMock

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.cases import CasesBus
from xcore_discord_bot.bus.maps import MapsBus
from xcore_discord_bot.bus.rating import RatingBus
from xcore_discord_bot.bus.security import SecurityBus
from xcore_discord_bot.bus.sentinel import SentinelBus
from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.registry import LiveServerRegistry
from xcore_discord_bot.rpc.mindustry_rpc import MindustryRpcClient
from xcore_discord_bot.settings import Settings

# The guild and role configuration every test bot starts with.
PERMISSIONS_SETTINGS = {
    "discord_guild_id": 123,
    "permissions_config_path": str(Path(__file__).with_name("permissions.toml")),
    "permissions_rpc_server": "mini-pvp",
}


def make_settings(**overrides) -> Settings:
    return Settings(
        **{
            "discord_bot_token": "test-token",
            "discord_admin_role_id": 5,
            "discord_private_channel_id": 0,
            **PERMISSIONS_SETTINGS,
            **overrides,
        }
    )


FAMILY_BUSES = (
    "accounts_bus",
    "cases_bus",
    "chat_bus",
    "maps_bus",
    "moderation_bus",
    "rating_bus",
    "security_bus",
    "sentinel_bus",
)


def make_bot(
    *, settings: Settings | None = None, bus=None, store=None
) -> XCoreDiscordBot:
    if settings is None:
        settings = make_settings()
    default_bus = bus is None
    if bus is None:
        bus = MagicMock(spec=RedisTransport)
        bus.claim_idempotency = AsyncMock(return_value=True)
    if store is None:
        store = MagicMock(spec=MongoStore)
        store.find_player_by_pid = AsyncMock(return_value=None)
    bot = XCoreDiscordBot(settings=settings, bus=bus, store=store)
    # A test may replace ServiceContainer.create with a container of its own.
    if default_bus and hasattr(bot.container, "moderation_bus"):

        async def run_log(event, callback):
            await callback()

        bot.container.moderation_bus.run_ban_log_once = AsyncMock(side_effect=run_log)
    return bot


def make_rpc(bus, *, servers=None, **families) -> MindustryRpcClient:
    """An RPC client whose family buses all sit on ``bus``, unless a test passes one.

    ``servers`` is the live server registry; a fresh, empty one by default.
    """
    defaults = {
        "cases": CasesBus,
        "maps": MapsBus,
        "rating": RatingBus,
        "security": SecurityBus,
        "sentinel": SentinelBus,
    }
    return MindustryRpcClient(
        **{name: families.get(name, family(bus)) for name, family in defaults.items()},
        servers=servers if servers is not None else LiveServerRegistry(),
    )
