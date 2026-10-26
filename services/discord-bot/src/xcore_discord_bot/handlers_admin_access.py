from __future__ import annotations

from typing import TYPE_CHECKING

import discord
from discord import Interaction
from xcore_protocol.generated.shared import ActorRefV1ActorType

from .bus.transport import RpcFailed
from .command_helpers import (
    claim_mutation,
    get_player_or_reply,
    require_player_uuid,
    send_paginated,
)
from .moderation_logs import add_embed_section
from .retry import TRANSIENT_EXCEPTIONS

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot
    from .daemons.admin_sync_daemon import ReconciledAdmin, SkippedAdmin
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
            reason=f"/admin by {interaction.user.display_name}",
        )
        daemon = bot.container.staff_sync_daemon
        assert daemon is not None
        result = await daemon.sync_member(discord_id)
    except (RpcFailed, discord.HTTPException, *TRANSIENT_EXCEPTIONS) as error:
        await interaction.followup.send(
            "Game role sync failed: " + _staff_error_message(error)
        )
        return
    await interaction.followup.send(_staff_sync_message(result))


def _format_reconcile_player_item(item: ReconciledAdmin) -> str:
    return f"`{item.nickname}` (pid={item.pid}, <@{item.discord_id}>)"


def _format_reconcile_skipped_item(item: SkippedAdmin) -> str:
    return (
        f"<@{item.discord_id}> — {item.player} ({item.reason})"
        if item.discord_id
        else f"{item.player} ({item.reason})"
    )


async def cmd_remove_admin(
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
        operation="remove-admin",
        scope=str(player_id),
    ):
        return

    linked_players = [player]
    if player.discord_id:
        linked_players = await bot.store.find_players_by_discord_id(player.discord_id)

    target_players = [
        linked_player
        for linked_player in linked_players
        if str(linked_player.uuid or "").strip()
    ]
    if not target_players:
        await interaction.response.send_message(
            "Cannot remove admin: no linked Mindustry account has a UUID.",
            ephemeral=True,
        )
        return

    if bot.settings.roles_mode:
        if not player.discord_id:
            await interaction.response.send_message(
                "Discord account is not linked.", ephemeral=True
            )
            return
        await _set_role_and_sync(
            bot, interaction, player.discord_id, should_have_role=False
        )
        return

    role_changed = False
    if player.discord_id:
        role_changed = await bot.set_discord_admin_role(
            discord_id=player.discord_id,
            should_have_role=False,
            reason=f"/admin remove by {interaction.user.display_name}",
        )

    any_changed = False
    for target_player in target_players:
        target_uuid = str(target_player.uuid or "").strip()
        matched, changed = await bot.store.set_admin_access(
            uuid=target_uuid,
            is_admin=False,
            admin_source="NONE",
        )
        if not matched:
            continue
        await bot.container.accounts_bus.publish_discord_admin_access_changed(
            player_uuid=target_uuid,
            player_pid=target_player.pid,
            player_name=target_player.nickname,
            discord_id=target_player.discord_id or "",
            discord_username=target_player.discord_username,
            admin=False,
            source_name="NONE",
            source_type=ActorRefV1ActorType.SYSTEM,
            actor_name=interaction.user.display_name,
            actor_discord_id=str(interaction.user.id),
            actor_type=ActorRefV1ActorType.DISCORD,
            reason="/admin remove",
        )
        any_changed = any_changed or changed

    target_names = ", ".join(f"`{item.nickname}`" for item in target_players)
    await interaction.response.send_message(
        f"Removed admin for {target_names}"
        if any_changed or role_changed
        else "No admin state was changed"
    )


