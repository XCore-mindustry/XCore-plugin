from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any

import pytest
from xcore_protocol.generated.rating import (
    RatingSeasonEndedV1,
    RatingSeasonEndingSoonV1,
    RatingSeasonRescheduledV1,
    RatingSeasonStartedV1,
)
from xcore_protocol.generated.shared import (
    ActorRefV1,
    DiscordIdentityRefV1,
    PlayerRefV1,
    SeasonPodiumEntryV1,
    SeasonRefV1,
    SeasonSummaryV1,
)

from xcore_discord_bot.handlers_seasons import (
    on_season_ended,
    on_season_ending_soon,
    on_season_rescheduled,
    on_season_started,
)
from xcore_discord_bot.runtime_consumers import consume_season_ended

SEASON = SeasonRefV1(
    ladder="minipvp",
    season=3,
    name="Winter",
    startsAt="2026-01-01T00:00:00Z",
    endsAt="2026-04-01T00:00:00Z",
)


class _Ratings:
    def __init__(self) -> None:
        self.claimed: set[str] = set()
        self.recorded: dict[str, int] = {}

    async def claim_post(self, season_id: str, kind: str) -> bool:
        key = f"{season_id}:{kind}"
        if key in self.claimed:
            return False
        self.claimed.add(key)
        return True

    async def record_post(self, season_id: str, kind: str, message_id: int) -> None:
        self.recorded[f"{season_id}:{kind}"] = message_id

    async def release_post(self, season_id: str, kind: str) -> None:
        self.claimed.discard(f"{season_id}:{kind}")


@dataclass
class _Channel:
    fail: bool = False
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send(self, *, embed, allowed_mentions) -> Any:
        if self.fail:
            raise RuntimeError("discord is down")
        self.sent.append({"embed": embed, "allowed_mentions": allowed_mentions})
        return SimpleNamespace(id=1000 + len(self.sent))


class _Bot:
    def __init__(self, channel: _Channel | None, channel_id: int = 777) -> None:
        self.seasons_channel_id = channel_id
        self.container = SimpleNamespace(ratings=_Ratings())
        self._channel = channel

    async def _resolve_messageable_channel(self, channel_id: int, *, context: str):
        assert channel_id == 777
        return self._channel


def _ended(podium: tuple[SeasonPodiumEntryV1, ...]) -> RatingSeasonEndedV1:
    return RatingSeasonEndedV1(
        season=SEASON,
        podium=podium,
        summary=SeasonSummaryV1(participants=30, matches=120),
        server="mini-pvp",
        occurredAt="2026-04-01T00:00:01Z",
    )


def _entry(place: int, name: str, discord_id: str | None) -> SeasonPodiumEntryV1:
    return SeasonPodiumEntryV1(
        place=place,
        player=PlayerRefV1(playerUuid=f"u{place}", playerName=name, playerPid=place),
        rating=1600 - place * 50,
        league="GOLD",
        matches=20,
        wins=12,
        discord=DiscordIdentityRefV1(discordId=discord_id, discordUsername="d")
        if discord_id
        else None,
    )


@pytest.mark.asyncio
async def test_started_is_posted_once_even_when_replayed() -> None:
    channel = _Channel()
    bot = _Bot(channel)
    event = RatingSeasonStartedV1(
        season=SEASON, server="mini-pvp", occurredAt="2026-01-01T00:00:00Z"
    )

    await on_season_started(bot, event)
    await on_season_started(bot, event)

    assert len(channel.sent) == 1
    assert "Winter" in channel.sent[0]["embed"].title
    assert bot.container.ratings.recorded == {"minipvp:3:started": 1001}


@pytest.mark.asyncio
async def test_started_tells_the_first_season_apart_from_a_reset() -> None:
    channel = _Channel()
    bot = _Bot(channel)

    await on_season_started(
        bot,
        RatingSeasonStartedV1(
            season=SEASON, server="mini-pvp", occurredAt="2026-01-01T00:00:00Z"
        ),
    )
    await on_season_started(
        bot,
        RatingSeasonStartedV1(
            season=SEASON,
            previousSeason=2,
            server="mini-pvp",
            occurredAt="2026-01-01T00:00:00Z",
        ),
    )

    # Both are the same season's start, so only the first one is posted.
    assert len(channel.sent) == 1
    assert "softly reset" not in channel.sent[0]["embed"].description
    assert "carries on" in channel.sent[0]["embed"].description


@pytest.mark.asyncio
async def test_nothing_is_posted_without_a_configured_channel() -> None:
    channel = _Channel()
    bot = _Bot(channel, channel_id=0)

    await on_season_started(
        bot,
        RatingSeasonStartedV1(
            season=SEASON, server="mini-pvp", occurredAt="2026-01-01T00:00:00Z"
        ),
    )

    assert channel.sent == []
    assert bot.container.ratings.claimed == set()


@pytest.mark.asyncio
async def test_each_ending_notice_is_its_own_announcement() -> None:
    channel = _Channel()
    bot = _Bot(channel)

    for notice in ("7d", "1d", "7d"):
        await on_season_ending_soon(
            bot,
            RatingSeasonEndingSoonV1(
                season=SEASON,
                notice=notice,
                server="mini-pvp",
                occurredAt="2026-03-25T00:00:00Z",
            ),
        )

    assert len(channel.sent) == 2


@pytest.mark.asyncio
async def test_ended_mentions_only_the_winners_who_linked_discord() -> None:
    channel = _Channel()
    bot = _Bot(channel)

    await on_season_ended(
        bot, _ended((_entry(1, "Ann", "4242"), _entry(2, "Bob", None)))
    )

    sent = channel.sent[0]
    description = sent["embed"].description
    assert "<@4242>" in description
    assert "Bob" in description
    mentions = sent["allowed_mentions"]
    assert [user.id for user in mentions.users] == [4242]
    assert mentions.everyone is False
    assert mentions.roles is False


@pytest.mark.asyncio
async def test_failed_post_is_released_so_a_replay_can_retry() -> None:
    channel = _Channel(fail=True)
    bot = _Bot(channel)
    event = _ended((_entry(1, "Ann", None),))

    with pytest.raises(RuntimeError):
        await on_season_ended(bot, event)
    assert bot.container.ratings.claimed == set()

    channel.fail = False
    await on_season_ended(bot, event)
    assert len(channel.sent) == 1


@pytest.mark.asyncio
async def test_rescheduling_announces_each_move_once() -> None:
    channel = _Channel()
    bot = _Bot(channel)
    event = RatingSeasonRescheduledV1(
        season=SEASON,
        previousEndsAt="2026-03-25T00:00:00Z",
        actor=ActorRefV1(actorName="Head"),
        server="mini-pvp",
        occurredAt="2026-03-20T00:00:00Z",
        reason="tournament",
    )

    await on_season_rescheduled(bot, event)
    await on_season_rescheduled(bot, event)

    assert len(channel.sent) == 1
    fields = {f.name: f.value for f in channel.sent[0]["embed"].fields}
    assert fields["By"] == "Head"
    assert fields["Reason"] == "tournament"


@pytest.mark.asyncio
async def test_ended_consumer_dispatches_to_the_handler() -> None:
    channel = _Channel()

    class _ConsumerBot(_Bot):
        async def consume_season_ended_stream(self, callback) -> None:
            await callback(_ended((_entry(1, "Ann", None),)))
            raise asyncio.CancelledError()

        async def reconnect_bus(self) -> None:
            raise AssertionError("reconnect should not be called")

    with pytest.raises(asyncio.CancelledError):
        await consume_season_ended(_ConsumerBot(channel))

    assert len(channel.sent) == 1
