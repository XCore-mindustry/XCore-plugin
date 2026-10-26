from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import discord
import pytest
from bot_fakes import make_bot, make_settings
from fakeredis.aioredis import FakeRedis
from test_cases_redis_transport import report_payload
from xcore_protocol.generated.moderation import ModerationBanCreatedV1
from xcore_protocol.generated.shared import ActorRefV1, PlayerRefV1

from xcore_discord_bot import runtime_consumers
from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.cases import parse_case_report_payload
from xcore_discord_bot.bus.moderation import ModerationBus
from xcore_discord_bot.cases.model import CaseKind
from xcore_discord_bot.cases.notify import Outcome
from xcore_discord_bot.cases.redis_transport import RedisCaseTransport
from xcore_discord_bot.cases.registry import Flow, FlowRegistry
from xcore_discord_bot.cases.transport import NoCaseTransport
from xcore_discord_bot.daemons.stream_supervisor import StreamSupervisor
from xcore_discord_bot.player_pids import NO_PID
from xcore_discord_bot.redis_bus import RedisBus
from xcore_discord_bot.settings import Settings


def service(
    *, flag=True, registered=True, hook=True, active=True, storage=True, callback=None
):
    settings = SimpleNamespace(
        cases_reports_enabled=flag,
        cases_appeals_enabled=False,
        cases_badges_enabled=False,
        cases_maps_enabled=False,
        cases_complaints_enabled=False,
        cases_enabled=flag,
    )
    registry = FlowRegistry()
    if registered:
        registry.register(
            Flow(
                kind=CaseKind.REPORT,
                card_builder=lambda case, locale: discord.Embed(),
                event_hooks={
                    runtime_consumers.CASE_REPORT_EVENT: callback or AsyncMock()
                }
                if hook
                else {},
            )
        )
        if active:
            registry.enable(CaseKind.REPORT)
    return SimpleNamespace(
        settings=settings, registry=registry, storage_available=storage
    )


def bot_for(cases, bus=None):
    bot = make_bot(bus=bus)
    bot.container.cases = cases
    ready = asyncio.Event()
    ready.set()
    bot.container.cases_reconcile_daemon = SimpleNamespace(ready=ready)
    return bot


@pytest.mark.parametrize(
    "kwargs,configured,active",
    [
        ({"flag": False}, False, False),
        ({"registered": False}, False, False),
        ({"hook": False}, False, False),
        ({"active": False}, True, False),
        ({"storage": False}, True, False),
        ({}, True, True),
    ],
)
def test_reports_require_flag_registered_hook_and_successful_startup(
    kwargs, configured, active
):
    bot = bot_for(service(**kwargs))
    assert runtime_consumers.case_reports_configured(bot) is configured
    assert runtime_consumers.case_reports_active(bot) is active


@pytest.mark.parametrize(
    "kwargs", [{"flag": False}, {"registered": False}, {"hook": False}]
)
async def test_unavailable_report_flow_calls_no_stream_consumer(kwargs):
    bot = bot_for(service(**kwargs), SimpleNamespace(consume_case_reports=AsyncMock()))
    await runtime_consumers.consume_case_reports(bot)
    bot.bus.consume_case_reports.assert_not_awaited()


async def once(bot, label, consume, callback):
    await consume(callback)


async def test_canonical_report_dispatch_passes_service_and_generated_model(
    monkeypatch,
):
    hook = AsyncMock()
    cases = service(callback=hook)
    payload = parse_case_report_payload(report_payload())
    bus = SimpleNamespace()

    async def consume(callback, *, is_active):
        assert is_active() is True
        await callback(payload)

    bus.consume_case_reports = consume
    bot = bot_for(cases, bus)
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    await runtime_consumers.consume_case_reports(bot)
    hook.assert_awaited_once_with(cases, payload)


async def test_report_hook_failure_propagates_to_stream_handler(monkeypatch):
    hook = AsyncMock(side_effect=RuntimeError("report creation failed"))
    cases = service(callback=hook)
    payload = parse_case_report_payload(report_payload())

    async def consume(callback, *, is_active):
        await callback(payload)

    bot = bot_for(cases, SimpleNamespace(consume_case_reports=consume))
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    with pytest.raises(RuntimeError, match="report creation failed"):
        await runtime_consumers.consume_case_reports(bot)
    hook.assert_awaited_once_with(cases, payload)


async def test_report_runtime_waits_for_startup_before_creating_group(monkeypatch):
    redis = FakeRedis(decode_responses=True)
    bus = RedisBus(
        SimpleNamespace(redis_group_prefix="xcore:cg", redis_consumer_name="bot")
    )
    bus._redis = redis
    ready = asyncio.Event()
    cases = service()
    bot = bot_for(cases, bus)
    bot.container.cases_reconcile_daemon = SimpleNamespace(ready=ready)
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    task = asyncio.create_task(runtime_consumers.consume_case_reports(bot))
    try:
        await asyncio.sleep(0)
        assert await redis.keys("*") == []
        consume = bot.container.cases_bus.consume_case_reports = AsyncMock()
        ready.set()
        await task
        consume.assert_awaited_once()
    finally:
        task.cancel()
        await asyncio.gather(task, return_exceptions=True)
        await redis.aclose()


