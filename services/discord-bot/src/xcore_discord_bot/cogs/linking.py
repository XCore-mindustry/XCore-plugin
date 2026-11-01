from __future__ import annotations

from discord import Interaction, app_commands
from discord.ext import commands

from .. import handlers_linking


class LinkingCog(commands.Cog):
    def __init__(self, bot) -> None:
        self.bot = bot

    @app_commands.command(
        name="link", description="Link your Discord account with a Mindustry account"
    )
    @app_commands.describe(code="One-time link code generated in-game")
    async def cmd_link(self, interaction: Interaction, code: str) -> None:
        await handlers_linking.cmd_link(self.bot, interaction, code)

    @app_commands.command(
        name="link-status",
        description="Show linked Mindustry accounts for your Discord account",
    )
    async def cmd_link_status(self, interaction: Interaction) -> None:
        await handlers_linking.cmd_link_status(self.bot, interaction)

    @app_commands.command(
        name="unlink", description="Unlink one of your linked Mindustry accounts"
    )
    @app_commands.describe(player_id="Numeric player ID")
    async def cmd_unlink(self, interaction: Interaction, player_id: int) -> None:
        await handlers_linking.cmd_unlink(self.bot, interaction, player_id)
