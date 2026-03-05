from __future__ import annotations

from discord import Interaction, app_commands
from discord.ext import commands
from typing import TYPE_CHECKING

from .autocomplete import _autocomplete_player_id
from .checks import admin_check, general_admin_check

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


_PERIOD_PRESETS: tuple[tuple[str, str], ...] = (
    ("10m", "10 minutes"),
    ("30m", "30 minutes"),
    ("1h", "1 hour"),
    ("6h", "6 hours"),
    ("1d", "1 day"),
    ("3d", "3 days"),
    ("1w", "1 week"),
    ("2w", "2 weeks"),
    ("30d", "1 month"),
    ("90d", "3 months"),
    ("1y", "1 year"),
)


async def _autocomplete_period(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    del interaction
    current_norm = current.strip().lower()
    choices: list[app_commands.Choice[str]] = []

    for value, label in _PERIOD_PRESETS:
        preset_text = f"{value} - {label}"
        if current_norm and current_norm not in preset_text.lower():
            continue
        choices.append(app_commands.Choice(name=preset_text, value=value))
        if len(choices) >= 25:
            break

    return choices


class AdminCog(commands.Cog):
    def __init__(self, bot: "XCoreDiscordBot") -> None:
        self.bot = bot

    @app_commands.command(name="search", description="Search players by name (admin)")
    @app_commands.describe(name="Player name to search for")
    @admin_check()
    async def cmd_search(self, interaction: Interaction, name: str) -> None:
        await self.bot._cmd_search(interaction, name)

    @app_commands.command(
        name="bans", description="List bans, optionally filtered by name (admin)"
    )
    @app_commands.describe(name="Optional name filter")
    @admin_check()
    async def cmd_bans(self, interaction: Interaction, name: str | None = None) -> None:
        await self.bot._cmd_bans(interaction, name)

    @app_commands.command(name="ban", description="Ban a player (admin)")
    @app_commands.describe(
        player_id="Numeric player ID",
        period="Duration e.g. 1d, 2w, 1y",
        reason="Ban reason",
    )
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @app_commands.autocomplete(period=_autocomplete_period)
    @admin_check()
    async def cmd_ban(
        self,
        interaction: Interaction,
        player_id: int,
        period: str,
        reason: str = "Not Specified",
    ) -> None:
        await self.bot._cmd_ban(interaction, player_id, period, reason)

    @app_commands.command(name="unban", description="Unban a player (admin)")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @admin_check()
    async def cmd_unban(self, interaction: Interaction, player_id: int) -> None:
        await self.bot._cmd_unban(interaction, player_id)

    @app_commands.command(name="mute", description="Mute a player (admin)")
    @app_commands.describe(
        player_id="Numeric player ID",
        period="Duration e.g. 10m, 1h",
        reason="Mute reason",
    )
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @app_commands.autocomplete(period=_autocomplete_period)
    @admin_check()
    async def cmd_mute(
        self,
        interaction: Interaction,
        player_id: int,
        period: str,
        reason: str = "Not Specified",
    ) -> None:
        await self.bot._cmd_mute(interaction, player_id, period, reason)

    @app_commands.command(name="unmute", description="Unmute a player (admin)")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @admin_check()
    async def cmd_unmute(self, interaction: Interaction, player_id: int) -> None:
        await self.bot._cmd_unmute(interaction, player_id)

    @app_commands.command(
        name="remove-admin",
        description="Remove admin from a player (general admin)",
    )
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @general_admin_check()
    async def cmd_remove_admin(self, interaction: Interaction, player_id: int) -> None:
        await self.bot._cmd_remove_admin(interaction, player_id)

    @app_commands.command(
        name="reset-password",
        description="Reset admin password for a player (general admin)",
    )
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @general_admin_check()
    async def cmd_reset_password(
        self, interaction: Interaction, player_id: int
    ) -> None:
        await self.bot._cmd_reset_password(interaction, player_id)

    @app_commands.command(
        name="test-error",
        description="Trigger an internal error for logging test (admin)",
    )
    @admin_check()
    async def cmd_test_error(self, interaction: Interaction) -> None:
        raise RuntimeError(
            f"Intentional test error from /test-error by {interaction.user.display_name}"
        )
