"""The `/stats` embed: who a player is and how they have been playing."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime

import discord

from .badges import get_badge
from .dto import BanRecord, MuteRecord, PlayerRecord
from .game_stats import GameStats
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
UNAVAILABLE = "Unavailable right now"


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


def clean(text: str | None) -> str:
    """Game text as Discord can show it: no colour tags, no markdown of its own."""
    stripped = strip_mindustry_colors(str(text or "")).replace("\n", " ").strip()
    return discord.utils.escape_markdown(stripped)


def code(text: str | None) -> str:
    """Game text for a code span, where markdown is not read and a backtick would end it."""
    stripped = strip_mindustry_colors(str(text or "")).replace("\n", " ").strip()
    return stripped.replace("`", "'")


def player_name(player: PlayerRecord) -> str:
    return clean(player.nickname) or "Unknown"


def _number(value: int) -> str:
    return f"{value:,}"


def _count(value: int, noun: str) -> str:
    return f"{_number(value)} {noun}" if value == 1 else f"{_number(value)} {noun}s"


def _moment(millis: object, style: str) -> str | None:
    if isinstance(millis, bool) or not isinstance(millis, (int, float)) or millis <= 0:
        return None
    return f"<t:{int(millis // 1000)}:{style}>"


def _title(player: PlayerRecord) -> str:
    title = player_name(player)
    shown_as = clean(player.custom_nickname)
    if shown_as and shown_as != title:
        title = f"{title} ({shown_as})"
    if len(title) <= DISCORD_EMBED_TITLE_MAX:
        return title
    return f"{title[: DISCORD_EMBED_TITLE_MAX - 1]}…"


def _identity(player: PlayerRecord) -> str:
    parts = [f"`#{player.pid}`" if is_assigned(player.pid) else "`no ID`"]
    if player.username:
        parts.append(f"`@{code(player.username)}`")
    badge = get_badge(player.active_badge or "")
    if badge is not None and not badge.system:
        parts.append(f"🎖️ {badge.label}")
    if player.is_admin:
        parts.append("🛡️ Admin")
    return " · ".join(parts)


def _presence(player: PlayerRecord) -> str:
    if player.online:
        line = "🟢 **Online**"
        if player.online_server:
            line += f" on `{code(player.online_server)}`"
        since = _moment(player.online_since, "R")
        return f"{line} · joined {since}" if since else line
    updated = _moment(player.updated_at, "R")
    return f"⚫ Offline · profile updated {updated}" if updated else "⚫ Offline"


def _description(player: PlayerRecord) -> str:
    lines = [_identity(player)]
    bio = clean(player.description)
    if bio:
        if len(bio) > BIO_LIMIT:
            bio = f"{bio[: BIO_LIMIT - 1]}…"
        lines.append(f"> {bio}")
    lines.append(_presence(player))
    return "\n".join(lines)


def _discord(player: PlayerRecord) -> str:
    discord_id = str(player.discord_id or "").strip()
    if not discord_id:
        return "Not linked"
    if discord_id.isdigit():
        who = f"<@{discord_id}>"
    else:
        who = f"`{code(player.discord_username) or code(discord_id)}`"
    linked = _moment(player.discord_linked_at, "R")
    return f"{who}\nlinked {linked}" if linked else who


def _games(games: GameStats | None) -> str:
    if games is None:
        return UNAVAILABLE
    if games.games <= 0:
        return "No recorded games yet"
    lines = [
        (
            f"`{_number(games.games)}` played · `{_number(games.wins)}` won "
            f"· `{games.win_rate}%` win rate"
        )
    ]
    if games.pvp.games > 0:
        lines.append(
            f"⚔️ **PvP** · {_count(games.pvp.games, 'game')} "
            f"· {_count(games.pvp.wins, 'win')} ({games.pvp.win_rate}%)"
        )
    if games.survival.games > 0:
        lines.append(
            f"🛡️ **Survival** · {_count(games.survival.games, 'game')} "
            f"· best wave {_number(games.survival.best_wave)} "
            f"· average {_number(games.survival.average_wave)}"
        )
    if games.hexed.games > 0:
        line = (
            f"⬡ **Hexed** · {_count(games.hexed.games, 'game')} "
            f"· {_count(games.hexed.wins, 'win')}"
        )
        if games.hexed.best_placement > 0:
            line += (
                f" · best `#{games.hexed.best_placement}` "
                f"· top 3 ×{_number(games.hexed.top3)}"
            )
        lines.append(line)
    return "\n".join(lines)


def _building(games: GameStats | None) -> str | None:
    """What the player built and lost; left out until there is something to count."""
    if games is None:
        return None
    blocks = games.blocks_built + games.blocks_deconstructed + games.blocks_destroyed
    units = games.units_produced + games.units_destroyed
    if blocks + units <= 0:
        return None
    lines = [
        (
            f"Blocks: `{_number(games.blocks_built)}` built "
            f"· `{_number(games.blocks_deconstructed)}` taken apart "
            f"· `{_number(games.blocks_destroyed)}` lost"
        )
    ]
    if units > 0:
        lines.append(
            f"Units: `{_number(games.units_produced)}` produced "
            f"· `{_number(games.units_destroyed)}` lost"
        )
    return "\n".join(lines)


def _hexed_rank(player: PlayerRecord) -> str | None:
    """The rank of the old Hexed mode; left out for players who never earned a win there."""
    if player.hexed_rank <= 0 and player.hexed_points <= 0:
        return None
    rank, progress = format_hexed_rank_block(
        rank_value=player.hexed_rank, points=player.hexed_points
    )
    return f"**{rank}**\n{progress}"


def _badges(player: PlayerRecord) -> str | None:
    if not player.unlocked_badges:
        return None
    active = (player.active_badge or "").strip().lower()
    names = []
    for badge_id in player.unlocked_badges:
        badge = get_badge(badge_id)
        label = badge.label if badge is not None else clean(badge_id)
        names.append(f"**{label}** (shown)" if badge_id.lower() == active else label)
    return " · ".join(names)


def _expired(expire_date: object) -> bool:
    if not isinstance(expire_date, datetime):
        return False
    expires = expire_date if expire_date.tzinfo else expire_date.replace(tzinfo=UTC)
    return expires <= datetime.now(UTC)


def _punishment(record: BanRecord | MuteRecord | None) -> str:
    if record is None or _expired(record.expire_date):
        return "none"
    return (
        f"until {format_ban_expire_date(record.expire_date)}\n"
        f"└ `{code(record.reason)}` by {clean(record.admin_name)}"
    )


def _staff(player: PlayerRecord, notes: StaffNotes) -> str:
    lines = []
    if notes.loaded:
        lines.append(f"🔨 Ban: {_punishment(notes.ban)}")
        lines.append(f"🔇 Mute: {_punishment(notes.mute)}")
    else:
        lines.append(f"🔨 Ban and mute: {UNAVAILABLE}")
    if player.is_admin:
        source = str(player.admin_source or "").strip() or "NONE"
        lines.append(f"🛡️ Admin source: `{source}`")
    language = str(player.language or "").strip() or "auto"
    translator = str(player.translator_language or "").strip() or "off"
    lines.append(f"🌐 Language: `{language}` · translator: `{translator}`")
    if not player.leaderboard:
        lines.append("🙈 Hidden from the leaderboards")
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
    embed = discord.Embed(
        title=_title(player),
        description=_description(player),
        color=_color(view.placings),
    )
    if view.show_discord and view.avatar_url:
        embed.set_thumbnail(url=view.avatar_url)

    embed.add_field(
        name="⏱️ Playtime",
        value=f"`{format_minutes(player.total_play_time)}`",
        inline=True,
    )
    embed.add_field(
        name="📅 First seen",
        value=_moment(player.created_at, "D") or "Unknown",
        inline=True,
    )
    if view.show_discord:
        embed.add_field(name="🔗 Discord", value=_discord(player), inline=True)

    embed.add_field(
        name="🏆 Season ratings",
        value=(
            UNAVAILABLE
            if view.placings is None
            else _fit(build_ratings_field(view.placings))
        ),
        inline=False,
    )
    embed.add_field(name="🎮 Games", value=_fit(_games(view.games)), inline=False)

    building = _building(view.games)
    if building:
        embed.add_field(name="🧱 Building", value=building, inline=False)

    hexed_rank = _hexed_rank(player)
    if hexed_rank:
        embed.add_field(name="⬡ Hexed rank", value=hexed_rank, inline=True)
    badges = _badges(player)
    if badges:
        embed.add_field(name="🎖️ Badges", value=_fit(badges), inline=True)

    if view.staff is not None:
        embed.add_field(
            name="🔒 Staff notes", value=_fit(_staff(player, view.staff)), inline=False
        )

    if view.show_discord and view.other_accounts:
        others = ", ".join(
            f"#{other.pid} {player_name(other)}" for other in view.other_accounts
        )
        embed.set_footer(text=f"Also linked: {others}"[:2048])
    return embed
