from __future__ import annotations

import json
from datetime import UTC, datetime
from types import MethodType, SimpleNamespace
from typing import Any

import pytest
from test_rating_store import _season, _store
from test_season_announcements import SEASON, _Bot, _Channel, _entry
from xcore_protocol.generated.rating import (
    RatingSeasonEndedV1,
    RatingSeasonEndingSoonV1,
)
from xcore_protocol.generated.shared import (
    SeasonPodiumEntryV1,
    SeasonPrizeV1,
    SeasonPrizeV1Kind,
    SeasonSummaryV1,
)

from xcore_discord_bot.badges import BADGE_BY_ID, get_badge
from xcore_discord_bot.cogs.seasons import SeasonsCog, parse_places
from xcore_discord_bot.handlers_seasons import (
    on_season_ended,
    on_season_ending_soon,
    prizes_from_event,
)
from xcore_discord_bot.rating_store import (
    PodiumEntry,
    Prize,
    PrizeGrantRecord,
    Season,
    season_from_doc,
)
from xcore_discord_bot.redis_bus import RedisBus, RpcRejected
from xcore_discord_bot.registry import server_registry
from xcore_discord_bot.rpc.mindustry_rpc import MindustryRpcClient
from xcore_discord_bot.season_embeds import (
    build_season_ended_embed,
    build_season_ending_soon_embed,
    build_season_info_embed,
    build_season_prizes_embed,
    podium_line,
    prize_lines,
    prize_text,
)
from xcore_discord_bot.services.rating_service import RatingService

CHAMPION = Prize(1, 1, "badge", "season-champion")
NITRO = Prize(1, 3, "custom", "Discord Nitro, 1 month", "A month of Nitro")


# ------------------------------------------------------------------- store


def test_prizes_are_read_from_the_season_document() -> None:
    season = season_from_doc(
        _season(
            "minipvp",
            2,
            "ACTIVE",
            prizes=[
                {"place_from": 1, "place_to": 1, "kind": "BADGE", "value": "season-champion", "description": ""},
                {"place_from": 2, "place_to": 4, "kind": "CUSTOM", "value": "Nitro", "description": "A month"},
            ],
        )
    )

    assert season.prizes == (
        Prize(1, 1, "badge", "season-champion", None),
        Prize(2, 4, "custom", "Nitro", "A month"),
    )
    assert season.prizes_for(3) == (season.prizes[1],)
    assert season.prizes_for(5) == ()
    assert season.prizes[1].places == "2-4"
    assert season.prizes[0].places == "1"


def test_a_season_without_prizes_has_none() -> None:
    assert season_from_doc(_season("minipvp", 1, "ACTIVE")).prizes == ()


@pytest.mark.asyncio
async def test_prize_grants_are_read_by_place() -> None:
    store, db = _store()
    db["rating_prize_grants"].docs += [
        {"_id": "b", "season_id": "minipvp:1", "place": 2, "prize_index": 1, "player_uuid": "bob",
         "prize": {"kind": "CUSTOM", "value": "Nitro", "description": ""}, "status": "PENDING",
         "granted_by": "system", "note": ""},
        {"_id": "a", "season_id": "minipvp:1", "place": 1, "prize_index": 0, "player_uuid": "ace",
         "prize": {"kind": "BADGE", "value": "season-champion", "description": ""}, "status": "GRANTED",
         "granted_by": "system", "note": "unlocked"},
        {"_id": "x", "season_id": "minipvp:2", "place": 1, "prize_index": 0, "player_uuid": "zed",
         "prize": {"kind": "BADGE", "value": "season-champion"}, "status": "GRANTED"},
    ]
    db["players"].docs.append({"uuid": "ace", "nickname": "Ace", "pid": 1})

    grants = await store.prize_grants("minipvp", 1)

    assert [(g.place, g.player_uuid, g.kind, g.status) for g in grants] == [
        (1, "ace", "badge", "GRANTED"),
        (2, "bob", "custom", "PENDING"),
    ]
    assert grants[0].note == "unlocked"
    assert grants[1].note is None
    assert await store.nicknames(["ace", "bob"]) == {"ace": "Ace"}


