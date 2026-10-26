from __future__ import annotations

import asyncio
import json
from copy import deepcopy
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from fakeredis.aioredis import FakeRedis
from xcore_protocol.generated.cases import CasesReportSubmittedV1

from xcore_discord_bot.bus import transport as bus_module
from xcore_discord_bot.bus.cases import (
    CASE_REPORT_GROUP_SUFFIX,
    CASE_REPORT_STREAM,
    CasesBus,
    parse_case_report_payload,
)
from xcore_discord_bot.bus.transport import RedisTransport

GROUP = f"xcore:cg:{CASE_REPORT_GROUP_SUFFIX}"


def report_payload():
    return {
        "messageType": "cases.report.submitted",
        "messageVersion": 1,
        "submissionId": "submission-1",
        "server": "mini-pvp",
        "reporter": {
            "playerUuid": "reporter",
            "playerName": "Reporter",
            "playerPid": 0,
        },
        "target": {"playerUuid": "target", "playerName": "Target", "playerPid": -5},
        "targetOnline": True,
        "reason": "new-open-reason",
        "chat": [
            {
                "sentAt": 1,
                "playerName": "Target",
                "playerPid": -5,
                "role": "target",
                "text": "hello",
                "team": True,
            }
        ],
        "sameNetwork": True,
        "occurredAt": "2026-10-13T12:00:00Z",
    }


@pytest.fixture
async def bus():
    redis = FakeRedis(decode_responses=True)
    bus = RedisTransport(
        SimpleNamespace(redis_group_prefix="xcore:cg", redis_consumer_name="bot")
    )
    bus._redis = redis
    yield bus
    await redis.aclose()


async def add_report(bus, payload=None):
    return await bus._redis.xadd(
        CASE_REPORT_STREAM,
        {
            "payload_json": json.dumps(
                report_payload() if payload is None else payload
            ),
            # Reports are replayable even when the envelope's expiry has passed.
            "expires_at": "1",
            "producer": "mini-pvp",
            "event_id": "report-1",
        },
    )


async def consume_one_batch(bus, callback, **kwargs):
    read = bus._redis.xreadgroup
    count = 0

    async def bounded_read(**read_kwargs):
        nonlocal count
        count += 1
        if count > 1:
            raise asyncio.CancelledError()
        return await read(**read_kwargs)

    bus._redis.xreadgroup = bounded_read
    try:
        with pytest.raises(asyncio.CancelledError):
            await CasesBus(bus).consume_case_reports(callback, **kwargs)
    finally:
        bus._redis.xreadgroup = read


async def reclaim(bus, callback, *, is_active=None):
    await bus._reclaim_pending(
        stream=CASE_REPORT_STREAM,
        group=GROUP,
        consumer="restarted-bot",
        callback=callback,
        parse_payload=parse_case_report_payload,
        parse_fields=None,
        skip_discord_producer=False,
        max_attempts=5,
        is_active=is_active,
    )


def test_report_generated_payload_and_signed_pids():
    event = parse_case_report_payload(report_payload())
    assert isinstance(event, CasesReportSubmittedV1)
    assert event.reporter.playerPid == 0 and event.target.playerPid == -5
    assert event.reason == "new-open-reason"
    assert event.to_payload() == report_payload()


@pytest.mark.parametrize("party", ["reporter", "target"])
@pytest.mark.parametrize("ip", ["192.0.2.1", None])
def test_report_rejects_forbidden_ip_keys(party, ip):
    payload = report_payload()
    payload[party]["ip"] = ip
    with pytest.raises((ValueError, TypeError)):
        parse_case_report_payload(payload)


@pytest.mark.parametrize("field", ["ip", "playerUuid", "discordId", "unknown"])
def test_chat_rejects_private_identifiers_and_unknown_fields(field):
    payload = report_payload()
    payload["chat"][0][field] = "private"
    with pytest.raises(ValueError):
        parse_case_report_payload(payload)


@pytest.mark.parametrize(
    "invalid",
    [
        "version",
        "type",
        "missing",
        "extra",
        "lines",
        "comment",
        "id",
        "locale",
        "name",
        "text",
    ],
)
def test_report_schema_validation(invalid):
    payload = report_payload()
    if invalid == "version":
        payload["messageVersion"] = 2
    elif invalid == "type":
        payload["messageType"] = "cases.notice.command"
    elif invalid == "missing":
        del payload["targetOnline"]
    elif invalid == "extra":
        payload["extra"] = True
    elif invalid == "lines":
        payload["chat"] *= 31
    elif invalid == "comment":
        payload["comment"] = "x" * 301
    elif invalid == "id":
        payload["submissionId"] = "x" * 65
    elif invalid == "locale":
        payload["reporterLocale"] = "x" * 17
    elif invalid == "name":
        payload["chat"][0]["playerName"] = "x" * 65
    else:
        payload["chat"][0]["text"] = "x" * 257
    with pytest.raises((ValueError, TypeError)):
        parse_case_report_payload(payload)


