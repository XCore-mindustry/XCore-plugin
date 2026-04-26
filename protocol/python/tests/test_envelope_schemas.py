from __future__ import annotations

import json

from xcore_protocol.generated import CommandEnvelopeV1
from xcore_protocol.generated import (
    DlqEnvelopeV1,
    EnvelopeBaseV1,
    EventEnvelopeV1,
    RpcRequestEnvelopeV1,
    RpcResponseEnvelopeV1,
)
from xcore_protocol.paths import spec_root
from xcore_protocol.schema_validation import validate_instance


def _canonical_event_payload() -> dict[str, object]:
    payload = {
        "messageType": "moderation.ban.created",
        "messageVersion": 1,
        "target": {"playerUuid": "uuid-1", "playerName": "Target"},
        "actor": {"actorName": "admin"},
        "reason": "rule violation",
    }
    return {
        "schema_version": "1",
        "message_kind": "event",
        "message_type": "moderation.ban.created",
        "message_version": 1,
        "message_id": "evt-1",
        "producer": "server:mini-pvp",
        "created_at": "1713870000000",
        "expires_at": "1713870120000",
        "content_type": "application/json",
        "payload_json": json.dumps(payload),
    }


def test_event_envelope_schema_accepts_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "event-envelope.v1.json"
    envelope = _canonical_event_payload()

    validate_instance(schema_path, envelope)


def test_envelope_base_generated_model_roundtrip_matches_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "envelope-base.v1.json"
    payload = _canonical_event_payload()

    model = EnvelopeBaseV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(schema_path, model.to_payload())


def test_event_envelope_generated_model_roundtrip_matches_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "event-envelope.v1.json"
    payload = _canonical_event_payload()

    model = EventEnvelopeV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(schema_path, model.to_payload())


def test_event_envelope_generated_model_rejects_missing_expires_at() -> None:
    payload = _canonical_event_payload()
    payload.pop("expires_at")

    try:
        EventEnvelopeV1.from_payload(payload)
    except ValueError as error:
        assert "missing required fields" in str(error)
    else:
        raise AssertionError("Expected strict event envelope parsing to require expires_at")


def test_command_envelope_schema_accepts_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "command-envelope.v1.json"
    payload = {
        "messageType": "moderation.kick-banned.command",
        "messageVersion": 1,
        "target": {"playerUuid": "uuid-1", "playerName": "Target"},
        "server": "mini-pvp",
    }
    envelope = {
        "schema_version": "1",
        "message_kind": "command",
        "message_type": "moderation.kick-banned.command",
        "message_version": 1,
        "message_id": "cmd-1",
        "producer": "server:mini-pvp",
        "target": "mini-pvp",
        "created_at": "1713870000000",
        "expires_at": "1713870005000",
        "content_type": "application/json",
        "payload_json": json.dumps(payload),
    }

    validate_instance(schema_path, envelope)


def test_command_envelope_generated_model_roundtrip_matches_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "command-envelope.v1.json"
    payload = {
        "schema_version": "1",
        "message_kind": "command",
        "message_type": "moderation.kick-banned.command",
        "message_version": 1,
        "message_id": "cmd-1",
        "producer": "server:mini-pvp",
        "target": "mini-pvp",
        "created_at": "1713870000000",
        "expires_at": "1713870005000",
        "content_type": "application/json",
        "payload_json": json.dumps(
            {
                "messageType": "moderation.kick-banned.command",
                "messageVersion": 1,
                "target": {"playerUuid": "uuid-1", "playerName": "Target"},
                "server": "mini-pvp",
            }
        ),
    }

    model = CommandEnvelopeV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(schema_path, model.to_payload())


def test_command_envelope_generated_model_rejects_missing_target() -> None:
    payload = {
        "schema_version": "1",
        "message_kind": "command",
        "message_type": "moderation.kick-banned.command",
        "message_version": 1,
        "message_id": "cmd-1",
        "producer": "server:mini-pvp",
        "created_at": "1713870000000",
        "expires_at": "1713870005000",
        "content_type": "application/json",
        "payload_json": json.dumps({"messageType": "moderation.kick-banned.command", "messageVersion": 1}),
    }

    try:
        CommandEnvelopeV1.from_payload(payload)
    except ValueError as error:
        assert "missing required fields" in str(error)
    else:
        raise AssertionError("Expected strict command envelope parsing to require target")


