from __future__ import annotations

import logging
import re
import secrets
import time
from collections.abc import Awaitable
from typing import TYPE_CHECKING, TypeVar

import discord
from discord import Interaction

from .command_helpers import claim_mutation, get_player_or_reply, send_paginated
from .dto import PlayerRecord
from .game_stats import GameStats
from .modal_factories import create_stats_ban_modal, create_stats_mute_modal
from .moderation_views import MapRemoveConfirmView, StatsActionsView
from .permissions import admin_role_ids, has_any_role, settings_from_interaction
from .player_pids import is_assigned
from .presentation import (
    format_ban_expire_date,
    format_epoch_millis,
    format_minutes,
    format_size,
)
from .rating_store import Placing
from .server_views import MapsListView, ServersView
from .staff_actions import (
    Actor,
    InvalidMapUpload,
    MapUploadFailed,
    MapUploadFile,
    MapUploadTimeout,
    perform_map_upload,
    prepare_map_upload,
)
from .stats_embed import StaffNotes, StatsView, build_stats_embed, player_name
from .utils.mindustry_colors import strip_mindustry_colors

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


def _map_text(value: str, limit: int = 80, *, inline_code: bool = False) -> str:
    text = " ".join(str(value).split()).replace("`", "'")[:limit]
    text = discord.utils.escape_mentions(text)
    return text if inline_code else discord.utils.escape_markdown(text)


def _map_contributor(item: dict[str, str], prefix: str) -> str:
    discord_id = item.get(f"{prefix}_discord_id", "")
    name = _map_text(
        item.get(f"{prefix}_name", "") or item.get(f"{prefix}_handle", "") or discord_id
    )
    name = re.sub(r"([\[\]()])", r"\\\1", name)
    if discord_id.isascii() and discord_id.isdigit():
        return f"[{name}](https://discord.com/users/{discord_id})"
    return name


def _map_list_row(item: dict[str, str]) -> str:
    name = _map_text(strip_mindustry_colors(item.get("name", "Unknown")))
    file_name = _map_text(item.get("file_name", ""), 100, inline_code=True)
    author = _map_text(
        strip_mindustry_colors(item.get("author", "Unknown")), inline_code=True
    )
    width = str(item.get("width", "")).strip()
    height = str(item.get("height", "")).strip()
    file_size = str(item.get("file_size_bytes", "")).strip()
    details = f" • {width}x{height}" if width.isdigit() and height.isdigit() else ""
    if file_size.isdigit():
        details += f" • {format_size(int(file_size))}"
    details += _map_rating_part(item)
    uploaded = bool(item.get("uploaded_by_discord_id"))
    author_label = "file author" if uploaded else "by"
    row = f"• {name} — {author_label} `{author}`"
    row += f"\n  `{file_name}`{details}" if file_name else details
    if uploaded:
        credits = []
        if item.get("creator_discord_id"):
            credits.append(f"🎨 {_map_contributor(item, 'creator')}")
        credits.append(f"⬆ {_map_contributor(item, 'uploaded_by')}")
        row += "\n  " + " • ".join(credits)
        uploaded_at = item.get("uploaded_at", "")
        if uploaded_at.isascii() and uploaded_at.isdigit():
            row += f" • <t:{int(uploaded_at) // 1000}:d>"
    return row


def _map_list_pages(maps: list[dict[str, str]], sort_mode: str) -> list[list[str]]:
    pages: list[list[str]] = [[]]
    length = 0
    for item in _sort_maps(maps, sort_mode):
        row = _map_list_row(item)
        row_length = len(row.encode("utf-16-le")) // 2
        if pages[-1] and (len(pages[-1]) >= 15 or length + row_length + 1 > 3800):
            pages.append([])
            length = 0
        pages[-1].append(row)
        length += row_length + 1
    return pages


async def _loaded(awaitable: Awaitable[T], what: str, player: PlayerRecord) -> T | None:
    """A section of the stats, or ``None`` when it cannot be read: the rest still opens."""
    try:
        return await awaitable
    except Exception:  # the rest of the stats still opens
        logger.exception("Cannot load %s for player #%s", what, player.pid)
        return None


async def _staff_notes(bot: XCoreDiscordBot, player: PlayerRecord) -> StaffNotes:
    if not player.uuid:
        return StaffNotes()
    try:
        return StaffNotes(
            ban=await bot.container.punishments.find_ban(
                uuid=player.uuid, ip=player.ip
            ),
            mute=await bot.container.punishments.find_mute(uuid=player.uuid),
        )
    except Exception:  # the rest of the stats still opens
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
    players = await bot.store.find_players_by_discord_id(str(getattr(member, "id", "")))
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


async def _stats_player(
    bot: XCoreDiscordBot, interaction: Interaction, reference: str | int
) -> PlayerRecord | None:
    text = str(reference).strip()
    pid = text.removeprefix("#")
    if re.fullmatch(r"-?\d+", pid):
        return await get_player_or_reply(bot, interaction, int(pid))
    username = text.removeprefix("@").strip()
    if username:
        player = await bot.store.find_player_by_username(username)
        if player is not None:
            return player
    await interaction.response.send_message(
        "Player not found. Enter a player ID or @username, "
        "or type a nickname and select a player from the suggestions.",
        ephemeral=True,
    )
    return None