async def test_report_delivery_in_dedicated_group_acks_after_handler(bus):
    message_id = await add_report(bus)
    # Another group neither receives nor owns the cases group's ACK.
    await bus._ensure_group(CASE_REPORT_STREAM, "xcore:cg:discord-ban")
    seen = []

    async def callback(event):
        assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 1
        seen.append(event)

    await consume_one_batch(bus, callback)
    assert len(seen) == 1 and isinstance(seen[0], CasesReportSubmittedV1)
    assert seen[0].submissionId == "submission-1"
    assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 0
    assert (await bus._redis.xinfo_groups(CASE_REPORT_STREAM))[0][
        "last-delivered-id"
    ] == "0-0"
    assert (await bus._redis.xrange(CASE_REPORT_STREAM))[0][0] == message_id


async def test_failed_handler_redelivers_after_restart_and_then_acks(bus, monkeypatch):
    await add_report(bus)
    failed = AsyncMock(side_effect=RuntimeError("Discord unavailable"))
    await consume_one_batch(bus, failed)
    assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 1
    assert await bus._redis.xlen("xcore:dlq:evt") == 0
    monkeypatch.setattr(bus_module, "RECLAIM_MIN_IDLE_MS", 0)
    delivered = AsyncMock()
    restarted = RedisTransport(bus._settings)
    restarted._redis = bus._redis
    await reclaim(restarted, delivered)
    delivered.assert_awaited_once_with(parse_case_report_payload(report_payload()))
    assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 0


async def test_handler_failure_dlq_after_five_attempts(bus, monkeypatch):
    await add_report(bus)
    failed = AsyncMock(side_effect=RuntimeError("flow failed"))
    await consume_one_batch(bus, failed)
    monkeypatch.setattr(bus_module, "RECLAIM_MIN_IDLE_MS", 0)
    for _ in range(3):
        await reclaim(bus, failed)
        assert await bus._redis.xlen("xcore:dlq:evt") == 0
        assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 1
    await reclaim(bus, failed)
    assert failed.await_count == 5
    rows = await bus._redis.xrange("xcore:dlq:evt")
    assert len(rows) == 1
    fields = rows[0][1]
    assert fields["source_stream"] == CASE_REPORT_STREAM
    assert fields["source_group"] == GROUP
    assert fields["failure_stage"] == "handler" and fields["attempts"] == "5"
    assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 0


@pytest.mark.parametrize("invalid", ["json", "schema", "ip"])
async def test_malformed_reports_use_validation_dlq(bus, invalid):
    payload = deepcopy(report_payload())
    if invalid == "ip":
        payload["target"]["ip"] = "192.0.2.1"
    elif invalid == "schema":
        del payload["reason"]
    if invalid == "json":
        await bus._redis.xadd(CASE_REPORT_STREAM, {"payload_json": "broken"})
    else:
        await add_report(bus, payload)
    callback = AsyncMock()
    await consume_one_batch(bus, callback)
    callback.assert_not_awaited()
    rows = await bus._redis.xrange("xcore:dlq:evt")
    assert len(rows) == 1 and rows[0][1]["failure_stage"] == "validation"
    assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 0


async def test_inactive_flow_never_creates_group_or_reads_backlog(bus, monkeypatch):
    await add_report(bus)
    callback = AsyncMock()

    async def stop(_):
        raise asyncio.CancelledError()

    monkeypatch.setattr(bus_module.asyncio, "sleep", stop)
    with pytest.raises(asyncio.CancelledError):
        await CasesBus(bus).consume_case_reports(callback, is_active=lambda: False)
    assert await bus._redis.xinfo_groups(CASE_REPORT_STREAM) == []
    assert await bus._redis.xlen(CASE_REPORT_STREAM) == 1
    callback.assert_not_awaited()
    await consume_one_batch(bus, callback)
    callback.assert_awaited_once()


async def test_disabled_pending_flow_is_not_claimed_or_failed(bus, monkeypatch):
    await add_report(bus)
    failed = AsyncMock(side_effect=RuntimeError("retry later"))
    await consume_one_batch(bus, failed)
    monkeypatch.setattr(bus_module, "RECLAIM_MIN_IDLE_MS", 0)
    callback = AsyncMock()
    await reclaim(bus, callback, is_active=lambda: False)
    callback.assert_not_awaited()
    assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 1
    assert await bus._redis.xlen("xcore:dlq:evt") == 0
    await reclaim(bus, callback, is_active=lambda: True)
    callback.assert_awaited_once()


async def test_flow_disabled_while_reading_preserves_batch(bus, monkeypatch):
    await add_report(bus)
    active = True
    original = bus._redis.xreadgroup

    async def read(**kwargs):
        nonlocal active
        result = await original(**kwargs)
        active = False
        return result

    async def stop(_):
        raise asyncio.CancelledError()

    bus._redis.xreadgroup = read
    monkeypatch.setattr(bus_module.asyncio, "sleep", stop)
    callback = AsyncMock()
    with pytest.raises(asyncio.CancelledError):
        await CasesBus(bus).consume_case_reports(callback, is_active=lambda: active)
    callback.assert_not_awaited()
    assert (await bus._redis.xpending(CASE_REPORT_STREAM, GROUP))["pending"] == 1
    assert await bus._redis.xlen("xcore:dlq:evt") == 0