@pytest.mark.parametrize(
    "kwargs,expected",
    [
        ({"flag": False}, False),
        ({"registered": False}, False),
        ({"hook": False}, False),
        ({"active": False}, True),
        ({}, True),
    ],
)
async def test_supervisor_installs_only_configured_report_task(
    monkeypatch, kwargs, expected
):
    names = []

    async def idle(bot):
        await asyncio.Event().wait()

    for name in (
        "consume_game_chat",
        "consume_global_chat",
        "consume_join_leave",
        "consume_server_actions",
        "consume_bans",
        "consume_mutes",
        "consume_vote_kicks",
        "consume_season_started",
        "consume_season_ending_soon",
        "consume_season_ended",
        "consume_season_rescheduled",
        "consume_server_heartbeats",
        "consume_case_reports",
    ):
        monkeypatch.setattr(runtime_consumers, name, idle)
    supervisor = StreamSupervisor(bot_for(service(**kwargs)))
    try:
        supervisor.start()
        names = [task.get_name() for task in supervisor._tasks]
        supervisor.start()
        assert len(supervisor._tasks) == len(names)
        assert ("redis-case-report-consumer" in names) is expected
        assert len(names) == 12 + int(expected)
    finally:
        await supervisor.stop()


@pytest.mark.parametrize("enabled", [False, True])
async def test_bot_installs_adapter_after_container_creation_and_wires_notifier(
    monkeypatch, enabled
):
    from cases_dummy_flow import DUMMY_FLOW
    from cases_fakes import core

    cases, _, _ = await core(flow=DUMMY_FLOW)
    cases_bus = MagicMock()
    container = SimpleNamespace(cases=cases, cases_bus=cases_bus, rpc=MagicMock())
    factory = MagicMock(return_value=container)
    monkeypatch.setattr("xcore_discord_bot.bot.ServiceContainer.create", factory)
    settings = Settings(
        _env_file=None,
        discord_bot_token="fake",
        discord_admin_role_id=10,
        discord_private_channel_id=20,
        cases_reports_enabled=enabled,
    )
    bus, store = MagicMock(), MagicMock()
    bot = XCoreDiscordBot(settings, bus, store)
    assert factory.call_count == 1
    assert isinstance(
        cases.transport, RedisCaseTransport if enabled else NoCaseTransport
    )
    if enabled:
        from test_cases_service import opened

        cases_bus.publish_case_notice = AsyncMock()
        case = await opened(cases)
        await cases.notifier.send(
            cases, case, Outcome(case.opener, "report_resolved", "cases-outcome")
        )
        cases_bus.publish_case_notice.assert_awaited_once_with(
            notice_id=f"{case.number}:opener-secret:report_resolved",
            player_uuid="opener-secret",
            server="server",
        )
        assert cases.transport._cases is cases_bus
        assert cases.transport._rpc is container.rpc
    # Close the base bot only; fake container daemons do not own running tasks.
    await discord.ext.commands.Bot.close(bot)


def ban_event(pid=None, ban_id=None):
    return ModerationBanCreatedV1(
        target=PlayerRefV1(playerUuid="target", playerPid=pid, playerName="Target"),
        actor=ActorRefV1(actorName="Admin", actorDiscordId="123"),
        reason="Rule 1",
        server="mini-pvp",
        occurredAt="2026-10-13T12:00:00Z",
        banId=ban_id,
    )


@pytest.mark.parametrize("pid", [0, -5, NO_PID, None])
@pytest.mark.parametrize("ban_id", [None, 57])
async def test_ban_consumer_old_and_new_signed_pid_and_ban_number(
    monkeypatch, pid, ban_id
):
    log = AsyncMock()
    monkeypatch.setattr(runtime_consumers, "post_ban_log", log)
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    event = ban_event(pid, ban_id)

    async def consume(callback):
        await callback(event)

    lookup = AsyncMock(return_value=SimpleNamespace(pid=-7))
    bot = make_bot(settings=make_settings(discord_bans_channel_id=777))
    bot.container.moderation_bus.consume_bans = consume
    bot.store.find_player_by_uuid = lookup
    bot.store.now_utc = lambda: datetime(2026, 10, 13, tzinfo=UTC)
    await runtime_consumers.consume_bans(bot)
    assert log.call_args.kwargs["pid"] == (-7 if pid is None else pid)
    if ban_id is not None:
        assert log.call_args.kwargs["ban_id"] == ban_id
    else:
        assert "ban_id" not in log.call_args.kwargs
    assert lookup.await_count == int(pid is None)
    assert ModerationBanCreatedV1.from_payload(event.to_payload()).banId == ban_id


