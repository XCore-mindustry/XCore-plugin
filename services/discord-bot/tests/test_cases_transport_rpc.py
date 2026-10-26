from __future__ import annotations

import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from bot_fakes import make_rpc
from fakeredis.aioredis import FakeRedis
from xcore_protocol.generated.cases import CasesNoticeCommandV1
from xcore_protocol.generated.maps import MapsInspectResponseV1

from xcore_discord_bot.bus import transport as bus_module
from xcore_discord_bot.bus.cases import CasesBus
from xcore_discord_bot.bus.maps import MapsBus
from xcore_discord_bot.bus.transport import RedisTransport, RpcFailed, RpcRejected
from xcore_discord_bot.cases.redis_transport import RedisCaseTransport
from xcore_discord_bot.protocol_outbound import (
    build_case_evidence_request,
    build_case_notice_command,
    build_maps_inspect_request,
)
from xcore_discord_bot.registry import server_registry
from xcore_discord_bot.rpc.mindustry_rpc import NoLiveServerError


@pytest.fixture
async def bus():
    redis = FakeRedis(decode_responses=True)
    bus = RedisTransport(
        SimpleNamespace(redis_group_prefix="xcore:cg", redis_consumer_name="bot")
    )
    bus._redis = redis
    yield bus
    await redis.aclose()


@pytest.fixture(autouse=True)
def clear_servers():
    with server_registry._lock:
        server_registry._servers.clear()
    yield
    with server_registry._lock:
        server_registry._servers.clear()


def live(*names):
    for name in names:
        server_registry.update_server(name, 1, 1, 30, "160", None, None)


def evidence(server="mini-pvp", online=True):
    return {
        "messageType": "cases.evidence.response",
        "messageVersion": 1,
        "server": server,
        "targetOnline": online,
        "capturedAt": "2026-10-13T12:00:00Z",
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
        "mapName": "Arena",
        "gameMode": "pvp",
    }


def inspected(server="mini-pvp", status="ok"):
    return {
        "messageType": "maps.inspect.response",
        "messageVersion": 1,
        "server": server,
        "status": status,
        "findings": [],
    }


async def test_notice_is_only_wakeup_with_server_binding_and_route_ttl(bus):
    transport = RedisCaseTransport(CasesBus(bus), make_rpc(bus))
    await transport.publish_case_notice(
        notice_id="notice-57", player_uuid="reporter", server="mini-pvp"
    )
    rows = await bus._redis.xrange("xcore:cmd:cases-notice:mini-pvp")
    assert len(rows) == 1
    fields = rows[0][1]
    assert (
        fields["event_type"] == "cases.notice.command"
        and fields["server"] == "mini-pvp"
    )
    assert int(fields["expires_at"]) - int(fields["created_at"]) == 120_000
    request = CasesNoticeCommandV1.from_payload(json.loads(fields["payload_json"]))
    assert request.noticeId == "notice-57" and request.playerUuid == "reporter"
    assert set(request.to_payload()) == {
        "messageType",
        "messageVersion",
        "server",
        "noticeId",
        "playerUuid",
        "requestedAt",
    }
    assert await bus._redis.keys("xcore:cmd:cases-notice:*") == [
        "xcore:cmd:cases-notice:mini-pvp"
    ]


def test_rpc_builders_are_typed_canonical_and_defaulted():
    request = build_case_evidence_request(server="mini-pvp", player_uuid="target")
    assert request.to_payload() == {
        "messageType": "cases.evidence.request",
        "messageVersion": 1,
        "server": "mini-pvp",
        "playerUuid": "target",
        "chatLines": 30,
        "windowSeconds": 600,
    }
    request = build_maps_inspect_request(
        server="mini-pvp", url="https://example/arena.msav", file_name="arena.msav"
    )
    assert request.to_payload() == {
        "messageType": "maps.inspect.request",
        "messageVersion": 1,
        "server": "mini-pvp",
        "file": {"url": "https://example/arena.msav", "fileName": "arena.msav"},
    }
    assert (
        build_maps_inspect_request(
            server="mini-pvp",
            url="https://example/arena.msav",
            file_name="arena.msav",
            profile="pvp",
        ).profile
        == "pvp"
    )
    assert build_case_notice_command(
        server="mini-pvp", notice_id="n", player_uuid="p"
    ).requestedAt


