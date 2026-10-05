from __future__ import annotations

import logging
import secrets
import time
from collections.abc import Awaitable
from typing import TYPE_CHECKING, TypeVar

import discord
from discord import Interaction

from .dto import PlayerRecord
from .game_stats import GameStats
from .modal_factories import create_stats_ban_modal, create_stats_mute_modal
from .moderation_views import MapRemoveConfirmView, StatsActionsView
from .permissions import admin_role_ids, has_any_role, settings_from_interaction
from .player_pids import is_assigned
from .presentation import (
    build_servers_embed,
    format_ban_expire_date,
    format_epoch_millis,
    format_minutes,
    format_size,
)
from .rating_store import Placing
from .retry import retry_read_rpc
from .server_views import MapsListView, ServersView
from .stats_embed import StaffNotes, StatsView, build_stats_embed, player_name

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


logger = logging.getLogger(__name__)

T = TypeVar("T")


def _format_admin_label(*, admin_name: str, admin_discord_id: str | None) -> str:
    discord_id = str(admin_discord_id or "").strip()
    return f"{admin_name} (<@{discord_id}>)" if discord_id else admin_name


def _map_rating_part(item: dict[str, str]) -> str:
    like = str(item.get("like", "")).strip()
    dislike = str(item.get("dislike", "")).strip()
    reputation = str(item.get("reputation", "")).strip()

    parts: list[str] = []
    if like.lstrip("-").isdigit() or dislike.lstrip("-").isdigit():
        like_value = like if like.lstrip("-").isdigit() else "0"
        dislike_value = dislike if dislike.lstrip("-").isdigit() else "0"
        parts.append(f"👍 {like_value}")
        parts.append(f"👎 {dislike_value}")

    if reputation.lstrip("-").isdigit():
        parts.append(f"rep {reputation}")

    return f" • {' • '.join(parts)}" if parts else ""


def _map_int_value(item: dict[str, str], key: str) -> int:
    value = str(item.get(key, "")).strip()
    return int(value) if value.lstrip("-").isdigit() else -(10**9)


def _map_float_value(item: dict[str, str], key: str) -> float:
    value = str(item.get(key, "")).strip()
    try:
        return float(value)
    except ValueError:
        return float("-inf")


def _sort_maps(maps: list[dict[str, str]], mode: str) -> list[dict[str, str]]:
    if mode == "name":
        return sorted(maps, key=lambda item: str(item.get("name", "")).lower())
    if mode == "popularity":
        return sorted(
            maps,
            key=lambda item: (
                -_map_float_value(item, "popularity"),
                -_map_int_value(item, "reputation"),
                str(item.get("name", "")).lower(),
            ),
        )
    return sorted(
        maps,
        key=lambda item: (
            -_map_int_value(item, "reputation"),
            -_map_int_value(item, "like"),
            str(item.get("name", "")).lower(),
        ),
    )


async def _loaded(awaitable: Awaitable[T], what: str, player: PlayerRecord) -> T | None:
    """A section of the stats, or ``None`` when it cannot be read: the rest still opens."""
    try:
        return await awaitable
    except Exception:
        logger.exception("Cannot load %s for player #%s", what, player.pid)
        return None


async def _staff_notes(bot: XCoreDiscordBot, player: PlayerRecord) -> StaffNotes:
    if not player.uuid:
        return StaffNotes()
    try:
        return StaffNotes(
            ban=await bot.find_ban(uuid=player.uuid, ip=player.ip),
            mute=await bot.find_mute(uuid=player.uuid),
        )
    except Exception:
        logger.exception("Cannot load punishments for player #%s", player.pid)
        return StaffNotes(loaded=False)


def _avatar_url(
    interaction: Interaction, player: PlayerRecord, member: object | None
) -> str | None:
    """The avatar of the Discord account the player is linked to, when it is at hand."""
    discord_id = str(player.discord_id or "").strip()
    if not discord_id.isdigit():
        return None
    if member is None or getattr(member, "id", None) != int(discord_id):
        guild = getattr(interaction, "guild", None)
        member = guild.get_member(int(discord_id)) if guild is not None else None
    avatar = getattr(member, "display_avatar", None)
    return str(avatar.url) if avatar is not None else None


async def _linked_accounts(
    bot: XCoreDiscordBot, interaction: Interaction, member: object
) -> list[PlayerRecord] | None:
    """The game accounts of a Discord user, most played first; replies when there are none."""
    players = await bot.find_players_by_discord_id(str(getattr(member, "id", "")))
    if players:
        return sorted(players, key=lambda row: row.total_play_time, reverse=True)
    own = getattr(member, "id", None) == interaction.user.id
    await interaction.response.send_message(
        (
            "You have no linked game account. Get a code in the game and use `/link`, "
            "or pass a player ID."
            if own
            else "That user has no linked game account."
        ),
        ephemeral=True,
    )
    return None