# ------------------------------------------------------------------ badges


def test_the_season_champion_badge_can_be_granted_like_the_others() -> None:
    badge = get_badge("Season-Champion")

    assert badge is not None
    assert badge.grantable and not badge.system
    assert "season-champion" in BADGE_BY_ID


# ----------------------------------------------------------------- embeds


def test_a_badge_prize_reads_as_the_badge_name_and_a_custom_one_as_written() -> None:
    assert prize_text(CHAMPION) == "🎖️ Season Champion badge"
    assert prize_text(Prize(1, 1, "badge", "no-such")) == "🎖️ no-such badge"
    assert prize_text(NITRO) == "🎁 A month of Nitro"
    assert prize_text(Prize(1, 1, "custom", "*bold*")) == "🎁 \\*bold\\*"


def test_prize_lines_are_ordered_by_place_and_fit_an_embed_field() -> None:
    text = prize_lines([NITRO, CHAMPION])

    assert text.splitlines() == [
        "🥇 🎖️ Season Champion badge",
        "`#1-3` 🎁 A month of Nitro",
    ]
    many = prize_lines([Prize(n, n, "custom", "x" * 90) for n in range(1, 40)])
    assert len(many) <= 1024
    assert many.endswith("…")


def test_a_winner_sees_the_prizes_of_their_place() -> None:
    entry = PodiumEntry(1, "u1", 1, "Ann", 1600, "GOLD", 20, 12, "42", None, prizes=(CHAMPION, NITRO))
    plain = PodiumEntry(2, "u2", 2, "Bob", 1500, "GOLD", 20, 12, None, None)

    assert "└ 🎖️ Season Champion badge · 🎁 A month of Nitro" in podium_line(entry)
    assert "└" not in podium_line(plain)


def test_ending_soon_lists_the_prizes_only_when_there_are_some() -> None:
    ends = datetime(2026, 4, 1, tzinfo=UTC)
    with_prizes = build_season_ending_soon_embed(
        ladder="minipvp", name=None, number=3, ends_at=ends, prizes=(CHAMPION,)
    )
    without = build_season_ending_soon_embed(ladder="minipvp", name=None, number=3, ends_at=ends)

    assert [field.name for field in with_prizes.fields] == ["Ends", "Prizes"]
    assert [field.name for field in without.fields] == ["Ends"]


def _season_model(status: str = "ACTIVE", prizes: tuple[Prize, ...] = ()) -> Season:
    return Season("minipvp", 3, "Winter", status, datetime(2026, 1, 1, tzinfo=UTC),
                  datetime(2026, 4, 1, tzinfo=UTC), 5, None, (), prizes)


def test_info_shows_prizes_of_the_running_season_only() -> None:
    now = datetime(2026, 2, 1, tzinfo=UTC)
    running = build_season_info_embed(_season_model(prizes=(CHAMPION,)), participants=3, now=now)
    over = build_season_info_embed(_season_model("ARCHIVED", (CHAMPION,)), participants=3, now=now)

    assert "Prizes" in [field.name for field in running.fields]
    assert "Prizes" not in [field.name for field in over.fields]


def test_the_admin_list_shows_prizes_and_where_each_grant_stands() -> None:
    grants = [
        PrizeGrantRecord("minipvp:3", 1, "ace", "badge", "season-champion", None, "GRANTED", "system", None),
        PrizeGrantRecord("minipvp:3", 2, "bob", "custom", "Nitro", None, "PENDING", "system", None),
        PrizeGrantRecord("minipvp:3", 2, "cat", "custom", "Nitro", None, "DELIVERED", "discord_user:5", "sent by DM"),
    ]

    embed = build_season_prizes_embed(
        _season_model("ARCHIVED", (CHAMPION, NITRO)), grants, names={"ace": "Ace", "bob": "Bob"}
    )

    assert "Season Champion badge" in embed.description
    lines = embed.fields[0].value.splitlines()
    assert "Ace" in lines[0] and "✅ given" in lines[0]
    assert "Bob" in lines[1] and "⏳ waiting" in lines[1]
    assert "cat" in lines[2] and "📦 delivered" in lines[2] and "sent by DM" in lines[2]
    empty = build_season_prizes_embed(_season_model(), [])
    assert empty.description == "No prizes are set for this season."
    assert empty.fields == []


