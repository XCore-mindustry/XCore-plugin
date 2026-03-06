from __future__ import annotations

import secrets
import time
from typing import TYPE_CHECKING

import discord
from discord import Interaction

from .dto import PlayerRecord
from .moderation_views import MapRemoveConfirmView, StatsActionsView
from .modal_factories import create_stats_ban_modal, create_stats_mute_modal
from .presentation import (
    build_servers_embed,
    build_stats_title,
    format_ban_expire_date,
    format_epoch_millis,
    format_hexed_rank_block,
    format_minutes,
    format_size,
)
from .retry import retry_read_rpc
from .server_views import PaginatorView, ServersView

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


async def cmd_stats(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    nickname = bot._player_name(player)
    custom_nickname = str(player.custom_nickname or "").strip()
    title = build_stats_title(nickname, custom_nickname)

    rank_label, rank_progress = format_hexed_rank_block(
        rank_value=player.hexed_rank,
        points=player.hexed_points,
    )

    embed = discord.Embed(title=title, color=discord.Color.blurple())
    embed.add_field(
        name="Identity",
        value=(f"PID: `{player.pid}`\nNickname: `{nickname}`"),
        inline=False,
    )
    embed.add_field(
        name="Progress",
        value=(
            f"Playtime: `{format_minutes(player.total_play_time)}`\n"
            f"PvP rating: `{player.pvp_rating}`\n"
            f"Hexed rank: `{rank_label}`\n"
            f"Hexed progress: `{rank_progress}`"
        ),
        inline=False,
    )

    admin_status = "✅" if player.is_admin else "❌"
    admin_confirmed = "✅" if player.admin_confirmed else "❌"
    embed.add_field(
        name="Permissions",
        value=(f"Admin: {admin_status}\nAdmin confirmed: {admin_confirmed}"),
        inline=False,
    )

    created_at = format_epoch_millis(player.created_at)
    updated_at = format_epoch_millis(player.updated_at)
    embed.set_footer(text=f"Created: {created_at} • Updated: {updated_at}")

    view = StatsActionsView(
        settings=bot.settings,
        player_id=player_id,
        player=_player_record_as_mapping(player),
        create_ban_modal=lambda **kwargs: create_stats_ban_modal(bot, **kwargs),
        create_mute_modal=lambda **kwargs: create_stats_mute_modal(bot, **kwargs),
    )
    await interaction.response.send_message(embed=embed, view=view)
    view.message = await interaction.original_response()


async def cmd_servers(bot: "XCoreDiscordBot", interaction: Interaction) -> None:
    view = ServersView(bot=bot, sort_mode="players")
    servers = bot._sort_live_servers(bot._get_live_servers(), view.sort_mode)
    embed = build_servers_embed(servers, sort_mode=view.sort_mode)
    await interaction.response.send_message(embed=embed, view=view)
    view.bot_message = await interaction.original_response()


async def cmd_search(
    bot: "XCoreDiscordBot",
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
                embed.add_field(
                    name=row.nickname,
                    value=f"ID: {row.pid} | playtime: {row.total_play_time}m",
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
        "total_play_time": player.total_play_time,
        "pvp_rating": player.pvp_rating,
        "hexed_rank": player.hexed_rank,
        "hexed_points": player.hexed_points,
        "is_admin": player.is_admin,
        "admin_confirmed": player.admin_confirmed,
        "created_at": player.created_at,
        "updated_at": player.updated_at,
    }


async def cmd_bans(
    bot: "XCoreDiscordBot",
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
                embed.add_field(
                    name=ban.name,
                    value=(
                        f"Admin: {ban.admin_name}\n"
                        f"Reason: {ban.reason}\n"
                        f"Unban: {unban_date}"
                    ),
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
    bot: "XCoreDiscordBot",
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

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        start = page * page_size
        end = start + page_size
        chunk = maps[start:end]

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

                file_part = f" (`{file_name}`)" if file_name else ""
                size_part = ""
                if file_size_raw.isdigit():
                    size_part = f" • {format_size(int(file_size_raw))}"

                dims_part = (
                    f" • {width}x{height}"
                    if width.isdigit() and height.isdigit()
                    else ""
                )
                lines.append(
                    f"- {name}{file_part} — by `{author}`{dims_part}{size_part}"
                )
            embed.description = "\n".join(lines)
        else:
            embed.description = "No maps found."
        has_next = end < len(maps)
        total_pages = max(1, (len(maps) + page_size - 1) // page_size)
        embed.set_footer(
            text=(
                f"Page {page + 1}/{total_pages} • total maps: {len(maps)} "
                f"• entries on page: {len(chunk)}"
            )
        )
        return embed, has_next

    first_embed, has_next = await fetch_page(0)
    view = PaginatorView(
        page=0, has_prev=False, has_next=has_next, fetch_page=fetch_page
    )
    sent = await interaction.followup.send(embed=first_embed, view=view)
    view.bot_message = sent


async def get_cached_maps(bot: "XCoreDiscordBot", server: str) -> list[dict[str, str]]:
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
    bot: "XCoreDiscordBot",
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
    bot: "XCoreDiscordBot",
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
    bot: "XCoreDiscordBot",
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
