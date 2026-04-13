from __future__ import annotations

from discord import Interaction, app_commands
from discord.ext import commands
from typing import TYPE_CHECKING

from .. import handlers_misc
from .autocomplete import _autocomplete_player_id
from .checks import admin_check

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


class InfoCog(commands.Cog):
    audit_group = app_commands.Group(
        name="audit",
        description="Inspect player audit history",
    )

    def __init__(self, bot: "XCoreDiscordBot") -> None:
        self.bot = bot

    @app_commands.command(name="stats", description="Show player stats")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    async def cmd_stats(self, interaction: Interaction, player_id: int) -> None:
        await handlers_misc.cmd_stats(self.bot, interaction, player_id)

    @app_commands.command(name="servers", description="Show all live Mindustry servers")
    async def cmd_servers(self, interaction: Interaction) -> None:
        await handlers_misc.cmd_servers(self.bot, interaction)

    @audit_group.command(name="target", description="Show audit history for a player")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @admin_check()
    async def cmd_audit_target(self, interaction: Interaction, player_id: int) -> None:
        player = await self.bot._get_player_or_reply(interaction, player_id)
        if player is None:
            return
        await handlers_misc.cmd_stats_audit(
            self.bot,
            interaction,
            player_id,
            handlers_misc._player_record_as_mapping(player),
            mode="target",
        )

    @audit_group.command(name="actor", description="Show actions performed by a player")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @admin_check()
    async def cmd_audit_actor(self, interaction: Interaction, player_id: int) -> None:
        player = await self.bot._get_player_or_reply(interaction, player_id)
        if player is None:
            return
        await handlers_misc.cmd_stats_audit(
            self.bot,
            interaction,
            player_id,
            handlers_misc._player_record_as_mapping(player),
            mode="actor",
        )
