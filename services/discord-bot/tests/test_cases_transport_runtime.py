from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import discord
import pytest
from backends import (
    connected_bus,
    connected_store,
    consumed,
    emit,
    published,
    real_bot,
    run_until,
)
from bot_fakes import PERMISSIONS_SETTINGS, make_bot, make_rpc, make_settings
from test_cases_redis_transport import report_payload
from xcore_protocol.generated.moderation import ModerationBanCreatedV1
from xcore_protocol.generated.shared import ActorRefV1, PlayerRefV1

from xcore_discord_bot import runtime_consumers
from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus.cases import (
    CASE_REPORT_STREAM,
    CasesBus,
    parse_case_report_payload,
)
from xcore_discord_bot.bus.moderation import ModerationBus
from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.cases.model import CaseKind
from xcore_discord_bot.cases.notify import Outcome
from xcore_discord_bot.cases.redis_transport import RedisCaseTransport
from xcore_discord_bot.cases.registry import Flow, FlowRegistry
from xcore_discord_bot.cases.transport import NoCaseTransport
from xcore_discord_bot.daemons.stream_supervisor import StreamSupervisor
from xcore_discord_bot.player_pids import NO_PID
from xcore_discord_bot.registry import LiveServerRegistry
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


BANS = "xcore:evt:moderation:ban"
REPORT = parse_case_report_payload(report_payload())


async def bot_for(cases, **settings):
    """A bot on the real store and bus whose case service is ``cases``, started up."""
    bot, _ = await real_bot(settings=make_settings(**settings))
    bot.container.cases = cases
    ready = asyncio.Event()
    ready.set()
    bot.container.cases_reconcile_daemon = SimpleNamespace(ready=ready)
    return bot


async def failed(bus: RedisTransport, stream: str) -> bool:
    """Whether the consumer has handled the one entry on ``stream`` and failed: the
    failure is counted and the entry left unacknowledged, for a later reclaim to
    deliver it again."""
    if not await bus.redis.keys("xcore:retries:*"):
        return False
    (group,) = await bus.redis.xinfo_groups(stream)
    return group["pending"] == 1


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
async def test_reports_require_flag_registered_hook_and_successful_startup(
    kwargs, configured, active
):
    bot = await bot_for(service(**kwargs))
    assert runtime_consumers.case_reports_configured(bot) is configured
    assert runtime_consumers.case_reports_active(bot) is active


@pytest.mark.parametrize(
    "kwargs", [{"flag": False}, {"registered": False}, {"hook": False}]
)
async def test_unavailable_report_flow_starts_no_stream_consumer(kwargs):
    bot = await bot_for(service(**kwargs))
    await emit(bot.bus, CASE_REPORT_STREAM, REPORT)
    async with asyncio.timeout(1):
        await runtime_consumers.consume_case_reports(bot)
    # It returned at once, and no consumer group reads the reports.
    assert await bot.bus.redis.xinfo_groups(CASE_REPORT_STREAM) == []


async def once(bot, label, consume, callback):
    await consume(callback)


async def test_canonical_report_dispatch_passes_service_and_generated_model():
    hook = AsyncMock()
    cases = service(callback=hook)
    bot = await bot_for(cases)
    await emit(bot.bus, CASE_REPORT_STREAM, REPORT)
    await consumed(
        bot.bus, runtime_consumers.consume_case_reports(bot), CASE_REPORT_STREAM
    )
    hook.assert_awaited_once_with(cases, REPORT)


async def test_report_hook_failure_leaves_the_report_for_a_retry():
    hook = AsyncMock(side_effect=RuntimeError("report creation failed"))
    cases = service(callback=hook)
    bot = await bot_for(cases)
    await emit(bot.bus, CASE_REPORT_STREAM, REPORT)
    await run_until(
        runtime_consumers.consume_case_reports(bot),
        lambda: failed(bot.bus, CASE_REPORT_STREAM),
    )
    hook.assert_awaited_once_with(cases, REPORT)


