from __future__ import annotations

from unittest.mock import MagicMock

from backends import connected_bus, published
from bot_fakes import PERMISSIONS_SETTINGS, make_settings

from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.container import ServiceContainer
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.settings import Settings


def test_service_container_creation() -> None:
    settings = Settings(
        discord_bot_token="fake-token",
        discord_admin_role_id=10,
        discord_private_channel_id=20,
        **PERMISSIONS_SETTINGS,
        redis_url="redis://localhost:6379",
        mongo_uri="mongodb://localhost:27017",
    )
    bot = MagicMock()
    container = ServiceContainer.create(
        settings, bot, store=MongoStore(settings), bus=RedisTransport(settings)
    )

    assert container.settings is settings
    assert container.players is not None
    assert container.presence_daemon is not None
    assert container.staff_sync_daemon is not None
    assert container.stream_supervisor is not None


def _container(bus: RedisTransport | None = None) -> ServiceContainer:
    settings = make_settings()
    return ServiceContainer.create(
        settings,
        MagicMock(),
        store=MongoStore(settings),
        bus=bus or RedisTransport(settings),
    )


async def test_one_live_server_registry_serves_the_broadcasts_and_the_rpc_client() -> (
    None
):
    bus = await connected_bus()
    container = _container(bus)
    container.servers.update_server("beta", 2, 0, 10, "v1")
    container.servers.update_server("alpha", 1, 0, 10, "v1")

    await container.moderation_bus.publish_kick_banned(uuid_value="u", ip=None)
    await container.chat_bus.publish_discord_message(None, "mod", "hello")

    async def first(server: str) -> str:
        return server

    for command in ("kick-banned", "discord-message"):
        for server in ("alpha", "beta"):
            assert len(await published(bus, f"xcore:cmd:{command}:{server}")) == 1
    assert await container.rpc.on_any_live_server(first) == "alpha"


def test_every_container_starts_with_its_own_empty_registry() -> None:
    first, second = _container(), _container()
    first.servers.update_server("alpha", 1, 0, 10, "v1")
    assert second.servers.get_all_servers() == []
