"""Construct the Discord client normally, without opening external connections."""

from contextlib import nullcontext
from unittest.mock import AsyncMock, MagicMock, patch

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


def make_settings(**overrides) -> Settings:
    return Settings(
        **{
            "discord_bot_token": "test-token",
            "discord_admin_role_id": 5,
            "discord_private_channel_id": 0,
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
FAMILY_CLASSES = (
    "AccountsBus",
    "CasesBus",
    "ChatBus",
    "MapsBus",
    "ModerationBus",
    "RatingBus",
    "SecurityBus",
    "SentinelBus",
)


def _itself(dependency, *_):
    return dependency


def make_bot(
    *, settings: Settings | None = None, bus=None, store=None
) -> XCoreDiscordBot:
    if settings is None:
        settings = make_settings()
    default_bus = bus is None
    fake_bus = not default_bus and not isinstance(bus, RedisTransport)
    fake_store = store is not None and not isinstance(store, MongoStore)
    if bus is None:
        bus = MagicMock(spec=RedisTransport)
        bus.claim_idempotency = AsyncMock(return_value=True)
    if store is None:
        store = MagicMock(spec=MongoStore)
        store.find_player_by_pid = AsyncMock(return_value=None)
    # A hand-written fake bus implements the family calls a test needs itself, so it
    # stands in for the family buses as well, also inside the services built on them.
    # A hand-written fake store stands in for the punishment and audit stores the same way.
    stand_ins = dict.fromkeys(FAMILY_CLASSES if fake_bus else (), _itself)
    if fake_store:
        stand_ins.update(AuditStore=_itself, PunishmentStore=_itself)
    with (
        patch.multiple("xcore_discord_bot.container", **stand_ins)
        if stand_ins
        else nullcontext()
    ):
        bot = XCoreDiscordBot(settings=settings, bus=bus, store=store)
    # A test may replace ServiceContainer.create with a container of its own.
    if default_bus and hasattr(bot.container, "moderation_bus"):

        async def run_log(event, callback):
            await callback()

        bot.container.moderation_bus.run_ban_log_once = AsyncMock(side_effect=run_log)
    return bot


def make_rpc(bus, *, servers=None, **families) -> MindustryRpcClient:
    """An RPC client whose family buses all sit on ``bus``, unless a test passes one.

    A hand-written fake bus stands in for the family buses itself, as in make_bot.
    ``servers`` is the live server registry; a fresh, empty one by default.
    """
    defaults = {
        "cases": CasesBus,
        "maps": MapsBus,
        "rating": RatingBus,
        "security": SecurityBus,
        "sentinel": SentinelBus,
    }
    fake_bus = not isinstance(bus, RedisTransport)
    return MindustryRpcClient(
        **{
            name: families.get(name, bus if fake_bus else family(bus))
            for name, family in defaults.items()
        },
        servers=servers if servers is not None else LiveServerRegistry(),
    )
