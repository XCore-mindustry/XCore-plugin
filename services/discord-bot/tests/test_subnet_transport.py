from __future__ import annotations

import json
from types import MethodType, SimpleNamespace

import pytest
from xcore_protocol.generated.sentinel import (
    SentinelSubnetRulesCheckRequestV1,
    SentinelSubnetRulesCheckResponseV1,
    SentinelSubnetRulesCommandV1,
    SentinelSubnetRulesListRequestV1,
    SentinelSubnetRulesListResponseV1,
    SentinelSubnetRulesResponseV1,
)
from xcore_protocol.generated.shared import ActorRefV1ActorType

from xcore_discord_bot.protocol_outbound import (
    build_sentinel_subnet_rules_check_request,
    build_sentinel_subnet_rules_command,
    build_sentinel_subnet_rules_list_request,
)
from xcore_discord_bot.redis_bus import RedisBus


def _settings() -> SimpleNamespace:
    return SimpleNamespace(redis_url="redis://localhost", redis_group_prefix="xcore", redis_consumer_name="discord")


def test_subnet_command_builder_round_trip() -> None:
    command = build_sentinel_subnet_rules_command(
        operation="ALLOW",
        rules=["1.2.3.0/24"],
        discord_id="42",
        discord_username="Moderator",
        request="req-1",
        idempotency="idem-1",
        target_server="survival-1",
        reason="trusted",
    )
    parsed = SentinelSubnetRulesCommandV1.from_payload(command.to_payload())
    assert parsed == command
    assert parsed.actor == command.actor
    assert parsed.actor.actorType is ActorRefV1ActorType.DISCORD
    assert parsed.actor.actorDiscordId == "42"
    assert parsed.actor.actorName == "Moderator"


@pytest.mark.parametrize(
    ("builder", "model", "expected"),
    [
        (build_sentinel_subnet_rules_list_request, SentinelSubnetRulesListRequestV1, {"target_server": "survival-1"}),
        (build_sentinel_subnet_rules_check_request, SentinelSubnetRulesCheckRequestV1, {"target_server": "survival-1", "ip": "1.2.3.4"}),
    ],
)
def test_subnet_request_builders_round_trip(builder, model, expected) -> None:
    request = builder(**expected, request="req-1")
    assert model.from_payload(request.to_payload()) == request


@pytest.mark.asyncio
async def test_subnet_rpc_methods_parse_typed_responses() -> None:
    bus = RedisBus(_settings())
    responses = {
        SentinelSubnetRulesCommandV1.MESSAGE_TYPE: SentinelSubnetRulesResponseV1(request="req", success=True, targetServer="s"),
        SentinelSubnetRulesListRequestV1.MESSAGE_TYPE: SentinelSubnetRulesListResponseV1(request="req", targetServer="s", rules=("1.2.3.0/24",)),
        SentinelSubnetRulesCheckRequestV1.MESSAGE_TYPE: SentinelSubnetRulesCheckResponseV1(request="req", targetServer="s", allowed=False, matchedRules=("1.2.3.0/24",)),
    }

    async def fake_rpc_request(self, *, server, rpc_type, payload, timeout_ms):
        assert server == "s"
        assert timeout_ms == 1234
        assert payload["messageVersion"] == 1
        return {"payload_json": json.dumps(responses[rpc_type].to_payload())}

    bus._rpc_request = MethodType(fake_rpc_request, bus)
    command = await bus.rpc_subnet_rules_command(
        operation="DENY", rules=["1.2.3.0/24"], discord_id="42", target_server="s", timeout_ms=1234
    )
    listed = await bus.rpc_subnet_rules_list("s", 1234)
    checked = await bus.rpc_subnet_rules_check("s", "1.2.3.4", 1234)
    assert isinstance(command, SentinelSubnetRulesResponseV1)
    assert isinstance(listed, SentinelSubnetRulesListResponseV1)
    assert isinstance(checked, SentinelSubnetRulesCheckResponseV1)
    assert listed.rules == ("1.2.3.0/24",)
    assert checked.allowed is False
