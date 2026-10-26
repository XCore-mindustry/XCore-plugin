from __future__ import annotations

import asyncio
import json
from datetime import UTC, datetime
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from bot_fakes import make_bot, make_settings
from fakeredis.aioredis import FakeRedis

from xcore_discord_bot import runtime_consumers
from xcore_discord_bot.bus import transport as bus_module
from xcore_discord_bot.contracts import ModerationBanCreatedV1, parse_ban_payload
from xcore_discord_bot.moderation_logs import post_ban_log
from xcore_discord_bot.player_pids import NO_PID
from xcore_discord_bot.redis_bus import RedisBus

STREAM = "xcore:evt:moderation:ban"
GROUP = "xcore:cg:discord-ban"
MARKERS = "xcore:idmp:ban-event-log:*"
NOW = datetime(2026, 10, 13, tzinfo=UTC)


def legacy_payload(pid=-5, ban_id=None):
    payload = {
        "messageType": "moderation.ban.created",
        "messageVersion": 1,
        "target": {"playerUuid": "target", "playerName": "Target"},
        "actor": {"actorName": "Admin", "actorDiscordId": "123"},
        "reason": "Rule 1",
    }
    if pid is not None:
        payload["target"]["playerPid"] = pid
    if ban_id is not None:
        payload["banId"] = ban_id
    return payload


@pytest.fixture
async def bus():
    redis = FakeRedis(decode_responses=True)
    bus = RedisBus(
        SimpleNamespace(redis_group_prefix="xcore:cg", redis_consumer_name="bot")
    )
    bus._redis = redis
    yield bus
    await redis.aclose()


def ban_bot(bus, *, hook=None, channel_id=777, resolver=None):
    channel = SimpleNamespace(send=AsyncMock())
    cases = SimpleNamespace(
        settings=SimpleNamespace(cases_enabled=True),
        storage_available=True,
        registry=SimpleNamespace(
            dispatch_event=hook if hook is not None else AsyncMock()
        ),
    )
    bot = make_bot(bus=bus, settings=make_settings(discord_bans_channel_id=channel_id))
    bot.container.cases = cases
    bot.resolve_channel = (
        resolver if resolver is not None else AsyncMock(return_value=channel)
    )
    bot.store.find_player_by_uuid = AsyncMock(return_value=SimpleNamespace(pid=-7))
    bot.store.now_utc = lambda: NOW
    return bot, cases, channel


async def add_ban(bus, *, event_id=None, payload=None, stream=STREAM, message_id="*"):
    fields = {"payload_json": json.dumps(payload or legacy_payload())}
    if event_id is not None:
        fields["event_id"] = event_id
    return await bus._redis.xadd(stream, fields, id=message_id)


async def live_batch(monkeypatch, bot):
    read = bot.bus._redis.xreadgroup
    reads = 0

    async def bounded_read(**kwargs):
        nonlocal reads
        reads += 1
        if reads > 1:
            raise asyncio.CancelledError()
        return await read(**kwargs)

    async def once(_bot, _label, consume, callback):
        await consume(callback)

    with monkeypatch.context() as patch:
        patch.setattr(bot.bus._redis, "xreadgroup", bounded_read)
        patch.setattr(runtime_consumers, "run_consumer_forever", once)
        with pytest.raises(asyncio.CancelledError):
            await runtime_consumers.consume_bans(bot)


async def reclaim_bans(monkeypatch, bot):
    async def once(_bot, _label, _consume, callback):
        await bot.bus._reclaim_pending(
            stream=STREAM,
            group=GROUP,
            consumer="restarted-bot",
            callback=callback,
            parse_payload=parse_ban_payload,
            parse_fields=None,
            skip_discord_producer=False,
            max_attempts=5,
        )

    with monkeypatch.context() as patch:
        patch.setattr(bus_module, "RECLAIM_MIN_IDLE_MS", 0)
        patch.setattr(runtime_consumers, "run_consumer_forever", once)
        await runtime_consumers.consume_bans(bot)


