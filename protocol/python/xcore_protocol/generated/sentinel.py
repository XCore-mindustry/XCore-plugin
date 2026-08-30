"""Generated canonical sentinel protocol models."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, ClassVar

from .shared import (
    ActorRefV1,
    ActorRefV1ActorType,
)

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


def _expect_enum(value: Any, field_name: str, enum_type: type[StrEnum]) -> StrEnum:
    if not isinstance(value, str):
        raise TypeError(f"{field_name} must be a string")
    try:
        return enum_type(value)
    except ValueError as error:
        allowed = ", ".join(member.value for member in enum_type)
        raise ValueError(f"{field_name} must be one of: {allowed}") from error


def _expect_instance(value: Any, field_name: str, expected_type: type[Any]) -> None:
    if not isinstance(value, expected_type):
        raise TypeError(f"{field_name} must be a {expected_type.__name__}")

@dataclass(frozen=True, slots=True)
class SentinelSubnetRulesCheckRequestV1:
    request: str
    targetServer: str
    ip: str

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-rules.check.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.request, 'request')
        _expect_str(self.targetServer, 'targetServer')
        _expect_str(self.ip, 'ip')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetRulesCheckRequestV1":
        mapping = _expect_mapping(payload, "SentinelSubnetRulesCheckRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'request', 'targetServer', 'ip')),
            allowed=frozenset(('messageType', 'messageVersion', 'request', 'targetServer', 'ip')),
            model_name="SentinelSubnetRulesCheckRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            request=_expect_str(mapping['request'], 'request'),
            targetServer=_expect_str(mapping['targetServer'], 'targetServer'),
            ip=_expect_str(mapping['ip'], 'ip'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['request'] = self.request
        payload['targetServer'] = self.targetServer
        payload['ip'] = self.ip
        return payload

@dataclass(frozen=True, slots=True)
class SentinelSubnetRulesCheckResponseV1:
    request: str
    targetServer: str
    allowed: bool
    matchedRules: tuple[str, ...]

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-rules.check.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.request, 'request')
        _expect_str(self.targetServer, 'targetServer')
        _expect_bool(self.allowed, 'allowed')
        if not isinstance(self.matchedRules, tuple):
            raise TypeError("matchedRules must be a tuple")
        for item in self.matchedRules:
            _expect_str(item, 'matchedRules[]')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetRulesCheckResponseV1":
        mapping = _expect_mapping(payload, "SentinelSubnetRulesCheckResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'request', 'targetServer', 'allowed', 'matchedRules')),
            allowed=frozenset(('messageType', 'messageVersion', 'request', 'targetServer', 'allowed', 'matchedRules')),
            model_name="SentinelSubnetRulesCheckResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            request=_expect_str(mapping['request'], 'request'),
            targetServer=_expect_str(mapping['targetServer'], 'targetServer'),
            allowed=_expect_bool(mapping['allowed'], 'allowed'),
            matchedRules=tuple(_expect_str(item, 'matchedRules[]') for item in _expect_list(mapping['matchedRules'], 'matchedRules')),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['request'] = self.request
        payload['targetServer'] = self.targetServer
        payload['allowed'] = self.allowed
        payload['matchedRules'] = [item for item in self.matchedRules]
        return payload

class SentinelSubnetRulesCommandV1Operation(StrEnum):
    ALLOW = 'ALLOW'
    DENY = 'DENY'
    REMOVE = 'REMOVE'
    IMPORT = 'IMPORT'
    RELOAD = 'RELOAD'

@dataclass(frozen=True, slots=True)
class SentinelSubnetRulesCommandV1:
    request: str
    idempotency: str
    actor: ActorRefV1
    operation: SentinelSubnetRulesCommandV1Operation
    rules: tuple[str, ...]
    targetServer: str | None = None
    source: str | None = None
    reason: str | None = None
    expiresAt: int | None = None

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-rules.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.request, 'request')
        _expect_str(self.idempotency, 'idempotency')
        _expect_instance(self.actor, 'actor', ActorRefV1)
        _expect_instance(self.operation, 'operation', SentinelSubnetRulesCommandV1Operation)
        if self.targetServer is not None:
            _expect_str(self.targetServer, 'targetServer')
        if not isinstance(self.rules, tuple):
            raise TypeError("rules must be a tuple")
        for item in self.rules:
            _expect_str(item, 'rules[]')
        if self.source is not None:
            _expect_str(self.source, 'source')
        if self.reason is not None:
            _expect_str(self.reason, 'reason')
        if self.expiresAt is not None:
            _expect_int(self.expiresAt, 'expiresAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetRulesCommandV1":
        mapping = _expect_mapping(payload, "SentinelSubnetRulesCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'request', 'idempotency', 'actor', 'operation', 'rules')),
            allowed=frozenset(('messageType', 'messageVersion', 'request', 'idempotency', 'actor', 'operation', 'targetServer', 'rules', 'source', 'reason', 'expiresAt')),
            model_name="SentinelSubnetRulesCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            request=_expect_str(mapping['request'], 'request'),
            idempotency=_expect_str(mapping['idempotency'], 'idempotency'),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            operation=_expect_enum(mapping['operation'], 'operation', SentinelSubnetRulesCommandV1Operation),
            targetServer=(_expect_str(mapping['targetServer'], 'targetServer') if 'targetServer' in mapping else None),
            rules=tuple(_expect_str(item, 'rules[]') for item in _expect_list(mapping['rules'], 'rules')),
            source=(_expect_str(mapping['source'], 'source') if 'source' in mapping else None),
            reason=(_expect_str(mapping['reason'], 'reason') if 'reason' in mapping else None),
            expiresAt=(_expect_int(mapping['expiresAt'], 'expiresAt') if 'expiresAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['request'] = self.request
        payload['idempotency'] = self.idempotency
        payload['actor'] = self.actor.to_payload()
        payload['operation'] = str(self.operation)
        if self.targetServer is not None:
            payload['targetServer'] = self.targetServer
        payload['rules'] = [item for item in self.rules]
        if self.source is not None:
            payload['source'] = self.source
        if self.reason is not None:
            payload['reason'] = self.reason
        if self.expiresAt is not None:
            payload['expiresAt'] = self.expiresAt
        return payload

@dataclass(frozen=True, slots=True)
class SentinelSubnetRulesInvalidatedV1:
    reason: str | None = None
    sourceServer: str | None = None
    occurredAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-rules.invalidated'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        if self.reason is not None:
            _expect_str(self.reason, 'reason')
        if self.sourceServer is not None:
            _expect_str(self.sourceServer, 'sourceServer')
        if self.occurredAt is not None:
            _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetRulesInvalidatedV1":
        mapping = _expect_mapping(payload, "SentinelSubnetRulesInvalidatedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion')),
            allowed=frozenset(('messageType', 'messageVersion', 'reason', 'sourceServer', 'occurredAt')),
            model_name="SentinelSubnetRulesInvalidatedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            reason=(_expect_str(mapping['reason'], 'reason') if 'reason' in mapping else None),
            sourceServer=(_expect_str(mapping['sourceServer'], 'sourceServer') if 'sourceServer' in mapping else None),
            occurredAt=(_expect_str(mapping['occurredAt'], 'occurredAt') if 'occurredAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        if self.reason is not None:
            payload['reason'] = self.reason
        if self.sourceServer is not None:
            payload['sourceServer'] = self.sourceServer
        if self.occurredAt is not None:
            payload['occurredAt'] = self.occurredAt
        return payload

@dataclass(frozen=True, slots=True)
class SentinelSubnetRulesListRequestV1:
    request: str
    targetServer: str

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-rules.list.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.request, 'request')
        _expect_str(self.targetServer, 'targetServer')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetRulesListRequestV1":
        mapping = _expect_mapping(payload, "SentinelSubnetRulesListRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'request', 'targetServer')),
            allowed=frozenset(('messageType', 'messageVersion', 'request', 'targetServer')),
            model_name="SentinelSubnetRulesListRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            request=_expect_str(mapping['request'], 'request'),
            targetServer=_expect_str(mapping['targetServer'], 'targetServer'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['request'] = self.request
        payload['targetServer'] = self.targetServer
        return payload

@dataclass(frozen=True, slots=True)
class SentinelSubnetRulesListResponseV1:
    request: str
    targetServer: str
    rules: tuple[str, ...]

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-rules.list.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.request, 'request')
        _expect_str(self.targetServer, 'targetServer')
        if not isinstance(self.rules, tuple):
            raise TypeError("rules must be a tuple")
        for item in self.rules:
            _expect_str(item, 'rules[]')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetRulesListResponseV1":
        mapping = _expect_mapping(payload, "SentinelSubnetRulesListResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'request', 'targetServer', 'rules')),
            allowed=frozenset(('messageType', 'messageVersion', 'request', 'targetServer', 'rules')),
            model_name="SentinelSubnetRulesListResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            request=_expect_str(mapping['request'], 'request'),
            targetServer=_expect_str(mapping['targetServer'], 'targetServer'),
            rules=tuple(_expect_str(item, 'rules[]') for item in _expect_list(mapping['rules'], 'rules')),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['request'] = self.request
        payload['targetServer'] = self.targetServer
        payload['rules'] = [item for item in self.rules]
        return payload

@dataclass(frozen=True, slots=True)
class SentinelSubnetRulesResponseV1:
    request: str
    success: bool
    targetServer: str | None = None
    error: str | None = None
    rules: tuple[str, ...] | None = None

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-rules.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.request, 'request')
        _expect_bool(self.success, 'success')
        if self.targetServer is not None:
            _expect_str(self.targetServer, 'targetServer')
        if self.error is not None:
            _expect_str(self.error, 'error')
        if self.rules is not None:
            if not isinstance(self.rules, tuple):
                raise TypeError("rules must be a tuple")
            for item in self.rules:
                _expect_str(item, 'rules[]')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetRulesResponseV1":
        mapping = _expect_mapping(payload, "SentinelSubnetRulesResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'request', 'success')),
            allowed=frozenset(('messageType', 'messageVersion', 'request', 'success', 'targetServer', 'error', 'rules')),
            model_name="SentinelSubnetRulesResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            request=_expect_str(mapping['request'], 'request'),
            success=_expect_bool(mapping['success'], 'success'),
            targetServer=(_expect_str(mapping['targetServer'], 'targetServer') if 'targetServer' in mapping else None),
            error=(_expect_str(mapping['error'], 'error') if 'error' in mapping else None),
            rules=(tuple(_expect_str(item, 'rules[]') for item in _expect_list(mapping['rules'], 'rules')) if 'rules' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['request'] = self.request
        payload['success'] = self.success
        if self.targetServer is not None:
            payload['targetServer'] = self.targetServer
        if self.error is not None:
            payload['error'] = self.error
        if self.rules is not None:
            payload['rules'] = [item for item in self.rules]
        return payload

@dataclass(frozen=True, slots=True)
class SentinelSubnetSweepCommandV1:
    actor: str | None = None
    reason: str | None = None
    occurredAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'sentinel.subnet-sweep.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        if self.actor is not None:
            _expect_str(self.actor, 'actor')
        if self.reason is not None:
            _expect_str(self.reason, 'reason')
        if self.occurredAt is not None:
            _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SentinelSubnetSweepCommandV1":
        mapping = _expect_mapping(payload, "SentinelSubnetSweepCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion')),
            allowed=frozenset(('messageType', 'messageVersion', 'actor', 'reason', 'occurredAt')),
            model_name="SentinelSubnetSweepCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            actor=(_expect_str(mapping['actor'], 'actor') if 'actor' in mapping else None),
            reason=(_expect_str(mapping['reason'], 'reason') if 'reason' in mapping else None),
            occurredAt=(_expect_str(mapping['occurredAt'], 'occurredAt') if 'occurredAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        if self.actor is not None:
            payload['actor'] = self.actor
        if self.reason is not None:
            payload['reason'] = self.reason
        if self.occurredAt is not None:
            payload['occurredAt'] = self.occurredAt
        return payload

__all__ = [
    "SentinelSubnetRulesCheckRequestV1",
    "SentinelSubnetRulesCheckResponseV1",
    "SentinelSubnetRulesCommandV1",
    "SentinelSubnetRulesInvalidatedV1",
    "SentinelSubnetRulesListRequestV1",
    "SentinelSubnetRulesListResponseV1",
    "SentinelSubnetRulesResponseV1",
    "SentinelSubnetSweepCommandV1",
    "SentinelSubnetRulesCommandV1Operation",
]
