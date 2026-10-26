"""B5: the ban and mute streams reach the report flow (9.1 step 12)."""

from __future__ import annotations

import logging
from unittest.mock import AsyncMock

import pytest
from cases_reports_fakes import moderation, open_report, reports_core
from test_cases_transport_runtime import bot_for, once, service

from xcore_discord_bot import runtime_consumers
from xcore_discord_bot.cases.model import CaseKind, CaseStatus


def stream_bot(cases, verb, event, *, mutes_channel_id=0):
    async def consume(callback):
        await callback(event)

    bot = bot_for(cases)
    setattr(bot.container.moderation_bus, f"consume_{verb}s", consume)
    bot.settings = bot.settings.model_copy(
        update={"discord_mutes_channel_id": mutes_channel_id}
    )
    return bot


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_a_ban_or_mute_from_the_stream_closes_the_open_report(verb, monkeypatch):
    cases, _, _ = await reports_core()
    case = await open_report(cases)
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    bot = stream_bot(cases, verb, moderation(verb))
    await getattr(runtime_consumers, f"consume_{verb}s")(bot)
    closed = await cases.get(case.number)
    assert closed.status == CaseStatus.RESOLVED
    assert closed.resolution.code == {"ban": "banned", "mute": "muted"}[verb]


async def test_a_failing_mute_hook_is_logged_and_the_log_is_not_posted_twice(
    monkeypatch, caplog
):
    cases = service()
    hook = AsyncMock(side_effect=RuntimeError("case store unavailable"))
    cases.registry.require(CaseKind.REPORT).event_hooks[
        runtime_consumers.CASE_MUTE_EVENT
    ] = hook
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    log = AsyncMock()
    monkeypatch.setattr(runtime_consumers, "post_mute_log", log)
    event = moderation("mute")
    bot = stream_bot(
        cases,
        "mute",
        event,
        mutes_channel_id=20,
    )
    monkeypatch.setattr(
        runtime_consumers, "_player_pid_for_uuid", AsyncMock(return_value=-5)
    )
    with caplog.at_level(logging.ERROR):
        await runtime_consumers.consume_mutes(bot)
    hook.assert_awaited_once_with(cases, event)
    log.assert_awaited_once()
    assert "Case hooks failed for a mute event" in caplog.text


async def test_with_cases_off_a_mute_reaches_no_hook(monkeypatch):
    cases = service(flag=False)
    hook = AsyncMock()
    cases.registry.require(CaseKind.REPORT).event_hooks[
        runtime_consumers.CASE_MUTE_EVENT
    ] = hook
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    await runtime_consumers.consume_mutes(stream_bot(cases, "mute", moderation("mute")))
    hook.assert_not_awaited()


async def test_without_storage_a_mute_reaches_no_hook(monkeypatch):
    cases = service(storage=False)
    hook = AsyncMock()
    cases.registry.require(CaseKind.REPORT).event_hooks[
        runtime_consumers.CASE_MUTE_EVENT
    ] = hook
    monkeypatch.setattr(runtime_consumers, "run_consumer_forever", once)
    bot = stream_bot(cases, "mute", moderation("mute"))
    await runtime_consumers.consume_mutes(bot)
    hook.assert_not_awaited()