def test_ended_embed_puts_each_winners_prizes_under_their_name() -> None:
    podium = (PodiumEntry(1, "u1", 1, "Ann", 1600, "GOLD", 20, 12, None, None, prizes=(CHAMPION,)),)

    embed = build_season_ended_embed(
        ladder="minipvp", name=None, number=3, podium=podium, participants=3, matches=9
    )

    assert "Season Champion badge" in embed.description


# --------------------------------------------------------------- handlers


def _prize_v1(place_from: int, place_to: int, kind: SeasonPrizeV1Kind, value: str, description=None):
    return SeasonPrizeV1(placeFrom=place_from, placeTo=place_to, kind=kind, value=value, description=description)


def test_prizes_are_read_from_an_event_and_their_absence_means_none() -> None:
    assert prizes_from_event(None) == ()
    assert prizes_from_event([_prize_v1(1, 2, SeasonPrizeV1Kind.CUSTOM, "Nitro", "Month")]) == (
        Prize(1, 2, "custom", "Nitro", "Month"),
    )


@pytest.mark.asyncio
async def test_the_ending_soon_post_lists_the_prizes() -> None:
    channel = _Channel()
    bot = _Bot(channel)

    await on_season_ending_soon(
        bot,
        RatingSeasonEndingSoonV1(
            season=SEASON,
            notice="7d",
            server="mini-pvp",
            occurredAt="2026-03-25T00:00:00Z",
            prizes=(_prize_v1(1, 1, SeasonPrizeV1Kind.BADGE, "season-champion"),),
        ),
    )

    fields = channel.sent[0]["embed"].fields
    assert fields[-1].name == "Prizes"
    assert "Season Champion" in fields[-1].value


@pytest.mark.asyncio
async def test_the_results_post_shows_what_each_winner_gets_and_still_mentions_them() -> None:
    channel = _Channel()
    bot = _Bot(channel)
    first = _entry(1, "Ann", "111")
    first = SeasonPodiumEntryV1(
        place=first.place, player=first.player, discord=first.discord, rating=first.rating,
        league=first.league, matches=first.matches, wins=first.wins,
        prizes=(_prize_v1(1, 1, SeasonPrizeV1Kind.BADGE, "season-champion"),),
    )

    await on_season_ended(
        bot,
        RatingSeasonEndedV1(
            season=SEASON,
            podium=(first, _entry(2, "Bob", None)),
            summary=SeasonSummaryV1(participants=30, matches=120),
            server="mini-pvp",
            occurredAt="2026-04-01T00:00:01Z",
        ),
    )

    description = channel.sent[0]["embed"].description
    assert "Season Champion badge" in description
    assert description.count("└") == 1


# ---------------------------------------------------------------- service


class _Rpc:
    def __init__(self) -> None:
        self.calls: list[tuple[str, dict[str, Any]]] = []

    async def add_season_prize(self, **kwargs):
        self.calls.append(("add", kwargs))
        return "added"

    async def remove_season_prizes(self, **kwargs):
        self.calls.append(("remove", kwargs))
        return "removed"

    async def mark_prize_delivered(self, **kwargs):
        self.calls.append(("delivered", kwargs))
        return "delivered"


