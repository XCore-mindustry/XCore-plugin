from __future__ import annotations

from discord import Interaction, app_commands
from discord.ext import commands
from typing import TYPE_CHECKING

from .checks import admin_check

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


class InfoCog(commands.Cog):
    def __init__(self, bot: "XCoreDiscordBot") -> None:
        self.bot = bot

    @app_commands.command(name="stats", description="Show player stats")
    @app_commands.describe(player_id="Numeric player ID")
    @admin_check()
    async def cmd_stats(self, interaction: Interaction, player_id: int) -> None:
        await self.bot._cmd_stats(interaction, player_id)

    @app_commands.command(name="servers", description="Show all live Mindustry servers")
    async def cmd_servers(self, interaction: Interaction) -> None:
        await self.bot._cmd_servers(interaction)
