from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING, Literal

import discord

from ..presentation import build_servers_embed
from ..registry import server_registry

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot

logger = logging.getLogger(__name__)

PRESENCE_UPDATE_INTERVAL_SECONDS = 30


class PresenceDaemon:
    def __init__(
        self, bot: XCoreDiscordBot, interval: float = PRESENCE_UPDATE_INTERVAL_SECONDS
    ) -> None:
        self._bot = bot
        self._interval = interval
        self._rotation_index = 0
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(self._run_loop(), name="presence_daemon")

    def stop(self) -> None:
        if self._task and not self._task.done():
            self._task.cancel()

    def get_live_servers(self):
        return server_registry.get_all_servers()

    @staticmethod
    def sort_live_servers(servers, mode: Literal["players", "name"]):
        if mode == "name":
            return sorted(servers, key=lambda s: s.name.lower())
        return sorted(servers, key=lambda s: (-s.players, s.name.lower()))

    def build_servers_embed_for_mode(
        self,
        mode: Literal["players", "name"],
    ) -> discord.Embed:
        servers = self.sort_live_servers(self.get_live_servers(), mode)
        return build_servers_embed(servers, sort_mode=mode)

    def build_presence_activity(self) -> discord.Activity:
        servers = self.get_live_servers()
        if not servers:
            return discord.Activity(
                type=discord.ActivityType.watching,
                name="silence on servers...",
            )

        total_players = sum(server.players for server in servers)
        server_count = len(servers)
        templates: tuple[tuple[discord.ActivityType, str], ...] = (
            (discord.ActivityType.watching, "{players} players on XCore"),
            (discord.ActivityType.playing, "Mindustry | {servers} servers"),
        )
        activity_type, template = templates[self._rotation_index % len(templates)]
        self._rotation_index += 1
        return discord.Activity(
            type=activity_type,
            name=template.format(players=total_players, servers=server_count),
        )

    async def update_presence_once(self) -> None:
        await self._bot.change_presence(activity=self.build_presence_activity())

    async def _run_loop(self) -> None:
        await self._bot.wait_until_ready()
        while not self._bot.is_closed():
            await asyncio.sleep(self._interval)
            try:
                await self.update_presence_once()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Failed to update Discord presence")