@pytest.mark.asyncio
async def test_the_service_turns_a_prize_into_one_rpc() -> None:
    rpc = _Rpc()
    service = RatingService(store=SimpleNamespace(), rpc=rpc, timeout_ms=1234)

    added = await service.add_prize(
        ladder="minipvp", place_from=1, place_to=3, kind=SeasonPrizeV1Kind.CUSTOM,
        value="Nitro", description="", discord_id="5", actor_name="Head",
    )
    await service.clear_prizes(ladder="minipvp", place_from=2, place_to=2, discord_id="5", actor_name="Head")
    await service.mark_prize_delivered(
        ladder="minipvp", season=3, place=2, discord_id="5", actor_name="Head", note="sent"
    )

    assert added == "added"
    (_, add), (_, remove), (_, delivered) = rpc.calls
    assert add["prize"] == SeasonPrizeV1(placeFrom=1, placeTo=3, kind=SeasonPrizeV1Kind.CUSTOM, value="Nitro")
    assert add["timeout_ms"] == 1234
    assert (remove["place_from"], remove["place_to"]) == (2, 2)
    assert (delivered["season"], delivered["place"], delivered["note"]) == (3, 2, "sent")


@pytest.mark.asyncio
async def test_prize_rpcs_go_to_a_live_server() -> None:
    server_registry._servers.clear()
    server_registry.update_server(name="mini-pvp", channel_id=1, players=0, max_players=10, version="1")
    seen: list[tuple[str, str]] = []

    class _Bus:
        async def rpc_season_prize_add(self, *, server, **kwargs):
            seen.append(("add", server))

        async def rpc_season_prize_remove(self, *, server, **kwargs):
            seen.append(("remove", server))

        async def rpc_prize_delivered(self, *, server, **kwargs):
            seen.append(("delivered", server))

    client = MindustryRpcClient(_Bus())
    prize = _prize_v1(1, 1, SeasonPrizeV1Kind.BADGE, "veteran")
    await client.add_season_prize(ladder="l", prize=prize, discord_id="5", actor_name="H", timeout_ms=1)
    await client.remove_season_prizes(
        ladder="l", place_from=1, place_to=1, discord_id="5", actor_name="H", timeout_ms=1
    )
    await client.mark_prize_delivered(
        ladder="l", season=1, place=1, discord_id="5", actor_name="H", note=None, timeout_ms=1
    )

    assert seen == [("add", "mini-pvp"), ("remove", "mini-pvp"), ("delivered", "mini-pvp")]


@pytest.mark.asyncio
async def test_the_bus_sends_the_canonical_prize_requests() -> None:
    bus = RedisBus(
        SimpleNamespace(
            redis_url="redis://127.0.0.1:6379",
            redis_group_prefix="xcore:cg",
            redis_consumer_name="discord-bot",
        )
    )
    sent: list[tuple[str, dict[str, Any]]] = []
    season = {"ladder": "minipvp", "season": 3, "name": "S",
              "startsAt": "2026-01-01T00:00:00Z", "endsAt": "2026-04-01T00:00:00Z"}

    async def fake_rpc_request(self, server, rpc_type, payload, timeout_ms):
        sent.append((rpc_type, payload))
        if rpc_type == "rating.prize.grant.update.request":
            body = {"messageType": "rating.prize.grant.update.response", "messageVersion": 1,
                    "server": server, "season": 3, "place": 2, "updated": 1}
        else:
            body = {"messageType": "rating.season.prizes.set.response", "messageVersion": 1,
                    "server": server, "season": season,
                    "prizes": [{"placeFrom": 1, "placeTo": 1, "kind": "badge", "value": "veteran"}]}
        return {"payload_json": json.dumps(body)}

    bus._rpc_request = MethodType(fake_rpc_request, bus)

    added = await bus.rpc_season_prize_add(
        server="mini-pvp", ladder="minipvp",
        prize=_prize_v1(1, 1, SeasonPrizeV1Kind.BADGE, "veteran"),
        discord_id="5", actor_name="Head", timeout_ms=100,
    )
    await bus.rpc_season_prize_remove(
        server="mini-pvp", ladder="minipvp", place_from=2, place_to=3,
        discord_id="5", actor_name="Head", timeout_ms=100,
    )
    delivered = await bus.rpc_prize_delivered(
        server="mini-pvp", ladder="minipvp", season=3, place=2,
        discord_id="5", actor_name="Head", note="sent", timeout_ms=100,
    )
    await bus.rpc_prize_delivered(
        server="mini-pvp", ladder="minipvp", season=3, place=2,
        discord_id="5", actor_name="Head", note=None, timeout_ms=100, player_pid=14,
    )

    assert added.prizes[0].value == "veteran"
    assert delivered.updated == 1
    (_, add), (_, remove), (grant_type, grant), (_, one_player) = sent
    assert add["operation"] == "add"
    assert add["prize"] == {"placeFrom": 1, "placeTo": 1, "kind": "badge", "value": "veteran"}
    assert add["actor"]["actorDiscordId"] == "5"
    assert remove["operation"] == "remove"
    assert (remove["placeFrom"], remove["placeTo"]) == (2, 3)
    assert "prize" not in remove
    assert grant_type == "rating.prize.grant.update.request"
    assert grant["status"] == "delivered"
    assert (grant["season"], grant["place"], grant["note"]) == (3, 2, "sent")
    assert "playerPid" not in grant
    assert one_player["playerPid"] == 14


