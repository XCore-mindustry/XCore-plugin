"""Generated canonical maps protocol models."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, ClassVar

from .shared import (
    MapEntryV1,
    MapFileSourceV1,
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
class MapsListRequestV1:
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'maps.list.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapsListRequestV1":
        mapping = _expect_mapping(payload, "MapsListRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'server')),
            model_name="MapsListRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class MapsListResponseV1:
    server: str
    maps: tuple[MapEntryV1, ...]

    MESSAGE_TYPE: ClassVar[str] = 'maps.list.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        if not isinstance(self.maps, tuple):
            raise TypeError("maps must be a tuple")
        for item in self.maps:
            _expect_instance(item, 'maps[]', MapEntryV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapsListResponseV1":
        mapping = _expect_mapping(payload, "MapsListResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'maps')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'maps')),
            model_name="MapsListResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            maps=tuple(MapEntryV1.from_payload(_expect_mapping(item, 'maps[]')) for item in _expect_list(mapping['maps'], 'maps')),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['maps'] = [item.to_payload() for item in self.maps]
        return payload

@dataclass(frozen=True, slots=True)
class MapsLoadCommandV1:
    server: str
    files: tuple[MapFileSourceV1, ...]

    MESSAGE_TYPE: ClassVar[str] = 'maps.load.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        if not isinstance(self.files, tuple):
            raise TypeError("files must be a tuple")
        for item in self.files:
            _expect_instance(item, 'files[]', MapFileSourceV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapsLoadCommandV1":
        mapping = _expect_mapping(payload, "MapsLoadCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'files')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'files')),
            model_name="MapsLoadCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            files=tuple(MapFileSourceV1.from_payload(_expect_mapping(item, 'files[]')) for item in _expect_list(mapping['files'], 'files')),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['files'] = [item.to_payload() for item in self.files]
        return payload

@dataclass(frozen=True, slots=True)
class MapsRemoveRequestV1:
    server: str
    fileName: str

    MESSAGE_TYPE: ClassVar[str] = 'maps.remove.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.fileName, 'fileName')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapsRemoveRequestV1":
        mapping = _expect_mapping(payload, "MapsRemoveRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'fileName')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'fileName')),
            model_name="MapsRemoveRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            fileName=_expect_str(mapping['fileName'], 'fileName'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['fileName'] = self.fileName
        return payload

@dataclass(frozen=True, slots=True)
class MapsRemoveResponseV1:
    server: str
    result: str

    MESSAGE_TYPE: ClassVar[str] = 'maps.remove.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.result, 'result')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapsRemoveResponseV1":
        mapping = _expect_mapping(payload, "MapsRemoveResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'result')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'result')),
            model_name="MapsRemoveResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            result=_expect_str(mapping['result'], 'result'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['result'] = self.result
        return payload

__all__ = [
    "MapsListRequestV1",
    "MapsListResponseV1",
    "MapsLoadCommandV1",
    "MapsRemoveRequestV1",
    "MapsRemoveResponseV1",
]
