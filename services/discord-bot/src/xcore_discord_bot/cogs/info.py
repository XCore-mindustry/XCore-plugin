from __future__ import annotations

from typing import TYPE_CHECKING

import discord
from discord import Interaction, app_commands
from discord.ext import commands

from .. import handlers_stats
from ..command_helpers import get_player_or_reply
from ..server_views import ServersView
from .autocomplete import autocomplete_player_id, autocomplete_stats_player
from .checks import admin_check

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


class InfoCog(commands.Cog):
    audit_group = app_commands.Group(
        name="audit",
        description="Inspect player audit history",
    )

    def __init__(self, bot: XCoreDiscordBot) -> None:
        self.bot = bot

    @app_commands.command(
        name="stats",
        description="Show a player's profile: your linked account when nobody is named",
    )
    @app_commands.describe(
        player_id="Player ID, nickname or @username",
        user="A Discord user with a linked game account",
    )
    @app_commands.autocomplete(player_id=autocomplete_stats_player)
    async def cmd_stats(
        self,
        interaction: Interaction,
        player_id: str | None = None,
        user: discord.User | None = None,
    ) -> None:
        await handlers_stats.cmd_stats(self.bot, interaction, player_id, user)

    @app_commands.command(name="servers", description="Show all live Mindustry servers")
    async def cmd_servers(self, interaction: Interaction) -> None:
        view = ServersView(self.bot.container.servers, sort_mode="players")
        await interaction.response.send_message(embed=view.build_embed(), view=view)
        view.bot_message = await interaction.original_response()

    @audit_group.command(name="target", description="Show audit history for a player")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=autocomplete_player_id)
    @admin_check()
    async def cmd_audit_target(self, interaction: Interaction, player_id: int) -> None:
        player = await get_player_or_reply(self.bot, interaction, player_id)
        if player is None:
            return
        await handlers_stats.cmd_stats_audit(
            self.bot,
            interaction,
            player_id,
            player,
            mode="target",
        )

    @audit_group.command(name="actor", description="Show actions performed by a player")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=autocomplete_player_id)
    @admin_check()
    async def cmd_audit_actor(self, interaction: Interaction, player_id: int) -> None:
        player = await get_player_or_reply(self.bot, interaction, player_id)
        if player is None:
            return
        await handlers_stats.cmd_stats_audit(
            self.bot,
            interaction,
            player_id,
            player,
            mode="actor",
        )