@pytest.mark.parametrize("event_ids", [("ban-event-1", "ban-event-2"), (None, None)])
async def test_distinct_legacy_bans_log_and_reclaim_retries_hooks_after_restart(
    bus, monkeypatch, event_ids
):
    payload = legacy_payload()
    assert "banId" not in payload and "occurredAt" not in payload
    ids = [
        await add_ban(bus, event_id=event_id, payload=payload) for event_id in event_ids
    ]
    assert ids[0] != ids[1]
    hook = AsyncMock(
        side_effect=[
            RuntimeError("case failed"),
            RuntimeError("case failed"),
            None,
            None,
        ]
    )
    bot, cases, channel = ban_bot(bus, hook=hook)

    await live_batch(monkeypatch, bot)
    assert channel.send.await_count == 2
    assert hook.await_count == 2
    assert len(await bus._redis.keys(MARKERS)) == 2
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 2
    assert await bus._redis.xlen("xcore:dlq:evt") == 0
    for call in hook.await_args_list:
        assert call.args[:2] == (runtime_consumers.CASE_BAN_EVENT, cases)
        assert isinstance(call.args[2], ModerationBanCreatedV1)
        assert call.args[2].to_payload() == payload

    # The marker belongs to the envelope delivery, and survives a new bus instance.
    restarted = RedisBus(bus._settings)
    restarted._redis = bus._redis
    bot.bus = restarted
    await reclaim_bans(monkeypatch, bot)
    assert channel.send.await_count == 2
    assert hook.await_count == 4
    assert len(await bus._redis.keys(MARKERS)) == 2
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 0

    # A later direct invocation of the same generated object has no stream identity.
    direct_log = AsyncMock()
    await bus.run_ban_log_once(hook.await_args_list[1].args[2], direct_log)
    direct_log.assert_awaited_once_with()
    assert len(await bus._redis.keys(MARKERS)) == 2


async def test_envelope_event_id_takes_precedence_over_distinct_message_ids(
    bus, monkeypatch
):
    first = await add_ban(bus, event_id="same-event")
    second = await add_ban(bus, event_id="same-event")
    assert first != second
    bot, cases, channel = ban_bot(bus)
    await live_batch(monkeypatch, bot)
    assert channel.send.await_count == 1
    assert cases.registry.dispatch_event.await_count == 2
    assert len(await bus._redis.keys(MARKERS)) == 1
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 0


async def test_missing_event_id_fallback_includes_source_stream(bus):
    # Redis IDs are unique only within a stream. Equal IDs in separate sources
    # must not share a log completion marker.
    log = AsyncMock()

    async def callback(event):
        await bus.run_ban_log_once(event, log)

    for stream in (STREAM, "xcore:evt:moderation:ban:legacy"):
        await add_ban(bus, stream=stream, message_id="1-0")
        await bus._ensure_group(stream, GROUP)
        rows = await bus._redis.xreadgroup(
            groupname=GROUP, consumername="bot", streams={stream: ">"}
        )
        message_id, fields = rows[0][1][0]
        for _ in range(2):
            await bus._process_event_message(
                stream=stream,
                group=GROUP,
                message_id=message_id,
                fields=fields,
                callback=callback,
                parse_payload=parse_ban_payload,
                parse_fields=None,
                skip_discord_producer=False,
                max_attempts=5,
                source="live",
            )
        assert (await bus._redis.xpending(stream, GROUP))["pending"] == 0
    assert log.await_count == 2
    assert len(await bus._redis.keys(MARKERS)) == 2


async def test_unresolved_channel_retries_send_then_retries_case_hook(bus, monkeypatch):
    await add_ban(bus, event_id="unresolved-channel")
    channel = SimpleNamespace(send=AsyncMock())
    resolver = AsyncMock(side_effect=[None, channel])
    hook = AsyncMock(side_effect=[RuntimeError("case failed after send"), None])
    bot, cases, _ = ban_bot(bus, hook=hook, resolver=resolver)

    # The real post_ban_log helper reports an unresolved configured channel.
    await live_batch(monkeypatch, bot)
    assert await bus._redis.keys(MARKERS) == []
    channel.send.assert_not_awaited()
    hook.assert_not_awaited()
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 1

    await reclaim_bans(monkeypatch, bot)
    channel.send.assert_awaited_once()
    assert len(await bus._redis.keys(MARKERS)) == 1
    assert hook.await_count == 1
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 1

    restarted = RedisBus(bus._settings)
    restarted._redis = bus._redis
    bot.bus = restarted
    await reclaim_bans(monkeypatch, bot)
    channel.send.assert_awaited_once()
    assert resolver.await_count == 2
    assert hook.await_count == 2
    hook.assert_awaited_with(
        runtime_consumers.CASE_BAN_EVENT, cases, parse_ban_payload(legacy_payload())
    )
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 0
    assert await bus._redis.xlen("xcore:dlq:evt") == 0


