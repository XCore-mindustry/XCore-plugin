"""Embeds for rating seasons: announcements, info and leaderboards."""

from __future__ import annotations

from collections.abc import Sequence
from datetime import datetime

import discord

from .badges import get_badge
from .rating_store import (
    Placing,
    PodiumEntry,
    Prize,
    PrizeGrantRecord,
    RankedStanding,
    Season,
)

LADDER_NAMES = {
    "minipvp": "Mini-PvP",
    "hexed": "HexedCore",
}
PLACE_MARKS = {1: "🥇", 2: "🥈", 3: "🥉"}
EMBED_TITLE_LIMIT = 256
FIELD_LIMIT = 1024
GRANT_MARKS = {
    "PENDING": "⏳ waiting",
    "GRANTED": "✅ given",
    "DELIVERED": "📦 delivered",
    "FAILED": "⚠️ failed",
}


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


def prize_text(prize: Prize) -> str:
    """One prize as players read it: a badge by its name, anything else as written."""
    if prize.kind == "badge":
        badge = get_badge(prize.value)
        return f"🎖️ {badge.label if badge else _escape(prize.value)} badge"
    return f"🎁 {_escape(prize.label)}"


def prize_lines(prizes: Sequence[Prize]) -> str:
    """The prizes of a season, one line per place or range, cut to fit an embed field."""
    ordered = sorted(prizes, key=lambda prize: (prize.place_from, prize.place_to))
    lines = [f"{_place_range(prize)} {prize_text(prize)}" for prize in ordered]
    text = "\n".join(lines)
    if len(text) <= FIELD_LIMIT:
        return text
    return text[: FIELD_LIMIT - 1].rsplit("\n", 1)[0] + "\n…"


def _place_range(prize: Prize) -> str:
    if prize.place_from == prize.place_to:
        return _place(prize.place_from)
    return f"`#{prize.place_from}-{prize.place_to}`"


def podium_line(entry: PodiumEntry) -> str:
    winner = f"**{_escape(entry.nickname)}**"
    if entry.discord_id:
        winner += f" (<@{entry.discord_id}>)"
    games = f"{entry.wins}/{entry.matches}" if entry.matches else "-"
    line = f"{_place(entry.place)} {winner} · `{entry.rating}` · {games} wins"
    if entry.prizes:
        line += "\n └ " + " · ".join(prize_text(prize) for prize in entry.prizes)
    return line


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
    *,
    ladder: str,
    name: str | None,
    number: int,
    starts_at: datetime,
    ends_at: datetime,
    first: bool = False,
) -> discord.Embed:
    if first:
        description = (
            "This ladder now plays in seasons. Your current rating carries on, and "
            "every match you play counts towards this season's leaderboard."
        )
    else:
        description = (
            "Ratings were softly reset. Every match you play now counts towards "
            "the new leaderboard."
        )
    embed = discord.Embed(
        title=f"🏁 {season_title(ladder, name, number)} has started",
        description=description,
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
    *,
    ladder: str,
    name: str | None,
    number: int,
    ends_at: datetime,
    prizes: Sequence[Prize] = (),
) -> discord.Embed:
    embed = discord.Embed(
        title=f"⏳ {season_title(ladder, name, number)} is ending soon",
        description=f"The season ends {timestamp(ends_at, 'R')}. Play your last matches!",
        color=discord.Color.orange(),
    )
    embed.add_field(name="Ends", value=timestamp(ends_at), inline=False)
    if prizes:
        embed.add_field(name="Prizes", value=prize_lines(prizes), inline=False)
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
    if season.prizes and season.running:
        embed.add_field(name="Prizes", value=prize_lines(season.prizes), inline=False)
    return embed


def build_season_prizes_embed(
    season: Season, grants: Sequence[PrizeGrantRecord], *, names: dict[str, str] | None = None
) -> discord.Embed:
    """What the admins see in `/season prize list`: the prizes set and who they went to."""
    embed = discord.Embed(
        title=f"🎁 {season_title(season.ladder, season.name, season.number)} · Prizes",
        color=discord.Color.gold(),
    )
    embed.description = (
        prize_lines(season.prizes) if season.prizes else "No prizes are set for this season."
    )
    if grants:
        names = names or {}
        lines = []
        for grant in grants:
            who = _escape(names.get(grant.player_uuid, grant.player_uuid))
            what = prize_text(
                Prize(grant.place, grant.place, grant.kind, grant.value, grant.description)
            )
            status = GRANT_MARKS.get(grant.status, grant.status)
            note = f" — {_escape(grant.note)}" if grant.note else ""
            lines.append(f"{_place(grant.place)} {who}: {what} · {status}{note}")
        text = "\n".join(lines)
        if len(text) > FIELD_LIMIT:
            text = text[: FIELD_LIMIT - 1].rsplit("\n", 1)[0] + "\n…"
        embed.add_field(name="Grants", value=text, inline=False)
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
        f"{f' (#{row.pid})' if row.pid is not None else ''} · `{row.rating}` · "
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
