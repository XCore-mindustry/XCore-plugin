from __future__ import annotations

from unittest.mock import MagicMock

from xcore_discord_bot.container import ServiceContainer
from xcore_discord_bot.settings import Settings


def test_service_container_creation() -> None:
    settings = Settings(
        discord_token="fake-token",
        discord_admin_role_id=10,
        discord_private_channel_id=20,
        discord_guild_id=123,
        redis_url="redis://localhost:6379",
        mongo_uri="mongodb://localhost:27017",
    )
    bot = MagicMock()
    container = ServiceContainer.create(settings, bot)

    assert container.settings is settings
    assert container.players is not None
    assert container.moderation is not None
    assert container.maps is not None
    assert container.presence_daemon is not None
    assert container.admin_sync_daemon is not None
    assert container.stream_supervisor is not None
