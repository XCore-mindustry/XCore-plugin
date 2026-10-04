from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta
from types import MethodType, SimpleNamespace
from typing import Any

import pytest
from xcore_protocol.generated.rating import RatingSeasonRescheduleRequestV1Operation

from xcore_discord_bot.daemons.rating_merge_daemon import RatingMergeDaemon
from xcore_discord_bot.dto import AccountMergeResult, PlayerRecord
from xcore_discord_bot.redis_bus import RedisBus, RpcFailed, RpcRejected
from xcore_discord_bot.registry import server_registry
from xcore_discord_bot.rpc.mindustry_rpc import (
    MAX_SERVERS_TRIED,
    MindustryRpcClient,
    NoLiveServerError,
)
from xcore_discord_bot.services.player_service import PlayerService
from xcore_discord_bot.services.rating_service import RatingService, RefusedMerge


class _Store:
    def __init__(self) -> None:
        self.pending: set[tuple[str, str]] = set()
        self.failed: dict[tuple[str, str], str] = {}

    async def add_pending_merge(self, source: str, target: str) -> None:
        self.pending.add((source, target))

    async def clear_pending_merge(self, source: str, target: str) -> None:
        self.pending.discard((source, target))

    async def pending_merges(self) -> list[tuple[str, str]]:
        return sorted(self.pending - set(self.failed))

    async def fail_pending_merge(self, source: str, target: str, error: str) -> None:
        self.failed[(source, target)] = error


class _Rpc:
    def __init__(self, *errors: Exception | None) -> None:
        self.errors = list(errors)
        self.merged: list[tuple[str, str]] = []
        self.rescheduled: list[dict[str, Any]] = []

    async def merge_ratings(self, *, source_uuid, target_uuid, timeout_ms):
        error = self.errors.pop(0) if self.errors else None
        if error:
            raise error
        self.merged.append((source_uuid, target_uuid))

    async def reschedule_season(self, **kwargs):
        self.rescheduled.append(kwargs)
        return "response"


def _service(*errors: Exception | None) -> tuple[RatingService, _Store, _Rpc]:
    store, rpc = _Store(), _Rpc(*errors)
    return RatingService(store=store, rpc=rpc, timeout_ms=1234), store, rpc


@pytest.mark.asyncio
async def test_successful_merge_leaves_nothing_queued() -> None:
    service, store, rpc = _service()

    outcome = await service.merge_standings("s", "t")

    assert outcome.merged is True
    assert rpc.merged == [("s", "t")]
    assert store.pending == set()


@pytest.mark.asyncio
async def test_unreachable_servers_queue_the_merge_and_a_retry_delivers_it() -> None:
    service, store, rpc = _service(TimeoutError(), None)

    outcome = await service.merge_standings("s", "t")
    assert (outcome.merged, outcome.queued) == (False, True)
    assert store.pending == {("s", "t")}

    report = await service.retry_pending_merges()
    assert (report.delivered, report.refused) == (1, ())
    assert store.pending == set()
    assert rpc.merged == [("s", "t")]


@pytest.mark.asyncio
async def test_refused_merge_is_reported_and_never_queued() -> None:
    refusal = RpcRejected("rating.accounts.merge.request", "REJECTED", "nope")
    service, store, _ = _service(refusal)

    outcome = await service.merge_standings("s", "t")

    assert (outcome.merged, outcome.queued) == (False, False)
    assert "nope" in (outcome.error or "")
    assert store.pending == set()


@pytest.mark.asyncio
async def test_a_merge_the_server_could_not_carry_out_is_queued_and_retried() -> None:
    failure = RpcFailed("rating.accounts.merge.request", "FAILED", "see the server log")
    service, store, rpc = _service(failure, failure, None)

    outcome = await service.merge_standings("s", "t")

    assert (outcome.merged, outcome.queued) == (False, True)
    assert store.pending == {("s", "t")}

    # Still failing: it stays in the queue instead of being written off as refused.
    report = await service.retry_pending_merges()
    assert (report.delivered, report.refused) == (0, ())
    assert store.failed == {}

    report = await service.retry_pending_merges()
    assert report.delivered == 1
    assert store.pending == set()
    assert rpc.merged == [("s", "t")]


