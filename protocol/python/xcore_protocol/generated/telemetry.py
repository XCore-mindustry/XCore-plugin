"""Generated canonical telemetry protocol models."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, ClassVar

from .shared import (
    MetricSampleV1,
    MetricSampleV1Type,
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
class MetricsSnapshotV1:
    server: str
    nodeId: str
    producer: str
    createdAtUnixMs: int
    startTimeUnixMs: int
    sequence: int
    intervalMs: int
    samples: tuple[MetricSampleV1, ...]

    SCHEMA_VERSION: ClassVar[str] = 'metrics.snapshot.v1'
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.nodeId, 'nodeId')
        _expect_str(self.producer, 'producer')
        _expect_int(self.createdAtUnixMs, 'createdAtUnixMs')
        _expect_int(self.startTimeUnixMs, 'startTimeUnixMs')
        _expect_int(self.sequence, 'sequence')
        _expect_int(self.intervalMs, 'intervalMs')
        if not isinstance(self.samples, tuple):
            raise TypeError("samples must be a tuple")
        for item in self.samples:
            _expect_instance(item, 'samples[]', MetricSampleV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MetricsSnapshotV1":
        mapping = _expect_mapping(payload, "MetricsSnapshotV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('schemaVersion', 'server', 'nodeId', 'producer', 'createdAtUnixMs', 'startTimeUnixMs', 'sequence', 'intervalMs', 'samples')),
            allowed=frozenset(('schemaVersion', 'server', 'nodeId', 'producer', 'createdAtUnixMs', 'startTimeUnixMs', 'sequence', 'intervalMs', 'samples')),
            model_name="MetricsSnapshotV1",
        )
        if mapping['schemaVersion'] != cls.SCHEMA_VERSION:
            raise ValueError('schemaVersion' + " must equal " + repr(cls.SCHEMA_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            nodeId=_expect_str(mapping['nodeId'], 'nodeId'),
            producer=_expect_str(mapping['producer'], 'producer'),
            createdAtUnixMs=_expect_int(mapping['createdAtUnixMs'], 'createdAtUnixMs'),
            startTimeUnixMs=_expect_int(mapping['startTimeUnixMs'], 'startTimeUnixMs'),
            sequence=_expect_int(mapping['sequence'], 'sequence'),
            intervalMs=_expect_int(mapping['intervalMs'], 'intervalMs'),
            samples=tuple(MetricSampleV1.from_payload(_expect_mapping(item, 'samples[]')) for item in _expect_list(mapping['samples'], 'samples')),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'schemaVersion': self.SCHEMA_VERSION,
        }
        payload['server'] = self.server
        payload['nodeId'] = self.nodeId
        payload['producer'] = self.producer
        payload['createdAtUnixMs'] = self.createdAtUnixMs
        payload['startTimeUnixMs'] = self.startTimeUnixMs
        payload['sequence'] = self.sequence
        payload['intervalMs'] = self.intervalMs
        payload['samples'] = [item.to_payload() for item in self.samples]
        return payload

__all__ = [
    "MetricsSnapshotV1",
]