async def cmd_add_admin(
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
        operation="add-admin",
        scope=str(player_id),
    ):
        return

    if not player.discord_id:
        await interaction.response.send_message(
            "Cannot grant admin: Discord account is not linked.",
            ephemeral=True,
        )
        return

    linked_players = await bot.store.find_players_by_discord_id(player.discord_id)
    target_players = [
        linked_player
        for linked_player in linked_players
        if str(linked_player.uuid or "").strip()
    ]
    if not target_players:
        await interaction.response.send_message(
            "Cannot grant admin: no linked Mindustry account has a UUID.",
            ephemeral=True,
        )
        return

    if bot.settings.roles_mode:
        if not player.discord_id:
            await interaction.response.send_message(
                "Discord account is not linked.", ephemeral=True
            )
            return
        await _set_role_and_sync(
            bot, interaction, player.discord_id, should_have_role=True
        )
        return

    role_changed = await bot.set_discord_admin_role(
        discord_id=player.discord_id,
        should_have_role=True,
        reason=f"/admin add by {interaction.user.display_name}",
    )

    any_changed = False
    for target_player in target_players:
        target_uuid = str(target_player.uuid or "").strip()
        matched, changed = await bot.store.set_admin_access(
            uuid=target_uuid,
            is_admin=True,
            admin_source="DISCORD_ROLE",
        )
        if not matched:
            continue
        await bot.container.accounts_bus.publish_discord_admin_access_changed(
            player_uuid=target_uuid,
            player_pid=target_player.pid,
            player_name=target_player.nickname,
            discord_id=target_player.discord_id or "",
            discord_username=target_player.discord_username,
            admin=True,
            source_name="DISCORD_ROLE",
            source_type=ActorRefV1ActorType.SYSTEM,
            actor_name=interaction.user.display_name,
            actor_discord_id=str(interaction.user.id),
            actor_type=ActorRefV1ActorType.DISCORD,
            reason="/admin add",
        )
        any_changed = any_changed or changed

    target_names = ", ".join(f"`{item.nickname}`" for item in target_players)
    await interaction.response.send_message(
        f"Granted admin for {target_names}"
        if any_changed or role_changed
        else "No admin state was changed"
    )


async def cmd_list_admins(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    players = await bot.store.find_discord_admin_players()
    if not players:
        await interaction.response.send_message(
            "No Discord admins found.", ephemeral=True
        )
        return

    page_size = 10
    total_entries = len(players)
    total_pages = max(1, (total_entries + page_size - 1) // page_size)

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        start = page * page_size
        end = start + page_size
        entries = players[start:end]

        embed = discord.Embed(
            title="Discord Admin Access",
            color=discord.Color.blurple(),
            description="Linked admin accounts with Discord role-backed access.",
        )

        for player in entries:
            discord_id = str(player.discord_id or "").strip()
            discord_ref = f"<@{discord_id}>" if discord_id else "not linked"
            discord_username = str(player.discord_username or "").strip()
            source = str(player.admin_source or "NONE").strip() or "NONE"

            value_lines = [
                f"PID: `{player.pid}`",
                f"Source: `{source}`",
                f"Discord: {discord_ref}",
            ]
            if discord_username:
                value_lines.append(f"Discord username: `{discord_username}`")

            embed.add_field(
                name=player.nickname,
                value="\n".join(value_lines),
                inline=False,
            )

        embed.set_footer(
            text=(
                f"Page {page + 1}/{total_pages} • total admins: {total_entries} "
                f"• entries on page: {len(entries)}"
            )
        )
        return embed, end < total_entries

    await send_paginated(
        interaction,
        fetch_page,
        ephemeral=False,
    )


async def cmd_sync_admins(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    if bot.settings.roles_mode:
        await interaction.response.defer(thinking=True)
        daemon = bot.container.staff_sync_daemon
        assert daemon is not None
        try:
            result = await daemon.reconcile()
        except (RpcFailed, discord.HTTPException, *TRANSIENT_EXCEPTIONS) as error:
            await interaction.followup.send(
                "Game role sync failed: " + _staff_error_message(error)
            )
            return
        await interaction.followup.send(_staff_sync_message(result))
        return
    result = await bot.container.admin_sync_daemon.reconcile()
    embed = discord.Embed(
        title="Admin Reconcile Complete",
        color=discord.Color.blurple(),
        description=(
            f"Applied: **{len(result.applied)}**\n"
            f"Revoked: **{len(result.revoked)}**\n"
            f"Discord role members: **{result.discord_admins}**"
        ),
    )

    if result.applied:
        applied_items = [_format_reconcile_player_item(item) for item in result.applied]
        add_embed_section(embed, name="Added", items=applied_items)

    if result.revoked:
        revoked_items = [_format_reconcile_player_item(item) for item in result.revoked]
        add_embed_section(embed, name="Revoked", items=revoked_items)

    if result.skipped:
        skipped_items = [
            _format_reconcile_skipped_item(item) for item in result.skipped
        ]
        add_embed_section(embed, name="Skipped", items=skipped_items)

    await interaction.response.send_message(
        embed=embed,
    )


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

    send_message = interaction.response.send_message
    if bot.settings.roles_mode:
        await interaction.response.defer(thinking=True)
        send_message = interaction.followup.send
    try:
        changed = await bot.container.players.reset_password(uuid=uuid_value)
    except (RpcFailed, *TRANSIENT_EXCEPTIONS) as error:
        if not bot.settings.roles_mode:
            raise
        await send_message("Password reset failed: " + _staff_error_message(error))
        return
    await send_message(
        f"Password reset for `{player.nickname}`"
        if changed
        else "Password reset did not update any row"
    )
