"""The embeds posted to the ban, mute and vote-kick log channels."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import TYPE_CHECKING

import discord
from xcore_protocol.generated.shared import VoteKickParticipantV1

from .cases.cards import case_secrets, safe_text
from .dto import record_identifiers
from .player_pids import is_assigned, or_none
from .utils.mindustry_colors import strip_mindustry_colors

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot

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


def add_embed_section(
    embed: discord.Embed, *, name: str, items: list[str], separator: str = ", "
) -> None:
    for index, chunk in enumerate(
        _split_embed_field_chunks(items, separator=separator), start=1
    ):
        field_name = name if index == 1 else f"{name} (cont. {index})"
        embed.add_field(name=field_name, value=chunk, inline=False)


def format_vote_kick_party_value(*, name: str, pid: int | None) -> str:
    safe_name = str(name).strip() or "Unknown"
    return f"{safe_name} (pid={pid})" if is_assigned(pid) else safe_name


def _format_vote_kick_participant(item: VoteKickParticipantV1) -> str:
    name = (
        strip_mindustry_colors(str(item.playerName).replace("`", "")).strip()
        or "Unknown"
    )
    segments = [f"`{name}`"]
    if item.playerPid is not None and item.playerPid > 0:
        segments.append(f"pid={item.playerPid}")
    discord_id = str(item.discordId or "").strip()
    if discord_id:
        segments.append(f"<@{discord_id}>")
    return (
        f"{segments[0]} ({', '.join(segments[1:])})"
        if len(segments) > 1
        else segments[0]
    )


async def _case_log_secrets(
    bot, number: int, supplied: tuple[str, ...], actor_id: str | None
) -> tuple[str, ...]:
    service = bot.container.cases
    if service is None:
        return supplied
    case = await service.get(number)
    secrets = [*supplied, *case_secrets(case)]
    if service.players is not None:
        for uuid in case_secrets(case):
            player = await service.players.find_player_by_uuid(uuid)
            if player is not None and player.ip:
                secrets.append(player.ip)
        if actor_id:
            for player in await service.players.find_players_by_discord_id(actor_id):
                secrets.extend((player.uuid or "", player.ip or ""))
    return tuple(secrets)


async def _log_texts(
    bot: XCoreDiscordBot,
    *,
    name: str,
    admin_name: str,
    admin_discord_id: str | None,
    reason: str,
    case_number: int | None,
    privacy_secrets: tuple[str, ...],
) -> tuple[str, str, str]:
    """The violator, admin and reason of a ban or mute log, with a case's secrets masked."""
    if case_number is None:
        safe_name = (
            strip_mindustry_colors(str(name).replace("`", "")).strip() or "Unknown"
        )
        safe_admin_name = (
            strip_mindustry_colors(str(admin_name).replace("`", "")).strip()
            or "Unknown"
        )
        safe_reason = strip_mindustry_colors(str(reason).replace("`", "")).strip()
    else:
        secrets = await _case_log_secrets(
            bot, case_number, privacy_secrets, admin_discord_id
        )
        safe_name = safe_text(name.replace("`", ""), secrets=secrets) or "Unknown"
        safe_admin_name = (
            safe_text(admin_name.replace("`", ""), secrets=secrets) or "Unknown"
        )
        safe_reason = safe_text(reason.replace("`", ""), secrets=secrets)
    admin_value = format_admin_value(
        admin_name=safe_admin_name, admin_discord_id=admin_discord_id
    )
    return safe_name, admin_value, safe_reason or "No reason provided"


async def post_ban_log(
    bot: XCoreDiscordBot,
    *,
    pid: int | None,
    name: str,
    admin_name: str,
    admin_discord_id: str | None,
    reason: str,
    expire: datetime,
    ban_id: int | None = None,
    case_number: int | None = None,
    title: str = "Ban Issued",
    privacy_secrets: tuple[str, ...] = (),
) -> bool:
    """Return whether a log was sent; legacy command callers may ignore the receipt."""
    bans_channel_id = bot.settings.discord_bans_channel_id
    if not bans_channel_id:
        return False

    channel = await bot.resolve_channel(bans_channel_id, context="ban logs")
    if channel is None:
        return False

    # Old protocol events provide only a pid. New consumers can pass ban_id directly.
    if ban_id is None and is_assigned(pid):
        player = await bot.store.find_player_by_pid(pid)
        if player is not None:
            uuid_value, ip_value = record_identifiers(player)
            if uuid_value is not None or ip_value is not None:
                ban = await bot.store.ensure_ban_id(uuid=uuid_value or "", ip=ip_value)
                if ban is not None:
                    ban_id = ban.ban_id
                    if case_number is None:
                        case_number = ban.case_number

    safe_name, admin_value, safe_reason = await _log_texts(
        bot,
        name=name,
        admin_name=admin_name,
        admin_discord_id=admin_discord_id,
        reason=reason,
        case_number=case_number,
        privacy_secrets=privacy_secrets,
    )

    safe_pid = or_none(pid)
    violator_value = (
        f"{safe_name} (pid={safe_pid})" if safe_pid is not None else safe_name
    )
    expire_utc = expire.replace(tzinfo=UTC) if expire.tzinfo is None else expire
    unban_value = (
        f"{discord.utils.format_dt(expire_utc, style='f')} "
        f"({discord.utils.format_dt(expire_utc, style='R')})"
    )

    embed = discord.Embed(title=title, color=discord.Color.red())
    if ban_id is not None:
        embed.add_field(name="Ban", value=f"B-{ban_id}", inline=False)
    if case_number is not None:
        embed.add_field(name="Case", value=f"#{case_number}", inline=False)
    embed.add_field(name="Violator", value=violator_value, inline=False)
    embed.add_field(name="Admin", value=admin_value, inline=False)
    embed.add_field(name="Reason", value=safe_reason, inline=False)
    embed.add_field(name="Expires", value=unban_value, inline=False)
    await channel.send(embed=embed)
    return True


