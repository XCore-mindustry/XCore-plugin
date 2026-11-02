from __future__ import annotations

from typing import TYPE_CHECKING

import discord
from discord import Interaction

from .bus.transport import RpcFailed
from .command_helpers import (
    claim_mutation,
    get_player_or_reply,
    require_player_uuid,
)
from .retry import TRANSIENT_EXCEPTIONS

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot
    from .daemons.staff_sync_daemon import StaffSyncResult


def _staff_error_message(error: Exception) -> str:
    if isinstance(error, RpcFailed):
        if error.error_code == "NOT_FOUND":
            return "The game account was not found or is no longer linked (NOT_FOUND)."
        if error.error_code == "UNAVAILABLE":
            return "The game server is unavailable or has roles switched off (UNAVAILABLE). Try again later."
        return f"The game server rejected the request ({error.error_code})."
    if isinstance(error, TimeoutError):
        return "The game server timed out. The outcome is unknown; try again later."
    if isinstance(error, discord.HTTPException):
        return "Discord could not update or fetch the member's roles. Try again later."
    return "Could not connect to the game server. Try again later."


def _staff_sync_message(result: StaffSyncResult) -> str:
    text = (
        f"Game role sync: {result.synced} synced, {result.skipped} skipped or failed."
    )
    if result.reason:
        text += f" {result.reason}"
    for code in result.errors:
        if code == "NOT_FOUND":
            text += " Some game accounts were not found or are no longer linked (NOT_FOUND)."
        elif code == "UNAVAILABLE":
            text += " The game server is unavailable or has roles switched off (UNAVAILABLE)."
        elif code == "TIMEOUT_OR_CONNECTION":
            text += " A game request timed out or could not connect; its outcome may be unknown."
        else:
            text += f" Game server error: {code}."
    if result.skipped and not result.errors and not result.reason:
        text += (
            " A complete Discord snapshot or a valid linked account was unavailable."
        )
    return text


async def _set_role_and_sync(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    discord_id: str,
    *,
    should_have_role: bool,
) -> None:
    await interaction.response.defer(thinking=True)
    try:
        await bot.set_discord_admin_role(
            discord_id=discord_id,
            should_have_role=should_have_role,
            reason=(
                f"/admin {'add' if should_have_role else 'remove'}"
                f" by {interaction.user.display_name}"
            ),
        )
        result = await bot.container.staff_sync_daemon.sync_member(discord_id)
    except (RpcFailed, discord.HTTPException, *TRANSIENT_EXCEPTIONS) as error:
        await interaction.followup.send(
            "Game role sync failed: " + _staff_error_message(error)
        )
        return
    await interaction.followup.send(_staff_sync_message(result))


async def _change_admin_role(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
    *,
    operation: str,
    should_have_role: bool,
) -> None:
    player = await get_player_or_reply(bot, interaction, player_id)
    if player is None:
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation=operation,
        scope=str(player_id),
    ):
        return

    if not player.discord_id:
        await interaction.response.send_message(
            "Discord account is not linked.", ephemeral=True
        )
        return
    linked_players = await bot.store.find_players_by_discord_id(player.discord_id)
    # Granting needs a game account to sync to; taking the role away never waits for one.
    if should_have_role and not any(
        str(linked.uuid or "").strip() for linked in linked_players
    ):
        await interaction.response.send_message(
            "No linked Mindustry account has a UUID.", ephemeral=True
        )
        return
    await _set_role_and_sync(
        bot, interaction, player.discord_id, should_have_role=should_have_role
    )


async def cmd_remove_admin(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
) -> None:
    await _change_admin_role(
        bot,
        interaction,
        player_id,
        operation="remove-admin",
        should_have_role=False,
    )


async def cmd_add_admin(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
) -> None:
    await _change_admin_role(
        bot,
        interaction,
        player_id,
        operation="add-admin",
        should_have_role=True,
    )


async def cmd_sync_admins(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    await interaction.response.defer(thinking=True)
    try:
        result = await bot.container.staff_sync_daemon.reconcile()
    except (RpcFailed, discord.HTTPException, *TRANSIENT_EXCEPTIONS) as error:
        await interaction.followup.send(
            "Game role sync failed: " + _staff_error_message(error)
        )
        return
    await interaction.followup.send(_staff_sync_message(result))


async def cmd_reset_password(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await get_player_or_reply(bot, interaction, player_id)
    if player is None:
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation="reset-password",
        scope=str(player_id),
    ):
        return

    uuid_value = await require_player_uuid(
        interaction,
        player,
        action="reset password",
    )
    if uuid_value is None:
        return

    await interaction.response.defer(thinking=True)
    try:
        changed = await bot.container.players.reset_password(uuid=uuid_value)
    except (RpcFailed, *TRANSIENT_EXCEPTIONS) as error:
        await interaction.followup.send(
            "Password reset failed: " + _staff_error_message(error)
        )
        return
    await interaction.followup.send(
        f"Password reset for `{player.nickname}`"
        if changed
        else "Password reset did not update any row"
    )
