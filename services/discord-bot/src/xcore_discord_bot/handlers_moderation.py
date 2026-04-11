from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import TYPE_CHECKING, cast

import discord
from discord import Interaction

from .contracts import VoteKickParticipant
from .dto import PlayerRecord
from .moderation_views import BanConfirmView, MuteUndoView
from .presentation import format_ban_expire_date

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


MSG_NO_ACTIVE_BAN = "No active ban found"
MSG_NO_ACTIVE_MUTE = "No active mute found"
_EMBED_FIELD_VALUE_LIMIT = 1024


def _split_embed_field_chunks(
    items: list[str], *, limit: int = _EMBED_FIELD_VALUE_LIMIT, separator: str = ", "
) -> list[str]:
    chunks: list[str] = []
    current = ""

    for item in items:
        joiner = separator if current else ""
        candidate = f"{current}{joiner}{item}"
        if len(candidate) <= limit:
            current = candidate
            continue

        if current:
            chunks.append(current)
            current = item
            continue

        chunks.append(item[:limit])

    if current:
        chunks.append(current)

    return chunks


def _add_embed_section(
    embed: discord.Embed, *, name: str, items: list[str], separator: str = ", "
) -> None:
    for index, chunk in enumerate(
        _split_embed_field_chunks(items, separator=separator), start=1
    ):
        field_name = name if index == 1 else f"{name} (cont. {index})"
        embed.add_field(name=field_name, value=chunk, inline=False)


def _format_vote_kick_party_value(*, name: str, pid: int | None) -> str:
    safe_name = str(name).strip() or "Unknown"
    return f"{safe_name} (pid={pid})" if pid is not None and pid > 0 else safe_name


def _format_vote_kick_participant(item: VoteKickParticipant) -> str:
    from .bot import strip_mindustry_colors

    name = strip_mindustry_colors(str(item.name).replace("`", "")).strip() or "Unknown"
    segments = [f"`{name}`"]
    if item.pid is not None and item.pid > 0:
        segments.append(f"pid={item.pid}")
    discord_id = str(item.discord_id or "").strip()
    if discord_id:
        segments.append(f"<@{discord_id}> ({discord_id})")
    return (
        f"{segments[0]} ({', '.join(segments[1:])})"
        if len(segments) > 1
        else segments[0]
    )


def _format_reconcile_player_item(item: dict[str, object]) -> str:
    return f"`{str(item['nickname'])}` (pid={str(item['pid'])}, <@{str(item['discord_id'])}>)"


def _format_reconcile_skipped_item(item: dict[str, str]) -> str:
    discord_id = str(item["discord_id"])
    player = str(item["player"])
    reason = str(item["reason"])
    return (
        f"<@{discord_id}> — {player} ({reason})"
        if discord_id
        else f"{player} ({reason})"
    )


def _normalize_audit_reason(reason: str | None) -> str:
    return str(reason or "Not Specified").strip() or "Not Specified"


