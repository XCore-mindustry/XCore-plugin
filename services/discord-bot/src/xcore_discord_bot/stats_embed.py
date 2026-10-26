"""The `/stats` embed: who a player is and how they have been playing."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime

import discord

from .badges import get_badge
from .dto import BanRecord, MuteRecord, PlayerRecord
from .game_stats import GameStats
from .i18n import DEFAULT, t
from .leagues import league_for
from .player_pids import is_assigned
from .presentation import (
    DISCORD_EMBED_TITLE_MAX,
    format_ban_expire_date,
    format_hexed_rank_block,
    format_minutes,
)
from .rating_store import Placing
from .season_embeds import FIELD_LIMIT, build_ratings_field
from .utils.mindustry_colors import strip_mindustry_colors

BIO_LIMIT = 300


@dataclass(frozen=True, slots=True)
class StaffNotes:
    """What only the admins see; ``loaded`` is false when the lookups failed."""

    ban: BanRecord | None = None
    mute: MuteRecord | None = None
    loaded: bool = True


@dataclass(frozen=True, slots=True)
class StatsView:
    """Everything the embed shows. ``None`` for a section means it could not be loaded."""

    player: PlayerRecord
    placings: Sequence[Placing] | None = None
    games: GameStats | None = None
    staff: StaffNotes | None = None
    # The game keeps a Discord link between the player and the staff: so does the embed.
    show_discord: bool = False
    avatar_url: str | None = None
    other_accounts: Sequence[PlayerRecord] = ()
    # The language of whoever asked: the profile is read by them, not by the player.
    locale: str = DEFAULT


def clean(text: str | None) -> str:
    """Game text as Discord can show it: no colour tags, no markdown of its own."""
    stripped = strip_mindustry_colors(str(text or "")).replace("\n", " ").strip()
    return discord.utils.escape_markdown(stripped)


def code(text: str | None) -> str:
    """Game text for a code span, where markdown is not read and a backtick would end it."""
    stripped = strip_mindustry_colors(str(text or "")).replace("\n", " ").strip()
    return stripped.replace("`", "'")


def player_name(player: PlayerRecord, locale: str = DEFAULT) -> str:
    return clean(player.nickname) or t(locale, "stats-unknown")


def _moment(millis: object, style: str) -> str | None:
    if isinstance(millis, bool) or not isinstance(millis, (int, float)) or millis <= 0:
        return None
    return f"<t:{int(millis // 1000)}:{style}>"


def _title(player: PlayerRecord, locale: str) -> str:
    title = player_name(player, locale)
    shown_as = clean(player.custom_nickname)
    if shown_as and shown_as != title:
        title = f"{title} ({shown_as})"
    if len(title) <= DISCORD_EMBED_TITLE_MAX:
        return title
    return f"{title[: DISCORD_EMBED_TITLE_MAX - 1]}…"


def _badge_name(badge_id: str, locale: str) -> str:
    badge = get_badge(badge_id)
    return (
        t(locale, f"badges-name-{badge.id}") if badge is not None else clean(badge_id)
    )


def _identity(player: PlayerRecord, locale: str) -> str:
    if is_assigned(player.pid):
        parts = [f"`#{player.pid}`"]
    else:
        parts = [f"`{t(locale, 'stats-no-id')}`"]
    if player.username:
        parts.append(f"`@{code(player.username)}`")
    badge = get_badge(player.active_badge or "")
    if badge is not None and not badge.system:
        parts.append(f"🎖️ {_badge_name(badge.id, locale)}")
    if player.is_admin:
        parts.append(t(locale, "stats-admin"))
    return " · ".join(parts)


def _presence(player: PlayerRecord, locale: str) -> str:
    if player.online:
        if player.online_server:
            line = t(locale, "stats-online-on", server=code(player.online_server))
        else:
            line = t(locale, "stats-online")
        since = _moment(player.online_since, "R")
        return f"{line} · {t(locale, 'stats-joined', since=since)}" if since else line
    updated = _moment(player.updated_at, "R")
    if updated:
        return t(locale, "stats-offline-updated", updated=updated)
    return t(locale, "stats-offline")


def _description(player: PlayerRecord, locale: str) -> str:
    lines = [_identity(player, locale)]
    bio = clean(player.description)
    if bio:
        if len(bio) > BIO_LIMIT:
            bio = f"{bio[: BIO_LIMIT - 1]}…"
        lines.append(f"> {bio}")
    lines.append(_presence(player, locale))
    return "\n".join(lines)


def _discord(player: PlayerRecord, locale: str) -> str:
    discord_id = str(player.discord_id or "").strip()
    if not discord_id:
        return t(locale, "stats-discord-not-linked")
    if discord_id.isdigit():
        who = f"<@{discord_id}>"
    else:
        who = f"`{code(player.discord_username) or code(discord_id)}`"
    linked = _moment(player.discord_linked_at, "R")
    if not linked:
        return who
    return f"{who}\n{t(locale, 'stats-discord-linked', since=linked)}"


def _games(games: GameStats | None, locale: str) -> str:
    if games is None:
        return t(locale, "stats-unavailable")
    if games.games <= 0:
        return t(locale, "stats-games-none")
    lines = [
        t(
            locale,
            "stats-games-total",
            games=games.games,
            wins=games.wins,
            rate=games.win_rate,
        )
    ]
    if games.pvp.games > 0:
        lines.append(
            t(
                locale,
                "stats-games-pvp",
                games=games.pvp.games,
                wins=games.pvp.wins,
                rate=games.pvp.win_rate,
            )
        )
    if games.survival.games > 0:
        lines.append(
            t(
                locale,
                "stats-games-survival",
                games=games.survival.games,
                best=games.survival.best_wave,
                average=games.survival.average_wave,
            )
        )
    if games.hexed.games > 0:
        line = t(
            locale, "stats-games-hexed", games=games.hexed.games, wins=games.hexed.wins
        )
        if games.hexed.best_placement > 0:
            best = t(
                locale,
                "stats-games-hexed-best",
                place=games.hexed.best_placement,
                top3=games.hexed.top3,
            )
            line += f" · {best}"
        lines.append(line)
    return "\n".join(lines)


def _building(games: GameStats | None, locale: str) -> str | None:
    """What the player built and lost; left out until there is something to count."""
    if games is None:
        return None
    blocks = games.blocks_built + games.blocks_deconstructed + games.blocks_destroyed
    units = games.units_produced + games.units_destroyed
    if blocks + units <= 0:
        return None
    lines = [
        t(
            locale,
            "stats-building-blocks",
            built=games.blocks_built,
            deconstructed=games.blocks_deconstructed,
            destroyed=games.blocks_destroyed,
        )
    ]
    if units > 0:
        lines.append(
            t(
                locale,
                "stats-building-units",
                produced=games.units_produced,
                destroyed=games.units_destroyed,
            )
        )
    return "\n".join(lines)


def _hexed_rank(player: PlayerRecord, locale: str) -> str | None:
    """The rank of the old Hexed mode; left out for players who never earned a win there."""
    if player.hexed_rank <= 0 and player.hexed_points <= 0:
        return None
    rank, progress = format_hexed_rank_block(
        rank_value=player.hexed_rank, points=player.hexed_points, locale=locale
    )
    return f"**{rank}**\n{progress}"


def _badges(player: PlayerRecord, locale: str) -> str | None:
    if not player.unlocked_badges:
        return None
    active = (player.active_badge or "").strip().lower()
    names = []
    for badge_id in player.unlocked_badges:
        label = _badge_name(badge_id, locale)
        if badge_id.lower() == active:
            label = t(locale, "stats-badge-shown", badge=label)
        names.append(label)
    return " · ".join(names)


def _expired(expire_date: object) -> bool:
    if not isinstance(expire_date, datetime):
        return False
    expires = expire_date if expire_date.tzinfo else expire_date.replace(tzinfo=UTC)
    return expires <= datetime.now(UTC)


def _punishment(record: BanRecord | MuteRecord | None, locale: str) -> str:
    if record is None or _expired(record.expire_date):
        return t(locale, "stats-punishment-none")
    return t(
        locale,
        "stats-punishment",
        until=format_ban_expire_date(record.expire_date),
        reason=code(record.reason),
        admin=clean(record.admin_name),
    )


def _staff(player: PlayerRecord, notes: StaffNotes, locale: str) -> str:
    lines = []
    if notes.loaded:
        lines.append(t(locale, "stats-ban", state=_punishment(notes.ban, locale)))
        lines.append(t(locale, "stats-mute", state=_punishment(notes.mute, locale)))
    else:
        lines.append(t(locale, "stats-punishments-unavailable"))
    if player.is_admin:
        source = str(player.admin_source or "").strip() or "NONE"
        lines.append(t(locale, "stats-admin-source", source=source))
    # The stored values themselves (a language code, "auto", "off") are left as they are.
    language = str(player.language or "").strip() or "auto"
    translator = str(player.translator_language or "").strip() or "off"
    lines.append(t(locale, "stats-language", language=language, translator=translator))
    if not player.leaderboard:
        lines.append(t(locale, "stats-hidden"))
    return "\n".join(lines)


def _color(placings: Sequence[Placing] | None) -> discord.Color:
    """The colour of the best league the player holds this season."""
    if not placings:
        return discord.Color.blurple()
    best = max(placing.rating for placing in placings)
    return discord.Color(league_for(best).color)


def _fit(text: str) -> str:
    if len(text) <= FIELD_LIMIT:
        return text
    return text[: FIELD_LIMIT - 1].rsplit("\n", 1)[0] + "\n…"


def build_stats_embed(view: StatsView) -> discord.Embed:
    player = view.player
    locale = view.locale
    embed = discord.Embed(
        title=_title(player, locale),
        description=_description(player, locale),
        color=_color(view.placings),
    )
    if view.show_discord and view.avatar_url:
        embed.set_thumbnail(url=view.avatar_url)

    embed.add_field(
        name=t(locale, "stats-playtime"),
        value=f"`{format_minutes(player.total_play_time, locale)}`",
        inline=True,
    )
    embed.add_field(
        name=t(locale, "stats-first-seen"),
        value=_moment(player.created_at, "D") or t(locale, "stats-unknown"),
        inline=True,
    )
    if view.show_discord:
        embed.add_field(name="🔗 Discord", value=_discord(player, locale), inline=True)

    embed.add_field(
        name=t(locale, "stats-season-ratings"),
        value=(
            t(locale, "stats-unavailable")
            if view.placings is None
            else _fit(build_ratings_field(view.placings, locale))
        ),
        inline=False,
    )
    embed.add_field(
        name=t(locale, "stats-games"),
        value=_fit(_games(view.games, locale)),
        inline=False,
    )

    building = _building(view.games, locale)
    if building:
        embed.add_field(name=t(locale, "stats-building"), value=building, inline=False)

    hexed_rank = _hexed_rank(player, locale)
    if hexed_rank:
        embed.add_field(
            name=t(locale, "stats-hexed-rank"), value=hexed_rank, inline=True
        )
    badges = _badges(player, locale)
    if badges:
        embed.add_field(name=t(locale, "stats-badges"), value=_fit(badges), inline=True)

    if view.staff is not None:
        embed.add_field(
            name=t(locale, "stats-staff-notes"),
            value=_fit(_staff(player, view.staff, locale)),
            inline=False,
        )

    if view.show_discord and view.other_accounts:
        others = ", ".join(
            f"#{other.pid} {player_name(other, locale)}"
            for other in view.other_accounts
        )
        embed.set_footer(text=t(locale, "stats-also-linked", accounts=others)[:2048])
    return embed