async def post_mute_log(
    bot: XCoreDiscordBot,
    *,
    pid: int,
    name: str,
    admin_name: str,
    admin_discord_id: str | None,
    reason: str,
    expire: datetime,
    case_number: int | None = None,
    privacy_secrets: tuple[str, ...] = (),
) -> None:
    mutes_channel_id = bot.settings.discord_mutes_channel_id
    if not mutes_channel_id:
        return

    channel = await bot.resolve_channel(mutes_channel_id, context="mute logs")
    if channel is None:
        return

    safe_name, admin_value, safe_reason = await _log_texts(
        bot,
        name=name,
        admin_name=admin_name,
        admin_discord_id=admin_discord_id,
        reason=reason,
        case_number=case_number,
        privacy_secrets=privacy_secrets,
    )

    safe_pid = or_none(pid)
    violator_value = (
        f"{safe_name} (pid={safe_pid})" if safe_pid is not None else safe_name
    )
    expire_utc = expire.replace(tzinfo=UTC) if expire.tzinfo is None else expire
    unmute_value = (
        f"{discord.utils.format_dt(expire_utc, style='f')} "
        f"({discord.utils.format_dt(expire_utc, style='R')})"
    )

    embed = discord.Embed(title="Mute Issued", color=discord.Color.orange())
    if case_number is not None:
        embed.add_field(name="Case", value=f"#{case_number}", inline=False)
    embed.add_field(name="Violator", value=violator_value, inline=False)
    embed.add_field(name="Admin", value=admin_value, inline=False)
    embed.add_field(name="Reason", value=safe_reason, inline=False)
    embed.add_field(name="Expires", value=unmute_value, inline=False)
    await channel.send(embed=embed)


async def post_vote_kick_log(
    bot: XCoreDiscordBot,
    *,
    target_name: str,
    target_pid: int | None,
    starter_name: str,
    starter_pid: int | None,
    starter_discord_id: str | None,
    reason: str,
    votes_for: list[VoteKickParticipantV1],
    votes_against: list[VoteKickParticipantV1],
) -> None:
    votekicks_channel_id = bot.settings.discord_votekicks_channel_id
    if not votekicks_channel_id:
        return

    channel = await bot.resolve_channel(votekicks_channel_id, context="vote-kick logs")
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

    target_value = format_vote_kick_party_value(
        name=safe_target_name,
        pid=target_pid,
    )
    starter_value = format_vote_kick_party_value(
        name=safe_starter_name,
        pid=starter_pid,
    )
    starter_mention = str(starter_discord_id or "").strip()
    if starter_mention:
        starter_value = f"{starter_value} (<@{starter_mention}>)"

    embed = discord.Embed(title="Vote-kick Passed", color=discord.Color.blurple())
    embed.add_field(name="Target", value=target_value, inline=False)
    embed.add_field(name="Initiator", value=starter_value, inline=False)
    embed.add_field(name="Reason", value=safe_reason, inline=False)

    yes_items = [_format_vote_kick_participant(item) for item in votes_for]
    no_items = [_format_vote_kick_participant(item) for item in votes_against]

    if yes_items:
        add_embed_section(
            embed,
            name=f"For ({len(yes_items)})",
            items=yes_items,
            separator="\n",
        )
    else:
        embed.add_field(name="For (0)", value="None", inline=False)

    if no_items:
        add_embed_section(
            embed,
            name=f"Against ({len(no_items)})",
            items=no_items,
            separator="\n",
        )
    else:
        embed.add_field(name="Against (0)", value="None", inline=False)

    await channel.send(embed=embed)


def format_admin_value(*, admin_name: str, admin_discord_id: str | None) -> str:
    discord_id = str(admin_discord_id or "").strip()
    return f"{admin_name} (<@{discord_id}>)" if discord_id else admin_name
