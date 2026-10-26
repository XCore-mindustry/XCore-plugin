from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import discord
from discord import Interaction
from xcore_protocol.generated.shared import ActorRefV1ActorType

from .bus.transport import RpcFailed
from .command_helpers import (
    claim_mutation,
    get_player_or_reply,
    parse_duration_or_reply,
    require_player_uuid,
    require_player_uuid_or_ip,
    send_paginated,
)
from .dto import (
    MERGE_KEEP_PID_TARGET,
    Actor,
    BanRecord,
    PlayerRecord,
    record_identifiers,
)
from .moderation_logs import add_embed_section, format_admin_value
from .moderation_views import AccountMergeConfirmView, BanConfirmView, MuteUndoView
from .presentation import (
    build_merge_preview_embed,
    build_moderation_reversal_embed,
    format_ban_expire_date,
)
from .retry import TRANSIENT_EXCEPTIONS
from .staff_actions import (
    BanNotFound,
    StaffActionError,
    perform_mute,
    perform_unban,
)
from .staff_actions import (
    perform_ban as perform_staff_ban,
)

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot
    from .daemons.admin_sync_daemon import ReconciledAdmin, SkippedAdmin
    from .daemons.staff_sync_daemon import StaffSyncResult


MSG_NO_ACTIVE_BAN = "No active ban found"
MSG_NO_ACTIVE_MUTE = "No active mute found"


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


def _normalize_audit_reason(reason: str | None) -> str:
    return str(reason or "Not Specified").strip() or "Not Specified"


async def _append_discord_moderation_audit(
    bot: XCoreDiscordBot,
    *,
    interaction: Interaction | None,
    action: str,
    player: PlayerRecord,
    reason: str,
    actor_name: str,
    actor_discord_id: str | None,
    duration: timedelta | None = None,
    expires_at: datetime | None = None,
    related_audit_id: str | None = None,
    supersedes_audit_id: str | None = None,
) -> str:
    request_id = str(getattr(interaction, "id", "") or "").strip() or None
    occurred_at = bot.store.now_utc()
    try:
        duration_ms = (
            int(duration.total_seconds() * 1000) if duration is not None else None
        )
    except (ValueError, TypeError, OverflowError):
        duration_ms = None
    uuid_value, ip_value = record_identifiers(player)
    return await bot.store.append_moderation_audit(
        action=action,
        target_uuid=uuid_value or "",
        target_pid=player.pid,
        target_name=player.nickname,
        target_ip=ip_value,
        actor_discord_id=actor_discord_id,
        actor_name=actor_name,
        reason=_normalize_audit_reason(reason),
        occurred_at=occurred_at,
        duration_ms=duration_ms,
        expires_at=expires_at,
        related_audit_id=related_audit_id,
        supersedes_audit_id=supersedes_audit_id,
        request_id=request_id,
    )


async def cmd_ban(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
    period: str,
    reason: str,
) -> None:
    duration = await parse_duration_or_reply(interaction, period)
    if duration is None:
        return

    player = await get_player_or_reply(bot, interaction, player_id)
    if player is None:
        return

    identifiers = await require_player_uuid_or_ip(
        interaction,
        player,
        action="ban player",
    )
    if identifiers is None:
        return

    view = BanConfirmView(
        requester_id=interaction.user.id,
        player_id=player_id,
        player=player,
        period=period,
        reason=reason,
        duration=duration,
        perform_ban=lambda **kwargs: perform_ban(bot, **kwargs),
    )
    await interaction.response.send_message(
        f"Are you sure you want to ban `{player.nickname}`?",
        view=view,
    )
    view.message = await interaction.original_response()


async def perform_ban(
    bot: XCoreDiscordBot,
    *,
    actor_name: str,
    actor_discord_id: str | None,
    player_id: int,
    period: str,
    reason: str,
    duration: timedelta,
    player: PlayerRecord,
    case_number: int | None = None,
) -> str:
    try:
        result = await perform_staff_ban(
            bot,
            actor=Actor(actor_discord_id, actor_name),
            player=replace(player, pid=player_id),
            period=period,
            reason=reason,
            duration=duration,
            case_number=case_number,
        )
    except StaffActionError as error:
        return str(error)
    return result.message


