"""Generated canonical security protocol models."""

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
class PlayerPasswordResetCommandV1:
    playerUuid: str
    server: str

    MESSAGE_TYPE: ClassVar[str] = 'player.password-reset.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.playerUuid, 'playerUuid')
        _expect_str(self.server, 'server')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerPasswordResetCommandV1":
        mapping = _expect_mapping(payload, "PlayerPasswordResetCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'playerUuid', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'playerUuid', 'server')),
            model_name="PlayerPasswordResetCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            server=_expect_str(mapping['server'], 'server'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['playerUuid'] = self.playerUuid
        payload['server'] = self.server
        return payload

@dataclass(frozen=True, slots=True)
class SecurityPermissionsChangedV1:
    playerUuid: str
    revision: int
    sourceServer: str | None = None
    occurredAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'security.permissions.changed'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.playerUuid, 'playerUuid')
        _expect_int(self.revision, 'revision')
        if self.sourceServer is not None:
            _expect_str(self.sourceServer, 'sourceServer')
        if self.occurredAt is not None:
            _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SecurityPermissionsChangedV1":
        mapping = _expect_mapping(payload, "SecurityPermissionsChangedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'playerUuid', 'revision')),
            allowed=frozenset(('messageType', 'messageVersion', 'playerUuid', 'revision', 'sourceServer', 'occurredAt')),
            model_name="SecurityPermissionsChangedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            revision=_expect_int(mapping['revision'], 'revision'),
            sourceServer=(_expect_str(mapping['sourceServer'], 'sourceServer') if 'sourceServer' in mapping else None),
            occurredAt=(_expect_str(mapping['occurredAt'], 'occurredAt') if 'occurredAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['playerUuid'] = self.playerUuid
        payload['revision'] = self.revision
        if self.sourceServer is not None:
            payload['sourceServer'] = self.sourceServer
        if self.occurredAt is not None:
            payload['occurredAt'] = self.occurredAt
        return payload

@dataclass(frozen=True, slots=True)
class SecurityStaffResetPasswordRequestV1:
    server: str
    operationId: str
    playerUuid: str

    MESSAGE_TYPE: ClassVar[str] = 'security.staff.reset-password.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.operationId, 'operationId')
        _expect_str(self.playerUuid, 'playerUuid')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SecurityStaffResetPasswordRequestV1":
        mapping = _expect_mapping(payload, "SecurityStaffResetPasswordRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'playerUuid')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'playerUuid')),
            model_name="SecurityStaffResetPasswordRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            operationId=_expect_str(mapping['operationId'], 'operationId'),
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['operationId'] = self.operationId
        payload['playerUuid'] = self.playerUuid
        return payload

@dataclass(frozen=True, slots=True)
class SecurityStaffResetPasswordResponseV1:
    server: str
    operationId: str
    changed: bool

    MESSAGE_TYPE: ClassVar[str] = 'security.staff.reset-password.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.operationId, 'operationId')
        _expect_bool(self.changed, 'changed')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SecurityStaffResetPasswordResponseV1":
        mapping = _expect_mapping(payload, "SecurityStaffResetPasswordResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'changed')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'changed')),
            model_name="SecurityStaffResetPasswordResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            operationId=_expect_str(mapping['operationId'], 'operationId'),
            changed=_expect_bool(mapping['changed'], 'changed'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['operationId'] = self.operationId
        payload['changed'] = self.changed
        return payload

@dataclass(frozen=True, slots=True)
class SecurityStaffSyncRequestV1:
    server: str
    operationId: str
    playerUuid: str
    discordId: str
    roleIds: tuple[str, ...]
    complete: bool

    MESSAGE_TYPE: ClassVar[str] = 'security.staff.sync.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.operationId, 'operationId')
        _expect_str(self.playerUuid, 'playerUuid')
        _expect_str(self.discordId, 'discordId')
        if not isinstance(self.roleIds, tuple):
            raise TypeError("roleIds must be a tuple")
        for item in self.roleIds:
            _expect_str(item, 'roleIds[]')
        if len(set(self.roleIds)) != len(self.roleIds):
            raise ValueError("roleIds must not contain duplicate items")
        _expect_bool(self.complete, 'complete')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SecurityStaffSyncRequestV1":
        mapping = _expect_mapping(payload, "SecurityStaffSyncRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'playerUuid', 'discordId', 'roleIds', 'complete')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'playerUuid', 'discordId', 'roleIds', 'complete')),
            model_name="SecurityStaffSyncRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            operationId=_expect_str(mapping['operationId'], 'operationId'),
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            discordId=_expect_str(mapping['discordId'], 'discordId'),
            roleIds=tuple(_expect_str(item, 'roleIds[]') for item in _expect_list(mapping['roleIds'], 'roleIds')),
            complete=_expect_bool(mapping['complete'], 'complete'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['operationId'] = self.operationId
        payload['playerUuid'] = self.playerUuid
        payload['discordId'] = self.discordId
        payload['roleIds'] = [item for item in self.roleIds]
        payload['complete'] = self.complete
        return payload

@dataclass(frozen=True, slots=True)
class SecurityStaffSyncResponseV1:
    server: str
    operationId: str
    revision: int
    changed: bool

    MESSAGE_TYPE: ClassVar[str] = 'security.staff.sync.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.operationId, 'operationId')
        _expect_int(self.revision, 'revision')
        _expect_bool(self.changed, 'changed')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SecurityStaffSyncResponseV1":
        mapping = _expect_mapping(payload, "SecurityStaffSyncResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'revision', 'changed')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'revision', 'changed')),
            model_name="SecurityStaffSyncResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            operationId=_expect_str(mapping['operationId'], 'operationId'),
            revision=_expect_int(mapping['revision'], 'revision'),
            changed=_expect_bool(mapping['changed'], 'changed'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['operationId'] = self.operationId
        payload['revision'] = self.revision
        payload['changed'] = self.changed
        return payload

__all__ = [
    "PlayerPasswordResetCommandV1",
    "SecurityPermissionsChangedV1",
    "SecurityStaffResetPasswordRequestV1",
    "SecurityStaffResetPasswordResponseV1",
    "SecurityStaffSyncRequestV1",
    "SecurityStaffSyncResponseV1",
]
