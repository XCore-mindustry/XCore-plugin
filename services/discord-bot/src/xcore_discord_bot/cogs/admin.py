from __future__ import annotations

from typing import TYPE_CHECKING

from discord import Interaction, app_commands
from discord.ext import commands

from .. import handlers_badges, handlers_misc, handlers_moderation
from ..dto import MERGE_KEEP_PID_SOURCE, MERGE_KEEP_PID_TARGET
from .autocomplete import _autocomplete_badge_id, _autocomplete_player_id
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
    admin_group = app_commands.Group(
        name="admin",
        description="Manage Discord-linked admin access",
    )
    badge_group = app_commands.Group(
        name="badge",
        description="Manage player badges",
    )

    def __init__(self, bot: XCoreDiscordBot) -> None:
        self.bot = bot

    @app_commands.command(name="search", description="Search players by name (admin)")
    @app_commands.describe(name="Player name to search for")
    @admin_check()
    async def cmd_search(self, interaction: Interaction, name: str) -> None:
        await handlers_misc.cmd_search(self.bot, interaction, name)

    @app_commands.command(
        name="bans", description="List bans, optionally filtered by name (admin)"
    )
    @app_commands.describe(name="Optional name filter")
    @admin_check()
    async def cmd_bans(self, interaction: Interaction, name: str | None = None) -> None:
        await handlers_misc.cmd_bans(self.bot, interaction, name)

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
        await handlers_moderation.cmd_ban(
            self.bot, interaction, player_id, period, reason
        )

    @app_commands.command(name="unban", description="Unban a player (admin)")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @admin_check()
    async def cmd_unban(self, interaction: Interaction, player_id: int) -> None:
        await handlers_moderation.cmd_unban(self.bot, interaction, player_id)

    @app_commands.command(name="pardon", description="Pardon a player (admin)")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @admin_check()
    async def cmd_pardon(self, interaction: Interaction, player_id: int) -> None:
        await handlers_moderation.cmd_pardon(self.bot, interaction, player_id)

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
        await handlers_moderation.cmd_mute(
            self.bot, interaction, player_id, period, reason
        )

    @app_commands.command(name="unmute", description="Unmute a player (admin)")
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @admin_check()
    async def cmd_unmute(self, interaction: Interaction, player_id: int) -> None:
        await handlers_moderation.cmd_unmute(self.bot, interaction, player_id)

    @app_commands.command(
        name="merge_player",
        description="Merge a player account into another account (admin)",
    )
    @app_commands.describe(
        source_pid="Source player ID (account to close and merge FROM)",
        target_pid="Target player ID (account to keep active and merge INTO)",
        keep_pid="Which PID the surviving account keeps (UUID always comes from the target)",
        reason="Reason for account merge",
    )
    @app_commands.autocomplete(source_pid=_autocomplete_player_id)
    @app_commands.autocomplete(target_pid=_autocomplete_player_id)
    @app_commands.choices(
        keep_pid=[
            app_commands.Choice(
                name="Target PID (default)", value=MERGE_KEEP_PID_TARGET
            ),
            app_commands.Choice(name="Source PID", value=MERGE_KEEP_PID_SOURCE),
        ]
    )
    @admin_check()
    async def cmd_merge_player(
        self,
        interaction: Interaction,
        source_pid: int,
        target_pid: int,
        keep_pid: str = MERGE_KEEP_PID_TARGET,
        reason: str = "Admin merge",
    ) -> None:
        await handlers_moderation.cmd_merge_player(
            self.bot,
            interaction,
            source_pid,
            target_pid,
            reason,
            keep_pid,
        )

    @admin_group.command(
        name="add", description="Grant admin to a player (general admin)"
    )
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @general_admin_check()
    async def cmd_admin_add(self, interaction: Interaction, player_id: int) -> None:
        await handlers_moderation.cmd_add_admin(self.bot, interaction, player_id)

    @admin_group.command(
        name="remove", description="Revoke admin from a player (general admin)"
    )
    @app_commands.describe(player_id="Numeric player ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @general_admin_check()
    async def cmd_admin_remove(self, interaction: Interaction, player_id: int) -> None:
        await handlers_moderation.cmd_remove_admin(self.bot, interaction, player_id)

    @admin_group.command(
        name="list", description="List Discord-linked admins (general admin)"
    )
    @general_admin_check()
    async def cmd_admin_list(self, interaction: Interaction) -> None:
        await handlers_moderation.cmd_list_admins(self.bot, interaction)

    @admin_group.command(
        name="sync",
        description="Reconcile Discord admin role with plugin state (general admin)",
    )
    @general_admin_check()
    async def cmd_admin_sync(self, interaction: Interaction) -> None:
        await handlers_moderation.cmd_sync_admins(self.bot, interaction)

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
        await handlers_moderation.cmd_reset_password(self.bot, interaction, player_id)

    @badge_group.command(
        name="grant",
        description="Grant a badge to a player (general admin)",
    )
    @app_commands.describe(player_id="Numeric player ID", badge_id="Badge ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @app_commands.autocomplete(badge_id=_autocomplete_badge_id)
    @general_admin_check()
    async def cmd_badge_grant(
        self,
        interaction: Interaction,
        player_id: int,
        badge_id: str,
    ) -> None:
        await handlers_badges.cmd_badge_grant(
            self.bot,
            interaction,
            player_id,
            badge_id,
        )

    @badge_group.command(
        name="revoke",
        description="Revoke a badge from a player (general admin)",
    )
    @app_commands.describe(player_id="Numeric player ID", badge_id="Badge ID")
    @app_commands.autocomplete(player_id=_autocomplete_player_id)
    @app_commands.autocomplete(badge_id=_autocomplete_badge_id)
    @general_admin_check()
    async def cmd_badge_revoke(
        self,
        interaction: Interaction,
        player_id: int,
        badge_id: str,
    ) -> None:
        await handlers_badges.cmd_badge_revoke(
            self.bot,
            interaction,
            player_id,
            badge_id,
        )

    @app_commands.command(
        name="test-error",
        description="Trigger an internal error for logging test (admin)",
    )
    @admin_check()
    async def cmd_test_error(self, interaction: Interaction) -> None:
        raise RuntimeError(
            f"Intentional test error from /test-error by {interaction.user.display_name}"
        )
