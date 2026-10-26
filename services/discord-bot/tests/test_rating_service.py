from __future__ import annotations

from datetime import timedelta
from types import SimpleNamespace
from typing import Any
from unittest.mock import AsyncMock

import pytest
from backends import GameServer, RpcError, fail, real_bot, refuse, unplug
from bot_fakes import make_rpc, make_settings
from cases_fakes import Database
from redis.exceptions import ConnectionError as RedisConnectionError
from test_account_merge import SOURCE_DOC, TARGET_DOC
from xcore_protocol.generated.rating import RatingSeasonRescheduleRequestV1Operation
from xcore_protocol.generated.shared import SeasonPrizeV1, SeasonPrizeV1Kind

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.transport import RpcFailed, RpcRejected
from xcore_discord_bot.dto import AccountMergeResult, PlayerRecord
from xcore_discord_bot.rating_store import PENDING_MERGES
from xcore_discord_bot.registry import LiveServerRegistry
from xcore_discord_bot.rpc.mindustry_rpc import (
    MAX_SERVERS_TRIED,
    NoLiveServerError,
)

MERGE = "rating.accounts.merge.request"
RESCHEDULE = "rating.season.reschedule.request"
PRIZES = "rating.season.prizes.set.request"
# A response the bot cannot read.
GARBLED: dict[str, Any] = {"messageType": "rating.accounts.merge.response"}


def _merged(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "messageType": "rating.accounts.merge.response",
        "messageVersion": 1,
        "server": payload["server"],
        "standingsMerged": 2,
    }


async def _ratings(
    *answers: dict[str, Any] | RpcError | None, online: bool = True, **settings: Any
) -> tuple[XCoreDiscordBot, Database]:
    """A bot with one game server online. It gives the first merge requests ``answers``
    in turn, None staying silent, and merges every later one."""
    bot, db = await real_bot(settings=make_settings(rpc_timeout_ms=50, **settings))
    if online:
        queue = list(answers)
        GameServer(bot).answers[MERGE] = lambda payload: (
            queue.pop(0) if queue else _merged(payload)
        )
    return bot, db


def _asked(bot: XCoreDiscordBot) -> list[tuple[str, str]]:
    """Every merge the game server was asked for, answered or not."""
    game = bot.bus.redis.game_servers["mini-pvp"]
    return [(p["sourceUuid"], p["targetUuid"]) for p in game.sent(MERGE)]


async def _queued(bot: XCoreDiscordBot) -> list[tuple[str, str]]:
    return await bot.container.ratings.pending_merges()


def _refused(db: Database) -> dict[tuple[str, str], str]:
    """The merges kept on record for an administrator, with the server's answer."""
    return {
        (doc["source_uuid"], doc["target_uuid"]): doc["error"]
        for doc in db[PENDING_MERGES].docs
        if doc.get("failed_at") is not None
    }


@pytest.mark.asyncio
async def test_successful_merge_leaves_nothing_queued() -> None:
    bot, _ = await _ratings()

    outcome = await bot.container.rating_service.merge_standings("s", "t")

    assert outcome.merged is True
    assert _asked(bot) == [("s", "t")]
    assert await _queued(bot) == []


@pytest.mark.asyncio
async def test_unreachable_servers_queue_the_merge_and_a_retry_delivers_it() -> None:
    bot, _ = await _ratings(None)
    service = bot.container.rating_service

    outcome = await service.merge_standings("s", "t")
    assert (outcome.merged, outcome.queued) == (False, True)
    assert await _queued(bot) == [("s", "t")]

    report = await service.retry_pending_merges()
    assert (report.delivered, report.refused) == (1, ())
    assert await _queued(bot) == []
    assert _asked(bot) == [("s", "t"), ("s", "t")]


