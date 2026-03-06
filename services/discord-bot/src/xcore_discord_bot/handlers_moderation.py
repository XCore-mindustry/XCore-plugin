from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import TYPE_CHECKING

import discord
from discord import Interaction

from .dto import PlayerRecord
from .moderation_views import BanConfirmView, MuteUndoView
from .presentation import format_ban_expire_date

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


MSG_NO_ACTIVE_BAN = "No active ban found"
MSG_NO_ACTIVE_MUTE = "No active mute found"


async def cmd_ban(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
    period: str,
    reason: str,
) -> None:
    duration = await bot._parse_duration_or_reply(interaction, period)
    if duration is None:
        return

    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    identifiers = await bot._require_player_uuid_or_ip(
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
        f"Are you sure you want to ban `{bot._player_name(player)}`?",
        view=view,
    )
    view.message = await interaction.original_response()


async def perform_ban(
    bot: "XCoreDiscordBot",
    *,
    actor_name: str,
    player_id: int,
    period: str,
    reason: str,
    duration: timedelta,
    player: PlayerRecord,
) -> str:
    uuid_value, ip_value = bot._player_identifiers(player)
    if uuid_value is None and ip_value is None:
        return "Cannot ban player: both UUID and IP are missing in player data."

    key_uuid = uuid_value or "none"
    key_ip = ip_value or "none"
    claim_key = f"ban-confirm:{key_uuid}:{key_ip}:{period}:{reason}"
    claimed = await bot.claim_idempotency(claim_key, ttl_seconds=600)
    if not claimed:
        return "This ban was already processed recently."

    expire = await bot.now_utc() + duration
    await bot.upsert_ban(
        uuid=uuid_value or "",
        ip=ip_value,
        name=bot._player_name(player),
        admin_name=actor_name,
        reason=reason,
        expire_date=expire,
    )
    await bot.publish_kick_banned(uuid_value=uuid_value or "", ip=ip_value)
    await post_ban_log(
        bot,
        pid=player_id,
        name=bot._player_name(player),
        admin_name=actor_name,
        reason=reason,
        expire=expire,
    )
    return (
        f"Banned `{bot._player_name(player)}` until "
        f"{discord.utils.format_dt(expire, style='f')} "
        f"({discord.utils.format_dt(expire, style='R')})"
    )


