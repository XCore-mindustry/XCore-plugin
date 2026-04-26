"""Generated canonical envelope models for the supported subset."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any, ClassVar

def _expect_mapping(value: Any, field_name: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise TypeError(f"{field_name} must be an object")
    invalid_keys = [key for key in value.keys() if not isinstance(key, str)]
    if invalid_keys:
        raise TypeError(f"{field_name} keys must be strings")
    return value


def _expect_list(value: Any, field_name: str) -> list[Any]:
    if not isinstance(value, list):
        raise TypeError(f"{field_name} must be a list")
    return value


def _expect_json_object(
    value: Any,
    field_name: str,
    *,
    allowed_types: tuple[str, ...],
    allow_null: bool,
) -> dict[str, Any]:
    mapping = _expect_mapping(value, field_name)
    for key, item in mapping.items():
        if item is None:
            if allow_null:
                continue
            raise TypeError(f"{field_name}.{key} must not be null")
        if isinstance(item, bool):
            allowed = "boolean" in allowed_types
        elif isinstance(item, str):
            allowed = "string" in allowed_types
        elif isinstance(item, int):
            allowed = "integer" in allowed_types or "number" in allowed_types
        elif isinstance(item, float):
            allowed = "number" in allowed_types
        else:
            raise TypeError(f"{field_name}.{key} has unsupported value type")
        if not allowed:
            raise TypeError(
                f"{field_name}.{key} must be one of: {', '.join(allowed_types)}"
            )
    return dict(mapping)


def _expect_exact_keys(
    payload: Mapping[str, Any],
    *,
    required: frozenset[str],
    allowed: frozenset[str],
    model_name: str,
) -> None:
    actual = frozenset(payload.keys())
    missing = sorted(required - actual)
    unexpected = sorted(actual - allowed)
    if missing:
        raise ValueError(f"{model_name} is missing required fields: {', '.join(missing)}")
    if unexpected:
        raise ValueError(f"{model_name} has unexpected fields: {', '.join(unexpected)}")


def _expect_str(value: Any, field_name: str) -> str:
    if not isinstance(value, str):
        raise TypeError(f"{field_name} must be a string")
    return value


def _expect_int(value: Any, field_name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{field_name} must be an integer")
    return value


def _expect_number(value: Any, field_name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError(f"{field_name} must be a number")
    return float(value)


def _expect_bool(value: Any, field_name: str) -> bool:
    if not isinstance(value, bool):
        raise TypeError(f"{field_name} must be a boolean")
    return value


def _expect_instance(value: Any, field_name: str, expected_type: type[Any]) -> None:
    if not isinstance(value, expected_type):
        raise TypeError(f"{field_name} must be a {expected_type.__name__}")

@dataclass(frozen=True, slots=True)
class EnvelopeBaseV1:
    message_kind: str
    message_type: str
    message_version: int
    message_id: str
    producer: str
    created_at: str
    payload_json: str
    correlation_id: str | None = None
    causation_id: str | None = None
    target: str | None = None
    expires_at: str | None = None
    schema_ref: str | None = None

    SCHEMA_VERSION: ClassVar[str] = '1'
    CONTENT_TYPE: ClassVar[str] = 'application/json'
    def __post_init__(self) -> None:
        _expect_str(self.message_kind, 'message_kind')
        _expect_str(self.message_type, 'message_type')
        _expect_int(self.message_version, 'message_version')
        _expect_str(self.message_id, 'message_id')
        if self.correlation_id is not None:
            _expect_str(self.correlation_id, 'correlation_id')
        if self.causation_id is not None:
            _expect_str(self.causation_id, 'causation_id')
        _expect_str(self.producer, 'producer')
        if self.target is not None:
            _expect_str(self.target, 'target')
        _expect_str(self.created_at, 'created_at')
        if self.expires_at is not None:
            _expect_str(self.expires_at, 'expires_at')
        if self.schema_ref is not None:
            _expect_str(self.schema_ref, 'schema_ref')
        _expect_str(self.payload_json, 'payload_json')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "EnvelopeBaseV1":
        mapping = _expect_mapping(payload, "EnvelopeBaseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'producer', 'created_at', 'content_type', 'payload_json')),
            allowed=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'causation_id', 'producer', 'target', 'created_at', 'expires_at', 'schema_ref', 'content_type', 'payload_json')),
            model_name="EnvelopeBaseV1",
        )
        if mapping['schema_version'] != cls.SCHEMA_VERSION:
            raise ValueError('schema_version' + " must equal " + repr(cls.SCHEMA_VERSION))
        if mapping['content_type'] != cls.CONTENT_TYPE:
            raise ValueError('content_type' + " must equal " + repr(cls.CONTENT_TYPE))
        return cls(
            message_kind=_expect_str(mapping['message_kind'], 'message_kind'),
            message_type=_expect_str(mapping['message_type'], 'message_type'),
            message_version=_expect_int(mapping['message_version'], 'message_version'),
            message_id=_expect_str(mapping['message_id'], 'message_id'),
            correlation_id=(_expect_str(mapping['correlation_id'], 'correlation_id') if 'correlation_id' in mapping else None),
            causation_id=(_expect_str(mapping['causation_id'], 'causation_id') if 'causation_id' in mapping else None),
            producer=_expect_str(mapping['producer'], 'producer'),
            target=(_expect_str(mapping['target'], 'target') if 'target' in mapping else None),
            created_at=_expect_str(mapping['created_at'], 'created_at'),
            expires_at=(_expect_str(mapping['expires_at'], 'expires_at') if 'expires_at' in mapping else None),
            schema_ref=(_expect_str(mapping['schema_ref'], 'schema_ref') if 'schema_ref' in mapping else None),
            payload_json=_expect_str(mapping['payload_json'], 'payload_json'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'schema_version': self.SCHEMA_VERSION,
            'content_type': self.CONTENT_TYPE,
        }
        payload['message_kind'] = self.message_kind
        payload['message_type'] = self.message_type
        payload['message_version'] = self.message_version
        payload['message_id'] = self.message_id
        if self.correlation_id is not None:
            payload['correlation_id'] = self.correlation_id
        if self.causation_id is not None:
            payload['causation_id'] = self.causation_id
        payload['producer'] = self.producer
        if self.target is not None:
            payload['target'] = self.target
        payload['created_at'] = self.created_at
        if self.expires_at is not None:
            payload['expires_at'] = self.expires_at
        if self.schema_ref is not None:
            payload['schema_ref'] = self.schema_ref
        payload['payload_json'] = self.payload_json
        return payload

@dataclass(frozen=True, slots=True)
class EventEnvelopeV1:
    message_type: str
    message_version: int
    message_id: str
    producer: str
    created_at: str
    expires_at: str
    payload_json: str
    correlation_id: str | None = None
    causation_id: str | None = None
    target: str | None = None
    schema_ref: str | None = None

    SCHEMA_VERSION: ClassVar[str] = '1'
    MESSAGE_KIND: ClassVar[str] = 'event'
    CONTENT_TYPE: ClassVar[str] = 'application/json'
    def __post_init__(self) -> None:
        _expect_str(self.message_type, 'message_type')
        _expect_int(self.message_version, 'message_version')
        _expect_str(self.message_id, 'message_id')
        if self.correlation_id is not None:
            _expect_str(self.correlation_id, 'correlation_id')
        if self.causation_id is not None:
            _expect_str(self.causation_id, 'causation_id')
        _expect_str(self.producer, 'producer')
        if self.target is not None:
            _expect_str(self.target, 'target')
        _expect_str(self.created_at, 'created_at')
        _expect_str(self.expires_at, 'expires_at')
        if self.schema_ref is not None:
            _expect_str(self.schema_ref, 'schema_ref')
        _expect_str(self.payload_json, 'payload_json')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "EventEnvelopeV1":
        mapping = _expect_mapping(payload, "EventEnvelopeV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'producer', 'created_at', 'expires_at', 'content_type', 'payload_json')),
            allowed=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'causation_id', 'producer', 'target', 'created_at', 'expires_at', 'schema_ref', 'content_type', 'payload_json')),
            model_name="EventEnvelopeV1",
        )
        if mapping['schema_version'] != cls.SCHEMA_VERSION:
            raise ValueError('schema_version' + " must equal " + repr(cls.SCHEMA_VERSION))
        if mapping['message_kind'] != cls.MESSAGE_KIND:
            raise ValueError('message_kind' + " must equal " + repr(cls.MESSAGE_KIND))
        if mapping['content_type'] != cls.CONTENT_TYPE:
            raise ValueError('content_type' + " must equal " + repr(cls.CONTENT_TYPE))
        return cls(
            message_type=_expect_str(mapping['message_type'], 'message_type'),
            message_version=_expect_int(mapping['message_version'], 'message_version'),
            message_id=_expect_str(mapping['message_id'], 'message_id'),
            correlation_id=(_expect_str(mapping['correlation_id'], 'correlation_id') if 'correlation_id' in mapping else None),
            causation_id=(_expect_str(mapping['causation_id'], 'causation_id') if 'causation_id' in mapping else None),
            producer=_expect_str(mapping['producer'], 'producer'),
            target=(_expect_str(mapping['target'], 'target') if 'target' in mapping else None),
            created_at=_expect_str(mapping['created_at'], 'created_at'),
            expires_at=_expect_str(mapping['expires_at'], 'expires_at'),
            schema_ref=(_expect_str(mapping['schema_ref'], 'schema_ref') if 'schema_ref' in mapping else None),
            payload_json=_expect_str(mapping['payload_json'], 'payload_json'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'schema_version': self.SCHEMA_VERSION,
            'message_kind': self.MESSAGE_KIND,
            'content_type': self.CONTENT_TYPE,
        }
        payload['message_type'] = self.message_type
        payload['message_version'] = self.message_version
        payload['message_id'] = self.message_id
        if self.correlation_id is not None:
            payload['correlation_id'] = self.correlation_id
        if self.causation_id is not None:
            payload['causation_id'] = self.causation_id
        payload['producer'] = self.producer
        if self.target is not None:
            payload['target'] = self.target
        payload['created_at'] = self.created_at
        payload['expires_at'] = self.expires_at
        if self.schema_ref is not None:
            payload['schema_ref'] = self.schema_ref
        payload['payload_json'] = self.payload_json
        return payload

@dataclass(frozen=True, slots=True)
class CommandEnvelopeV1:
    message_type: str
    message_version: int
    message_id: str
    producer: str
    target: str
    created_at: str
    expires_at: str
    payload_json: str
    correlation_id: str | None = None
    causation_id: str | None = None
    schema_ref: str | None = None

    SCHEMA_VERSION: ClassVar[str] = '1'
    MESSAGE_KIND: ClassVar[str] = 'command'
    CONTENT_TYPE: ClassVar[str] = 'application/json'
    def __post_init__(self) -> None:
        _expect_str(self.message_type, 'message_type')
        _expect_int(self.message_version, 'message_version')
        _expect_str(self.message_id, 'message_id')
        if self.correlation_id is not None:
            _expect_str(self.correlation_id, 'correlation_id')
        if self.causation_id is not None:
            _expect_str(self.causation_id, 'causation_id')
        _expect_str(self.producer, 'producer')
        _expect_str(self.target, 'target')
        _expect_str(self.created_at, 'created_at')
        _expect_str(self.expires_at, 'expires_at')
        if self.schema_ref is not None:
            _expect_str(self.schema_ref, 'schema_ref')
        _expect_str(self.payload_json, 'payload_json')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CommandEnvelopeV1":
        mapping = _expect_mapping(payload, "CommandEnvelopeV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'producer', 'target', 'created_at', 'expires_at', 'content_type', 'payload_json')),
            allowed=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'causation_id', 'producer', 'target', 'created_at', 'expires_at', 'schema_ref', 'content_type', 'payload_json')),
            model_name="CommandEnvelopeV1",
        )
        if mapping['schema_version'] != cls.SCHEMA_VERSION:
            raise ValueError('schema_version' + " must equal " + repr(cls.SCHEMA_VERSION))
        if mapping['message_kind'] != cls.MESSAGE_KIND:
            raise ValueError('message_kind' + " must equal " + repr(cls.MESSAGE_KIND))
        if mapping['content_type'] != cls.CONTENT_TYPE:
            raise ValueError('content_type' + " must equal " + repr(cls.CONTENT_TYPE))
        return cls(
            message_type=_expect_str(mapping['message_type'], 'message_type'),
            message_version=_expect_int(mapping['message_version'], 'message_version'),
            message_id=_expect_str(mapping['message_id'], 'message_id'),
            correlation_id=(_expect_str(mapping['correlation_id'], 'correlation_id') if 'correlation_id' in mapping else None),
            causation_id=(_expect_str(mapping['causation_id'], 'causation_id') if 'causation_id' in mapping else None),
            producer=_expect_str(mapping['producer'], 'producer'),
            target=_expect_str(mapping['target'], 'target'),
            created_at=_expect_str(mapping['created_at'], 'created_at'),
            expires_at=_expect_str(mapping['expires_at'], 'expires_at'),
            schema_ref=(_expect_str(mapping['schema_ref'], 'schema_ref') if 'schema_ref' in mapping else None),
            payload_json=_expect_str(mapping['payload_json'], 'payload_json'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'schema_version': self.SCHEMA_VERSION,
            'message_kind': self.MESSAGE_KIND,
            'content_type': self.CONTENT_TYPE,
        }
        payload['message_type'] = self.message_type
        payload['message_version'] = self.message_version
        payload['message_id'] = self.message_id
        if self.correlation_id is not None:
            payload['correlation_id'] = self.correlation_id
        if self.causation_id is not None:
            payload['causation_id'] = self.causation_id
        payload['producer'] = self.producer
        payload['target'] = self.target
        payload['created_at'] = self.created_at
        payload['expires_at'] = self.expires_at
        if self.schema_ref is not None:
            payload['schema_ref'] = self.schema_ref
        payload['payload_json'] = self.payload_json
        return payload

@dataclass(frozen=True, slots=True)
class RpcRequestEnvelopeV1:
    message_type: str
    message_version: int
    message_id: str
    correlation_id: str
    producer: str
    target: str
    created_at: str
    expires_at: str
    payload_json: str
    reply_to: str
    timeout_ms: str
    causation_id: str | None = None
    schema_ref: str | None = None

    SCHEMA_VERSION: ClassVar[str] = '1'
    MESSAGE_KIND: ClassVar[str] = 'rpc_request'
    CONTENT_TYPE: ClassVar[str] = 'application/json'
    def __post_init__(self) -> None:
        _expect_str(self.message_type, 'message_type')
        _expect_int(self.message_version, 'message_version')
        _expect_str(self.message_id, 'message_id')
        _expect_str(self.correlation_id, 'correlation_id')
        if self.causation_id is not None:
            _expect_str(self.causation_id, 'causation_id')
        _expect_str(self.producer, 'producer')
        _expect_str(self.target, 'target')
        _expect_str(self.created_at, 'created_at')
        _expect_str(self.expires_at, 'expires_at')
        if self.schema_ref is not None:
            _expect_str(self.schema_ref, 'schema_ref')
        _expect_str(self.payload_json, 'payload_json')
        _expect_str(self.reply_to, 'reply_to')
        _expect_str(self.timeout_ms, 'timeout_ms')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RpcRequestEnvelopeV1":
        mapping = _expect_mapping(payload, "RpcRequestEnvelopeV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'producer', 'target', 'created_at', 'expires_at', 'content_type', 'payload_json', 'reply_to', 'timeout_ms')),
            allowed=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'causation_id', 'producer', 'target', 'created_at', 'expires_at', 'schema_ref', 'content_type', 'payload_json', 'reply_to', 'timeout_ms')),
            model_name="RpcRequestEnvelopeV1",
        )
        if mapping['schema_version'] != cls.SCHEMA_VERSION:
            raise ValueError('schema_version' + " must equal " + repr(cls.SCHEMA_VERSION))
        if mapping['message_kind'] != cls.MESSAGE_KIND:
            raise ValueError('message_kind' + " must equal " + repr(cls.MESSAGE_KIND))
        if mapping['content_type'] != cls.CONTENT_TYPE:
            raise ValueError('content_type' + " must equal " + repr(cls.CONTENT_TYPE))
        return cls(
            message_type=_expect_str(mapping['message_type'], 'message_type'),
            message_version=_expect_int(mapping['message_version'], 'message_version'),
            message_id=_expect_str(mapping['message_id'], 'message_id'),
            correlation_id=_expect_str(mapping['correlation_id'], 'correlation_id'),
            causation_id=(_expect_str(mapping['causation_id'], 'causation_id') if 'causation_id' in mapping else None),
            producer=_expect_str(mapping['producer'], 'producer'),
            target=_expect_str(mapping['target'], 'target'),
            created_at=_expect_str(mapping['created_at'], 'created_at'),
            expires_at=_expect_str(mapping['expires_at'], 'expires_at'),
            schema_ref=(_expect_str(mapping['schema_ref'], 'schema_ref') if 'schema_ref' in mapping else None),
            payload_json=_expect_str(mapping['payload_json'], 'payload_json'),
            reply_to=_expect_str(mapping['reply_to'], 'reply_to'),
            timeout_ms=_expect_str(mapping['timeout_ms'], 'timeout_ms'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'schema_version': self.SCHEMA_VERSION,
            'message_kind': self.MESSAGE_KIND,
            'content_type': self.CONTENT_TYPE,
        }
        payload['message_type'] = self.message_type
        payload['message_version'] = self.message_version
        payload['message_id'] = self.message_id
        payload['correlation_id'] = self.correlation_id
        if self.causation_id is not None:
            payload['causation_id'] = self.causation_id
        payload['producer'] = self.producer
        payload['target'] = self.target
        payload['created_at'] = self.created_at
        payload['expires_at'] = self.expires_at
        if self.schema_ref is not None:
            payload['schema_ref'] = self.schema_ref
        payload['payload_json'] = self.payload_json
        payload['reply_to'] = self.reply_to
        payload['timeout_ms'] = self.timeout_ms
        return payload

@dataclass(frozen=True, slots=True)
class RpcResponseEnvelopeV1:
    message_type: str
    message_version: int
    message_id: str
    correlation_id: str
    producer: str
    created_at: str
    payload_json: str
    status: str
    responded_at: str
    causation_id: str | None = None
    target: str | None = None
    expires_at: str | None = None
    schema_ref: str | None = None
    error_code: str | None = None
    error_message: str | None = None

    SCHEMA_VERSION: ClassVar[str] = '1'
    MESSAGE_KIND: ClassVar[str] = 'rpc_response'
    CONTENT_TYPE: ClassVar[str] = 'application/json'
    def __post_init__(self) -> None:
        _expect_str(self.message_type, 'message_type')
        _expect_int(self.message_version, 'message_version')
        _expect_str(self.message_id, 'message_id')
        _expect_str(self.correlation_id, 'correlation_id')
        if self.causation_id is not None:
            _expect_str(self.causation_id, 'causation_id')
        _expect_str(self.producer, 'producer')
        if self.target is not None:
            _expect_str(self.target, 'target')
        _expect_str(self.created_at, 'created_at')
        if self.expires_at is not None:
            _expect_str(self.expires_at, 'expires_at')
        if self.schema_ref is not None:
            _expect_str(self.schema_ref, 'schema_ref')
        _expect_str(self.payload_json, 'payload_json')
        _expect_str(self.status, 'status')
        if self.error_code is not None:
            _expect_str(self.error_code, 'error_code')
        if self.error_message is not None:
            _expect_str(self.error_message, 'error_message')
        _expect_str(self.responded_at, 'responded_at')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RpcResponseEnvelopeV1":
        mapping = _expect_mapping(payload, "RpcResponseEnvelopeV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'producer', 'created_at', 'content_type', 'payload_json', 'status', 'responded_at')),
            allowed=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'causation_id', 'producer', 'target', 'created_at', 'expires_at', 'schema_ref', 'content_type', 'payload_json', 'status', 'error_code', 'error_message', 'responded_at')),
            model_name="RpcResponseEnvelopeV1",
        )
        if mapping['schema_version'] != cls.SCHEMA_VERSION:
            raise ValueError('schema_version' + " must equal " + repr(cls.SCHEMA_VERSION))
        if mapping['message_kind'] != cls.MESSAGE_KIND:
            raise ValueError('message_kind' + " must equal " + repr(cls.MESSAGE_KIND))
        if mapping['content_type'] != cls.CONTENT_TYPE:
            raise ValueError('content_type' + " must equal " + repr(cls.CONTENT_TYPE))
        return cls(
            message_type=_expect_str(mapping['message_type'], 'message_type'),
            message_version=_expect_int(mapping['message_version'], 'message_version'),
            message_id=_expect_str(mapping['message_id'], 'message_id'),
            correlation_id=_expect_str(mapping['correlation_id'], 'correlation_id'),
            causation_id=(_expect_str(mapping['causation_id'], 'causation_id') if 'causation_id' in mapping else None),
            producer=_expect_str(mapping['producer'], 'producer'),
            target=(_expect_str(mapping['target'], 'target') if 'target' in mapping else None),
            created_at=_expect_str(mapping['created_at'], 'created_at'),
            expires_at=(_expect_str(mapping['expires_at'], 'expires_at') if 'expires_at' in mapping else None),
            schema_ref=(_expect_str(mapping['schema_ref'], 'schema_ref') if 'schema_ref' in mapping else None),
            payload_json=_expect_str(mapping['payload_json'], 'payload_json'),
            status=_expect_str(mapping['status'], 'status'),
            error_code=(_expect_str(mapping['error_code'], 'error_code') if 'error_code' in mapping else None),
            error_message=(_expect_str(mapping['error_message'], 'error_message') if 'error_message' in mapping else None),
            responded_at=_expect_str(mapping['responded_at'], 'responded_at'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'schema_version': self.SCHEMA_VERSION,
            'message_kind': self.MESSAGE_KIND,
            'content_type': self.CONTENT_TYPE,
        }
        payload['message_type'] = self.message_type
        payload['message_version'] = self.message_version
        payload['message_id'] = self.message_id
        payload['correlation_id'] = self.correlation_id
        if self.causation_id is not None:
            payload['causation_id'] = self.causation_id
        payload['producer'] = self.producer
        if self.target is not None:
            payload['target'] = self.target
        payload['created_at'] = self.created_at
        if self.expires_at is not None:
            payload['expires_at'] = self.expires_at
        if self.schema_ref is not None:
            payload['schema_ref'] = self.schema_ref
        payload['payload_json'] = self.payload_json
        payload['status'] = self.status
        if self.error_code is not None:
            payload['error_code'] = self.error_code
        if self.error_message is not None:
            payload['error_message'] = self.error_message
        payload['responded_at'] = self.responded_at
        return payload

@dataclass(frozen=True, slots=True)
class DlqEnvelopeV1:
    message_type: str
    message_version: int
    message_id: str
    producer: str
    created_at: str
    payload_json: str
    source_stream: str
    source_group: str
    source_id: str
    failed_at: str
    attempts: str
    failure_reason: str
    correlation_id: str | None = None
    causation_id: str | None = None
    target: str | None = None
    expires_at: str | None = None
    schema_ref: str | None = None

    SCHEMA_VERSION: ClassVar[str] = '1'
    MESSAGE_KIND: ClassVar[str] = 'dlq'
    CONTENT_TYPE: ClassVar[str] = 'application/json'
    def __post_init__(self) -> None:
        _expect_str(self.message_type, 'message_type')
        _expect_int(self.message_version, 'message_version')
        _expect_str(self.message_id, 'message_id')
        if self.correlation_id is not None:
            _expect_str(self.correlation_id, 'correlation_id')
        if self.causation_id is not None:
            _expect_str(self.causation_id, 'causation_id')
        _expect_str(self.producer, 'producer')
        if self.target is not None:
            _expect_str(self.target, 'target')
        _expect_str(self.created_at, 'created_at')
        if self.expires_at is not None:
            _expect_str(self.expires_at, 'expires_at')
        if self.schema_ref is not None:
            _expect_str(self.schema_ref, 'schema_ref')
        _expect_str(self.payload_json, 'payload_json')
        _expect_str(self.source_stream, 'source_stream')
        _expect_str(self.source_group, 'source_group')
        _expect_str(self.source_id, 'source_id')
        _expect_str(self.failed_at, 'failed_at')
        _expect_str(self.attempts, 'attempts')
        _expect_str(self.failure_reason, 'failure_reason')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DlqEnvelopeV1":
        mapping = _expect_mapping(payload, "DlqEnvelopeV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'producer', 'created_at', 'content_type', 'payload_json', 'source_stream', 'source_group', 'source_id', 'failed_at', 'attempts', 'failure_reason')),
            allowed=frozenset(('schema_version', 'message_kind', 'message_type', 'message_version', 'message_id', 'correlation_id', 'causation_id', 'producer', 'target', 'created_at', 'expires_at', 'schema_ref', 'content_type', 'payload_json', 'source_stream', 'source_group', 'source_id', 'failed_at', 'attempts', 'failure_reason')),
            model_name="DlqEnvelopeV1",
        )
        if mapping['schema_version'] != cls.SCHEMA_VERSION:
            raise ValueError('schema_version' + " must equal " + repr(cls.SCHEMA_VERSION))
        if mapping['message_kind'] != cls.MESSAGE_KIND:
            raise ValueError('message_kind' + " must equal " + repr(cls.MESSAGE_KIND))
        if mapping['content_type'] != cls.CONTENT_TYPE:
            raise ValueError('content_type' + " must equal " + repr(cls.CONTENT_TYPE))
        return cls(
            message_type=_expect_str(mapping['message_type'], 'message_type'),
            message_version=_expect_int(mapping['message_version'], 'message_version'),
            message_id=_expect_str(mapping['message_id'], 'message_id'),
            correlation_id=(_expect_str(mapping['correlation_id'], 'correlation_id') if 'correlation_id' in mapping else None),
            causation_id=(_expect_str(mapping['causation_id'], 'causation_id') if 'causation_id' in mapping else None),
            producer=_expect_str(mapping['producer'], 'producer'),
            target=(_expect_str(mapping['target'], 'target') if 'target' in mapping else None),
            created_at=_expect_str(mapping['created_at'], 'created_at'),
            expires_at=(_expect_str(mapping['expires_at'], 'expires_at') if 'expires_at' in mapping else None),
            schema_ref=(_expect_str(mapping['schema_ref'], 'schema_ref') if 'schema_ref' in mapping else None),
            payload_json=_expect_str(mapping['payload_json'], 'payload_json'),
            source_stream=_expect_str(mapping['source_stream'], 'source_stream'),
            source_group=_expect_str(mapping['source_group'], 'source_group'),
            source_id=_expect_str(mapping['source_id'], 'source_id'),
            failed_at=_expect_str(mapping['failed_at'], 'failed_at'),
            attempts=_expect_str(mapping['attempts'], 'attempts'),
            failure_reason=_expect_str(mapping['failure_reason'], 'failure_reason'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'schema_version': self.SCHEMA_VERSION,
            'message_kind': self.MESSAGE_KIND,
            'content_type': self.CONTENT_TYPE,
        }
        payload['message_type'] = self.message_type
        payload['message_version'] = self.message_version
        payload['message_id'] = self.message_id
        if self.correlation_id is not None:
            payload['correlation_id'] = self.correlation_id
        if self.causation_id is not None:
            payload['causation_id'] = self.causation_id
        payload['producer'] = self.producer
        if self.target is not None:
            payload['target'] = self.target
        payload['created_at'] = self.created_at
        if self.expires_at is not None:
            payload['expires_at'] = self.expires_at
        if self.schema_ref is not None:
            payload['schema_ref'] = self.schema_ref
        payload['payload_json'] = self.payload_json
        payload['source_stream'] = self.source_stream
        payload['source_group'] = self.source_group
        payload['source_id'] = self.source_id
        payload['failed_at'] = self.failed_at
        payload['attempts'] = self.attempts
        payload['failure_reason'] = self.failure_reason
        return payload

__all__ = [
    "EnvelopeBaseV1",
    "EventEnvelopeV1",
    "CommandEnvelopeV1",
    "RpcRequestEnvelopeV1",
    "RpcResponseEnvelopeV1",
    "DlqEnvelopeV1",
]