@pytest.mark.parametrize(
    "kwargs",
    [
        {"chat_lines": 0},
        {"chat_lines": 31},
        {"chat_lines": True},
        {"window_seconds": 59},
        {"window_seconds": 1801},
        {"window_seconds": False},
    ],
)
def test_evidence_request_schema_bounds(kwargs):
    with pytest.raises((ValueError, TypeError)):
        build_case_evidence_request(server="mini-pvp", player_uuid="target", **kwargs)


@pytest.mark.parametrize("kind,ttl", [("evidence", 15_000), ("inspect", 60_000)])
async def test_rpc_roundtrip_correlates_and_uses_route_envelope(bus, kind, ttl):
    original = bus._redis.xadd
    request_fields = {}
    payload = evidence() if kind == "evidence" else inspected()

    async def replying_add(stream, fields, **kwargs):
        result = await original(stream, fields, **kwargs)
        if stream == "xcore:rpc:req:mini-pvp":
            request_fields.update(fields)
            # An unrelated callback must not be mistaken for our response.
            await original(
                "xcore:rpc:resp:discord",
                {"correlation_id": "other", "payload_json": "{}"},
            )
            await original(
                fields["reply_to"],
                {
                    "correlation_id": fields["correlation_id"],
                    "status": "ok",
                    "payload_json": json.dumps(payload),
                },
            )
        return result

    bus._redis.xadd = replying_add
    transport = RedisCaseTransport(CasesBus(bus), make_rpc(bus))
    if kind == "evidence":
        result = await transport.rpc_case_evidence(
            server="mini-pvp", player_uuid="target", chat_lines=10, window_seconds=60
        )
    else:
        result = await transport.rpc_inspect_map(
            server="mini-pvp",
            url="https://example/arena.msav",
            file_name="arena.msav",
            profile="pvp",
        )
    assert result == payload
    assert request_fields["reply_to"] == "xcore:rpc:resp:discord"
    assert request_fields["timeout_ms"] == str(ttl)
    assert int(request_fields["expires_at"]) - int(request_fields["created_at"]) == ttl
    request = json.loads(request_fields["payload_json"])
    assert (
        request["server"] == "mini-pvp"
        and request_fields["rpc_type"] == request["messageType"]
    )
    if kind == "evidence":
        assert request["chatLines"] == 10 and request["windowSeconds"] == 60
        assert "playerUuid" not in result and "ip" not in result
    else:
        assert (
            request["profile"] == "pvp" and request["file"]["fileName"] == "arena.msav"
        )


@pytest.mark.parametrize("kind", ["evidence", "inspect"])
@pytest.mark.parametrize(
    "error_code,expected", [("REJECTED", RpcRejected), ("FAILED", RpcFailed)]
)
async def test_rpc_error_callback_preserves_existing_error_types(
    bus, kind, error_code, expected
):
    original = bus._redis.xadd

    async def replying_add(stream, fields, **kwargs):
        result = await original(stream, fields, **kwargs)
        await original(
            fields["reply_to"],
            {
                "correlation_id": fields["correlation_id"],
                "status": "error",
                "error_code": error_code,
                "error_message": "cannot inspect",
            },
        )
        return result

    bus._redis.xadd = replying_add
    transport = RedisCaseTransport(CasesBus(bus), make_rpc(bus))
    with pytest.raises(expected) as raised:
        if kind == "evidence":
            await transport.rpc_case_evidence(server="mini-pvp", player_uuid="target")
        else:
            await transport.rpc_inspect_map(
                server="mini-pvp",
                url="https://example/arena.msav",
                file_name="arena.msav",
            )
    assert raised.value.error_code == error_code


@pytest.mark.parametrize("kind", ["evidence", "inspect"])
async def test_rpc_timeout_propagates(bus, monkeypatch, kind):
    ticks = iter([0, 61])
    monkeypatch.setattr(
        bus_module,
        "time",
        SimpleNamespace(time=lambda: 1, monotonic=lambda: next(ticks)),
    )
    transport = RedisCaseTransport(CasesBus(bus), make_rpc(bus))
    with pytest.raises(TimeoutError):
        if kind == "evidence":
            await transport.rpc_case_evidence(server="mini-pvp", player_uuid="target")
        else:
            await transport.rpc_inspect_map(
                server="mini-pvp",
                url="https://example/arena.msav",
                file_name="arena.msav",
            )


