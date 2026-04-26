"""Generated canonical moderation protocol models."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any, ClassVar

from .shared import (
    ActorRefV1,
    ExpirationInfoV1,
    PlayerRefV1,
    VoteKickParticipantV1,
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


def _expect_instance(value: Any, field_name: str, expected_type: type[Any]) -> None:
    if not isinstance(value, expected_type):
        raise TypeError(f"{field_name} must be a {expected_type.__name__}")

@dataclass(frozen=True, slots=True)
class ModerationAuditAppendedV1:
    entryType: str
    target: PlayerRefV1
    actor: ActorRefV1
    reason: str
    server: str | None = None
    occurredAt: str | None = None
    details: dict[str, Any] | None = None

    MESSAGE_TYPE: ClassVar[str] = 'moderation.audit.appended'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.entryType, 'entryType')
        _expect_instance(self.target, 'target', PlayerRefV1)
        _expect_instance(self.actor, 'actor', ActorRefV1)
        _expect_str(self.reason, 'reason')
        if self.server is not None:
            _expect_str(self.server, 'server')
        if self.occurredAt is not None:
            _expect_str(self.occurredAt, 'occurredAt')
        if self.details is not None:
            _expect_json_object(self.details, 'details', allowed_types=('string', 'number', 'boolean'), allow_null=True)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ModerationAuditAppendedV1":
        mapping = _expect_mapping(payload, "ModerationAuditAppendedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'entryType', 'target', 'actor', 'reason')),
            allowed=frozenset(('messageType', 'messageVersion', 'entryType', 'target', 'actor', 'reason', 'server', 'occurredAt', 'details')),
            model_name="ModerationAuditAppendedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            entryType=_expect_str(mapping['entryType'], 'entryType'),
            target=PlayerRefV1.from_payload(_expect_mapping(mapping['target'], 'target')),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            reason=_expect_str(mapping['reason'], 'reason'),
            server=(_expect_str(mapping['server'], 'server') if 'server' in mapping else None),
            occurredAt=(_expect_str(mapping['occurredAt'], 'occurredAt') if 'occurredAt' in mapping else None),
            details=(_expect_json_object(mapping['details'], 'details', allowed_types=('string', 'number', 'boolean'), allow_null=True) if 'details' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['entryType'] = self.entryType
        payload['target'] = self.target.to_payload()
        payload['actor'] = self.actor.to_payload()
        payload['reason'] = self.reason
        if self.server is not None:
            payload['server'] = self.server
        if self.occurredAt is not None:
            payload['occurredAt'] = self.occurredAt
        if self.details is not None:
            payload['details'] = dict(self.details)
        return payload

@dataclass(frozen=True, slots=True)
class ModerationBanCreatedV1:
    target: PlayerRefV1
    actor: ActorRefV1
    reason: str
    expiration: ExpirationInfoV1 | None = None
    server: str | None = None
    occurredAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'moderation.ban.created'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.target, 'target', PlayerRefV1)
        _expect_instance(self.actor, 'actor', ActorRefV1)
        _expect_str(self.reason, 'reason')
        if self.expiration is not None:
            _expect_instance(self.expiration, 'expiration', ExpirationInfoV1)
        if self.server is not None:
            _expect_str(self.server, 'server')
        if self.occurredAt is not None:
            _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ModerationBanCreatedV1":
        mapping = _expect_mapping(payload, "ModerationBanCreatedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'target', 'actor', 'reason')),
            allowed=frozenset(('messageType', 'messageVersion', 'target', 'actor', 'reason', 'expiration', 'server', 'occurredAt')),
            model_name="ModerationBanCreatedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            target=PlayerRefV1.from_payload(_expect_mapping(mapping['target'], 'target')),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            reason=_expect_str(mapping['reason'], 'reason'),
            expiration=(ExpirationInfoV1.from_payload(_expect_mapping(mapping['expiration'], 'expiration')) if 'expiration' in mapping else None),
            server=(_expect_str(mapping['server'], 'server') if 'server' in mapping else None),
            occurredAt=(_expect_str(mapping['occurredAt'], 'occurredAt') if 'occurredAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['target'] = self.target.to_payload()
        payload['actor'] = self.actor.to_payload()
        payload['reason'] = self.reason
        if self.expiration is not None:
            payload['expiration'] = self.expiration.to_payload()
        if self.server is not None:
            payload['server'] = self.server
        if self.occurredAt is not None:
            payload['occurredAt'] = self.occurredAt
        return payload

@dataclass(frozen=True, slots=True)
class ModerationKickBannedCommandV1:
    target: PlayerRefV1
    server: str
    requestedAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'moderation.kick-banned.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.target, 'target', PlayerRefV1)
        _expect_str(self.server, 'server')
        if self.requestedAt is not None:
            _expect_str(self.requestedAt, 'requestedAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ModerationKickBannedCommandV1":
        mapping = _expect_mapping(payload, "ModerationKickBannedCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'target', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'target', 'server', 'requestedAt')),
            model_name="ModerationKickBannedCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            target=PlayerRefV1.from_payload(_expect_mapping(mapping['target'], 'target')),
            server=_expect_str(mapping['server'], 'server'),
            requestedAt=(_expect_str(mapping['requestedAt'], 'requestedAt') if 'requestedAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['target'] = self.target.to_payload()
        payload['server'] = self.server
        if self.requestedAt is not None:
            payload['requestedAt'] = self.requestedAt
        return payload

@dataclass(frozen=True, slots=True)
class ModerationMuteCreatedV1:
    target: PlayerRefV1
    actor: ActorRefV1
    reason: str
    expiration: ExpirationInfoV1 | None = None
    server: str | None = None
    occurredAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'moderation.mute.created'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.target, 'target', PlayerRefV1)
        _expect_instance(self.actor, 'actor', ActorRefV1)
        _expect_str(self.reason, 'reason')
        if self.expiration is not None:
            _expect_instance(self.expiration, 'expiration', ExpirationInfoV1)
        if self.server is not None:
            _expect_str(self.server, 'server')
        if self.occurredAt is not None:
            _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ModerationMuteCreatedV1":
        mapping = _expect_mapping(payload, "ModerationMuteCreatedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'target', 'actor', 'reason')),
            allowed=frozenset(('messageType', 'messageVersion', 'target', 'actor', 'reason', 'expiration', 'server', 'occurredAt')),
            model_name="ModerationMuteCreatedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            target=PlayerRefV1.from_payload(_expect_mapping(mapping['target'], 'target')),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            reason=_expect_str(mapping['reason'], 'reason'),
            expiration=(ExpirationInfoV1.from_payload(_expect_mapping(mapping['expiration'], 'expiration')) if 'expiration' in mapping else None),
            server=(_expect_str(mapping['server'], 'server') if 'server' in mapping else None),
            occurredAt=(_expect_str(mapping['occurredAt'], 'occurredAt') if 'occurredAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['target'] = self.target.to_payload()
        payload['actor'] = self.actor.to_payload()
        payload['reason'] = self.reason
        if self.expiration is not None:
            payload['expiration'] = self.expiration.to_payload()
        if self.server is not None:
            payload['server'] = self.server
        if self.occurredAt is not None:
            payload['occurredAt'] = self.occurredAt
        return payload

@dataclass(frozen=True, slots=True)
class ModerationPardonCommandV1:
    target: PlayerRefV1
    server: str
    requestedAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'moderation.pardon.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.target, 'target', PlayerRefV1)
        _expect_str(self.server, 'server')
        if self.requestedAt is not None:
            _expect_str(self.requestedAt, 'requestedAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ModerationPardonCommandV1":
        mapping = _expect_mapping(payload, "ModerationPardonCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'target', 'server')),
            allowed=frozenset(('messageType', 'messageVersion', 'target', 'server', 'requestedAt')),
            model_name="ModerationPardonCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            target=PlayerRefV1.from_payload(_expect_mapping(mapping['target'], 'target')),
            server=_expect_str(mapping['server'], 'server'),
            requestedAt=(_expect_str(mapping['requestedAt'], 'requestedAt') if 'requestedAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['target'] = self.target.to_payload()
        payload['server'] = self.server
        if self.requestedAt is not None:
            payload['requestedAt'] = self.requestedAt
        return payload

@dataclass(frozen=True, slots=True)
class ModerationVoteKickCreatedV1:
    target: PlayerRefV1
    starter: ActorRefV1
    reason: str
    votesFor: tuple[VoteKickParticipantV1, ...] | None = None
    votesAgainst: tuple[VoteKickParticipantV1, ...] | None = None
    server: str | None = None
    occurredAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'moderation.vote-kick.created'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.target, 'target', PlayerRefV1)
        _expect_instance(self.starter, 'starter', ActorRefV1)
        _expect_str(self.reason, 'reason')
        if self.votesFor is not None:
            if not isinstance(self.votesFor, tuple):
                raise TypeError("votesFor must be a tuple")
            for item in self.votesFor:
                _expect_instance(item, 'votesFor[]', VoteKickParticipantV1)
        if self.votesAgainst is not None:
            if not isinstance(self.votesAgainst, tuple):
                raise TypeError("votesAgainst must be a tuple")
            for item in self.votesAgainst:
                _expect_instance(item, 'votesAgainst[]', VoteKickParticipantV1)
        if self.server is not None:
            _expect_str(self.server, 'server')
        if self.occurredAt is not None:
            _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ModerationVoteKickCreatedV1":
        mapping = _expect_mapping(payload, "ModerationVoteKickCreatedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'target', 'starter', 'reason')),
            allowed=frozenset(('messageType', 'messageVersion', 'target', 'starter', 'reason', 'votesFor', 'votesAgainst', 'server', 'occurredAt')),
            model_name="ModerationVoteKickCreatedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            target=PlayerRefV1.from_payload(_expect_mapping(mapping['target'], 'target')),
            starter=ActorRefV1.from_payload(_expect_mapping(mapping['starter'], 'starter')),
            reason=_expect_str(mapping['reason'], 'reason'),
            votesFor=(tuple(VoteKickParticipantV1.from_payload(_expect_mapping(item, 'votesFor[]')) for item in _expect_list(mapping['votesFor'], 'votesFor')) if 'votesFor' in mapping else None),
            votesAgainst=(tuple(VoteKickParticipantV1.from_payload(_expect_mapping(item, 'votesAgainst[]')) for item in _expect_list(mapping['votesAgainst'], 'votesAgainst')) if 'votesAgainst' in mapping else None),
            server=(_expect_str(mapping['server'], 'server') if 'server' in mapping else None),
            occurredAt=(_expect_str(mapping['occurredAt'], 'occurredAt') if 'occurredAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['target'] = self.target.to_payload()
        payload['starter'] = self.starter.to_payload()
        payload['reason'] = self.reason
        if self.votesFor is not None:
            payload['votesFor'] = [item.to_payload() for item in self.votesFor]
        if self.votesAgainst is not None:
            payload['votesAgainst'] = [item.to_payload() for item in self.votesAgainst]
        if self.server is not None:
            payload['server'] = self.server
        if self.occurredAt is not None:
            payload['occurredAt'] = self.occurredAt
        return payload

__all__ = [
    "ModerationAuditAppendedV1",
    "ModerationBanCreatedV1",
    "ModerationKickBannedCommandV1",
    "ModerationMuteCreatedV1",
    "ModerationPardonCommandV1",
    "ModerationVoteKickCreatedV1",
]