@pytest.mark.asyncio
async def test_refused_merge_is_reported_and_never_queued() -> None:
    bot, db = await _ratings(refuse("nope"))

    outcome = await bot.container.rating_service.merge_standings("s", "t")

    assert (outcome.merged, outcome.queued) == (False, False)
    assert "nope" in (outcome.error or "")
    assert await _queued(bot) == [] and not db[PENDING_MERGES].docs


@pytest.mark.asyncio
async def test_a_merge_the_server_could_not_carry_out_is_queued_and_retried() -> None:
    failure = fail("see the server log")
    bot, db = await _ratings(failure, failure)
    service = bot.container.rating_service

    outcome = await service.merge_standings("s", "t")

    assert (outcome.merged, outcome.queued) == (False, True)
    assert await _queued(bot) == [("s", "t")]

    # Still failing: it stays in the queue instead of being written off as refused.
    report = await service.retry_pending_merges()
    assert (report.delivered, report.refused) == (0, ())
    assert _refused(db) == {}

    report = await service.retry_pending_merges()
    assert report.delivered == 1
    assert await _queued(bot) == []
    assert len(_asked(bot)) == 3


@pytest.mark.asyncio
async def test_a_queued_merge_that_is_refused_is_kept_reported_and_not_retried() -> (
    None
):
    bot, db = await _ratings(refuse("nope"))
    service = bot.container.rating_service
    await bot.container.ratings.add_pending_merge("s", "t")

    report = await service.retry_pending_merges()

    assert report.delivered == 0
    assert [(r.source_uuid, r.target_uuid) for r in report.refused] == [("s", "t")]
    assert "nope" in report.refused[0].error
    # Still on record for an administrator, but out of the retry queue.
    assert await _queued(bot) == []
    assert "nope" in _refused(db)[("s", "t")]
    assert (await service.retry_pending_merges()).refused == ()
    assert _asked(bot) == [("s", "t")]


@pytest.mark.asyncio
async def test_the_daemon_tells_the_administrators_about_a_refused_merge() -> None:
    bot, _ = await _ratings(refuse("nope"), discord_private_channel_id=42)
    channel = SimpleNamespace(send=AsyncMock())
    bot.resolve_channel = AsyncMock(return_value=channel)  # type: ignore[method-assign]
    await bot.container.ratings.add_pending_merge("s", "t")

    await bot.container.rating_merge_daemon.run_once()
    await bot.container.rating_merge_daemon.run_once()

    assert bot.resolve_channel.await_args is not None
    assert bot.resolve_channel.await_args.args == (42,)
    channel.send.assert_awaited_once()
    sent = channel.send.await_args.args[0]
    assert "`s` → `t`" in sent and "nope" in sent


@pytest.mark.asyncio
async def test_the_daemon_survives_a_refusal_it_cannot_report() -> None:
    bot, db = await _ratings(refuse("nope"), discord_private_channel_id=0)
    await bot.container.ratings.add_pending_merge("s", "t")

    await bot.container.rating_merge_daemon.run_once()

    assert "nope" in _refused(db)[("s", "t")]


@pytest.mark.asyncio
async def test_a_queued_merge_stays_until_a_server_answers() -> None:
    bot, _ = await _ratings(None)
    await bot.container.ratings.add_pending_merge("s", "t")

    report = await bot.container.rating_service.retry_pending_merges()
    assert (report.delivered, report.refused) == (0, ())
    assert await _queued(bot) == [("s", "t")]


@pytest.mark.asyncio
async def test_an_unexpected_merge_failure_is_logged_with_its_traceback_and_queued(
    caplog,
) -> None:
    bot, _ = await _ratings(GARBLED, GARBLED)
    service = bot.container.rating_service

    outcome = await service.merge_standings("s", "t")
    # The accounts are already merged, so the ratings must not be dropped.
    assert (outcome.merged, outcome.queued) == (False, True)
    assert await _queued(bot) == [("s", "t")]

    report = await service.retry_pending_merges()
    assert (report.delivered, report.refused) == (0, ())
    assert await _queued(bot) == [("s", "t")]

    unexpected = [r for r in caplog.records if "failed unexpectedly" in r.message]
    assert len(unexpected) == 2
    assert all(r.exc_info and r.exc_info[0] is ValueError for r in unexpected)


