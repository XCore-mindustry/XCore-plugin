from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import AsyncMock

import discord

from xcore_discord_bot.daemons.presence_daemon import PresenceDaemon
from xcore_discord_bot.registry import server_registry


def test_build_presence_activity_offline() -> None:
    with server_registry._lock:
        server_registry._servers.clear()

    bot = SimpleNamespace(change_presence=AsyncMock())
    daemon = PresenceDaemon(bot)

    activity = daemon.build_presence_activity()

    assert activity.type == discord.ActivityType.watching
    assert activity.name == "silence on servers..."


def test_build_presence_activity_rotates_two_templates() -> None:
    with server_registry._lock:
        server_registry._servers.clear()
    server_registry.update_server("alpha", 100, 5, 20, "v1", None, None)
    server_registry.update_server("beta", 101, 7, 20, "v1", None, None)

    bot = SimpleNamespace(change_presence=AsyncMock())
    daemon = PresenceDaemon(bot)

    first = daemon.build_presence_activity()
    second = daemon.build_presence_activity()
    third = daemon.build_presence_activity()

    assert first.type == discord.ActivityType.watching
    assert first.name == "12 players on XCore"

    assert second.type == discord.ActivityType.playing
    assert second.name == "Mindustry | 2 servers"

    assert third.type == discord.ActivityType.watching
    assert third.name == "12 players on XCore"


async def test_update_presence_once_calls_change_presence() -> None:
    with server_registry._lock:
        server_registry._servers.clear()
    server_registry.update_server("alpha", 100, 3, 20, "v1", None, None)

    bot = SimpleNamespace(change_presence=AsyncMock())
    daemon = PresenceDaemon(bot)
    bot.change_presence = AsyncMock()

    await daemon.update_presence_once()

    bot.change_presence.assert_awaited_once()
    call = bot.change_presence.await_args
    activity = call.kwargs["activity"]
    assert activity.type == discord.ActivityType.watching
    assert activity.name == "3 players on XCore"