async def cmd_stats(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int | None = None,
    user: discord.abc.User | None = None,
) -> None:
    settings = settings_from_interaction(interaction)
    is_admin_viewer = settings is not None and has_any_role(
        interaction.user,
        admin_role_ids(settings),
    )

    member: discord.abc.User | None = None
    other_accounts: list[PlayerRecord] = []
    if player_id is not None:
        player = await bot._get_player_or_reply(interaction, player_id)
        if player is None:
            return
    else:
        member = user or interaction.user
        if member.id != interaction.user.id and not is_admin_viewer:
            await interaction.response.send_message(
                "Only admins can look a player up by their Discord account. "
                "Pass a player ID instead.",
                ephemeral=True,
            )
            return
        accounts = await _linked_accounts(bot, interaction, member)
        if accounts is None:
            return
        player, other_accounts = accounts[0], accounts[1:]

    uuid = player.uuid or ""
    embed = build_stats_embed(
        StatsView(
            player=player,
            placings=(
                await _loaded(_placings(bot, uuid), "season ratings", player)
                if uuid
                else []
            ),
            games=await _loaded(_game_stats(bot, uuid), "game stats", player),
            staff=await _staff_notes(bot, player) if is_admin_viewer else None,
            show_discord=is_admin_viewer
            or str(player.discord_id or "") == str(interaction.user.id),
            avatar_url=_avatar_url(interaction, player, member),
            other_accounts=other_accounts,
        )
    )

    if not is_admin_viewer:
        await interaction.response.send_message(embed=embed)
        return

    view = StatsActionsView(
        settings=bot.settings,
        player_id=player.pid,
        player=_player_record_as_mapping(player),
        create_ban_modal=lambda **kwargs: create_stats_ban_modal(bot, **kwargs),
        create_mute_modal=lambda **kwargs: create_stats_mute_modal(bot, **kwargs),
        open_target_audit=lambda interaction, pid, player_mapping: cmd_stats_audit(
            bot, interaction, pid, player_mapping
        ),
        open_actor_audit=lambda interaction, pid, player_mapping: cmd_stats_audit(
            bot, interaction, pid, player_mapping, mode="actor"
        ),
    )
    await interaction.response.send_message(embed=embed, view=view)
    view.message = await interaction.original_response()


# The container is reached inside the coroutine, so a bot without one is a failed load too.
async def _placings(bot: XCoreDiscordBot, uuid: str) -> list[Placing]:
    return await bot.container.ratings.placings(uuid)


async def _game_stats(bot: XCoreDiscordBot, uuid: str) -> GameStats:
    return await bot.container.game_stats.overview(uuid)


def _summarize_audit_reason(reason: str | None) -> str:
    normalized = str(reason or "Not Specified").strip() or "Not Specified"
    return normalized if len(normalized) <= 80 else normalized[:77] + "..."


def _format_audit_entry_name(action: str, occurred_at: object) -> str:
    when = (
        format_epoch_millis(occurred_at)
        if isinstance(occurred_at, (int, float))
        else str(occurred_at or "Unknown time")
    )
    return f"{action} • {when}"