async def _append_discord_moderation_audit(
    bot: "XCoreDiscordBot",
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
    occurred_at = await bot.now_utc()
    duration_ms = int(duration.total_seconds() * 1000) if duration is not None else None
    uuid_value, ip_value = bot._player_identifiers(player)
    return await bot.append_moderation_audit(
        action=action,
        target_uuid=uuid_value or "",
        target_pid=player.pid,
        target_name=bot._player_name(player),
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
    actor_discord_id: str | None,
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
        pid=player_id,
        name=bot._player_name(player),
        admin_name=actor_name,
        admin_discord_id=actor_discord_id,
        reason=reason,
        expire_date=expire,
    )
    await _append_discord_moderation_audit(
        bot,
        interaction=None,
        action="BAN",
        player=player,
        reason=reason,
        actor_name=actor_name,
        actor_discord_id=actor_discord_id,
        duration=duration,
        expires_at=expire,
    )
    await bot.publish_kick_banned(uuid_value=uuid_value or "", ip=ip_value)
    await post_ban_log(
        bot,
        pid=player_id,
        name=bot._player_name(player),
        admin_name=actor_name,
        admin_discord_id=actor_discord_id,
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
    await _append_discord_moderation_audit(
        bot,
        interaction=interaction,
        action="UNBAN",
        player=player,
        reason=ban_doc.reason if ban_doc is not None else "Not Specified",
        actor_name=interaction.user.display_name,
        actor_discord_id=str(interaction.user.id),
    )

    embed = bot._build_moderation_reversal_embed(
        action_label="Unbanned",
        subject_name=bot._player_name(player),
        player_id=player_id,
        previous_actor_label="Admin who banned",
        previous_actor_value=(
            _format_admin_value(
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
        pid=player_id,
        name=bot._player_name(player),
        admin_name=interaction.user.display_name,
        admin_discord_id=str(interaction.user.id),
        reason=reason,
        expire_date=expire,
    )
    await _append_discord_moderation_audit(
        bot,
        interaction=interaction,
        action="MUTE",
        player=player,
        reason=reason,
        actor_name=interaction.user.display_name,
        actor_discord_id=str(interaction.user.id),
        duration=duration,
        expires_at=expire,
    )
    player_name = bot._player_name(player)
    await post_mute_log(
        bot,
        pid=player_id,
        name=player_name,
        admin_name=interaction.user.display_name,
        admin_discord_id=str(interaction.user.id),
        reason=reason,
        expire=expire,
    )
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
    await _append_discord_moderation_audit(
        bot,
        interaction=interaction,
        action="UNMUTE",
        player=player,
        reason=mute_doc.reason if mute_doc is not None else "Not Specified",
        actor_name=interaction.user.display_name,
        actor_discord_id=str(interaction.user.id),
    )

    embed = bot._build_moderation_reversal_embed(
        action_label="Unmuted",
        subject_name=bot._player_name(player),
        player_id=player_id,
        previous_actor_label="Admin who muted",
        previous_actor_value=(
            _format_admin_value(
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

    linked_players = [player]
    if player.discord_id:
        linked_players = await bot.find_players_by_discord_id(player.discord_id)

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
        matched, changed = await bot.set_admin_access(
            uuid=target_uuid,
            is_admin=False,
            admin_source="NONE",
        )
        if not matched:
            continue
        await bot.publish_discord_admin_access_changed(
            player_uuid=target_uuid,
            player_pid=target_player.pid,
            discord_id=target_player.discord_id or "",
            discord_username=target_player.discord_username,
            admin=False,
            admin_source="NONE",
            requested_by=interaction.user.display_name,
            reason="/admin remove",
        )
        any_changed = any_changed or changed

    target_names = ", ".join(f"`{bot._player_name(item)}`" for item in target_players)
    await interaction.response.send_message(
        f"Removed admin for {target_names}"
        if any_changed or role_changed
        else "No admin state was changed"
    )


async def cmd_add_admin(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    if not await bot._claim_mutation(
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

    linked_players = await bot.find_players_by_discord_id(player.discord_id)
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

    role_changed = await bot.set_discord_admin_role(
        discord_id=player.discord_id,
        should_have_role=True,
        reason=f"/admin add by {interaction.user.display_name}",
    )

    any_changed = False
    for target_player in target_players:
        target_uuid = str(target_player.uuid or "").strip()
        matched, changed = await bot.set_admin_access(
            uuid=target_uuid,
            is_admin=True,
            admin_source="DISCORD_ROLE",
        )
        if not matched:
            continue
        await bot.publish_discord_admin_access_changed(
            player_uuid=target_uuid,
            player_pid=target_player.pid,
            discord_id=target_player.discord_id or "",
            discord_username=target_player.discord_username,
            admin=True,
            admin_source="DISCORD_ROLE",
            requested_by=interaction.user.display_name,
            reason="/admin add",
        )
        any_changed = any_changed or changed

    target_names = ", ".join(f"`{bot._player_name(item)}`" for item in target_players)
    await interaction.response.send_message(
        f"Granted admin for {target_names}"
        if any_changed or role_changed
        else "No admin state was changed"
    )


async def cmd_list_admins(bot: "XCoreDiscordBot", interaction: Interaction) -> None:
    players = await bot.find_discord_admin_players()
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
                name=bot._player_name(player),
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

    await bot._send_paginated(
        interaction,
        fetch_page,
        ephemeral=False,
    )


async def cmd_sync_admins(bot: "XCoreDiscordBot", interaction: Interaction) -> None:
    result = await bot.reconcile_discord_admin_access()
    applied_count = int(cast(int, result["applied"]))
    revoked_count = int(cast(int, result["revoked"]))
    discord_admin_count = int(cast(int, result["discord_admins"]))
    embed = discord.Embed(
        title="Admin Reconcile Complete",
        color=discord.Color.blurple(),
        description=(
            f"Applied: **{applied_count}**\n"
            f"Revoked: **{revoked_count}**\n"
            f"Discord role members: **{discord_admin_count}**"
        ),
    )

    applied_players = cast(list[dict[str, object]], result.get("applied_players", []))
    if applied_players:
        applied_items = [
            _format_reconcile_player_item(item) for item in applied_players
        ]
        _add_embed_section(embed, name="Added", items=applied_items)

    revoked_players = cast(list[dict[str, object]], result.get("revoked_players", []))
    if revoked_players:
        revoked_items = [
            _format_reconcile_player_item(item) for item in revoked_players
        ]
        _add_embed_section(embed, name="Revoked", items=revoked_items)

    skipped = cast(list[dict[str, str]], result.get("skipped", []))
    if skipped:
        skipped_items = [_format_reconcile_skipped_item(item) for item in skipped]
        _add_embed_section(embed, name="Skipped", items=skipped_items)

    await interaction.response.send_message(
        embed=embed,
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
        await bot.publish_player_password_reset(uuid_value=uuid_value)
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
    admin_discord_id: str | None,
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
    admin_value = _format_admin_value(
        admin_name=safe_admin_name,
        admin_discord_id=admin_discord_id,
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
    embed.add_field(name="Admin", value=admin_value, inline=False)
    embed.add_field(name="Reason", value=safe_reason, inline=False)
    embed.add_field(name="Expires", value=unban_value, inline=False)
    await channel.send(embed=embed)


async def post_mute_log(
    bot: "XCoreDiscordBot",
    *,
    pid: int,
    name: str,
    admin_name: str,
    admin_discord_id: str | None,
    reason: str,
    expire: datetime,
) -> None:
    from .bot import strip_mindustry_colors

    mutes_channel_id = bot.mutes_channel_id
    if not mutes_channel_id:
        return

    channel = await bot._resolve_messageable_channel(
        mutes_channel_id, context="mute logs"
    )
    if channel is None:
        return

    safe_name = strip_mindustry_colors(str(name).replace("`", "")).strip() or "Unknown"
    safe_admin_name = (
        strip_mindustry_colors(str(admin_name).replace("`", "")).strip() or "Unknown"
    )
    admin_value = _format_admin_value(
        admin_name=safe_admin_name,
        admin_discord_id=admin_discord_id,
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
    unmute_value = (
        f"{discord.utils.format_dt(expire_utc, style='f')} "
        f"({discord.utils.format_dt(expire_utc, style='R')})"
    )

    embed = discord.Embed(title="Mute Issued", color=discord.Color.orange())
    embed.add_field(name="Violator", value=violator_value, inline=False)
    embed.add_field(name="Admin", value=admin_value, inline=False)
    embed.add_field(name="Reason", value=safe_reason, inline=False)
    embed.add_field(name="Expires", value=unmute_value, inline=False)
    await channel.send(embed=embed)


async def post_vote_kick_log(
    bot: "XCoreDiscordBot",
    *,
    target_name: str,
    target_pid: int | None,
    starter_name: str,
    starter_pid: int | None,
    starter_discord_id: str | None,
    reason: str,
    votes_for: list[VoteKickParticipant],
    votes_against: list[VoteKickParticipant],
) -> None:
    from .bot import strip_mindustry_colors

    votekicks_channel_id = bot.votekicks_channel_id
    if not votekicks_channel_id:
        return

    channel = await bot._resolve_messageable_channel(
        votekicks_channel_id, context="vote-kick logs"
    )
    if channel is None:
        return

    safe_target_name = (
        strip_mindustry_colors(str(target_name).replace("`", "")).strip() or "Unknown"
    )
    safe_starter_name = (
        strip_mindustry_colors(str(starter_name).replace("`", "")).strip() or "Unknown"
    )
    safe_reason = strip_mindustry_colors(str(reason).replace("`", "")).strip()
    if not safe_reason:
        safe_reason = "No reason provided"

    target_value = _format_vote_kick_party_value(
        name=safe_target_name,
        pid=target_pid,
    )
    starter_value = _format_vote_kick_party_value(
        name=safe_starter_name,
        pid=starter_pid,
    )
    starter_mention = str(starter_discord_id or "").strip()
    if starter_mention:
        starter_value = f"{starter_value} (<@{starter_mention}> / {starter_mention})"

    embed = discord.Embed(title="Vote-kick Passed", color=discord.Color.blurple())
    embed.add_field(name="Target", value=target_value, inline=False)
    embed.add_field(name="Initiator", value=starter_value, inline=False)
    embed.add_field(name="Reason", value=safe_reason, inline=False)

    yes_items = [_format_vote_kick_participant(item) for item in votes_for]
    no_items = [_format_vote_kick_participant(item) for item in votes_against]

    if yes_items:
        _add_embed_section(
            embed,
            name=f"For ({len(yes_items)})",
            items=yes_items,
            separator="\n",
        )
    else:
        embed.add_field(name="For (0)", value="None", inline=False)

    if no_items:
        _add_embed_section(
            embed,
            name=f"Against ({len(no_items)})",
            items=no_items,
            separator="\n",
        )
    else:
        embed.add_field(name="Against (0)", value="None", inline=False)

    await channel.send(embed=embed)


def _format_admin_value(*, admin_name: str, admin_discord_id: str | None) -> str:
    discord_id = str(admin_discord_id or "").strip()
    return f"{admin_name} (<@{discord_id}>)" if discord_id else admin_name
