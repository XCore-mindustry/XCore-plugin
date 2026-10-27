from __future__ import annotations

import logging
import re
from collections.abc import Awaitable
from typing import TYPE_CHECKING, TypeVar

import discord
from discord import Interaction

from .command_helpers import get_player_or_reply, reply_locale, send_paginated
from .dto import PlayerRecord
from .game_stats import GameStats
from .i18n import t
from .modal_factories import create_stats_ban_modal, create_stats_mute_modal
from .moderation_views import StatsActionsView
from .permissions import admin_role_ids, has_any_role, settings_from_interaction
from .presentation import format_epoch_millis, format_minutes
from .rating_store import Placing
from .stats_embed import StaffNotes, StatsView, build_stats_embed, player_name

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


logger = logging.getLogger(__name__)

T = TypeVar("T")


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
    bot: XCoreDiscordBot, interaction: Interaction, member: object, locale: str
) -> list[PlayerRecord] | None:
    """The game accounts of a Discord user, most played first; replies when there are none."""
    players = await bot.store.find_players_by_discord_id(str(getattr(member, "id", "")))
    if players:
        return sorted(players, key=lambda row: row.total_play_time, reverse=True)
    own = getattr(member, "id", None) == interaction.user.id
    await interaction.response.send_message(
        t(locale, "stats-no-linked-account" if own else "stats-user-not-linked"),
        ephemeral=True,
    )
    return None


async def _stats_player(
    bot: XCoreDiscordBot, interaction: Interaction, reference: str | int, locale: str
) -> PlayerRecord | None:
    text = str(reference).strip()
    pid = text.removeprefix("#")
    if re.fullmatch(r"-?\d+", pid):
        return await get_player_or_reply(bot, interaction, int(pid), locale=locale)
    username = text.removeprefix("@").strip()
    if username:
        player = await bot.store.find_player_by_username(username)
        if player is not None:
            return player
    await interaction.response.send_message(
        t(locale, "stats-player-not-found"), ephemeral=True
    )
    return None


async def cmd_stats(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    player_id: str | int | None = None,
    user: discord.abc.User | None = None,
) -> None:
    locale = reply_locale(bot, interaction)
    settings = settings_from_interaction(interaction)
    is_admin_viewer = settings is not None and has_any_role(
        interaction.user,
        admin_role_ids(settings),
    )

    member: discord.abc.User | None = None
    other_accounts: list[PlayerRecord] = []
    if player_id is not None:
        player = await _stats_player(bot, interaction, player_id, locale)
        if player is None:
            return
    else:
        member = user or interaction.user
        if member.id != interaction.user.id and not is_admin_viewer:
            await interaction.response.send_message(
                t(locale, "stats-lookup-by-user-admin-only"), ephemeral=True
            )
            return
        accounts = await _linked_accounts(bot, interaction, member, locale)
        if accounts is None:
            return
        player, other_accounts = accounts[0], accounts[1:]

    show_discord = is_admin_viewer or str(player.discord_id or "") == str(
        interaction.user.id
    )
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
            show_discord=show_discord,
            avatar_url=_avatar_url(interaction, player, member),
            other_accounts=other_accounts,
            locale=locale,
        )
    )

    if not is_admin_viewer:
        await interaction.response.send_message(embed=embed, ephemeral=show_discord)
        return

    view = StatsActionsView(
        settings=bot.settings,
        player_id=player.pid,
        player=player,
        create_ban_modal=lambda **kwargs: create_stats_ban_modal(bot, **kwargs),
        create_mute_modal=lambda **kwargs: create_stats_mute_modal(bot, **kwargs),
        open_target_audit=lambda interaction, pid, player: cmd_stats_audit(
            bot, interaction, pid, player
        ),
        open_actor_audit=lambda interaction, pid, player: cmd_stats_audit(
            bot, interaction, pid, player, mode="actor"
        ),
        locale=locale,
    )
    await interaction.response.send_message(embed=embed, view=view, ephemeral=True)
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
    player: PlayerRecord,
    mode: str = "target",
) -> None:
    page_size = 6
    nickname = player.nickname or "Unknown"
    if mode == "actor":
        actor_discord_id = str(player.discord_id or "").strip() or None
        actor_id = nickname
        total_entries = await bot.container.audit.count_audit_for_actor(
            actor_id=actor_id,
            actor_discord_id=actor_discord_id,
        )
    else:
        uuid_value = str(player.uuid or "").strip()
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