@pytest.mark.asyncio
@pytest.mark.parametrize("outage", ["no server online", "failing", "silent", "redis"])
async def test_an_unavailable_server_is_not_logged_as_a_failure(outage, caplog) -> None:
    answer = fail("busy") if outage == "failing" else None
    bot, _ = await _ratings(answer, answer, online=outage != "no server online")
    if outage == "redis":
        unplug(bot.bus)
    service = bot.container.rating_service

    await service.merge_standings("s", "t")
    await service.retry_pending_merges()

    assert await _queued(bot) == [("s", "t")]
    assert all(r.exc_info is None for r in caplog.records)


def _merge_result(source_uuid="s", target_uuid="t") -> AccountMergeResult:
    return AccountMergeResult(
        success=True,
        source_before=PlayerRecord(pid=1, nickname="a", uuid=source_uuid),
        target_before=PlayerRecord(pid=2, nickname="b", uuid=target_uuid),
        target_after=PlayerRecord(pid=2, nickname="b", uuid=target_uuid),
    )


@pytest.mark.asyncio
async def test_account_merge_moves_ratings_between_the_two_uuids() -> None:
    bot, _ = await _ratings()

    outcome = await bot.container.rating_service.merge_after_account_merge(
        _merge_result()
    )

    assert outcome is not None and outcome.merged
    assert _asked(bot) == [("s", "t")]


@pytest.mark.asyncio
async def test_account_merge_without_distinct_uuids_has_nothing_to_move() -> None:
    bot, _ = await _ratings()

    service = bot.container.rating_service
    assert await service.merge_after_account_merge(_merge_result("t", "t")) is None
    assert _asked(bot) == []


async def _merge_accounts(bot: XCoreDiscordBot, db: Database) -> AccountMergeResult:
    await db["players"].insert_one(dict(SOURCE_DOC))
    await db["players"].insert_one(dict(TARGET_DOC))
    return await bot.container.players.merge_player_accounts(
        source_pid=SOURCE_DOC["pid"],
        target_pid=TARGET_DOC["pid"],
        actor_name="a",
        actor_discord_id=None,
        reason="r",
    )


@pytest.mark.asyncio
async def test_player_service_reports_the_rating_outcome_on_the_merge_result() -> None:
    bot, db = await _ratings(None)

    result = await _merge_accounts(bot, db)

    assert result.success is True
    assert result.ratings_merged is False
    assert result.ratings_pending is True
    assert _asked(bot) == [("source-uuid", "target-uuid")]


@pytest.mark.asyncio
async def test_a_kick_that_cannot_be_sent_does_not_skip_the_rating_merge() -> None:
    bot, db = await _ratings()
    unplug(bot.bus)

    with pytest.raises(RedisConnectionError):
        await _merge_accounts(bot, db)

    # The accounts are merged already, so the ratings were attempted and queued regardless.
    assert await _queued(bot) == [("source-uuid", "target-uuid")]


@pytest.mark.asyncio
async def test_season_administration_goes_out_as_one_rpc_each() -> None:
    bot, _ = await _ratings()
    game = bot.bus.redis.game_servers["mini-pvp"]
    game.answers[RESCHEDULE] = _rescheduled

    await bot.container.rating_service.extend_season(
        ladder="minipvp",
        by=timedelta(days=2),
        discord_id="5",
        actor_name="Head",
        reason=None,
    )

    [request] = game.sent(RESCHEDULE)
    assert request["operation"] == "extend"
    assert request["extendSeconds"] == 172800
    assert game.requests[0]["timeout_ms"] == "50"


