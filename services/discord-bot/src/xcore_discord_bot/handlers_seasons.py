"""Season announcements: one post per season and kind, however often an event is replayed."""

from __future__ import annotations

import logging
from datetime import UTC, datetime
from typing import TYPE_CHECKING

import discord

from .contracts import (
    RatingSeasonEndedV1,
    RatingSeasonEndingSoonV1,
    RatingSeasonRescheduledV1,
    RatingSeasonStartedV1,
)
from .rating_store import PodiumEntry, Prize
from .season_embeds import (
    allowed_mentions_for,
    build_season_ended_embed,
    build_season_ending_soon_embed,
    build_season_rescheduled_embed,
    build_season_started_embed,
)

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot

logger = logging.getLogger(__name__)


def parse_instant(value: str) -> datetime:
    parsed = datetime.fromisoformat(value)
    return parsed if parsed.tzinfo is not None else parsed.replace(tzinfo=UTC)


def prizes_from_event(prizes) -> tuple[Prize, ...]:
    """The prizes an event carries; an event without any has none."""
    return tuple(
        Prize(
            place_from=prize.placeFrom,
            place_to=prize.placeTo,
            kind=str(prize.kind.value),
            value=prize.value,
            description=prize.description,
        )
        for prize in prizes or ()
    )


def podium_from_event(event: RatingSeasonEndedV1) -> tuple[PodiumEntry, ...]:
    entries: list[PodiumEntry] = []
    for entry in event.podium:
        discord_id = str(entry.discord.discordId or "").strip() if entry.discord else ""
        username = str(entry.discord.discordUsername or "").strip() if entry.discord else ""
        entries.append(
            PodiumEntry(
                place=entry.place,
                uuid=str(entry.player.playerUuid or ""),
                pid=entry.player.playerPid,
                nickname=str(entry.player.playerName or "Unknown"),
                rating=entry.rating,
                league=entry.league,
                matches=entry.matches,
                wins=entry.wins,
                discord_id=discord_id or None,
                discord_username=username or None,
                prizes=prizes_from_event(entry.prizes),
            )
        )
    return tuple(entries)


async def _post_once(
    bot: XCoreDiscordBot,
    *,
    season_id: str,
    kind: str,
    embed: discord.Embed,
    allowed_mentions: discord.AllowedMentions | None = None,
) -> bool:
    """Posts the announcement unless it is already out. True when this call posted it."""
    channel_id = bot.seasons_channel_id
    if not channel_id:
        return False
    store = bot.container.ratings
    if not await store.claim_post(season_id, kind):
        logger.debug("Season announcement %s:%s was already posted", season_id, kind)
        return False
    try:
        channel = await bot._resolve_messageable_channel(
            channel_id, context="season announcement"
        )
        if channel is None:
            raise RuntimeError(f"Season channel {channel_id} is not reachable")
        message = await channel.send(
            embed=embed, allowed_mentions=allowed_mentions or discord.AllowedMentions.none()
        )
    except Exception:
        # Hand the claim back so a replay of the event can try again.
        await store.release_post(season_id, kind)
        raise
    await store.record_post(season_id, kind, message.id)
    return True


async def on_season_started(bot: XCoreDiscordBot, event: RatingSeasonStartedV1) -> None:
    season = event.season
    await _post_once(
        bot,
        season_id=f"{season.ladder}:{season.season}",
        kind="started",
        embed=build_season_started_embed(
            ladder=season.ladder,
            name=season.name,
            number=season.season,
            starts_at=parse_instant(season.startsAt),
            ends_at=parse_instant(season.endsAt),
            first=event.previousSeason is None,
        ),
    )


async def on_season_ending_soon(
    bot: XCoreDiscordBot, event: RatingSeasonEndingSoonV1
) -> None:
    season = event.season
    await _post_once(
        bot,
        season_id=f"{season.ladder}:{season.season}",
        kind=f"ending-soon:{event.notice}",
        embed=build_season_ending_soon_embed(
            ladder=season.ladder,
            name=season.name,
            number=season.season,
            ends_at=parse_instant(season.endsAt),
            prizes=prizes_from_event(event.prizes),
        ),
    )


async def on_season_ended(bot: XCoreDiscordBot, event: RatingSeasonEndedV1) -> None:
    season = event.season
    podium = podium_from_event(event)
    await _post_once(
        bot,
        season_id=f"{season.ladder}:{season.season}",
        kind="ended",
        embed=build_season_ended_embed(
            ladder=season.ladder,
            name=season.name,
            number=season.season,
            podium=podium,
            participants=event.summary.participants,
            matches=event.summary.matches,
        ),
        allowed_mentions=allowed_mentions_for(podium),
    )


async def on_season_rescheduled(
    bot: XCoreDiscordBot, event: RatingSeasonRescheduledV1
) -> None:
    season = event.season
    ends_at = parse_instant(season.endsAt)
    await _post_once(
        bot,
        season_id=f"{season.ladder}:{season.season}",
        # Every move is its own announcement, told apart by where the end went.
        kind=f"rescheduled:{int(ends_at.timestamp())}",
        embed=build_season_rescheduled_embed(
            ladder=season.ladder,
            name=season.name,
            number=season.season,
            previous_ends_at=parse_instant(event.previousEndsAt),
            ends_at=ends_at,
            actor_name=event.actor.actorName,
            reason=event.reason,
        ),
    )