# -------------------------------------------------------------------- cog


def test_places_are_a_number_or_a_range() -> None:
    assert parse_places("1") == (1, 1)
    assert parse_places(" 2-5 ") == (2, 5)
    for bad in ("", "0", "3-1", "a", "1-b", "-2", "1-2-3"):
        with pytest.raises(ValueError):
            parse_places(bad)


def test_prize_commands_are_registered_under_season() -> None:
    cog = SeasonsCog(SimpleNamespace())
    season = cog.__cog_app_commands__[0]
    prize = next(command for command in season.commands if command.name == "prize")

    assert {command.name for command in prize.commands} == {"set", "clear", "list", "delivered"}


class _Service:
    def __init__(self, error: Exception | None = None) -> None:
        self.error = error
        self.calls: list[tuple[str, dict[str, Any]]] = []

    def _answer(self, name: str, kwargs: dict[str, Any], **extra):
        self.calls.append((name, kwargs))
        if self.error:
            raise self.error
        return SimpleNamespace(prizes=[1, 2], **extra)

    async def add_prize(self, **kwargs):
        return self._answer("add", kwargs)

    async def clear_prizes(self, **kwargs):
        return self._answer("clear", kwargs)

    async def mark_prize_delivered(self, **kwargs):
        return self._answer("delivered", kwargs, updated=2)


class _Followup:
    def __init__(self) -> None:
        self.sent: list[dict[str, Any]] = []

    async def send(self, content=None, **kwargs) -> None:
        self.sent.append({"content": content, **kwargs})


class _Response:
    def __init__(self) -> None:
        self.sent: list[dict[str, Any]] = []
        self.deferred = False

    async def send_message(self, content=None, **kwargs) -> None:
        self.sent.append({"content": content, **kwargs})

    async def defer(self, **kwargs) -> None:
        self.deferred = True


def _interaction() -> SimpleNamespace:
    return SimpleNamespace(
        user=SimpleNamespace(id=5, display_name="Head"),
        response=_Response(),
        followup=_Followup(),
    )


def _cog(service: _Service, ratings: Any = None) -> SeasonsCog:
    return SeasonsCog(SimpleNamespace(container=SimpleNamespace(rating_service=service, ratings=ratings)))


@pytest.mark.asyncio
async def test_set_sends_the_prize_with_the_admins_identity() -> None:
    service, interaction = _Service(), _interaction()
    cog = _cog(service)

    await cog.cmd_prize_set.callback(cog, interaction, "minipvp", "1-3", "custom", "  Nitro ", "  A month ")

    name, kwargs = service.calls[0]
    assert name == "add"
    assert (kwargs["place_from"], kwargs["place_to"]) == (1, 3)
    assert kwargs["kind"] is SeasonPrizeV1Kind.CUSTOM
    assert kwargs["value"] == "Nitro"
    assert kwargs["description"] == "A month"
    assert kwargs["discord_id"] == "5"
    assert "2 prize(s)" in interaction.followup.sent[0]["content"]