async def test_report_runtime_waits_for_startup_before_creating_group():
    hook = AsyncMock()
    cases = service(callback=hook)
    bot = await bot_for(cases)
    ready = asyncio.Event()
    bot.container.cases_reconcile_daemon = SimpleNamespace(ready=ready)
    await emit(bot.bus, CASE_REPORT_STREAM, REPORT)
    task = asyncio.ensure_future(runtime_consumers.consume_case_reports(bot))
    await asyncio.sleep(0.05)
    groups = await bot.bus.redis.xinfo_groups(CASE_REPORT_STREAM)
    ready.set()
    await consumed(bot.bus, task, CASE_REPORT_STREAM)
    assert groups == []
    hook.assert_awaited_once_with(cases, REPORT)


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
    supervisor = StreamSupervisor(await bot_for(service(**kwargs)))
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
    bus = await connected_bus()
    store, _ = await connected_store()
    container = SimpleNamespace(cases=cases, cases_bus=CasesBus(bus), rpc=make_rpc(bus))
    factory = MagicMock(return_value=container)
    monkeypatch.setattr("xcore_discord_bot.bot.ServiceContainer.create", factory)
    settings = Settings(
        _env_file=None,
        **PERMISSIONS_SETTINGS,
        discord_bot_token="fake",
        discord_admin_role_id=10,
        discord_private_channel_id=20,
        cases_reports_enabled=enabled,
    )
    bot = XCoreDiscordBot(settings, bus, store)
    assert factory.call_count == 1
    assert isinstance(
        cases.transport, RedisCaseTransport if enabled else NoCaseTransport
    )
    if enabled:
        from test_cases_service import opened

        case = await opened(cases)
        await cases.notifier.send(
            cases, case, Outcome(case.opener, "report_resolved", "cases-outcome")
        )
        assert await notices(bus) == [
            (f"{case.number}:opener-secret:report_resolved", "opener-secret")
        ]
        assert cases.transport._cases is container.cases_bus
        assert cases.transport._rpc is container.rpc
    # Close the base bot only; fake container daemons do not own running tasks.
    await discord.ext.commands.Bot.close(bot)


async def notices(bus):
    """The case notices the game server ``server`` was told to show: (id, player)."""
    return [
        (notice["noticeId"], notice["playerUuid"])
        for notice in await published(bus, "xcore:cmd:cases-notice:server")
    ]


def ban_event(pid=None, ban_id=None):
    return ModerationBanCreatedV1(
        target=PlayerRefV1(playerUuid="target", playerPid=pid, playerName="Target"),
        actor=ActorRefV1(actorName="Admin", actorDiscordId="123"),
        reason="Rule 1",
        server="mini-pvp",
        occurredAt="2026-10-13T12:00:00Z",
        banId=ban_id,
    )


async def ban_log_bot():
    """A bot that logs bans to channel 777; the target's account has pid -7."""
    bot, db = await real_bot(settings=make_settings(discord_bans_channel_id=777))
    await db["players"].insert_one({"uuid": "target", "pid": -7, "nickname": "Target"})
    bot.store.now_utc = lambda: datetime(2026, 10, 13, tzinfo=UTC)
    return bot


@pytest.mark.parametrize("pid", [0, -5, NO_PID, None])
@pytest.mark.parametrize("ban_id", [None, 57])
async def test_ban_consumer_old_and_new_signed_pid_and_ban_number(
    monkeypatch, pid, ban_id
):
    log = AsyncMock()
    monkeypatch.setattr(runtime_consumers, "post_ban_log", log)
    event = ban_event(pid, ban_id)
    bot = await ban_log_bot()
    await emit(bot.bus, BANS, event)
    await consumed(bot.bus, runtime_consumers.consume_bans(bot), BANS)
    # An event without a pid is logged with the one on the target's account.
    assert log.call_args.kwargs["pid"] == (-7 if pid is None else pid)
    if ban_id is not None:
        assert log.call_args.kwargs["ban_id"] == ban_id
    else:
        assert "ban_id" not in log.call_args.kwargs
    assert ModerationBanCreatedV1.from_payload(event.to_payload()).banId == ban_id


