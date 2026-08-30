"""Generated canonical sentinel protocol models."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
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
    "SentinelSubnetRulesInvalidatedV1",
    "SentinelSubnetSweepCommandV1",
]