@pytest.mark.asyncio
async def test_set_and_clear_refuse_bad_places_without_asking_a_server() -> None:
    service, interaction = _Service(), _interaction()
    cog = _cog(service)

    await cog.cmd_prize_set.callback(cog, interaction, "minipvp", "first", "badge", "veteran", None)
    await cog.cmd_prize_clear.callback(cog, interaction, "minipvp", "3-1")

    assert service.calls == []
    assert len(interaction.response.sent) == 2
    assert all(sent["ephemeral"] for sent in interaction.response.sent)


@pytest.mark.asyncio
async def test_a_refused_prize_is_worded_for_the_admin() -> None:
    refusal = RpcRejected("rating.season.prizes.set.request", "REJECTED", "Badge 'x' was not found")
    service, interaction = _Service(refusal), _interaction()
    cog = _cog(service)

    await cog.cmd_prize_set.callback(cog, interaction, "minipvp", "1", "badge", "x", None)

    assert interaction.followup.sent[0]["content"] == "❌ The server refused: Badge 'x' was not found"


@pytest.mark.asyncio
async def test_clear_and_delivered_report_what_changed() -> None:
    service, interaction = _Service(), _interaction()
    cog = _cog(service)

    await cog.cmd_prize_clear.callback(cog, interaction, "minipvp", "2")
    await cog.cmd_prize_delivered.callback(cog, interaction, "minipvp", 3, 2, " sent ")
    await cog.cmd_prize_delivered.callback(cog, interaction, "minipvp", 3, 2, None, 14)

    assert [name for name, _ in service.calls] == ["clear", "delivered", "delivered"]
    assert service.calls[1][1]["note"] == "sent"
    assert service.calls[1][1]["player_pid"] is None
    assert service.calls[2][1]["player_pid"] == 14
    assert "Marked 2 prize(s) of place 2 in" in interaction.followup.sent[1]["content"]
    assert "of place 2 for player #14 in" in interaction.followup.sent[2]["content"]


@pytest.mark.asyncio
async def test_list_is_private_and_shows_the_season_and_its_grants() -> None:
    season = _season_model("ARCHIVED", (CHAMPION,))

    class _Ratings:
        async def find_season(self, ladder, number):
            return season if number == 3 else None

        async def current_season(self, ladder):
            return season

        async def prize_grants(self, ladder, number):
            return [PrizeGrantRecord("minipvp:3", 1, "ace", "badge", "season-champion", None, "GRANTED", "system", None)]

        async def nicknames(self, uuids):
            return {"ace": "Ace"}

    interaction = _interaction()
    cog = _cog(_Service(), _Ratings())

    await cog.cmd_prize_list.callback(cog, interaction, "minipvp", 3)
    await cog.cmd_prize_list.callback(cog, interaction, "minipvp", 9)

    shown = interaction.response.sent[0]
    assert shown["ephemeral"] is True
    assert "Ace" in shown["embed"].fields[0].value
    assert "No such season" in interaction.response.sent[1]["content"]


@pytest.mark.asyncio
async def test_badge_prizes_suggest_grantable_badges_and_custom_ones_nothing() -> None:
    from xcore_discord_bot.cogs.seasons import _autocomplete_badge

    def asking(kind: str | None) -> SimpleNamespace:
        return SimpleNamespace(namespace=SimpleNamespace(kind=kind))

    badge = await _autocomplete_badge(asking("badge"), "champ")

    assert [choice.value for choice in badge] == ["season-champion"]
    assert await _autocomplete_badge(asking("custom"), "champ") == []
    assert await _autocomplete_badge(asking(None), "") == []
    assert "admin" not in [choice.value for choice in await _autocomplete_badge(asking("badge"), "")]