@pytest.mark.asyncio
async def test_a_queued_merge_that_is_refused_is_kept_reported_and_not_retried() -> None:
    service, store, rpc = _service(RpcRejected("x", "REJECTED", "nope"))
    store.pending.add(("s", "t"))

    report = await service.retry_pending_merges()

    assert report.delivered == 0
    assert [(r.source_uuid, r.target_uuid) for r in report.refused] == [("s", "t")]
    assert "nope" in report.refused[0].error
    # Still on record for an administrator, but out of the retry queue.
    assert store.pending == {("s", "t")}
    assert "nope" in store.failed[("s", "t")]
    assert (await service.retry_pending_merges()).refused == ()
    assert rpc.merged == []


@pytest.mark.asyncio
async def test_the_daemon_tells_the_administrators_about_a_refused_merge() -> None:
    sent: list[str] = []

    class _Channel:
        async def send(self, content, **kwargs) -> None:
            sent.append(content)

    class _Bot:
        private_channel_id = 42

        async def _resolve_messageable_channel(self, channel_id, *, context):
            assert channel_id == 42
            return _Channel()

    service, store, _ = _service(RpcRejected("x", "REJECTED", "nope"))
    store.pending.add(("s", "t"))

    await RatingMergeDaemon(_Bot(), service).run_once()
    await RatingMergeDaemon(_Bot(), service).run_once()

    assert len(sent) == 1
    assert "`s` → `t`" in sent[0] and "nope" in sent[0]


@pytest.mark.asyncio
async def test_the_daemon_survives_a_refusal_it_cannot_report() -> None:
    class _Bot:
        private_channel_id = 0

    class _Ratings:
        async def retry_pending_merges(self):
            from xcore_discord_bot.services.rating_service import MergeRetryReport

            return MergeRetryReport(refused=(RefusedMerge("s", "t", "nope"),))

    await RatingMergeDaemon(_Bot(), _Ratings()).run_once()


@pytest.mark.asyncio
async def test_a_queued_merge_stays_until_a_server_answers() -> None:
    service, store, _ = _service(TimeoutError())
    store.pending.add(("s", "t"))

    report = await service.retry_pending_merges()
    assert (report.delivered, report.refused) == (0, ())
    assert store.pending == {("s", "t")}


def _merge_result(source_uuid="s", target_uuid="t") -> AccountMergeResult:
    return AccountMergeResult(
        success=True,
        source_before=PlayerRecord(pid=1, nickname="a", uuid=source_uuid),
        target_before=PlayerRecord(pid=2, nickname="b", uuid=target_uuid),
        target_after=PlayerRecord(pid=2, nickname="b", uuid=target_uuid),
    )


@pytest.mark.asyncio
async def test_account_merge_moves_ratings_between_the_two_uuids() -> None:
    service, _, rpc = _service()

    outcome = await service.merge_after_account_merge(_merge_result())

    assert outcome is not None and outcome.merged
    assert rpc.merged == [("s", "t")]


@pytest.mark.asyncio
async def test_account_merge_without_distinct_uuids_has_nothing_to_move() -> None:
    service, _, rpc = _service()

    assert await service.merge_after_account_merge(_merge_result("t", "t")) is None
    assert rpc.merged == []


@pytest.mark.asyncio
async def test_player_service_reports_the_rating_outcome_on_the_merge_result() -> None:
    class _PlayerStore:
        async def merge_player_accounts(self, **kwargs):
            return _merge_result()

    class _Bus:
        async def publish_kick_banned(self, **kwargs) -> None:
            pass

    service, _, _ = _service(TimeoutError())
    players = PlayerService(_PlayerStore(), _Bus(), ratings=service)

    result = await players.merge_player_accounts(
        source_pid=1, target_pid=2, actor_name="a", actor_discord_id=None, reason="r"
    )

    assert result.success is True
    assert result.ratings_merged is False
    assert result.ratings_pending is True