async def cmd_stats(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: str | int | None = None,
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
        player = await _stats_player(bot, interaction, player_id)
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
        total_entries = await bot.container.audit.count_audit_for_actor(
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
        total_entries = await bot.container.audit.count_audit_for_player(
            uuid=uuid_value
        )
    total_pages = max(1, (total_entries + page_size - 1) // page_size)

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        if mode == "actor":
            rows = await bot.container.audit.list_audit_for_actor(
                actor_id=actor_id,
                actor_discord_id=actor_discord_id,
                limit=page_size,
                page=page,
            )
        else:
            rows = await bot.container.audit.list_audit_for_player(
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

    await send_paginated(interaction, fetch_page, ephemeral=True)


async def cmd_servers(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    view = ServersView(sort_mode="players")
    await interaction.response.send_message(embed=view.build_embed(), view=view)
    view.bot_message = await interaction.original_response()


async def cmd_search(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    name: str,
) -> None:
    page_size = 6
    total_matches = await bot.store.count_players_by_name(name)
    total_pages = max(1, (total_matches + page_size - 1) // page_size)

    async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
        rows = await bot.store.search_players(name, limit=page_size, page=page)
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

    await send_paginated(interaction, fetch_page)


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

    await send_paginated(interaction, fetch_page)


async def cmd_maps(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
    creator: discord.abc.User | None = None,
) -> None:
    await interaction.response.defer()
    try:
        maps = await bot.container.rpc.list_maps(
            server=server, timeout_ms=bot.settings.rpc_timeout_ms
        )
    except TimeoutError:
        await interaction.followup.send("No response from target server (timeout).")
        return

    if creator is not None:
        maps = [
            item for item in maps if item.get("creator_discord_id") == str(creator.id)
        ]
    if not maps:
        await interaction.followup.send(
            f"No maps found on server `{server}`"
            + (" for this creator." if creator else "")
        )
        return

    default_sort_mode = "reputation"

    async def fetch_page(page: int, sort_mode: str) -> tuple[discord.Embed, bool]:
        pages = _map_list_pages(maps, sort_mode)
        chunk = pages[page]
        embed = discord.Embed(
            title=f"Maps on `{server}`",
            description="\n".join(chunk),
            color=discord.Color.green(),
        )
        embed.set_footer(
            text=(
                f"Sort: {sort_mode} • Page {page + 1}/{len(pages)} "
                f"• total maps: {len(maps)} • entries on page: {len(chunk)}"
                + (
                    " • 🎨 declared creator • ⬆ added via Discord"
                    if any(item.get("uploaded_by_discord_id") for item in maps)
                    else ""
                )
            )
        )
        return embed, page + 1 < len(pages)

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
    cached = bot.map_cache.get(server)
    if cached is not None:
        ts, maps = cached
        if now - ts < 60:
            return maps

    try:
        maps = await bot.container.rpc.list_maps(server=server, timeout_ms=3000)
        bot.map_cache[server] = (now, maps)
        return maps
    except TimeoutError:
        return bot.map_cache.get(server, (0.0, []))[1]


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
    if not await bot.bus.claim_idempotency(claim_key, ttl_seconds=600):
        return "This map removal was already processed."

    try:
        result = await bot.container.rpc.remove_map(
            server=server,
            file_name=file_name,
            timeout_ms=bot.settings.rpc_timeout_ms,
        )
    except TimeoutError:
        return "No response from target server (timeout)."

    return f"🗑️ `{server}` remove-map `{file_name}`: {result}"


async def cmd_upload_map(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    server: str,
    attachments: list[discord.Attachment | None],
    creator: discord.abc.User | None = None,
    creators: list[discord.abc.User | None] | None = None,
) -> None:
    def identity(user: discord.abc.User) -> Actor:
        return Actor(
            str(user.id),
            user.display_name or user.name,
            handle=getattr(user, "name", None),
        )

    uploader = identity(interaction.user)
    files = [
        MapUploadFile(
            attachment.url,
            attachment.filename,
            creator=identity(creators[index])
            if creators and index < len(creators) and creators[index] is not None
            else None,
        )
        for index, attachment in enumerate(attachments)
        if attachment is not None and attachment.filename.lower().endswith(".msav")
    ]
    creator_actor = identity(creator) if creator is not None else None
    try:
        prepare_map_upload(server, files, uploader, creator_actor)
    except InvalidMapUpload as error:
        await interaction.response.send_message(str(error), ephemeral=True)
        return
    if not await claim_mutation(
        bot, interaction, operation="upload-map", scope=f"{server}:{len(files)}"
    ):
        return

    await interaction.response.send_message(
        f"Uploading {len(files)} map(s) to `{_map_text(server, inline_code=True)}`…",
        allowed_mentions=discord.AllowedMentions.none(),
    )
    try:
        response = await perform_map_upload(bot, server, files, uploader, creator_actor)
    except MapUploadTimeout as error:
        await interaction.edit_original_response(
            content=str(error),
            allowed_mentions=discord.AllowedMentions.none(),
        )
        return
    except MapUploadFailed as error:
        logger.exception("Map upload confirmation failed for %s", server)
        await interaction.edit_original_response(
            content=str(error),
            allowed_mentions=discord.AllowedMentions.none(),
        )
        return
    labels = {
        "installed": "✅ Installed; attribution saved",
        "download_failed": "❌ Download or file write failed",
        "invalid_map": "❌ Invalid map file",
        "superseded": "⚠️ Replaced by a newer upload",
        "metadata_failed": "⚠️ Installed; attribution was not saved",
        "rejected": "❌ Rejected filename",
    }
    rows = [f"Maps on `{_map_text(server, inline_code=True)}`:"]
    for result in response.results:
        rows.append(
            f"{labels[result.status.value]} — `{_map_text(result.fileName, 100, inline_code=True)}`"
        )
    await interaction.edit_original_response(
        content="\n".join(rows), allowed_mentions=discord.AllowedMentions.none()
    )