async def test_ban_cases_dispatch_works_without_log_channel_and_retries_errors(
    monkeypatch,
):
    cases = service()
    hook = AsyncMock(side_effect=RuntimeError("auto-resolution failed"))
    cases.registry.require(CaseKind.REPORT).event_hooks[
        runtime_consumers.CASE_BAN_EVENT
    ] = hook
    event = ban_event(0, 57)
    bot = await bot_for(cases)
    await emit(bot.bus, BANS, event)
    log = AsyncMock()
    monkeypatch.setattr(runtime_consumers, "post_ban_log", log)
    await run_until(runtime_consumers.consume_bans(bot), lambda: failed(bot.bus, BANS))
    hook.assert_awaited_once_with(cases, event)
    log.assert_not_awaited()


async def test_direct_bans_without_stream_identity_do_not_share_log_checkpoint(
    monkeypatch,
):
    cases = service()
    hook = AsyncMock(side_effect=[RuntimeError("failed"), None, None])
    cases.registry.require(CaseKind.REPORT).event_hooks[
        runtime_consumers.CASE_BAN_EVENT
    ] = hook
    event = ban_event(-5, 57)
    log = AsyncMock()
    monkeypatch.setattr(runtime_consumers, "post_ban_log", log)
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    bot = await bot_for(cases, discord_bans_channel_id=777)
    bot.store.now_utc = lambda: datetime(2026, 10, 13, tzinfo=UTC)

    # Called directly, not delivered from the stream.
    async def consume(callback):
        with pytest.raises(RuntimeError, match="failed"):
            await callback(event)
        await callback(event)
        await callback(event)

    bot.container.moderation_bus.consume_bans = consume
    await runtime_consumers.consume_bans(bot)
    assert log.await_count == 3 and hook.await_count == 3
    assert await bot.bus.redis.keys("xcore:idmp:ban-event-log:*") == []


async def test_failed_direct_ban_log_remains_retryable_without_checkpoint():
    bus = await connected_bus()
    callback = AsyncMock(side_effect=[RuntimeError("Discord failed"), None, None])
    with pytest.raises(RuntimeError, match="Discord failed"):
        await ModerationBus(bus, LiveServerRegistry()).run_ban_log_once(
            ban_event(0, 57), callback
        )
    assert await bus.redis.keys("xcore:idmp:ban-event-log:*") == []
    await ModerationBus(bus, LiveServerRegistry()).run_ban_log_once(
        ban_event(0, 57), callback
    )
    await ModerationBus(bus, LiveServerRegistry()).run_ban_log_once(
        ban_event(0, 57), callback
    )
    assert callback.await_count == 3
    assert await bus.redis.keys("xcore:idmp:ban-event-log:*") == []


@pytest.mark.parametrize("pid", [0, -5, NO_PID, None])
@pytest.mark.parametrize("ban_id", [None, 57])
async def test_received_ban_log_embed_preserves_old_fixture_and_shows_new_number(
    monkeypatch, pid, ban_id
):
    event = ban_event(pid, ban_id)
    channel = SimpleNamespace(send=AsyncMock())
    bot, _ = await real_bot(settings=make_settings(discord_bans_channel_id=777))
    bot.store.now_utc = lambda: datetime(2026, 10, 13, tzinfo=UTC)
    bot.resolve_channel = AsyncMock(return_value=channel)
    await emit(bot.bus, BANS, event)
    await consumed(bot.bus, runtime_consumers.consume_bans(bot), BANS)
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
    bus = await connected_bus()
    container = SimpleNamespace(cases=cases, cases_bus=CasesBus(bus), rpc=make_rpc(bus))
    monkeypatch.setattr(
        "xcore_discord_bot.bot.ServiceContainer.create", lambda *a, **kw: container
    )
    bot = make_bot(settings=make_settings(cases_reports_enabled=True), bus=bus)
    assert bot.container is container
    assert isinstance(cases.transport, RedisCaseTransport)
    from test_cases_service import opened

    case = await opened(cases)
    await cases.notifier.send(
        cases, case, Outcome(case.opener, "report_resolved", "cases-outcome")
    )
    assert await notices(bus) == [
        (f"{case.number}:opener-secret:report_resolved", "opener-secret")
    ]
