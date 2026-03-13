from __future__ import annotations

import discord
from discord import Interaction, app_commands
from discord.ext import commands
from typing import TYPE_CHECKING

from .. import handlers_misc
from .autocomplete import _autocomplete_map_file
from .checks import map_reviewer_check
from ..registry import server_registry

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


async def _autocomplete_server_for_command(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    current_norm = current.strip().lower()
    choices: list[app_commands.Choice[str]] = []
    for server in sorted(srv.name for srv in server_registry.get_all_servers()):
        if current_norm and current_norm not in server.lower():
            continue
        choices.append(app_commands.Choice(name=server, value=server))
        if len(choices) >= 25:
            break
    return choices


class MapsCog(commands.Cog):
    map_group = app_commands.Group(
        name="map",
        description="Browse and manage server maps",
    )

    def __init__(self, bot: "XCoreDiscordBot") -> None:
        self.bot = bot

    @map_group.command(name="list", description="List maps on a server")
    @app_commands.describe(server="Server name")
    @app_commands.autocomplete(server=_autocomplete_server_for_command)
    async def cmd_maps(self, interaction: Interaction, server: str) -> None:
        await handlers_misc.cmd_maps(self.bot, interaction, server)

    @map_group.command(
        name="remove",
        description="Remove a map from a server (map reviewer)",
    )
    @app_commands.describe(
        server="Server name", file_name="Map file name (.msav) to remove"
    )
    @app_commands.autocomplete(server=_autocomplete_server_for_command)
    @app_commands.autocomplete(file_name=_autocomplete_map_file)
    @map_reviewer_check()
    async def cmd_remove_map(
        self,
        interaction: Interaction,
        server: str,
        file_name: str,
    ) -> None:
        await handlers_misc.cmd_remove_map(self.bot, interaction, server, file_name)

    @map_group.command(
        name="upload",
        description="Upload .msav map files to a server (up to 3 per command)",
    )
    @app_commands.describe(
        server="Server name",
        file1="Map file (.msav)",
        file2="Second .msav file (optional)",
        file3="Third .msav file (optional)",
    )
    @app_commands.autocomplete(server=_autocomplete_server_for_command)
    @map_reviewer_check()
    async def cmd_upload_map(
        self,
        interaction: Interaction,
        server: str,
        file1: discord.Attachment,
        file2: discord.Attachment | None = None,
        file3: discord.Attachment | None = None,
    ) -> None:
        await handlers_misc.cmd_upload_map(
            self.bot,
            interaction,
            server,
            [file1, file2, file3],
        )
