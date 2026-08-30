from __future__ import annotations

from xcore_protocol.generated import (
    SENTINEL_SUBNET_RULES_INVALIDATED_V1,
    SENTINEL_SUBNET_SWEEP_COMMAND_V1,
    SentinelSubnetRulesInvalidatedV1,
    SentinelSubnetSweepCommandV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_sentinel_subnet_rules_invalidated_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "sentinel" / "sentinel.subnet-rules.invalidated.v1.json")

    model = SentinelSubnetRulesInvalidatedV1.from_payload(payload)

    assert model.reason == payload["reason"]
    assert model.sourceServer == payload["sourceServer"]
    assert model.occurredAt == payload["occurredAt"]
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "sentinel" / "sentinel.subnet-rules.invalidated.v1.json",
        model.to_payload(),
    )


def test_generated_sentinel_subnet_sweep_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "sentinel" / "sentinel.subnet-sweep.command.v1.json")

    model = SentinelSubnetSweepCommandV1.from_payload(payload)

    assert model.actor == payload["actor"]
    assert model.reason == payload["reason"]
    assert model.occurredAt == payload["occurredAt"]
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "sentinel" / "sentinel.subnet-sweep.command.v1.json",
        model.to_payload(),
    )


def test_generated_sentinel_route_registry_matches_expected_messages() -> None:
    assert SENTINEL_SUBNET_RULES_INVALIDATED_V1.payloadType is SentinelSubnetRulesInvalidatedV1
    assert SENTINEL_SUBNET_SWEEP_COMMAND_V1.payloadType is SentinelSubnetSweepCommandV1
    assert ROUTES_BY_MESSAGE[("sentinel.subnet-rules.invalidated", 1)].kind == "event"
    assert ROUTES_BY_MESSAGE[("sentinel.subnet-rules.invalidated", 1)].stream == "xcore:evt:sentinel:subnet:invalidated"
    assert ROUTES_BY_MESSAGE[("sentinel.subnet-sweep.command", 1)].kind == "command"
    assert ROUTES_BY_MESSAGE[("sentinel.subnet-sweep.command", 1)].stream == "xcore:cmd:sentinel:subnet:sweep:broadcast"
