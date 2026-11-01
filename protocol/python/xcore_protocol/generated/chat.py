"""Generated canonical chat protocol models."""

from __future__ import annotations

import re
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
class ChatDiscordIngressCommandV1:
    authorName: str
    message: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'chat.discord-ingress.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.authorName, 'authorName')
        if len(self.authorName) < 1:
            raise ValueError("authorName is too short")
        _expect_str(self.message, 'message')
        if len(self.message) < 1:
            raise ValueError("message is too short")
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ChatDiscordIngressCommandV1":
        mapping = _expect_mapping(payload, "ChatDiscordIngressCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'authorName', 'message', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'authorName', 'message', 'server')),
            model_name="ChatDiscordIngressCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            authorName=_expect_str(mapping['authorName'], 'authorName'),
            message=_expect_str(mapping['message'], 'message'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['authorName'] = self.authorName
        payload['message'] = self.message
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class ChatGlobalV1:
    authorName: str
    message: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'chat.global'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.authorName, 'authorName')
        if len(self.authorName) < 1:
            raise ValueError("authorName is too short")
        _expect_str(self.message, 'message')
        if len(self.message) < 1:
            raise ValueError("message is too short")
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ChatGlobalV1":
        mapping = _expect_mapping(payload, "ChatGlobalV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'authorName', 'message', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'authorName', 'message', 'server')),
            model_name="ChatGlobalV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            authorName=_expect_str(mapping['authorName'], 'authorName'),
            message=_expect_str(mapping['message'], 'message'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['authorName'] = self.authorName
        payload['message'] = self.message
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class ChatMessageV1:
    authorName: str
    message: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'chat.message'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.authorName, 'authorName')
        if len(self.authorName) < 1:
            raise ValueError("authorName is too short")
        _expect_str(self.message, 'message')
        if len(self.message) < 1:
            raise ValueError("message is too short")
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ChatMessageV1":
        mapping = _expect_mapping(payload, "ChatMessageV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'authorName', 'message', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'authorName', 'message', 'server')),
            model_name="ChatMessageV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            authorName=_expect_str(mapping['authorName'], 'authorName'),
            message=_expect_str(mapping['message'], 'message'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['authorName'] = self.authorName
        payload['message'] = self.message
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class ChatPrivateV1:
    fromUuid: str
    fromPid: int
    fromName: str
    toUuid: str
    toPid: int
    message: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'chat.private'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.fromUuid, 'fromUuid')
        if len(self.fromUuid) < 1:
            raise ValueError("fromUuid is too short")
        _expect_int(self.fromPid, 'fromPid')
        _expect_str(self.fromName, 'fromName')
        if len(self.fromName) < 1:
            raise ValueError("fromName is too short")
        _expect_str(self.toUuid, 'toUuid')
        if len(self.toUuid) < 1:
            raise ValueError("toUuid is too short")
        _expect_int(self.toPid, 'toPid')
        _expect_str(self.message, 'message')
        if len(self.message) < 1:
            raise ValueError("message is too short")
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ChatPrivateV1":
        mapping = _expect_mapping(payload, "ChatPrivateV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'fromUuid', 'fromPid', 'fromName', 'toUuid', 'toPid', 'message', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'fromUuid', 'fromPid', 'fromName', 'toUuid', 'toPid', 'message', 'server')),
            model_name="ChatPrivateV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            fromUuid=_expect_str(mapping['fromUuid'], 'fromUuid'),
            fromPid=_expect_int(mapping['fromPid'], 'fromPid'),
            fromName=_expect_str(mapping['fromName'], 'fromName'),
            toUuid=_expect_str(mapping['toUuid'], 'toUuid'),
            toPid=_expect_int(mapping['toPid'], 'toPid'),
            message=_expect_str(mapping['message'], 'message'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['fromUuid'] = self.fromUuid
        payload['fromPid'] = self.fromPid
        payload['fromName'] = self.fromName
        payload['toUuid'] = self.toUuid
        payload['toPid'] = self.toPid
        payload['message'] = self.message
        payload['server'] = self.server
        return payload

__all__ = [
    "ChatDiscordIngressCommandV1",
    "ChatGlobalV1",
    "ChatMessageV1",
    "ChatPrivateV1",
]