def test_rpc_request_envelope_schema_requires_reply_to_and_timeout() -> None:
    schema_path = spec_root() / "envelopes" / "rpc-request-envelope.v1.json"
    payload = {"messageType": "maps.list.request", "messageVersion": 1, "server": "mini-pvp"}
    envelope = {
        "schema_version": "1",
        "message_kind": "rpc_request",
        "message_type": "maps.list.request",
        "message_version": 1,
        "message_id": "req-1",
        "correlation_id": "corr-1",
        "producer": "discord-bot",
        "target": "mini-pvp",
        "created_at": "1713870000000",
        "expires_at": "1713870005000",
        "content_type": "application/json",
        "payload_json": json.dumps(payload),
        "reply_to": "xcore:rpc:resp:discord-bot",
        "timeout_ms": "5000",
    }

    validate_instance(schema_path, envelope)


def test_rpc_request_envelope_generated_model_roundtrip_matches_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "rpc-request-envelope.v1.json"
    payload = {
        "schema_version": "1",
        "message_kind": "rpc_request",
        "message_type": "maps.list.request",
        "message_version": 1,
        "message_id": "req-1",
        "correlation_id": "corr-1",
        "producer": "discord-bot",
        "target": "mini-pvp",
        "created_at": "1713870000000",
        "expires_at": "1713870005000",
        "content_type": "application/json",
        "payload_json": json.dumps(
            {"messageType": "maps.list.request", "messageVersion": 1, "server": "mini-pvp"}
        ),
        "reply_to": "xcore:rpc:resp:discord-bot",
        "timeout_ms": "5000",
    }

    model = RpcRequestEnvelopeV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(schema_path, model.to_payload())


def test_rpc_request_envelope_generated_model_rejects_missing_correlation_id() -> None:
    payload = {
        "schema_version": "1",
        "message_kind": "rpc_request",
        "message_type": "maps.list.request",
        "message_version": 1,
        "message_id": "req-1",
        "producer": "discord-bot",
        "target": "mini-pvp",
        "created_at": "1713870000000",
        "expires_at": "1713870005000",
        "content_type": "application/json",
        "payload_json": json.dumps(
            {"messageType": "maps.list.request", "messageVersion": 1, "server": "mini-pvp"}
        ),
        "reply_to": "xcore:rpc:resp:discord-bot",
        "timeout_ms": "5000",
    }

    try:
        RpcRequestEnvelopeV1.from_payload(payload)
    except ValueError as error:
        assert "missing required fields" in str(error)
    else:
        raise AssertionError("Expected strict rpc request envelope parsing to require correlation_id")


def test_rpc_response_envelope_schema_accepts_error_sample() -> None:
    schema_path = spec_root() / "envelopes" / "rpc-response-envelope.v1.json"
    payload = {"messageType": "maps.list.response", "messageVersion": 1, "maps": []}
    envelope = {
        "schema_version": "1",
        "message_kind": "rpc_response",
        "message_type": "maps.list.response",
        "message_version": 1,
        "message_id": "resp-1",
        "correlation_id": "corr-1",
        "producer": "server:mini-pvp",
        "created_at": "1713870000000",
        "responded_at": "1713870000100",
        "content_type": "application/json",
        "payload_json": json.dumps(payload),
        "status": "error",
        "error_code": "maps_unavailable",
        "error_message": "Map backend unavailable",
    }

    validate_instance(schema_path, envelope)