@pytest.mark.asyncio
async def test_a_kick_that_cannot_be_sent_does_not_skip_the_rating_merge() -> None:
    class _PlayerStore:
        async def merge_player_accounts(self, **kwargs):
            return _merge_result()

    class _Bus:
        async def publish_kick_banned(self, **kwargs) -> None:
            raise ConnectionError("redis is down")

    service, store, _ = _service(TimeoutError())
    players = PlayerService(_PlayerStore(), _Bus(), ratings=service)

    with pytest.raises(ConnectionError):
        await players.merge_player_accounts(
            source_pid=1, target_pid=2, actor_name="a", actor_discord_id=None, reason="r"
        )

    # The accounts are merged already, so the ratings were attempted and queued regardless.
    assert len(store.pending) == 1


@pytest.mark.asyncio
async def test_season_administration_goes_out_as_one_rpc_each() -> None:
    service, _, rpc = _service()

    await service.extend_season(
        ladder="minipvp",
        by=timedelta(days=2),
        discord_id="5",
        actor_name="Head",
        reason=None,
    )

    call = rpc.rescheduled[0]
    assert call["operation"] is RatingSeasonRescheduleRequestV1Operation.EXTEND
    assert call["extend_seconds"] == 172800
    assert call["timeout_ms"] == 1234


# ------------------------------------------------------------- server fallback


def _live(*names: str) -> None:
    server_registry._servers.clear()
    for name in names:
        server_registry.update_server(
            name=name, channel_id=1, players=0, max_players=10, version="1"
        )


@pytest.mark.asyncio
async def test_rpc_client_tries_the_next_server_when_one_stays_silent() -> None:
    _live("b-server", "a-server")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        if server == "a-server":
            raise TimeoutError()
        return server

    result = await MindustryRpcClient(SimpleNamespace()).on_any_live_server(call)

    assert result == "b-server"
    assert tried == ["a-server", "b-server"]


@pytest.mark.asyncio
async def test_rpc_client_gives_up_after_a_few_silent_servers() -> None:
    _live("a", "b", "c", "d", "e")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        raise TimeoutError()

    with pytest.raises(TimeoutError):
        await MindustryRpcClient(SimpleNamespace()).on_any_live_server(call)

    assert len(tried) == MAX_SERVERS_TRIED


@pytest.mark.asyncio
async def test_rpc_client_does_not_ask_another_server_after_a_refusal() -> None:
    _live("a", "b")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        raise RpcRejected("x", "REJECTED", "no")

    with pytest.raises(RpcRejected):
        await MindustryRpcClient(SimpleNamespace()).on_any_live_server(call)

    assert tried == ["a"]


@pytest.mark.asyncio
async def test_rpc_client_tries_the_next_server_when_one_could_not_carry_it_out() -> None:
    _live("a", "b")
    tried: list[str] = []

    async def call(server: str) -> str:
        tried.append(server)
        if server == "a":
            raise RpcFailed("x", "FAILED", "see the server log")
        return server

    assert await MindustryRpcClient(SimpleNamespace()).on_any_live_server(call) == "b"
    assert tried == ["a", "b"]


@pytest.mark.asyncio
async def test_rpc_client_names_a_season_change_once_for_every_server_it_tries() -> None:
    _live("a", "b")
    seen: list[tuple[str, str, str]] = []

    class _Bus:
        async def rpc_season_reschedule(self, *, server, request_id, **kwargs):
            seen.append(("reschedule", server, request_id))
            if server == "a":
                raise TimeoutError()
            return "moved"

        async def rpc_season_prize_add(self, *, server, request_id, **kwargs):
            seen.append(("add", server, request_id))
            if server == "a":
                raise RpcFailed("x", "FAILED", "see the server log")
            return "added"

        async def rpc_season_prize_remove(self, *, server, request_id, **kwargs):
            seen.append(("remove", server, request_id))
            return "removed"

    client = MindustryRpcClient(_Bus())
    actor = {"discord_id": "5", "actor_name": "Head", "timeout_ms": 100}
    await client.reschedule_season(
        ladder="minipvp",
        operation=RatingSeasonRescheduleRequestV1Operation.END_NOW,
        **actor,
    )
    await client.add_season_prize(ladder="minipvp", prize=None, **actor)
    await client.remove_season_prizes(ladder="minipvp", place_from=1, place_to=3, **actor)

    assert [(kind, server) for kind, server, _ in seen] == [
        ("reschedule", "a"),
        ("reschedule", "b"),
        ("add", "a"),
        ("add", "b"),
        ("remove", "a"),
    ]
    ids = [request_id for _, _, request_id in seen]
    assert all(ids)
    # The server that did not confirm and the one asked after it see the same request, so
    # only one of them applies it; every administrator action is a request of its own.
    assert ids[0] == ids[1] and ids[2] == ids[3]
    assert len({ids[0], ids[2], ids[4]}) == 3


