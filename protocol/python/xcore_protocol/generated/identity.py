"""Generated canonical identity protocol models."""

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
class PlayerActiveBadgeChangedCommandV1:
    playerUuid: str
    activeBadge: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'player.active-badge.changed.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.playerUuid, 'playerUuid')
        _expect_str(self.activeBadge, 'activeBadge')
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerActiveBadgeChangedCommandV1":
        mapping = _expect_mapping(payload, "PlayerActiveBadgeChangedCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'playerUuid', 'activeBadge', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'playerUuid', 'activeBadge', 'server')),
            model_name="PlayerActiveBadgeChangedCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            activeBadge=_expect_str(mapping['activeBadge'], 'activeBadge'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['playerUuid'] = self.playerUuid
        payload['activeBadge'] = self.activeBadge
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class PlayerBadgeInventoryChangedCommandV1:
    playerUuid: str
    activeBadge: str
    unlockedBadges: tuple[str, ...]
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'player.badge-inventory.changed.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.playerUuid, 'playerUuid')
        _expect_str(self.activeBadge, 'activeBadge')
        if not isinstance(self.unlockedBadges, tuple):
            raise TypeError("unlockedBadges must be a tuple")
        for item in self.unlockedBadges:
            _expect_str(item, 'unlockedBadges[]')
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerBadgeInventoryChangedCommandV1":
        mapping = _expect_mapping(payload, "PlayerBadgeInventoryChangedCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'playerUuid', 'activeBadge', 'unlockedBadges', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'playerUuid', 'activeBadge', 'unlockedBadges', 'server')),
            model_name="PlayerBadgeInventoryChangedCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            activeBadge=_expect_str(mapping['activeBadge'], 'activeBadge'),
            unlockedBadges=tuple(_expect_str(item, 'unlockedBadges[]') for item in _expect_list(mapping['unlockedBadges'], 'unlockedBadges')),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['playerUuid'] = self.playerUuid
        payload['activeBadge'] = self.activeBadge
        payload['unlockedBadges'] = [item for item in self.unlockedBadges]
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class PlayerBadgeSymbolColorModeChangedCommandV1:
    playerUuid: str
    badgeSymbolColorMode: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'player.badge-symbol-color-mode.changed.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.playerUuid, 'playerUuid')
        _expect_str(self.badgeSymbolColorMode, 'badgeSymbolColorMode')
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerBadgeSymbolColorModeChangedCommandV1":
        mapping = _expect_mapping(payload, "PlayerBadgeSymbolColorModeChangedCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'playerUuid', 'badgeSymbolColorMode', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'playerUuid', 'badgeSymbolColorMode', 'server')),
            model_name="PlayerBadgeSymbolColorModeChangedCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            badgeSymbolColorMode=_expect_str(mapping['badgeSymbolColorMode'], 'badgeSymbolColorMode'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['playerUuid'] = self.playerUuid
        payload['badgeSymbolColorMode'] = self.badgeSymbolColorMode
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class PlayerCustomNicknameChangedCommandV1:
    playerUuid: str
    customNickname: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'player.custom-nickname.changed.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.playerUuid, 'playerUuid')
        _expect_str(self.customNickname, 'customNickname')
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerCustomNicknameChangedCommandV1":
        mapping = _expect_mapping(payload, "PlayerCustomNicknameChangedCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'playerUuid', 'customNickname', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'playerUuid', 'customNickname', 'server')),
            model_name="PlayerCustomNicknameChangedCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            customNickname=_expect_str(mapping['customNickname'], 'customNickname'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['playerUuid'] = self.playerUuid
        payload['customNickname'] = self.customNickname
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class PlayerJoinLeaveV1:
    playerName: str
    server: str
    joined: bool

    MESSAGE_TYPE: ClassVar[str] = 'player.join-leave'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.playerName, 'playerName')
        _expect_str(self.server, 'server')
        _expect_bool(self.joined, 'joined')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerJoinLeaveV1":
        mapping = _expect_mapping(payload, "PlayerJoinLeaveV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'playerName', 'server', 'joined')),
            allowed=frozenset(('messageType', 'messageVersion', 'playerName', 'server', 'joined')),
            model_name="PlayerJoinLeaveV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            playerName=_expect_str(mapping['playerName'], 'playerName'),
            server=_expect_str(mapping['server'], 'server'),
            joined=_expect_bool(mapping['joined'], 'joined'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['playerName'] = self.playerName
        payload['server'] = self.server
        payload['joined'] = self.joined
        return payload

__all__ = [
    "PlayerActiveBadgeChangedCommandV1",
    "PlayerBadgeInventoryChangedCommandV1",
    "PlayerBadgeSymbolColorModeChangedCommandV1",
    "PlayerCustomNicknameChangedCommandV1",
    "PlayerJoinLeaveV1",
]
