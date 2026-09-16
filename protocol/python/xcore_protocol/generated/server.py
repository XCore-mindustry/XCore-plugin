"""Generated canonical server protocol models."""

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
class PlayerDataCacheReloadCommandV1:
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'player-data-cache.reload.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerDataCacheReloadCommandV1":
        mapping = _expect_mapping(payload, "PlayerDataCacheReloadCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'server')),
            model_name="PlayerDataCacheReloadCommandV1",
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
class ServerCommandExecuteCommandV1:
    command: str
    targetServers: tuple[str, ...]
    exclusion: bool

    MESSAGE_TYPE: ClassVar[str] = 'server-command.execute.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.command, 'command')
        if not isinstance(self.targetServers, tuple):
            raise TypeError("targetServers must be a tuple")
        for item in self.targetServers:
            _expect_str(item, 'targetServers[]')
        _expect_bool(self.exclusion, 'exclusion')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ServerCommandExecuteCommandV1":
        mapping = _expect_mapping(payload, "ServerCommandExecuteCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'command', 'targetServers', 'exclusion')),
            allowed=frozenset(('messageType', 'messageVersion', 'command', 'targetServers', 'exclusion')),
            model_name="ServerCommandExecuteCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            command=_expect_str(mapping['command'], 'command'),
            targetServers=tuple(_expect_str(item, 'targetServers[]') for item in _expect_list(mapping['targetServers'], 'targetServers')),
            exclusion=_expect_bool(mapping['exclusion'], 'exclusion'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['command'] = self.command
        payload['targetServers'] = [item for item in self.targetServers]
        payload['exclusion'] = self.exclusion
        return payload

@dataclass(frozen=True, slots=True)
class ServerActionV1:
    message: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'server.action'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.message, 'message')
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ServerActionV1":
        mapping = _expect_mapping(payload, "ServerActionV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'message', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'message', 'server')),
            model_name="ServerActionV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            message=_expect_str(mapping['message'], 'message'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['message'] = self.message
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class ServerHeartbeatV1:
    serverName: str
    discordChannelId: int
    players: int
    maxPlayers: int
    version: str
    host: str | None = None
    port: int | None = None

    MESSAGE_TYPE: ClassVar[str] = 'server.heartbeat'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.serverName, 'serverName')
        _expect_int(self.discordChannelId, 'discordChannelId')
        _expect_int(self.players, 'players')
        _expect_int(self.maxPlayers, 'maxPlayers')
        _expect_str(self.version, 'version')
        if self.host is not None:
            _expect_str(self.host, 'host')
        if self.port is not None:
            _expect_int(self.port, 'port')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ServerHeartbeatV1":
        mapping = _expect_mapping(payload, "ServerHeartbeatV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'serverName', 'discordChannelId', 'players', 'maxPlayers', 'version')),
            allowed=frozenset(('messageType', 'messageVersion', 'serverName', 'discordChannelId', 'players', 'maxPlayers', 'version', 'host', 'port')),
            model_name="ServerHeartbeatV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            serverName=_expect_str(mapping['serverName'], 'serverName'),
            discordChannelId=_expect_int(mapping['discordChannelId'], 'discordChannelId'),
            players=_expect_int(mapping['players'], 'players'),
            maxPlayers=_expect_int(mapping['maxPlayers'], 'maxPlayers'),
            version=_expect_str(mapping['version'], 'version'),
            host=(_expect_str(mapping['host'], 'host') if 'host' in mapping else None),
            port=(_expect_int(mapping['port'], 'port') if 'port' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['serverName'] = self.serverName
        payload['discordChannelId'] = self.discordChannelId
        payload['players'] = self.players
        payload['maxPlayers'] = self.maxPlayers
        payload['version'] = self.version
        if self.host is not None:
            payload['host'] = self.host
        if self.port is not None:
            payload['port'] = self.port
        return payload

__all__ = [
    "PlayerDataCacheReloadCommandV1",
    "ServerCommandExecuteCommandV1",
    "ServerActionV1",
    "ServerHeartbeatV1",
]
