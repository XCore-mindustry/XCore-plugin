from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timedelta
from typing import TYPE_CHECKING

import discord
from discord import Interaction

from .command_helpers import (
    claim_mutation,
    get_player_or_reply,
    parse_duration_or_reply,
    require_player_uuid,
    require_player_uuid_or_ip,
    send_paginated,
)
from .dto import Actor, BanRecord, PlayerRecord, record_identifiers
from .moderation_logs import format_admin_value
from .moderation_views import BanConfirmView, MuteUndoView
from .player_pids import is_assigned
from .presentation import build_moderation_reversal_embed, format_ban_expire_date
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


MSG_NO_ACTIVE_BAN = "No active ban found"
MSG_NO_ACTIVE_MUTE = "No active mute found"


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
    return await bot.container.audit.append_moderation_audit(
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

    ban_doc = await bot.container.punishments.find_ban(
        uuid=uuid_value or "", ip=ip_value
    )
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
        delete_mute=bot.container.punishments.delete_mute,
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

    mute_doc = await bot.container.punishments.find_mute(uuid=uuid_value)
    deleted = await bot.container.punishments.delete_mute(uuid=uuid_value)
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


async def cmd_bans(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    name_filter: str | None,
) -> None:
    page_size = 6
    total_bans = await bot.container.punishments.count_bans(name_filter=name_filter)
    total_pages = max(1, (total_bans + page_size - 1) // page_size)

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        bans = await bot.container.punishments.list_bans(
            name_filter=name_filter, limit=page_size, page=page
        )
        embed = discord.Embed(
            title="Bans",
            color=discord.Color.green() if bans else discord.Color.red(),
        )
        if bans:
            for ban in bans:
                unban_date = format_ban_expire_date(ban.expire_date)
                ban_value = (
                    (f"PID: `{ban.pid}`\n" if is_assigned(ban.pid) else "")
                    + f"Admin: {format_admin_value(admin_name=ban.admin_name, admin_discord_id=ban.admin_discord_id)}\n"
                    + f"Reason: {ban.reason}\n"
                    + f"Unban: {unban_date}"
                )
                embed.add_field(
                    name=ban.name,
                    value=ban_value,
                    inline=False,
                )
        else:
            embed.description = "No bans found."
        has_next = len(bans) == page_size
        filter_suffix = f" • filter: {name_filter}" if name_filter else ""
        embed.set_footer(
            text=(
                f"Page {page + 1}/{total_pages} • total bans: {total_bans} "
                f"• entries on page: {len(bans)}{filter_suffix}"
            )
        )
        return embed, has_next

    await send_paginated(interaction, fetch_page)