def _rescheduled(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "messageType": "rating.season.reschedule.response",
        "messageVersion": 1,
        "server": payload["server"],
        "season": {
            "ladder": "minipvp",
            "season": 1,
            "name": "S",
            "startsAt": "2026-01-01T00:00:00Z",
            "endsAt": "2026-04-01T00:00:00Z",
        },
        "previousEndsAt": "2026-03-01T00:00:00Z",
        "ended": False,
    }


def _prizes_set(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "messageType": "rating.season.prizes.set.response",
        "messageVersion": 1,
        "server": payload["server"],
        "season": _rescheduled(payload)["season"],
        "prizes": [],
    }


# ------------------------------------------------------------- server fallback


def _live(*names: str) -> LiveServerRegistry:
    servers = LiveServerRegistry()
    for name in names:
        servers.update_server(
            name=name, channel_id=1, players=0, max_players=10, version="1"
        )
    return servers


@pytest.mark.asyncio
async def test_rpc_client_tries_the_next_server_when_one_stays_silent() -> None:
    servers = _live("b-server", "a-server")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        if server == "a-server":
            raise TimeoutError()
        return server

    result = await make_rpc(
        SimpleNamespace(), rating=SimpleNamespace(), servers=servers
    ).on_any_live_server(call)

    assert result == "b-server"
    assert tried == ["a-server", "b-server"]


@pytest.mark.asyncio
async def test_rpc_client_gives_up_after_a_few_silent_servers() -> None:
    servers = _live("a", "b", "c", "d", "e")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        raise TimeoutError()

    with pytest.raises(TimeoutError):
        await make_rpc(
            SimpleNamespace(), rating=SimpleNamespace(), servers=servers
        ).on_any_live_server(call)

    assert len(tried) == MAX_SERVERS_TRIED


@pytest.mark.asyncio
async def test_rpc_client_does_not_ask_another_server_after_a_refusal() -> None:
    servers = _live("a", "b")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        raise RpcRejected("x", "REJECTED", "no")

    with pytest.raises(RpcRejected):
        await make_rpc(
            SimpleNamespace(), rating=SimpleNamespace(), servers=servers
        ).on_any_live_server(call)

    assert tried == ["a"]


@pytest.mark.asyncio
async def test_rpc_client_tries_the_next_server_when_one_could_not_carry_it_out() -> (
    None
):
    servers = _live("a", "b")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        if server == "a":
            raise RpcFailed("x", "FAILED", "see the server log")
        return server

    assert (
        await make_rpc(
            SimpleNamespace(), rating=SimpleNamespace(), servers=servers
        ).on_any_live_server(call)
        == "b"
    )
    assert tried == ["a", "b"]


