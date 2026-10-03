"""Embeds for rating seasons: announcements, info and leaderboards."""

from __future__ import annotations

from collections.abc import Sequence
from datetime import datetime

import discord

from .rating_store import Placing, PodiumEntry, RankedStanding, Season

LADDER_NAMES = {
    "minipvp": "Mini-PvP",
    "hexed": "HexedCore",
}
PLACE_MARKS = {1: "🥇", 2: "🥈", 3: "🥉"}
EMBED_TITLE_LIMIT = 256


def ladder_name(ladder: str) -> str:
    return LADDER_NAMES.get(ladder, ladder)


def season_title(ladder: str, name: str | None, number: int) -> str:
    return f"{ladder_name(ladder)} · {name or f'Season {number}'}"


def timestamp(value: datetime, style: str = "f") -> str:
    return f"<t:{int(value.timestamp())}:{style}>"


def _place(place: int) -> str:
    return PLACE_MARKS.get(place, f"`#{place}`")


def _escape(text: str) -> str:
    return discord.utils.escape_markdown(text)


def podium_line(entry: PodiumEntry) -> str:
    winner = f"**{_escape(entry.nickname)}**"
    if entry.discord_id:
        winner += f" (<@{entry.discord_id}>)"
    games = f"{entry.wins}/{entry.matches}" if entry.matches else "-"
    return f"{_place(entry.place)} {winner} · `{entry.rating}` · {games} wins"


def mentioned_user_ids(podium: Sequence[PodiumEntry]) -> list[discord.Object]:
    """The winners to notify; every other mention stays silent."""
    seen: dict[int, discord.Object] = {}
    for entry in podium:
        if entry.discord_id and entry.discord_id.isdigit():
            seen.setdefault(int(entry.discord_id), discord.Object(id=int(entry.discord_id)))
    return list(seen.values())


def allowed_mentions_for(podium: Sequence[PodiumEntry]) -> discord.AllowedMentions:
    return discord.AllowedMentions(
        everyone=False, roles=False, users=mentioned_user_ids(podium)
    )


def build_season_started_embed(
    *, ladder: str, name: str | None, number: int, starts_at: datetime, ends_at: datetime
) -> discord.Embed:
    embed = discord.Embed(
        title=f"🏁 {season_title(ladder, name, number)} has started",
        description=(
            "Ratings were softly reset. Every match you play now counts towards "
            "the new leaderboard."
        ),
        color=discord.Color.green(),
    )
    embed.add_field(name="Started", value=timestamp(starts_at), inline=True)
    embed.add_field(
        name="Ends",
        value=f"{timestamp(ends_at)} ({timestamp(ends_at, 'R')})",
        inline=True,
    )
    return embed


def build_season_ending_soon_embed(
    *, ladder: str, name: str | None, number: int, ends_at: datetime
) -> discord.Embed:
    embed = discord.Embed(
        title=f"⏳ {season_title(ladder, name, number)} is ending soon",
        description=f"The season ends {timestamp(ends_at, 'R')}. Play your last matches!",
        color=discord.Color.orange(),
    )
    embed.add_field(name="Ends", value=timestamp(ends_at), inline=False)
    return embed


def build_season_ended_embed(
    *,
    ladder: str,
    name: str | None,
    number: int,
    podium: Sequence[PodiumEntry],
    participants: int | None,
    matches: int | None,
) -> discord.Embed:
    embed = discord.Embed(
        title=f"🏆 {season_title(ladder, name, number)} is over",
        color=discord.Color.gold(),
    )
    embed.description = (
        "\n".join(podium_line(entry) for entry in podium)
        if podium
        else "Nobody played this season."
    )
    totals = []
    if participants is not None:
        totals.append(f"Players: `{participants}`")
    if matches is not None:
        totals.append(f"Matches: `{matches}`")
    if totals:
        embed.add_field(name="Season in numbers", value=" · ".join(totals), inline=False)
    return embed


def build_season_rescheduled_embed(
    *,
    ladder: str,
    name: str | None,
    number: int,
    previous_ends_at: datetime,
    ends_at: datetime,
    actor_name: str,
    reason: str | None,
) -> discord.Embed:
    embed = discord.Embed(
        title=f"📅 {season_title(ladder, name, number)} was rescheduled",
        color=discord.Color.blurple(),
    )
    embed.add_field(name="Was", value=timestamp(previous_ends_at), inline=True)
    embed.add_field(
        name="Now",
        value=f"{timestamp(ends_at)} ({timestamp(ends_at, 'R')})",
        inline=True,
    )
    embed.add_field(name="By", value=_escape(actor_name), inline=True)
    if reason:
        embed.add_field(name="Reason", value=_escape(reason)[:1000], inline=False)
    return embed


def build_season_info_embed(
    season: Season, *, participants: int, now: datetime
) -> discord.Embed:
    embed = discord.Embed(
        title=f"📊 {season_title(season.ladder, season.name, season.number)}",
        color=discord.Color.blurple(),
    )
    embed.add_field(name="Started", value=timestamp(season.starts_at), inline=True)
    if season.running and season.ends_at > now:
        embed.add_field(
            name="Ends",
            value=f"{timestamp(season.ends_at)} ({timestamp(season.ends_at, 'R')})",
            inline=True,
        )
    elif season.running:
        embed.add_field(name="Ends", value="Wrapping up…", inline=True)
    else:
        embed.add_field(name="Ended", value=timestamp(season.ends_at), inline=True)
    embed.add_field(name="Players", value=f"`{participants}`", inline=True)
    embed.add_field(name="Matches", value=f"`{season.matches}`", inline=True)
    return embed


def build_season_top_embed(
    season: Season, standings: Sequence[RankedStanding], *, participants: int
) -> discord.Embed:
    embed = discord.Embed(
        title=f"🏅 {season_title(season.ladder, season.name, season.number)} · Top",
        color=discord.Color.gold(),
    )
    if not standings:
        embed.description = "Nobody has played this season yet."
        return embed
    embed.description = "\n".join(
        f"{_place(row.rank)} **{_escape(row.nickname)}**"
        f"{f' (#{row.pid})' if row.pid else ''} · `{row.rating}` · "
        f"{row.wins}/{row.matches} wins"
        for row in standings
    )
    embed.set_footer(text=f"{participants} players this season")
    return embed


def build_ratings_field(placings: Sequence[Placing]) -> str:
    """The `/stats` lines: one per ladder the player has taken part in."""
    if not placings:
        return "No rated matches this season"
    return "\n".join(
        f"{ladder_name(placing.season.ladder)}: `{placing.rating}` "
        f"(#{placing.rank} of {placing.participants})"
        for placing in placings
    )