def test_rpc_response_envelope_generated_model_roundtrip_matches_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "rpc-response-envelope.v1.json"
    payload = {
        "schema_version": "1",
        "message_kind": "rpc_response",
        "message_type": "maps.list.response",
        "message_version": 1,
        "message_id": "resp-1",
        "correlation_id": "corr-1",
        "producer": "server:mini-pvp",
        "created_at": "1713870000000",
        "responded_at": "1713870000100",
        "content_type": "application/json",
        "payload_json": json.dumps(
            {"messageType": "maps.list.response", "messageVersion": 1, "maps": []}
        ),
        "status": "ok",
    }

    model = RpcResponseEnvelopeV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(schema_path, model.to_payload())


def test_rpc_response_envelope_generated_model_rejects_missing_status() -> None:
    payload = {
        "schema_version": "1",
        "message_kind": "rpc_response",
        "message_type": "maps.list.response",
        "message_version": 1,
        "message_id": "resp-1",
        "correlation_id": "corr-1",
        "producer": "server:mini-pvp",
        "created_at": "1713870000000",
        "responded_at": "1713870000100",
        "content_type": "application/json",
        "payload_json": json.dumps(
            {"messageType": "maps.list.response", "messageVersion": 1, "maps": []}
        ),
    }

    try:
        RpcResponseEnvelopeV1.from_payload(payload)
    except ValueError as error:
        assert "missing required fields" in str(error)
    else:
        raise AssertionError("Expected strict rpc response envelope parsing to require status")


def test_dlq_envelope_schema_accepts_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "dlq-envelope.v1.json"
    payload = {
        "messageType": "maps.list.request",
        "messageVersion": 1,
        "server": "mini-pvp",
    }
    envelope = {
        "schema_version": "1",
        "message_kind": "dlq",
        "message_type": "maps.list.request",
        "message_version": 1,
        "message_id": "dlq-1",
        "correlation_id": "corr-1",
        "producer": "redis-transport",
        "created_at": "1713870000000",
        "content_type": "application/json",
        "payload_json": json.dumps(payload),
        "source_stream": "xcore:maps:requests",
        "source_group": "maps-workers",
        "source_id": "1713870000000-0",
        "failed_at": "1713870000100",
        "failure_reason": "handler_timeout",
        "attempts": "3",
    }

    validate_instance(schema_path, envelope)


def test_dlq_envelope_generated_model_roundtrip_matches_canonical_sample() -> None:
    schema_path = spec_root() / "envelopes" / "dlq-envelope.v1.json"
    payload = {
        "schema_version": "1",
        "message_kind": "dlq",
        "message_type": "maps.list.request",
        "message_version": 1,
        "message_id": "dlq-1",
        "correlation_id": "corr-1",
        "producer": "redis-transport",
        "created_at": "1713870000000",
        "content_type": "application/json",
        "payload_json": json.dumps(
            {
                "messageType": "maps.list.request",
                "messageVersion": 1,
                "server": "mini-pvp",
            }
        ),
        "source_stream": "xcore:maps:requests",
        "source_group": "maps-workers",
        "source_id": "1713870000000-0",
        "failed_at": "1713870000100",
        "failure_reason": "handler_timeout",
        "attempts": "3",
    }

    model = DlqEnvelopeV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(schema_path, model.to_payload())


def test_dlq_envelope_generated_model_rejects_missing_source_stream() -> None:
    payload = {
        "schema_version": "1",
        "message_kind": "dlq",
        "message_type": "maps.list.request",
        "message_version": 1,
        "message_id": "dlq-1",
        "correlation_id": "corr-1",
        "producer": "redis-transport",
        "created_at": "1713870000000",
        "content_type": "application/json",
        "payload_json": json.dumps(
            {
                "messageType": "maps.list.request",
                "messageVersion": 1,
                "server": "mini-pvp",
            }
        ),
        "source_group": "maps-workers",
        "source_id": "1713870000000-0",
        "failed_at": "1713870000100",
        "failure_reason": "handler_timeout",
        "attempts": "3",
    }

    try:
        DlqEnvelopeV1.from_payload(payload)
    except ValueError as error:
        assert "missing required fields" in str(error)
    else:
        raise AssertionError("Expected strict DLQ envelope parsing to require source_stream")
