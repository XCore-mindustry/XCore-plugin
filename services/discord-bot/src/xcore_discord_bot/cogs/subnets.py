from __future__ import annotations

from typing import TYPE_CHECKING

from discord import Interaction, app_commands
from discord.ext import commands

from .. import handlers_subnets
from ..registry import server_registry
from .checks import general_admin_check

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


async def _autocomplete_server(
    interaction: Interaction, current: str
) -> list[app_commands.Choice[str]]:
    del interaction
    needle = current.strip().lower()
    return [
        app_commands.Choice(name=server, value=server)
        for server in sorted(s.name for s in server_registry.get_all_servers())
        if not needle or needle in server.lower()
    ][:25]


class SubnetCog(commands.Cog):
    subnet_group = app_commands.Group(name="subnet", description="Manage subnet rules")

    def __init__(self, bot: XCoreDiscordBot) -> None:
        self.bot = bot

    @subnet_group.command(name="list", description="List subnet rules")
    @app_commands.describe(server="Target server")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_list(self, interaction: Interaction, server: str) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_list, self.bot, interaction, server)

    @subnet_group.command(name="check", description="Check an IP against subnet rules")
    @app_commands.describe(server="Target server", ip="IP address")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_check(self, interaction: Interaction, server: str, ip: str) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_check, self.bot, interaction, server, ip)

    @subnet_group.command(name="allow", description="Allow a CIDR network")
    @app_commands.describe(server="Target server", cidr="CIDR network", reason="Optional reason")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_allow(self, interaction: Interaction, server: str, cidr: str, reason: str | None = None) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_allow, self.bot, interaction, server, cidr, reason)

    @subnet_group.command(name="deny", description="Deny a CIDR network")
    @app_commands.describe(server="Target server", cidr="CIDR network", reason="Optional reason")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_deny(self, interaction: Interaction, server: str, cidr: str, reason: str | None = None) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_deny, self.bot, interaction, server, cidr, reason)

    @subnet_group.command(name="remove", description="Remove a CIDR network")
    @app_commands.describe(server="Target server", cidr="CIDR network")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_remove(self, interaction: Interaction, server: str, cidr: str) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_remove, self.bot, interaction, server, cidr)

    @subnet_group.command(name="reload", description="Reload subnet rules")
    @app_commands.describe(server="Target server")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_reload(self, interaction: Interaction, server: str) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_reload, self.bot, interaction, server)

    @subnet_group.command(name="import", description="Import CIDR networks")
    @app_commands.describe(server="Target server", text="CIDR networks separated by commas or lines")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_import(self, interaction: Interaction, server: str, text: str) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_import, self.bot, interaction, server, text)

    @subnet_group.command(name="sweep", description="Sweep subnet rules")
    @app_commands.describe(server="Target server", cluster="Sweep the cluster")
    @app_commands.autocomplete(server=_autocomplete_server)
    @general_admin_check()
    async def cmd_sweep(self, interaction: Interaction, server: str, cluster: bool = False) -> None:
        await handlers_subnets.safe_handler(handlers_subnets.cmd_subnet_sweep, self.bot, interaction, server, cluster)