async def cmd_unban(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    identifiers = await bot._require_player_uuid_or_ip(
        interaction,
        player,
        action="unban player",
    )
    if identifiers is None:
        return
    uuid_value, ip_value = identifiers

    if not await bot._claim_mutation(
        interaction,
        operation="unban",
        scope=str(player_id),
    ):
        return

    ban_doc = await bot.find_ban(uuid=uuid_value or "", ip=ip_value)
    deleted = await bot.delete_ban(uuid=uuid_value or "", ip=ip_value)
    if deleted == 0:
        await interaction.response.send_message(MSG_NO_ACTIVE_BAN, ephemeral=True)
        return
    if uuid_value is not None:
        await bot.publish_pardon_player(uuid_value=uuid_value)

    embed = bot._build_moderation_reversal_embed(
        action_label="Unbanned",
        subject_name=bot._player_name(player),
        player_id=player_id,
        previous_actor_label="Admin who banned",
        previous_actor_value=ban_doc.admin_name if ban_doc is not None else "Unknown",
        reason=ban_doc.reason if ban_doc is not None else "Not specified",
        expire_value=ban_doc.expire_date if ban_doc is not None else None,
        actor_label="Unbanned by",
        actor_name=interaction.user.display_name,
        format_expire_date=format_ban_expire_date,
    )
    await interaction.response.send_message(embed=embed)


async def cmd_pardon(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    if not await bot._claim_mutation(
        interaction,
        operation="pardon",
        scope=str(player_id),
    ):
        return

    uuid_value = await bot._require_player_uuid(
        interaction,
        player,
        action="pardon player",
    )
    if uuid_value is None:
        return

    await bot.publish_pardon_player(uuid_value=uuid_value)
    await interaction.response.send_message(f"Pardoned `{bot._player_name(player)}`")


async def cmd_mute(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
    period: str,
    reason: str,
) -> None:
    duration = await bot._parse_duration_or_reply(interaction, period)
    if duration is None:
        return

    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    uuid_value = await bot._require_player_uuid(
        interaction,
        player,
        action="mute player",
    )
    if uuid_value is None:
        return

    if not await bot._claim_mutation(
        interaction,
        operation="mute",
        scope=f"{player_id}:{period}:{reason}",
    ):
        return

    expire = await bot.now_utc() + duration
    await bot.upsert_mute(
        uuid=uuid_value,
        name=bot._player_name(player),
        admin_name=interaction.user.display_name,
        reason=reason,
        expire_date=expire,
    )
    player_name = bot._player_name(player)
    view = MuteUndoView(
        requester_id=interaction.user.id,
        uuid=uuid_value,
        player_name=player_name,
        delete_mute=bot.delete_mute,
    )
    await interaction.response.send_message(
        f"Muted `{player_name}` until {discord.utils.format_dt(expire, style='f')} "
        f"({discord.utils.format_dt(expire, style='R')})",
        view=view,
    )
    view.message = await interaction.original_response()


async def cmd_unmute(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    uuid_value = await bot._require_player_uuid(
        interaction,
        player,
        action="unmute player",
    )
    if uuid_value is None:
        return

    if not await bot._claim_mutation(
        interaction,
        operation="unmute",
        scope=str(player_id),
    ):
        return

    mute_doc = await bot.find_mute(uuid=uuid_value)
    deleted = await bot.delete_mute(uuid=uuid_value)
    if deleted == 0:
        await interaction.response.send_message(MSG_NO_ACTIVE_MUTE, ephemeral=True)
        return

    embed = bot._build_moderation_reversal_embed(
        action_label="Unmuted",
        subject_name=bot._player_name(player),
        player_id=player_id,
        previous_actor_label="Admin who muted",
        previous_actor_value=mute_doc.admin_name if mute_doc is not None else "Unknown",
        reason=mute_doc.reason if mute_doc is not None else "Not specified",
        expire_value=mute_doc.expire_date if mute_doc is not None else None,
        actor_label="Unmuted by",
        actor_name=interaction.user.display_name,
        format_expire_date=format_ban_expire_date,
    )
    await interaction.response.send_message(embed=embed)


async def cmd_remove_admin(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    if not await bot._claim_mutation(
        interaction,
        operation="remove-admin",
        scope=str(player_id),
    ):
        return

    uuid_value = await bot._require_player_uuid(
        interaction,
        player,
        action="remove admin",
    )
    if uuid_value is None:
        return

    changed = await bot.remove_admin(uuid=uuid_value)
    await bot.publish_remove_admin(uuid_value=uuid_value)
    await interaction.response.send_message(
        f"Removed admin for `{bot._player_name(player)}`"
        if changed
        else "No admin state was changed"
    )


async def cmd_reset_password(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    if not await bot._claim_mutation(
        interaction,
        operation="reset-password",
        scope=str(player_id),
    ):
        return

    uuid_value = await bot._require_player_uuid(
        interaction,
        player,
        action="reset password",
    )
    if uuid_value is None:
        return

    changed = await bot.reset_password(uuid=uuid_value)
    if changed:
        await bot.publish_reload_player_data_cache()
    await interaction.response.send_message(
        f"Password reset for `{bot._player_name(player)}`"
        if changed
        else "Password reset did not update any row"
    )


async def post_ban_log(
    bot: "XCoreDiscordBot",
    *,
    pid: int,
    name: str,
    admin_name: str,
    reason: str,
    expire: datetime,
) -> None:
    from .bot import strip_mindustry_colors

    bans_channel_id = bot.bans_channel_id
    if not bans_channel_id:
        return

    channel = await bot._resolve_messageable_channel(
        bans_channel_id, context="ban logs"
    )
    if channel is None:
        return

    safe_name = strip_mindustry_colors(str(name).replace("`", "")).strip() or "Unknown"
    safe_admin_name = (
        strip_mindustry_colors(str(admin_name).replace("`", "")).strip() or "Unknown"
    )
    safe_reason = strip_mindustry_colors(str(reason).replace("`", "")).strip()
    if not safe_reason:
        safe_reason = "No reason provided"

    safe_pid = pid if pid > 0 else None
    violator_value = (
        f"{safe_name} (pid={safe_pid})" if safe_pid is not None else safe_name
    )
    expire_utc = (
        expire.replace(tzinfo=timezone.utc) if expire.tzinfo is None else expire
    )
    unban_value = (
        f"{discord.utils.format_dt(expire_utc, style='f')} "
        f"({discord.utils.format_dt(expire_utc, style='R')})"
    )

    embed = discord.Embed(title="Ban Issued", color=discord.Color.red())
    embed.add_field(name="Violator", value=violator_value, inline=False)
    embed.add_field(name="Admin", value=safe_admin_name, inline=False)
    embed.add_field(name="Reason", value=safe_reason, inline=False)
    embed.add_field(name="Expires", value=unban_value, inline=False)
    await channel.send(embed=embed)
