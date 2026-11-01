"""Generated canonical discord protocol models."""

from __future__ import annotations

import re
from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, ClassVar

from .shared import (
    ActorRefV1,
    DiscordIdentityRefV1,
    PlayerRefV1,
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

class DiscordAccountOperationRequestV1Action(StrEnum):
    LINK = 'link'
    UNLINK = 'unlink'

@dataclass(frozen=True, slots=True)
class DiscordAccountOperationRequestV1:
    server: str
    operationId: str
    action: DiscordAccountOperationRequestV1Action
    player: PlayerRefV1
    discord: DiscordIdentityRefV1
    issuedAt: int
    expiresAt: int
    code: str | None = None
    expectedLinkedAt: int | None = None

    MESSAGE_TYPE: ClassVar[str] = 'discord.account.operation.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.operationId, 'operationId')
        if re.search('^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$', self.operationId) is None:
            raise ValueError("operationId does not match its pattern")
        _expect_instance(self.action, 'action', DiscordAccountOperationRequestV1Action)
        _expect_instance(self.player, 'player', PlayerRefV1)
        _expect_instance(self.discord, 'discord', DiscordIdentityRefV1)
        _expect_int(self.issuedAt, 'issuedAt')
        if self.issuedAt < 0:
            raise ValueError("issuedAt must be >= 0")
        _expect_int(self.expiresAt, 'expiresAt')
        if self.expiresAt < 0:
            raise ValueError("expiresAt must be >= 0")
        if self.code is not None:
            _expect_str(self.code, 'code')
            if len(self.code) < 1:
                raise ValueError("code is too short")
        if self.expectedLinkedAt is not None:
            _expect_int(self.expectedLinkedAt, 'expectedLinkedAt')
            if self.expectedLinkedAt < 0:
                raise ValueError("expectedLinkedAt must be >= 0")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DiscordAccountOperationRequestV1":
        mapping = _expect_mapping(payload, "DiscordAccountOperationRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'action', 'player', 'discord', 'issuedAt', 'expiresAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'action', 'player', 'discord', 'issuedAt', 'expiresAt', 'code', 'expectedLinkedAt')),
            model_name="DiscordAccountOperationRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            operationId=_expect_str(mapping['operationId'], 'operationId'),
            action=_expect_enum(mapping['action'], 'action', DiscordAccountOperationRequestV1Action),
            player=PlayerRefV1.from_payload(_expect_mapping(mapping['player'], 'player')),
            discord=DiscordIdentityRefV1.from_payload(_expect_mapping(mapping['discord'], 'discord')),
            issuedAt=_expect_int(mapping['issuedAt'], 'issuedAt'),
            expiresAt=_expect_int(mapping['expiresAt'], 'expiresAt'),
            code=(_expect_str(mapping['code'], 'code') if 'code' in mapping else None),
            expectedLinkedAt=(_expect_int(mapping['expectedLinkedAt'], 'expectedLinkedAt') if 'expectedLinkedAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['operationId'] = self.operationId
        payload['action'] = str(self.action)
        payload['player'] = self.player.to_payload()
        payload['discord'] = self.discord.to_payload()
        payload['issuedAt'] = self.issuedAt
        payload['expiresAt'] = self.expiresAt
        if self.code is not None:
            payload['code'] = self.code
        if self.expectedLinkedAt is not None:
            payload['expectedLinkedAt'] = self.expectedLinkedAt
        return payload

class DiscordAccountOperationResponseV1Status(StrEnum):
    SUCCESS = 'success'
    REJECTED = 'rejected'
    FAILED = 'failed'
    UNKNOWN = 'unknown'

@dataclass(frozen=True, slots=True)
class DiscordAccountOperationResponseV1:
    server: str
    operationId: str
    status: DiscordAccountOperationResponseV1Status
    reason: str | None = None
    linkedAt: int | None = None

    MESSAGE_TYPE: ClassVar[str] = 'discord.account.operation.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.operationId, 'operationId')
        if re.search('^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$', self.operationId) is None:
            raise ValueError("operationId does not match its pattern")
        _expect_instance(self.status, 'status', DiscordAccountOperationResponseV1Status)
        if self.reason is not None:
            _expect_str(self.reason, 'reason')
            if len(self.reason) < 1:
                raise ValueError("reason is too short")
        if self.linkedAt is not None:
            _expect_int(self.linkedAt, 'linkedAt')
            if self.linkedAt < 0:
                raise ValueError("linkedAt must be >= 0")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DiscordAccountOperationResponseV1":
        mapping = _expect_mapping(payload, "DiscordAccountOperationResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'status')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'operationId', 'status', 'reason', 'linkedAt')),
            model_name="DiscordAccountOperationResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            operationId=_expect_str(mapping['operationId'], 'operationId'),
            status=_expect_enum(mapping['status'], 'status', DiscordAccountOperationResponseV1Status),
            reason=(_expect_str(mapping['reason'], 'reason') if 'reason' in mapping else None),
            linkedAt=(_expect_int(mapping['linkedAt'], 'linkedAt') if 'linkedAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['operationId'] = self.operationId
        payload['status'] = str(self.status)
        if self.reason is not None:
            payload['reason'] = self.reason
        if self.linkedAt is not None:
            payload['linkedAt'] = self.linkedAt
        return payload

@dataclass(frozen=True, slots=True)
class DiscordLinkCodeCreatedV1:
    code: str
    player: PlayerRefV1
    server: str
    createdAt: str
    expiresAt: str

    MESSAGE_TYPE: ClassVar[str] = 'discord.link-code-created'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.code, 'code')
        if len(self.code) < 1:
            raise ValueError("code is too short")
        _expect_instance(self.player, 'player', PlayerRefV1)
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.createdAt, 'createdAt')
        _expect_str(self.expiresAt, 'expiresAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DiscordLinkCodeCreatedV1":
        mapping = _expect_mapping(payload, "DiscordLinkCodeCreatedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'code', 'player', 'server', 'createdAt', 'expiresAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'code', 'player', 'server', 'createdAt', 'expiresAt')),
            model_name="DiscordLinkCodeCreatedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            code=_expect_str(mapping['code'], 'code'),
            player=PlayerRefV1.from_payload(_expect_mapping(mapping['player'], 'player')),
            server=_expect_str(mapping['server'], 'server'),
            createdAt=_expect_str(mapping['createdAt'], 'createdAt'),
            expiresAt=_expect_str(mapping['expiresAt'], 'expiresAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['code'] = self.code
        payload['player'] = self.player.to_payload()
        payload['server'] = self.server
        payload['createdAt'] = self.createdAt
        payload['expiresAt'] = self.expiresAt
        return payload

@dataclass(frozen=True, slots=True)
class DiscordLinkConfirmCommandV1:
    code: str
    player: PlayerRefV1
    discord: DiscordIdentityRefV1
    server: str
    confirmedAt: str

    MESSAGE_TYPE: ClassVar[str] = 'discord.link.confirm.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.code, 'code')
        if len(self.code) < 1:
            raise ValueError("code is too short")
        _expect_instance(self.player, 'player', PlayerRefV1)
        _expect_instance(self.discord, 'discord', DiscordIdentityRefV1)
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.confirmedAt, 'confirmedAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DiscordLinkConfirmCommandV1":
        mapping = _expect_mapping(payload, "DiscordLinkConfirmCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'code', 'player', 'discord', 'server', 'confirmedAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'code', 'player', 'discord', 'server', 'confirmedAt')),
            model_name="DiscordLinkConfirmCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            code=_expect_str(mapping['code'], 'code'),
            player=PlayerRefV1.from_payload(_expect_mapping(mapping['player'], 'player')),
            discord=DiscordIdentityRefV1.from_payload(_expect_mapping(mapping['discord'], 'discord')),
            server=_expect_str(mapping['server'], 'server'),
            confirmedAt=_expect_str(mapping['confirmedAt'], 'confirmedAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['code'] = self.code
        payload['player'] = self.player.to_payload()
        payload['discord'] = self.discord.to_payload()
        payload['server'] = self.server
        payload['confirmedAt'] = self.confirmedAt
        return payload

class DiscordLinkStatusChangedV1Action(StrEnum):
    LINKED = 'linked'
    UNLINKED = 'unlinked'

@dataclass(frozen=True, slots=True)
class DiscordLinkStatusChangedV1:
    player: PlayerRefV1
    discord: DiscordIdentityRefV1
    action: DiscordLinkStatusChangedV1Action
    server: str
    occurredAt: str

    MESSAGE_TYPE: ClassVar[str] = 'discord.link.status-changed'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.player, 'player', PlayerRefV1)
        _expect_instance(self.discord, 'discord', DiscordIdentityRefV1)
        _expect_instance(self.action, 'action', DiscordLinkStatusChangedV1Action)
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DiscordLinkStatusChangedV1":
        mapping = _expect_mapping(payload, "DiscordLinkStatusChangedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'player', 'discord', 'action', 'server', 'occurredAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'player', 'discord', 'action', 'server', 'occurredAt')),
            model_name="DiscordLinkStatusChangedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            player=PlayerRefV1.from_payload(_expect_mapping(mapping['player'], 'player')),
            discord=DiscordIdentityRefV1.from_payload(_expect_mapping(mapping['discord'], 'discord')),
            action=_expect_enum(mapping['action'], 'action', DiscordLinkStatusChangedV1Action),
            server=_expect_str(mapping['server'], 'server'),
            occurredAt=_expect_str(mapping['occurredAt'], 'occurredAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['player'] = self.player.to_payload()
        payload['discord'] = self.discord.to_payload()
        payload['action'] = str(self.action)
        payload['server'] = self.server
        payload['occurredAt'] = self.occurredAt
        return payload

@dataclass(frozen=True, slots=True)
class DiscordUnlinkCommandV1:
    player: PlayerRefV1
    discord: DiscordIdentityRefV1
    actor: ActorRefV1
    server: str
    requestedAt: str

    MESSAGE_TYPE: ClassVar[str] = 'discord.unlink.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.player, 'player', PlayerRefV1)
        _expect_instance(self.discord, 'discord', DiscordIdentityRefV1)
        _expect_instance(self.actor, 'actor', ActorRefV1)
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.requestedAt, 'requestedAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DiscordUnlinkCommandV1":
        mapping = _expect_mapping(payload, "DiscordUnlinkCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'player', 'discord', 'actor', 'server', 'requestedAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'player', 'discord', 'actor', 'server', 'requestedAt')),
            model_name="DiscordUnlinkCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            player=PlayerRefV1.from_payload(_expect_mapping(mapping['player'], 'player')),
            discord=DiscordIdentityRefV1.from_payload(_expect_mapping(mapping['discord'], 'discord')),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            server=_expect_str(mapping['server'], 'server'),
            requestedAt=_expect_str(mapping['requestedAt'], 'requestedAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['player'] = self.player.to_payload()
        payload['discord'] = self.discord.to_payload()
        payload['actor'] = self.actor.to_payload()
        payload['server'] = self.server
        payload['requestedAt'] = self.requestedAt
        return payload

__all__ = [
    "DiscordAccountOperationRequestV1",
    "DiscordAccountOperationResponseV1",
    "DiscordLinkCodeCreatedV1",
    "DiscordLinkConfirmCommandV1",
    "DiscordLinkStatusChangedV1",
    "DiscordUnlinkCommandV1",
    "DiscordAccountOperationRequestV1Action",
    "DiscordAccountOperationResponseV1Status",
    "DiscordLinkStatusChangedV1Action",
]