async def cmd_stats_audit(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: int,
    player: dict[str, object],
    mode: str = "target",
) -> None:
    page_size = 6
    nickname = str(player.get("nickname") or "Unknown")
    if mode == "actor":
        actor_discord_id = str(player.get("discord_id") or "").strip() or None
        actor_id = nickname
        total_entries = await bot.count_audit_for_actor(
            actor_id=actor_id,
            actor_discord_id=actor_discord_id,
        )
    else:
        uuid_value = str(player.get("uuid") or "").strip()
        if not uuid_value:
            await interaction.response.send_message(
                "Cannot open audit: UUID is missing in player data.",
                ephemeral=True,
            )
            return
        total_entries = await bot.count_audit_for_player(uuid=uuid_value)
    total_pages = max(1, (total_entries + page_size - 1) // page_size)

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        if mode == "actor":
            rows = await bot.list_audit_for_actor(
                actor_id=actor_id,
                actor_discord_id=actor_discord_id,
                limit=page_size,
                page=page,
            )
        else:
            rows = await bot.list_audit_for_player(
                uuid=uuid_value, limit=page_size, page=page
            )
        embed = discord.Embed(
            title=f"{'Actions' if mode == 'actor' else 'History'} • {nickname}",
            color=discord.Color.orange() if rows else discord.Color.red(),
        )
        if rows:
            for row in rows:
                counterpart = (
                    str(row.target_name or "Unknown")
                    if mode == "actor"
                    else str(row.actor_name or "Unknown")
                )
                embed.add_field(
                    name=_format_audit_entry_name(row.action, row.occurred_at),
                    value=(
                        f"{'Target' if mode == 'actor' else 'Actor'}: `{counterpart}`\n"
                        f"Reason: `{_summarize_audit_reason(row.reason)}`\n"
                        f"Audit ID: `{row.audit_id}`"
                    ),
                    inline=False,
                )
        else:
            embed.description = (
                "No audit actions found."
                if mode == "actor"
                else "No audit entries found."
            )

        has_next = len(rows) == page_size
        embed.set_footer(
            text=(
                f"Page {page + 1}/{total_pages} • total entries: {total_entries} "
                f"• entries on page: {len(rows)}"
            )
        )
        return embed, has_next

    await bot._send_paginated(interaction, fetch_page, ephemeral=True)


async def cmd_servers(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    view = ServersView(bot=bot, sort_mode="players")
    servers = bot._sort_live_servers(bot._get_live_servers(), view.sort_mode)
    embed = build_servers_embed(servers, sort_mode=view.sort_mode)
    await interaction.response.send_message(embed=embed, view=view)
    view.bot_message = await interaction.original_response()


async def cmd_search(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    name: str,
) -> None:
    page_size = 6
    total_matches = await bot.count_players_by_name(name)
    total_pages = max(1, (total_matches + page_size - 1) // page_size)

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        rows = await bot.search_players(name, limit=page_size, page=page)
        embed = discord.Embed(
            title=f"Search: '{name}'",
            color=discord.Color.green() if rows else discord.Color.red(),
        )
        if rows:
            for row in rows:
                facts = [f"`#{row.pid}`"]
                if row.username:
                    facts.append(f"`@{row.username}`")
                facts.append(f"played `{format_minutes(row.total_play_time)}`")
                if row.online:
                    facts.append("🟢 online")
                embed.add_field(
                    name=player_name(row),
                    value=" · ".join(facts),
                    inline=False,
                )
        else:
            embed.description = "Players not found."
        has_next = len(rows) == page_size
        embed.set_footer(
            text=(
                f"Page {page + 1}/{total_pages} • total matches: {total_matches} "
                f"• entries on page: {len(rows)}"
            )
        )
        return embed, has_next

    await bot._send_paginated(interaction, fetch_page)


def _player_record_as_mapping(player: PlayerRecord) -> dict[str, object]:
    return {
        "pid": player.pid,
        "nickname": player.nickname,
        "uuid": player.uuid,
        "ip": player.ip,
        "last_ip": player.last_ip,
        "custom_nickname": player.custom_nickname,
        "description": player.description,
        "language": player.language,
        "translator_language": player.translator_language,
        "total_play_time": player.total_play_time,
        "hexed_rank": player.hexed_rank,
        "hexed_points": player.hexed_points,
        "leaderboard": player.leaderboard,
        "unlocked_badges": player.unlocked_badges,
        "active_badge": player.active_badge,
        "blocked_private_uuids": player.blocked_private_uuids,
        "is_admin": player.is_admin,
        "admin_source": player.admin_source,
        "discord_id": player.discord_id,
        "created_at": player.created_at,
        "updated_at": player.updated_at,
    }


async def cmd_bans(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    name_filter: str | None,
) -> None:
    page_size = 6
    total_bans = await bot.count_bans(name_filter=name_filter)
    total_pages = max(1, (total_bans + page_size - 1) // page_size)

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        bans = await bot.list_bans(name_filter=name_filter, limit=page_size, page=page)
        embed = discord.Embed(
            title="Bans",
            color=discord.Color.green() if bans else discord.Color.red(),
        )
        if bans:
            for ban in bans:
                unban_date = format_ban_expire_date(ban.expire_date)
                ban_value = (
                    (f"PID: `{ban.pid}`\n" if is_assigned(ban.pid) else "")
                    + f"Admin: {_format_admin_label(admin_name=ban.admin_name, admin_discord_id=ban.admin_discord_id)}\n"
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

    await bot._send_paginated(interaction, fetch_page)


async def cmd_maps(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
) -> None:
    await interaction.response.defer()
    try:
        maps = await bot.rpc_maps_list(server=server, timeout_ms=bot.rpc_timeout_ms)
    except TimeoutError:
        await interaction.followup.send("No response from target server (timeout).")
        return

    if not maps:
        await interaction.followup.send(f"No maps found on server `{server}`")
        return

    page_size = 15
    default_sort_mode = "reputation"

    async def fetch_page(page: int, sort_mode: str) -> tuple[discord.Embed, bool]:
        sorted_maps = _sort_maps(maps, sort_mode)
        start = page * page_size
        end = start + page_size
        chunk = sorted_maps[start:end]

        embed = discord.Embed(
            title=f"Maps on `{server}`",
            color=discord.Color.green(),
        )
        if chunk:
            lines: list[str] = []
            for item in chunk:
                name = item.get("name", "Unknown")
                file_name = item.get("file_name", "")
                author = item.get("author", "Unknown")
                width = str(item.get("width", "")).strip()
                height = str(item.get("height", "")).strip()
                file_size_raw = str(item.get("file_size_bytes", "")).strip()

                size_part = ""
                if file_size_raw.isdigit():
                    size_part = f" • {format_size(int(file_size_raw))}"

                dims_part = (
                    f" • {width}x{height}"
                    if width.isdigit() and height.isdigit()
                    else ""
                )
                rating_part = _map_rating_part(item)
                lines.append(
                    f"• {name} — by `{author}`\n"
                    f"  `{file_name}`{dims_part}{size_part}{rating_part}"
                    if file_name
                    else f"• {name} — by `{author}`{dims_part}{size_part}{rating_part}"
                )
            embed.description = "\n".join(lines)
        else:
            embed.description = "No maps found."
        has_next = end < len(sorted_maps)
        total_pages = max(1, (len(sorted_maps) + page_size - 1) // page_size)
        embed.set_footer(
            text=(
                f"Sort: {sort_mode} • Page {page + 1}/{total_pages} "
                f"• total maps: {len(sorted_maps)} • entries on page: {len(chunk)}"
            )
        )
        return embed, has_next

    first_embed, has_next = await fetch_page(0, default_sort_mode)
    view = MapsListView(
        page=0,
        has_prev=False,
        has_next=has_next,
        sort_mode=default_sort_mode,
        fetch_page=fetch_page,
    )
    sent = await interaction.followup.send(embed=first_embed, view=view)
    view.bot_message = sent


async def get_cached_maps(bot: XCoreDiscordBot, server: str) -> list[dict[str, str]]:
    now = time.monotonic()
    cached = bot._map_cache.get(server)
    if cached is not None:
        ts, maps = cached
        if now - ts < 60:
            return maps

    try:
        maps = await retry_read_rpc(
            lambda: bot.rpc_maps_list(server=server, timeout_ms=3000)
        )
        bot._map_cache[server] = (now, maps)
        return maps
    except TimeoutError:
        return bot._map_cache.get(server, (0.0, []))[1]


async def cmd_remove_map(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
    file_name: str,
) -> None:
    normalized = file_name.strip()
    if not normalized:
        await interaction.response.send_message(
            "Map file name must not be empty.", ephemeral=True
        )
        return

    request_nonce = secrets.token_hex(6)
    view = MapRemoveConfirmView(
        requester_id=interaction.user.id,
        server=server,
        file_name=normalized,
        request_nonce=request_nonce,
        perform_remove_map=lambda **kwargs: perform_remove_map(bot, **kwargs),
    )
    await interaction.response.send_message(
        f"Are you sure you want to remove map file `{normalized}` from `{server}`?",
        view=view,
    )
    view.message = await interaction.original_response()


async def perform_remove_map(
    bot: XCoreDiscordBot,
    *,
    server: str,
    file_name: str,
    request_nonce: str,
) -> str:
    claim_key = f"remove-map:{server}:{file_name}:{request_nonce}"
    if not await bot.claim_idempotency(claim_key, ttl_seconds=600):
        return "This map removal was already processed."

    try:
        result = await bot.rpc_remove_map(
            server=server,
            file_name=file_name,
            timeout_ms=bot.rpc_timeout_ms,
        )
    except TimeoutError:
        return "No response from target server (timeout)."

    return f"🗑️ `{server}` remove-map `{file_name}`: {result}"


async def cmd_upload_map(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
    attachments: list[discord.Attachment | None],
) -> None:
    files = [
        {"url": att.url, "filename": att.filename}
        for att in attachments
        if att is not None and att.filename.lower().endswith(".msav")
    ]

    if not files:
        await interaction.response.send_message(
            "No valid .msav files attached.", ephemeral=True
        )
        return

    if not await bot._claim_mutation(
        interaction,
        operation="upload-map",
        scope=f"{server}:{len(files)}",
    ):
        return

    await bot.publish_maps_load(server=server, files=files)
    await interaction.response.send_message(
        f"Uploaded {len(files)} map(s) to `{server}`: "
        + ", ".join(item["filename"] for item in files)
    )