class _ReplyingRedis:
    """Answers every RPC with an error carrying the given code."""

    def __init__(self, error_code: str | None) -> None:
        self.error_code = error_code
        self.correlation_id = ""

    async def xrevrange(self, stream, count):
        return []

    async def xadd(self, stream, fields, **kwargs):
        self.correlation_id = fields["correlation_id"]

    async def xread(self, streams, count, block):
        body = {"correlation_id": self.correlation_id, "status": "error", "error_message": "why"}
        if self.error_code is not None:
            body["error_code"] = self.error_code
        return [("reply", [("1-0", body)])]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("error_code", "expected"),
    [("REJECTED", RpcRejected), ("FAILED", RpcFailed), (None, RpcFailed)],
)
async def test_bus_tells_a_refusal_from_a_failure(error_code, expected) -> None:
    bus = RedisBus(
        SimpleNamespace(
            redis_url="redis://127.0.0.1:6379",
            redis_group_prefix="xcore:cg",
            redis_consumer_name="discord-bot",
        )
    )
    bus._redis = _ReplyingRedis(error_code)

    with pytest.raises(expected) as raised:
        await bus._rpc_request(
            server="mini-pvp",
            rpc_type="rating.accounts.merge.request",
            payload={},
            timeout_ms=500,
        )

    assert type(raised.value) is expected
    assert raised.value.error_message == "why"


@pytest.mark.asyncio
async def test_rpc_client_needs_a_live_server() -> None:
    _live()

    with pytest.raises(NoLiveServerError):
        await MindustryRpcClient(SimpleNamespace()).on_any_live_server(
            lambda server: None
        )


# --------------------------------------------------------------------- the bus


@dataclass
class _Captured:
    server: str | None = None
    rpc_type: str | None = None
    payload: dict[str, Any] | None = None


@pytest.mark.asyncio
async def test_bus_sends_the_canonical_season_and_merge_requests() -> None:
    bus = RedisBus(
        SimpleNamespace(
            redis_url="redis://127.0.0.1:6379",
            redis_group_prefix="xcore:cg",
            redis_consumer_name="discord-bot",
        )
    )
    seen: list[_Captured] = []

    async def fake_rpc_request(self, server, rpc_type, payload, timeout_ms):
        seen.append(_Captured(server, rpc_type, payload))
        if rpc_type == "rating.accounts.merge.request":
            body = {"messageType": "rating.accounts.merge.response", "messageVersion": 1,
                    "server": server, "standingsMerged": 2}
        else:
            body = {
                "messageType": "rating.season.reschedule.response",
                "messageVersion": 1,
                "server": server,
                "season": {
                    "ladder": "minipvp", "season": 1, "name": "S",
                    "startsAt": "2026-01-01T00:00:00Z", "endsAt": "2026-04-01T00:00:00Z",
                },
                "previousEndsAt": "2026-03-01T00:00:00Z",
                "ended": False,
            }
        import json

        return {"payload_json": json.dumps(body)}

    bus._rpc_request = MethodType(fake_rpc_request, bus)

    merged = await bus.rpc_ratings_merge(
        server="mini-pvp", source_uuid="s", target_uuid="t", timeout_ms=100
    )
    moved = await bus.rpc_season_reschedule(
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
    assert seen[0].payload["sourceUuid"] == "s"
    assert seen[1].payload["operation"] == "extend"
    assert seen[1].payload["extendSeconds"] == 60
    assert seen[1].payload["actor"]["actorDiscordId"] == "5"
    assert "requestId" not in seen[1].payload

    await bus.rpc_season_reschedule(
        server="mini-pvp",
        ladder="minipvp",
        operation=RatingSeasonRescheduleRequestV1Operation.END_NOW,
        discord_id="5",
        actor_name="Head",
        timeout_ms=100,
        request_id="req-1",
    )
    assert seen[2].payload["requestId"] == "req-1"


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