@pytest.mark.parametrize("kind", ["evidence", "inspect"])
@pytest.mark.parametrize("invalid", ["server", "message", "unknown"])
async def test_rpc_decoder_rejects_mismatched_and_unknown_response(bus, kind, invalid):
    payload = evidence() if kind == "evidence" else inspected()
    if invalid == "server":
        payload["server"] = "other-server"
    elif invalid == "message":
        payload["messageType"] = "maps.list.response"
    else:
        payload["playerUuid"] = "private"
    bus.rpc_request = AsyncMock(return_value={"payload_json": json.dumps(payload)})
    with pytest.raises(ValueError):
        if kind == "evidence":
            await CasesBus(bus).rpc_case_evidence(
                server="mini-pvp", player_uuid="target"
            )
        else:
            await MapsBus(bus).rpc_inspect_map(
                server="mini-pvp",
                url="https://example/arena.msav",
                file_name="arena.msav",
            )


@pytest.mark.parametrize("field", ["ip", "playerUuid", "discordId"])
async def test_evidence_chat_cannot_leak_private_fields(bus, field):
    payload = evidence()
    payload["chat"][0][field] = "private"
    bus.rpc_request = AsyncMock(return_value={"payload_json": json.dumps(payload)})
    with pytest.raises(ValueError):
        await CasesBus(bus).rpc_case_evidence(server="mini-pvp", player_uuid="target")


async def test_offline_evidence_response_is_valid_and_never_falls_back(bus):
    bus.rpc_request = AsyncMock(
        return_value={"payload_json": json.dumps(evidence(online=False))}
    )
    result = await RedisCaseTransport(CasesBus(bus), make_rpc(bus)).rpc_case_evidence(
        server="mini-pvp", player_uuid="target"
    )
    assert result["targetOnline"] is False
    assert bus.rpc_request.await_count == 1


@pytest.mark.parametrize(
    "error", [TimeoutError(), RpcFailed("maps.inspect.request", "FAILED", "storage")]
)
async def test_inspection_any_live_server_falls_back_in_stable_order(bus, error):
    maps = MapsBus(bus)
    live("zulu", "beta", "Alpha")
    maps.rpc_inspect_map = AsyncMock(
        side_effect=[error, MapsInspectResponseV1.from_payload(inspected("beta"))]
    )
    result = await RedisCaseTransport(
        CasesBus(bus), make_rpc(bus, maps=maps)
    ).rpc_inspect_map(
        url="https://example/arena.msav", file_name="arena.msav", profile="pvp"
    )
    assert result["server"] == "beta"
    assert [call.kwargs["server"] for call in maps.rpc_inspect_map.call_args_list] == [
        "Alpha",
        "beta",
    ]


async def test_explicit_inspection_does_not_switch_servers(bus):
    maps = MapsBus(bus)
    live("other")
    maps.rpc_inspect_map = AsyncMock(side_effect=TimeoutError())
    with pytest.raises(TimeoutError):
        await RedisCaseTransport(
            CasesBus(bus), make_rpc(bus, maps=maps)
        ).rpc_inspect_map(
            server="chosen", url="https://example/arena.msav", file_name="arena.msav"
        )
    assert [call.kwargs["server"] for call in maps.rpc_inspect_map.call_args_list] == [
        "chosen"
    ]


async def test_inspection_refusal_is_final_and_no_live_server_is_explicit(bus):
    maps = MapsBus(bus)
    transport = RedisCaseTransport(CasesBus(bus), make_rpc(bus, maps=maps))
    maps.rpc_inspect_map = AsyncMock()
    with pytest.raises(NoLiveServerError):
        await transport.rpc_inspect_map(
            url="https://example/arena.msav", file_name="arena.msav"
        )
    maps.rpc_inspect_map.assert_not_awaited()
    live("alpha", "beta")
    maps.rpc_inspect_map.side_effect = RpcRejected(
        "maps.inspect.request", "REJECTED", "refused"
    )
    with pytest.raises(RpcRejected):
        await transport.rpc_inspect_map(
            url="https://example/arena.msav", file_name="arena.msav"
        )
    assert maps.rpc_inspect_map.await_count == 1


@pytest.mark.parametrize("status", ["download_failed", "invalid_map"])
async def test_inspection_business_failure_is_a_typed_result(bus, status):
    bus.rpc_request = AsyncMock(
        return_value={"payload_json": json.dumps(inspected(status=status))}
    )
    result = await RedisCaseTransport(CasesBus(bus), make_rpc(bus)).rpc_inspect_map(
        server="mini-pvp", url="https://example/arena.msav", file_name="arena.msav"
    )
    assert result["status"] == status