async def test_ban_cases_dispatch_works_without_log_channel_and_propagates_errors(
    monkeypatch,
):
    cases = service()
    hook = AsyncMock(side_effect=RuntimeError("auto-resolution failed"))
    cases.registry.require(CaseKind.REPORT).event_hooks[
        runtime_consumers.CASE_BAN_EVENT
    ] = hook
    event = ban_event(0, 57)

    async def consume(callback):
        await callback(event)

    bot = bot_for(cases)
    bot.container.moderation_bus.consume_bans = consume
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    log = AsyncMock()
    monkeypatch.setattr(runtime_consumers, "post_ban_log", log)
    with pytest.raises(RuntimeError, match="auto-resolution failed"):
        await runtime_consumers.consume_bans(bot)
    hook.assert_awaited_once_with(cases, event)
    log.assert_not_awaited()


async def test_direct_bans_without_stream_identity_do_not_share_log_checkpoint(
    monkeypatch,
):
    redis = FakeRedis(decode_responses=True)
    bus = RedisBus(
        SimpleNamespace(redis_group_prefix="xcore:cg", redis_consumer_name="bot")
    )
    bus._redis = redis
    cases = service()
    hook = AsyncMock(side_effect=[RuntimeError("failed"), None, None])
    cases.registry.require(CaseKind.REPORT).event_hooks[
        runtime_consumers.CASE_BAN_EVENT
    ] = hook
    event = ban_event(-5, 57)
    log = AsyncMock()
    monkeypatch.setattr(runtime_consumers, "post_ban_log", log)
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    bot = bot_for(cases, bus)
    bot.settings = make_settings(discord_bans_channel_id=777)
    bot.store.now_utc = lambda: datetime(2026, 10, 13, tzinfo=UTC)

    async def consume(callback):
        with pytest.raises(RuntimeError, match="failed"):
            await callback(event)
        await callback(event)
        await callback(event)

    bot.container.moderation_bus.consume_bans = consume
    try:
        await runtime_consumers.consume_bans(bot)
        assert log.await_count == 3 and hook.await_count == 3
        assert await redis.keys("xcore:idmp:ban-event-log:*") == []
    finally:
        await redis.aclose()


async def test_failed_direct_ban_log_remains_retryable_without_checkpoint():
    redis = FakeRedis(decode_responses=True)
    bus = RedisBus(
        SimpleNamespace(redis_group_prefix="xcore:cg", redis_consumer_name="bot")
    )
    bus._redis = redis
    callback = AsyncMock(side_effect=[RuntimeError("Discord failed"), None, None])
    try:
        with pytest.raises(RuntimeError, match="Discord failed"):
            await ModerationBus(bus).run_ban_log_once(ban_event(0, 57), callback)
        assert await redis.keys("xcore:idmp:ban-event-log:*") == []
        await ModerationBus(bus).run_ban_log_once(ban_event(0, 57), callback)
        await ModerationBus(bus).run_ban_log_once(ban_event(0, 57), callback)
        assert callback.await_count == 3
        assert await redis.keys("xcore:idmp:ban-event-log:*") == []
    finally:
        await redis.aclose()


@pytest.mark.parametrize("pid", [0, -5, NO_PID, None])
@pytest.mark.parametrize("ban_id", [None, 57])
async def test_received_ban_log_embed_preserves_old_fixture_and_shows_new_number(
    monkeypatch, pid, ban_id
):
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    event = ban_event(pid, ban_id)
    channel = SimpleNamespace(send=AsyncMock())

    async def consume(callback):
        await callback(event)

    bot = make_bot(settings=make_settings(discord_bans_channel_id=777))
    bot.container.moderation_bus.consume_bans = consume
    bot.store.find_player_by_uuid = AsyncMock(return_value=None)
    bot.store.now_utc = lambda: datetime(2026, 10, 13, tzinfo=UTC)
    bot.resolve_channel = AsyncMock(return_value=channel)
    await runtime_consumers.consume_bans(bot)
    embed = channel.send.call_args.kwargs["embed"]
    fields = {field.name: field.value for field in embed.fields}
    if ban_id is None:
        assert "Ban" not in fields
    else:
        assert fields["Ban"] == "B-57"
    if pid is None or pid == NO_PID:
        assert "pid=" not in fields["Violator"]
    else:
        assert f"pid={pid}" in fields["Violator"]


async def test_constructor_installs_transport_for_notifier(monkeypatch):
    from cases_dummy_flow import DUMMY_FLOW
    from cases_fakes import core

    cases, _, _ = await core(flow=DUMMY_FLOW)
    container = SimpleNamespace(cases=cases, cases_bus=MagicMock(), rpc=MagicMock())
    monkeypatch.setattr(
        "xcore_discord_bot.bot.ServiceContainer.create", lambda *a, **kw: container
    )
    bot = make_bot(settings=make_settings(cases_reports_enabled=True))
    assert bot.container is container
    assert isinstance(cases.transport, RedisCaseTransport)
    from test_cases_service import opened

    container.cases_bus.publish_case_notice = AsyncMock()
    case = await opened(cases)
    await cases.notifier.send(
        cases, case, Outcome(case.opener, "report_resolved", "cases-outcome")
    )
    container.cases_bus.publish_case_notice.assert_awaited_once_with(
        notice_id=f"{case.number}:opener-secret:report_resolved",
        player_uuid="opener-secret",
        server="server",
    )