async def test_failed_send_does_not_mark_or_ack_and_can_be_reclaimed(bus, monkeypatch):
    await add_ban(bus, event_id="send-error")
    bot, cases, channel = ban_bot(bus)
    channel.send.side_effect = [RuntimeError("Discord send failed"), None]
    await live_batch(monkeypatch, bot)
    assert await bus._redis.keys(MARKERS) == []
    cases.registry.dispatch_event.assert_not_awaited()
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 1
    await reclaim_bans(monkeypatch, bot)
    assert channel.send.await_count == 2
    assert cases.registry.dispatch_event.await_count == 1
    assert len(await bus._redis.keys(MARKERS)) == 1
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 0


async def test_zero_channel_skips_log_and_retries_case_hook(bus, monkeypatch):
    await add_ban(bus, event_id="disabled-channel")
    hook = AsyncMock(side_effect=[RuntimeError("case failed"), None])
    bot, cases, channel = ban_bot(bus, hook=hook, channel_id=0)
    await live_batch(monkeypatch, bot)
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 1
    assert await bus._redis.keys(MARKERS) == []
    await reclaim_bans(monkeypatch, bot)
    assert hook.await_count == 2
    hook.assert_awaited_with(
        runtime_consumers.CASE_BAN_EVENT, cases, parse_ban_payload(legacy_payload())
    )
    bot.resolve_channel.assert_not_awaited()
    channel.send.assert_not_awaited()
    assert await bus._redis.keys(MARKERS) == []
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 0


@pytest.mark.parametrize("pid", [0, -5, NO_PID, None])
@pytest.mark.parametrize("ban_id", [None, 57])
async def test_stream_logs_preserve_legacy_signed_pid_and_optional_ban_id(
    bus, monkeypatch, pid, ban_id
):
    await add_ban(bus, event_id="signed-player", payload=legacy_payload(pid, ban_id))
    bot, cases, channel = ban_bot(bus)
    await live_batch(monkeypatch, bot)
    channel.send.assert_awaited_once()
    fields = {
        field.name: field.value
        for field in channel.send.call_args.kwargs["embed"].fields
    }
    expected_pid = -7 if pid is None else pid
    assert fields["Violator"] == (
        "Target" if expected_pid == NO_PID else f"Target (pid={expected_pid})"
    )
    assert bot.store.find_player_by_uuid.await_count == int(pid is None)
    if ban_id is None:
        assert "Ban" not in fields
    else:
        assert fields["Ban"] == "B-57"
    assert cases.registry.dispatch_event.await_count == 1
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 0


async def test_cancelled_callback_cleans_up_delivery_identity_without_mark_or_ack(
    bus, monkeypatch
):
    await add_ban(bus, event_id="cancelled-event")
    seen = []

    async def callback(event):
        seen.append(event)
        await bus.run_ban_log_once(
            event, AsyncMock(side_effect=asyncio.CancelledError())
        )

    async def once(_bot, _label, consume, _callback):
        await consume(callback)

    bot, _, _ = ban_bot(bus)
    with monkeypatch.context() as patch:
        patch.setattr(runtime_consumers, "run_consumer_forever", once)
        with pytest.raises(asyncio.CancelledError):
            await runtime_consumers.consume_bans(bot)
    assert len(seen) == 1
    assert await bus._redis.keys(MARKERS) == []
    assert (await bus._redis.xpending(STREAM, GROUP))["pending"] == 1
    direct_log = AsyncMock()
    await bus.run_ban_log_once(seen[0], direct_log)
    direct_log.assert_awaited_once_with()
    assert await bus._redis.keys(MARKERS) == []


async def test_post_ban_log_receipt_preserves_legacy_missing_channel_behavior(bus):
    bot, _, channel = ban_bot(bus, resolver=AsyncMock(return_value=None))
    kwargs = {
        "pid": 0,
        "name": "Target",
        "admin_name": "Admin",
        "admin_discord_id": None,
        "reason": "Rule 1",
        "expire": NOW,
    }
    assert await post_ban_log(bot, **kwargs) is False
    bot.settings = bot.settings.model_copy(update={"discord_bans_channel_id": 0})
    assert await post_ban_log(bot, **kwargs) is False
    assert bot.resolve_channel.await_count == 1
    bot.settings = bot.settings.model_copy(update={"discord_bans_channel_id": 777})
    bot.resolve_channel.return_value = channel
    assert await post_ban_log(bot, **kwargs) is True
    channel.send.assert_awaited_once()
