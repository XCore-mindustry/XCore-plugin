from __future__ import annotations

from collections.abc import Callable
from typing import Any

import pytest
from backends import GameServer, RpcAnswer, real_bot
from bot_fakes import make_settings
from xcore_protocol.generated.maps import MapsListRequestV1, MapsRemoveRequestV1
from xcore_protocol.generated.sentinel import (
    SentinelSubnetRulesCheckRequestV1,
    SentinelSubnetRulesCommandV1,
    SentinelSubnetRulesListRequestV1,
)

from xcore_discord_bot.rpc.mindustry_rpc import MindustryRpcClient

TIMEOUT_MS = 50


def maps_list(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "messageType": "maps.list.response",
        "messageVersion": 1,
        "server": payload["server"],
        "maps": [{"fileName": "arena.msav", "name": "Arena", "author": "Alice"}],
    }


def subnet_list(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "messageType": "sentinel.subnet-rules.list.response",
        "messageVersion": 1,
        "request": payload["request"],
        "targetServer": payload["targetServer"],
        "rules": ["10.0.0.0/8"],
    }


def subnet_check(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "messageType": "sentinel.subnet-rules.check.response",
        "messageVersion": 1,
        "request": payload["request"],
        "targetServer": payload["targetServer"],
        "allowed": False,
        "matchedRules": ["1.2.3.0/24"],
    }


def after_silence(answer: RpcAnswer) -> RpcAnswer:
    """Stays silent on the first request, then answers with ``answer``."""
    calls = 0

    def reply(payload: dict[str, Any]) -> Any:
        nonlocal calls
        calls += 1
        return None if calls == 1 else answer(payload)

    return reply


async def game_server() -> tuple[MindustryRpcClient, GameServer]:
    bot, _ = await real_bot(settings=make_settings(rpc_timeout_ms=TIMEOUT_MS))
    return bot.container.rpc, GameServer(bot)


@pytest.mark.parametrize(
    ("rpc_type", "answer", "call", "check"),
    [
        (
            MapsListRequestV1.MESSAGE_TYPE,
            maps_list,
            lambda rpc: rpc.list_maps(server="mini-pvp", timeout_ms=TIMEOUT_MS),
            lambda result: [entry["file_name"] for entry in result] == ["arena.msav"],
        ),
        (
            SentinelSubnetRulesListRequestV1.MESSAGE_TYPE,
            subnet_list,
            lambda rpc: rpc.subnet_rules_list(server="mini-pvp", timeout_ms=TIMEOUT_MS),
            lambda result: result.rules == ("10.0.0.0/8",),
        ),
        (
            SentinelSubnetRulesCheckRequestV1.MESSAGE_TYPE,
            subnet_check,
            lambda rpc: rpc.subnet_rules_check(
                server="mini-pvp", ip="1.2.3.4", timeout_ms=TIMEOUT_MS
            ),
            lambda result: result.matchedRules == ("1.2.3.0/24",),
        ),
    ],
)
async def test_reads_are_retried_once_on_a_transient_failure(
    rpc_type: str,
    answer: RpcAnswer,
    call: Callable[[MindustryRpcClient], Any],
    check: Callable[[Any], bool],
) -> None:
    rpc, game = await game_server()
    game.answers[rpc_type] = after_silence(answer)

    assert check(await call(rpc))
    assert len(game.sent(rpc_type)) == 2


async def test_a_read_that_keeps_failing_raises_after_the_retry() -> None:
    rpc, game = await game_server()
    game.answers[MapsListRequestV1.MESSAGE_TYPE] = lambda payload: None

    with pytest.raises(TimeoutError):
        await rpc.list_maps(server="mini-pvp", timeout_ms=TIMEOUT_MS)
    assert len(game.sent(MapsListRequestV1.MESSAGE_TYPE)) == 2


# A write that timed out may still have been applied on the server.


async def test_a_map_removal_is_not_retried() -> None:
    rpc, game = await game_server()
    game.answers[MapsRemoveRequestV1.MESSAGE_TYPE] = lambda payload: None

    with pytest.raises(TimeoutError):
        await rpc.remove_map(
            server="mini-pvp", file_name="a.msav", timeout_ms=TIMEOUT_MS
        )
    assert len(game.sent(MapsRemoveRequestV1.MESSAGE_TYPE)) == 1


async def test_a_subnet_rules_command_is_not_retried() -> None:
    rpc, game = await game_server()
    game.answers[SentinelSubnetRulesCommandV1.MESSAGE_TYPE] = lambda payload: None

    with pytest.raises(TimeoutError):
        await rpc.subnet_rules_command(
            operation="DENY",
            rules=["10.0.0.0/8"],
            discord_id="1",
            target_server="mini-pvp",
            timeout_ms=TIMEOUT_MS,
        )
    assert len(game.sent(SentinelSubnetRulesCommandV1.MESSAGE_TYPE)) == 1


async def test_a_map_upload_is_not_retried() -> None:
    rpc, game = await game_server()
    game.upload_error = TimeoutError()

    with pytest.raises(TimeoutError):
        await rpc.upload_maps(server="mini-pvp", files=[], timeout_ms=TIMEOUT_MS)
    assert len(game.upload_calls) == 1
