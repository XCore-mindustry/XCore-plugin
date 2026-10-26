from __future__ import annotations

from typing import TYPE_CHECKING, cast

import discord
from discord import Interaction, app_commands
from discord.ext import commands

from .. import handlers_misc
from ..cases.flows.maps import intake
from ..cases.modals import error_text
from ..cases.model import AccessDenied, ActionFailed, CaseKind
from ..i18n import t
from ..permissions import has_any_role, map_reviewer_role_ids
from .autocomplete import _autocomplete_map_file
from .checks import map_reviewer_check

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


async def _autocomplete_server_for_command(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    bot = cast("XCoreDiscordBot", interaction.client)
    current_norm = current.strip().lower()
    choices: list[app_commands.Choice[str]] = []
    for server in bot.container.servers.names():
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

    def __init__(self, bot: XCoreDiscordBot) -> None:
        self.bot = bot

    @map_group.command(name="review", description="Start map review in this forum post")
    async def cmd_review_map(self, interaction: Interaction) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        service = self.bot.container.cases
        locale = await service.store.remember_interaction(interaction)
        try:
            service.registry.require(CaseKind.MAP)
            if not has_any_role(
                interaction.user, map_reviewer_role_ids(service.settings)
            ):
                raise AccessDenied()
            thread = interaction.channel
            if (
                getattr(thread, "parent_id", None)
                != service.settings.discord_maps_forum_id
            ):
                raise ActionFailed(t(locale, "maps-review-in-post"))
            if str(interaction.user.id) == str(thread.owner_id):
                raise AccessDenied()
            case = await intake(service, thread, manual=True)
            await interaction.followup.send(
                t(locale, "cases-updated", number=case.number)
                if case
                else t(locale, "maps-review-no-file"),
                ephemeral=True,
            )
        except ValueError as error:
            await interaction.followup.send(error_text(locale, error), ephemeral=True)

    @map_group.command(name="list", description="List maps on a server")
    @app_commands.describe(
        server="Server name", creator="Only maps by this declared creator"
    )
    @app_commands.autocomplete(server=_autocomplete_server_for_command)
    async def cmd_maps(
        self, interaction: Interaction, server: str, creator: discord.User | None = None
    ) -> None:
        await handlers_misc.cmd_maps(self.bot, interaction, server, creator=creator)

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
        creator="Default creator for all files (optional)",
        creator1="Creator of file1 (overrides the default)",
        creator2="Creator of file2 (overrides the default)",
        creator3="Creator of file3 (overrides the default)",
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
        creator: discord.User | None = None,
        creator1: discord.User | None = None,
        creator2: discord.User | None = None,
        creator3: discord.User | None = None,
    ) -> None:
        await handlers_misc.cmd_upload_map(
            self.bot,
            interaction,
            server,
            [file1, file2, file3],
            creator=creator,
            creators=[creator1, creator2, creator3],
        )