@pytest.mark.asyncio
async def test_rpc_client_names_a_season_change_once_for_every_server_it_tries() -> (
    None
):
    bot, _ = await real_bot()
    a, b = GameServer(bot, "a"), GameServer(bot, "b")
    # "a" stays silent on the season and cannot set prizes; "b" does everything asked.
    a.answers[RESCHEDULE] = lambda payload: None
    a.answers[PRIZES] = lambda payload: (
        fail("see the server log")
        if payload["operation"] == "add"
        else _prizes_set(payload)
    )
    b.answers[RESCHEDULE] = _rescheduled
    b.answers[PRIZES] = _prizes_set

    client = bot.container.rpc
    actor = {"discord_id": "5", "actor_name": "Head", "timeout_ms": 100}
    await client.reschedule_season(
        ladder="minipvp",
        operation=RatingSeasonRescheduleRequestV1Operation.END_NOW,
        **actor,
    )
    prize = SeasonPrizeV1(
        placeFrom=1, placeTo=1, kind=SeasonPrizeV1Kind.BADGE, value="veteran"
    )
    await client.add_season_prize(ladder="minipvp", prize=prize, **actor)
    await client.remove_season_prizes(
        ladder="minipvp", place_from=1, place_to=3, **actor
    )

    seen = [
        (payload.get("operation"), game.name, payload["requestId"])
        for game in (a, b)
        for payload in game.sent(RESCHEDULE) + game.sent(PRIZES)
    ]
    assert sorted((kind, server) for kind, server, _ in seen) == [
        ("add", "a"),
        ("add", "b"),
        ("end-now", "a"),
        ("end-now", "b"),
        ("remove", "a"),
    ]
    ids = {(kind, server): request_id for kind, server, request_id in seen}
    assert all(ids.values())
    # The server that did not confirm and the one asked after it see the same request, so
    # only one of them applies it; every administrator action is a request of its own.
    assert ids["end-now", "a"] == ids["end-now", "b"]
    assert ids["add", "a"] == ids["add", "b"]
    assert len({ids["end-now", "a"], ids["add", "a"], ids["remove", "a"]}) == 3


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("answer", "expected"),
    [
        (refuse("why"), RpcRejected),
        (fail("why"), RpcFailed),
        (RpcError(None, "why"), RpcFailed),
    ],
)
async def test_bus_tells_a_refusal_from_a_failure(answer, expected) -> None:
    bot, _ = await real_bot()
    GameServer(bot).answers[MERGE] = lambda payload: answer

    with pytest.raises(expected) as raised:
        await bot.container.rating_bus.rpc_ratings_merge(
            server="mini-pvp", source_uuid="s", target_uuid="t", timeout_ms=500
        )

    assert type(raised.value) is expected
    assert raised.value.error_message == "why"


async def test_rpc_client_needs_a_live_server() -> None:
    servers = _live()

    with pytest.raises(NoLiveServerError):
        await make_rpc(
            SimpleNamespace(), rating=SimpleNamespace(), servers=servers
        ).on_any_live_server(lambda server: None)


# --------------------------------------------------------------------- the bus


@pytest.mark.asyncio
async def test_bus_sends_the_canonical_season_and_merge_requests() -> None:
    bot, _ = await real_bot()
    game = GameServer(bot)
    game.answers = {MERGE: _merged, RESCHEDULE: _rescheduled}
    rating = bot.container.rating_bus

    merged = await rating.rpc_ratings_merge(
        server="mini-pvp", source_uuid="s", target_uuid="t", timeout_ms=100
    )
    moved = await rating.rpc_season_reschedule(
        server="mini-pvp",
        ladder="minipvp",
        operation=RatingSeasonRescheduleRequestV1Operation.EXTEND,
        discord_id="5",
        actor_name="Head",
        timeout_ms=100,
        extend_seconds=60,
    )

    assert merged.standingsMerged == 2
    assert moved.ended is False
    [merge] = game.sent(MERGE)
    assert (merge["sourceUuid"], merge["targetUuid"]) == ("s", "t")
    [extend] = game.sent(RESCHEDULE)
    assert extend["operation"] == "extend"
    assert extend["extendSeconds"] == 60
    assert extend["actor"]["actorDiscordId"] == "5"
    assert "requestId" not in extend

    await rating.rpc_season_reschedule(
        server="mini-pvp",
        ladder="minipvp",
        operation=RatingSeasonRescheduleRequestV1Operation.END_NOW,
        discord_id="5",
        actor_name="Head",
        timeout_ms=100,
        request_id="req-1",
    )
    assert game.sent(RESCHEDULE)[1]["requestId"] == "req-1"


def test_merge_embed_tells_the_admin_what_happened_to_the_ratings() -> None:
    from dataclasses import replace

    from xcore_discord_bot.presentation import build_merge_result_embed

    def notes(**flags: Any) -> str:
        embed = build_merge_result_embed(replace(_merge_result(), **flags))
        return next((f.value for f in embed.fields if "Notes" in f.name), "")

    assert "ratings moved" in notes(ratings_merged=True)
    assert "retrying" in notes(ratings_merged=False, ratings_pending=True)
    assert "NOT moved" in notes(ratings_merged=False, ratings_error="boom")
    assert "ratings" not in notes()