async def cmd_unban(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await get_player_or_reply(bot, interaction, player_id)
    if player is None:
        return

    identifiers = await require_player_uuid_or_ip(
        interaction,
        player,
        action="unban player",
    )
    if identifiers is None:
        return
    uuid_value, ip_value = identifiers

    if not await claim_mutation(
        bot,
        interaction,
        operation="unban",
        scope=str(player_id),
    ):
        return

    ban_doc = await bot.store.find_ban(uuid=uuid_value or "", ip=ip_value)
    # Keep the command's player snapshot for its audit and reply.
    action_ban = (
        replace(ban_doc, uuid=uuid_value, ip=ip_value)
        if ban_doc is not None
        else BanRecord(
            uuid=uuid_value,
            ip=ip_value,
            pid=player_id,
            name=player.nickname,
            admin_name="Unknown",
            reason="Not Specified",
            expire_date=None,
        )
    )
    try:
        await perform_unban(
            bot,
            action_ban,
            Actor(str(interaction.user.id), interaction.user.display_name),
            player=player,
            request_id=str(interaction.id),
        )
    except BanNotFound:
        await interaction.response.send_message(MSG_NO_ACTIVE_BAN, ephemeral=True)
        return

    embed = build_moderation_reversal_embed(
        action_label="Unbanned",
        subject_name=player.nickname,
        player_id=player_id,
        previous_actor_label="Admin who banned",
        previous_actor_value=(
            format_admin_value(
                admin_name=ban_doc.admin_name,
                admin_discord_id=ban_doc.admin_discord_id,
            )
            if ban_doc is not None
            else "Unknown"
        ),
        reason=ban_doc.reason if ban_doc is not None else "Not specified",
        expire_value=ban_doc.expire_date if ban_doc is not None else None,
        actor_label="Unbanned by",
        actor_name=interaction.user.display_name,
        format_expire_date=format_ban_expire_date,
    )
    await interaction.response.send_message(embed=embed)


async def cmd_pardon(
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
        operation="pardon",
        scope=str(player_id),
    ):
        return

    uuid_value = await require_player_uuid(
        interaction,
        player,
        action="pardon player",
    )
    if uuid_value is None:
        return

    await bot.container.moderation_bus.publish_pardon_player(uuid_value=uuid_value)
    await interaction.response.send_message(f"Pardoned `{player.nickname}`")


async def cmd_mute(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
    period: str,
    reason: str,
) -> None:
    duration = await parse_duration_or_reply(interaction, period)
    if duration is None:
        return

    player = await get_player_or_reply(bot, interaction, player_id)
    if player is None:
        return

    uuid_value = await require_player_uuid(
        interaction,
        player,
        action="mute player",
    )
    if uuid_value is None:
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation="mute",
        scope=f"{player_id}:{period}:{reason}",
    ):
        return

    result = await perform_mute(
        bot,
        player,
        duration,
        reason,
        Actor(str(interaction.user.id), interaction.user.display_name),
        request_id=str(interaction.id),
    )
    player_name = player.nickname
    view = MuteUndoView(
        requester_id=interaction.user.id,
        uuid=uuid_value,
        player_name=player_name,
        delete_mute=bot.store.delete_mute,
    )
    await interaction.response.send_message(
        result.message,
        view=view,
    )
    view.message = await interaction.original_response()


async def cmd_unmute(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await get_player_or_reply(bot, interaction, player_id)
    if player is None:
        return

    uuid_value = await require_player_uuid(
        interaction,
        player,
        action="unmute player",
    )
    if uuid_value is None:
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation="unmute",
        scope=str(player_id),
    ):
        return

    mute_doc = await bot.store.find_mute(uuid=uuid_value)
    deleted = await bot.store.delete_mute(uuid=uuid_value)
    if deleted == 0:
        await interaction.response.send_message(MSG_NO_ACTIVE_MUTE, ephemeral=True)
        return
    await _append_discord_moderation_audit(
        bot,
        interaction=interaction,
        action="UNMUTE",
        player=player,
        reason=mute_doc.reason if mute_doc is not None else "Not Specified",
        actor_name=interaction.user.display_name,
        actor_discord_id=str(interaction.user.id),
    )

    embed = build_moderation_reversal_embed(
        action_label="Unmuted",
        subject_name=player.nickname,
        player_id=player_id,
        previous_actor_label="Admin who muted",
        previous_actor_value=(
            format_admin_value(
                admin_name=mute_doc.admin_name,
                admin_discord_id=mute_doc.admin_discord_id,
            )
            if mute_doc is not None
            else "Unknown"
        ),
        reason=mute_doc.reason if mute_doc is not None else "Not specified",
        expire_value=mute_doc.expire_date if mute_doc is not None else None,
        actor_label="Unmuted by",
        actor_name=interaction.user.display_name,
        format_expire_date=format_ban_expire_date,
    )
    await interaction.response.send_message(embed=embed)


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


async def cmd_merge_player(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    source_pid: int,
    target_pid: int,
    reason: str = "Admin merge",
    keep_pid: str = MERGE_KEEP_PID_TARGET,
) -> None:
    if source_pid == target_pid:
        await interaction.response.send_message(
            "Cannot merge an account into itself.",
            ephemeral=True,
        )
        return

    source = await get_player_or_reply(bot, interaction, source_pid)
    if source is None:
        return

    target = await get_player_or_reply(bot, interaction, target_pid)
    if target is None:
        return

    if source.uuid and source.uuid.startswith("merged:"):
        await interaction.response.send_message(
            f"Source account #{source.pid} was already merged into another account.",
            ephemeral=True,
        )
        return

    if target.uuid and target.uuid.startswith("merged:"):
        await interaction.response.send_message(
            f"Target account #{target.pid} is closed and cannot receive a merge.",
            ephemeral=True,
        )
        return

    online = [
        f"#{player.pid} ({player.nickname})"
        for player in (source, target)
        if player.online
    ]
    if online:
        await interaction.response.send_message(
            "Cannot merge while an account is online: "
            f"{', '.join(online)}. Ask the player to disconnect, then retry.",
            ephemeral=True,
        )
        return

    embed = build_merge_preview_embed(
        source=source,
        target=target,
        keep_pid=keep_pid,
        reason=reason,
    )

    view = AccountMergeConfirmView(
        requester_id=interaction.user.id,
        source_pid=source_pid,
        target_pid=target_pid,
        source_player=source,
        target_player=target,
        reason=reason,
        perform_merge=lambda **kwargs: bot.container.players.merge_player_accounts(
            **kwargs
        ),
        keep_pid=keep_pid,
    )

    await interaction.response.send_message(embed=embed, view=view)
    view.message = await interaction.original_response()
