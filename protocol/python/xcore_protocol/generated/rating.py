"""Generated canonical rating protocol models."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, ClassVar

from .shared import (
    ActorRefV1,
    DiscordIdentityRefV1,
    PlayerRefV1,
    SeasonPodiumEntryV1,
    SeasonPrizeV1,
    SeasonRefV1,
    SeasonSummaryV1,
    ActorRefV1ActorType,
    SeasonPrizeV1Kind,
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
class RatingAccountsMergeRequestV1:
    server: str
    sourceUuid: str
    targetUuid: str

    MESSAGE_TYPE: ClassVar[str] = 'rating.accounts.merge.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.sourceUuid, 'sourceUuid')
        _expect_str(self.targetUuid, 'targetUuid')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingAccountsMergeRequestV1":
        mapping = _expect_mapping(payload, "RatingAccountsMergeRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'sourceUuid', 'targetUuid')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'sourceUuid', 'targetUuid')),
            model_name="RatingAccountsMergeRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            sourceUuid=_expect_str(mapping['sourceUuid'], 'sourceUuid'),
            targetUuid=_expect_str(mapping['targetUuid'], 'targetUuid'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['sourceUuid'] = self.sourceUuid
        payload['targetUuid'] = self.targetUuid
        return payload

@dataclass(frozen=True, slots=True)
class RatingAccountsMergeResponseV1:
    server: str
    standingsMerged: int

    MESSAGE_TYPE: ClassVar[str] = 'rating.accounts.merge.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_int(self.standingsMerged, 'standingsMerged')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingAccountsMergeResponseV1":
        mapping = _expect_mapping(payload, "RatingAccountsMergeResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'standingsMerged')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'standingsMerged')),
            model_name="RatingAccountsMergeResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            standingsMerged=_expect_int(mapping['standingsMerged'], 'standingsMerged'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['standingsMerged'] = self.standingsMerged
        return payload

class RatingPrizeGrantUpdateRequestV1Status(StrEnum):
    DELIVERED = 'delivered'

@dataclass(frozen=True, slots=True)
class RatingPrizeGrantUpdateRequestV1:
    server: str
    ladder: str
    season: int
    place: int
    status: RatingPrizeGrantUpdateRequestV1Status
    actor: ActorRefV1
    playerPid: int | None = None
    note: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'rating.prize.grant.update.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.ladder, 'ladder')
        _expect_int(self.season, 'season')
        _expect_int(self.place, 'place')
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        _expect_instance(self.status, 'status', RatingPrizeGrantUpdateRequestV1Status)
        _expect_instance(self.actor, 'actor', ActorRefV1)
        if self.note is not None:
            _expect_str(self.note, 'note')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingPrizeGrantUpdateRequestV1":
        mapping = _expect_mapping(payload, "RatingPrizeGrantUpdateRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'ladder', 'season', 'place', 'status', 'actor')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'ladder', 'season', 'place', 'playerPid', 'status', 'actor', 'note')),
            model_name="RatingPrizeGrantUpdateRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            ladder=_expect_str(mapping['ladder'], 'ladder'),
            season=_expect_int(mapping['season'], 'season'),
            place=_expect_int(mapping['place'], 'place'),
            playerPid=(_expect_int(mapping['playerPid'], 'playerPid') if 'playerPid' in mapping else None),
            status=_expect_enum(mapping['status'], 'status', RatingPrizeGrantUpdateRequestV1Status),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            note=(_expect_str(mapping['note'], 'note') if 'note' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['ladder'] = self.ladder
        payload['season'] = self.season
        payload['place'] = self.place
        if self.playerPid is not None:
            payload['playerPid'] = self.playerPid
        payload['status'] = str(self.status)
        payload['actor'] = self.actor.to_payload()
        if self.note is not None:
            payload['note'] = self.note
        return payload

@dataclass(frozen=True, slots=True)
class RatingPrizeGrantUpdateResponseV1:
    server: str
    season: int
    place: int
    updated: int

    MESSAGE_TYPE: ClassVar[str] = 'rating.prize.grant.update.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_int(self.season, 'season')
        _expect_int(self.place, 'place')
        _expect_int(self.updated, 'updated')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingPrizeGrantUpdateResponseV1":
        mapping = _expect_mapping(payload, "RatingPrizeGrantUpdateResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'season', 'place', 'updated')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'season', 'place', 'updated')),
            model_name="RatingPrizeGrantUpdateResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            season=_expect_int(mapping['season'], 'season'),
            place=_expect_int(mapping['place'], 'place'),
            updated=_expect_int(mapping['updated'], 'updated'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['season'] = self.season
        payload['place'] = self.place
        payload['updated'] = self.updated
        return payload

@dataclass(frozen=True, slots=True)
class RatingSeasonEndedV1:
    season: SeasonRefV1
    podium: tuple[SeasonPodiumEntryV1, ...]
    summary: SeasonSummaryV1
    server: str
    occurredAt: str

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.ended'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.season, 'season', SeasonRefV1)
        if not isinstance(self.podium, tuple):
            raise TypeError("podium must be a tuple")
        for item in self.podium:
            _expect_instance(item, 'podium[]', SeasonPodiumEntryV1)
        _expect_instance(self.summary, 'summary', SeasonSummaryV1)
        _expect_str(self.server, 'server')
        _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonEndedV1":
        mapping = _expect_mapping(payload, "RatingSeasonEndedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'season', 'podium', 'summary', 'server', 'occurredAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'season', 'podium', 'summary', 'server', 'occurredAt')),
            model_name="RatingSeasonEndedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            season=SeasonRefV1.from_payload(_expect_mapping(mapping['season'], 'season')),
            podium=tuple(SeasonPodiumEntryV1.from_payload(_expect_mapping(item, 'podium[]')) for item in _expect_list(mapping['podium'], 'podium')),
            summary=SeasonSummaryV1.from_payload(_expect_mapping(mapping['summary'], 'summary')),
            server=_expect_str(mapping['server'], 'server'),
            occurredAt=_expect_str(mapping['occurredAt'], 'occurredAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['season'] = self.season.to_payload()
        payload['podium'] = [item.to_payload() for item in self.podium]
        payload['summary'] = self.summary.to_payload()
        payload['server'] = self.server
        payload['occurredAt'] = self.occurredAt
        return payload

@dataclass(frozen=True, slots=True)
class RatingSeasonEndingSoonV1:
    season: SeasonRefV1
    notice: str
    server: str
    occurredAt: str
    prizes: tuple[SeasonPrizeV1, ...] | None = None

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.ending-soon'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.season, 'season', SeasonRefV1)
        _expect_str(self.notice, 'notice')
        _expect_str(self.server, 'server')
        _expect_str(self.occurredAt, 'occurredAt')
        if self.prizes is not None:
            if not isinstance(self.prizes, tuple):
                raise TypeError("prizes must be a tuple")
            for item in self.prizes:
                _expect_instance(item, 'prizes[]', SeasonPrizeV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonEndingSoonV1":
        mapping = _expect_mapping(payload, "RatingSeasonEndingSoonV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'season', 'notice', 'server', 'occurredAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'season', 'notice', 'server', 'occurredAt', 'prizes')),
            model_name="RatingSeasonEndingSoonV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            season=SeasonRefV1.from_payload(_expect_mapping(mapping['season'], 'season')),
            notice=_expect_str(mapping['notice'], 'notice'),
            server=_expect_str(mapping['server'], 'server'),
            occurredAt=_expect_str(mapping['occurredAt'], 'occurredAt'),
            prizes=(tuple(SeasonPrizeV1.from_payload(_expect_mapping(item, 'prizes[]')) for item in _expect_list(mapping['prizes'], 'prizes')) if 'prizes' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['season'] = self.season.to_payload()
        payload['notice'] = self.notice
        payload['server'] = self.server
        payload['occurredAt'] = self.occurredAt
        if self.prizes is not None:
            payload['prizes'] = [item.to_payload() for item in self.prizes]
        return payload

class RatingSeasonPrizesSetRequestV1Operation(StrEnum):
    ADD = 'add'
    REMOVE = 'remove'

@dataclass(frozen=True, slots=True)
class RatingSeasonPrizesSetRequestV1:
    server: str
    ladder: str
    operation: RatingSeasonPrizesSetRequestV1Operation
    actor: ActorRefV1
    prize: SeasonPrizeV1 | None = None
    placeFrom: int | None = None
    placeTo: int | None = None
    requestId: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.prizes.set.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.ladder, 'ladder')
        _expect_instance(self.operation, 'operation', RatingSeasonPrizesSetRequestV1Operation)
        if self.prize is not None:
            _expect_instance(self.prize, 'prize', SeasonPrizeV1)
        if self.placeFrom is not None:
            _expect_int(self.placeFrom, 'placeFrom')
        if self.placeTo is not None:
            _expect_int(self.placeTo, 'placeTo')
        _expect_instance(self.actor, 'actor', ActorRefV1)
        if self.requestId is not None:
            _expect_str(self.requestId, 'requestId')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonPrizesSetRequestV1":
        mapping = _expect_mapping(payload, "RatingSeasonPrizesSetRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'ladder', 'operation', 'actor')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'ladder', 'operation', 'prize', 'placeFrom', 'placeTo', 'actor', 'requestId')),
            model_name="RatingSeasonPrizesSetRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            ladder=_expect_str(mapping['ladder'], 'ladder'),
            operation=_expect_enum(mapping['operation'], 'operation', RatingSeasonPrizesSetRequestV1Operation),
            prize=(SeasonPrizeV1.from_payload(_expect_mapping(mapping['prize'], 'prize')) if 'prize' in mapping else None),
            placeFrom=(_expect_int(mapping['placeFrom'], 'placeFrom') if 'placeFrom' in mapping else None),
            placeTo=(_expect_int(mapping['placeTo'], 'placeTo') if 'placeTo' in mapping else None),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            requestId=(_expect_str(mapping['requestId'], 'requestId') if 'requestId' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['ladder'] = self.ladder
        payload['operation'] = str(self.operation)
        if self.prize is not None:
            payload['prize'] = self.prize.to_payload()
        if self.placeFrom is not None:
            payload['placeFrom'] = self.placeFrom
        if self.placeTo is not None:
            payload['placeTo'] = self.placeTo
        payload['actor'] = self.actor.to_payload()
        if self.requestId is not None:
            payload['requestId'] = self.requestId
        return payload

@dataclass(frozen=True, slots=True)
class RatingSeasonPrizesSetResponseV1:
    server: str
    season: SeasonRefV1
    prizes: tuple[SeasonPrizeV1, ...]

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.prizes.set.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_instance(self.season, 'season', SeasonRefV1)
        if not isinstance(self.prizes, tuple):
            raise TypeError("prizes must be a tuple")
        for item in self.prizes:
            _expect_instance(item, 'prizes[]', SeasonPrizeV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonPrizesSetResponseV1":
        mapping = _expect_mapping(payload, "RatingSeasonPrizesSetResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'season', 'prizes')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'season', 'prizes')),
            model_name="RatingSeasonPrizesSetResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            season=SeasonRefV1.from_payload(_expect_mapping(mapping['season'], 'season')),
            prizes=tuple(SeasonPrizeV1.from_payload(_expect_mapping(item, 'prizes[]')) for item in _expect_list(mapping['prizes'], 'prizes')),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['season'] = self.season.to_payload()
        payload['prizes'] = [item.to_payload() for item in self.prizes]
        return payload

class RatingSeasonRescheduleRequestV1Operation(StrEnum):
    EXTEND = 'extend'
    SET_END = 'set-end'
    END_NOW = 'end-now'

@dataclass(frozen=True, slots=True)
class RatingSeasonRescheduleRequestV1:
    server: str
    ladder: str
    operation: RatingSeasonRescheduleRequestV1Operation
    actor: ActorRefV1
    extendSeconds: int | None = None
    endsAt: str | None = None
    reason: str | None = None
    requestId: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.reschedule.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_str(self.ladder, 'ladder')
        _expect_instance(self.operation, 'operation', RatingSeasonRescheduleRequestV1Operation)
        if self.extendSeconds is not None:
            _expect_int(self.extendSeconds, 'extendSeconds')
        if self.endsAt is not None:
            _expect_str(self.endsAt, 'endsAt')
        _expect_instance(self.actor, 'actor', ActorRefV1)
        if self.reason is not None:
            _expect_str(self.reason, 'reason')
        if self.requestId is not None:
            _expect_str(self.requestId, 'requestId')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonRescheduleRequestV1":
        mapping = _expect_mapping(payload, "RatingSeasonRescheduleRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'ladder', 'operation', 'actor')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'ladder', 'operation', 'extendSeconds', 'endsAt', 'actor', 'reason', 'requestId')),
            model_name="RatingSeasonRescheduleRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            ladder=_expect_str(mapping['ladder'], 'ladder'),
            operation=_expect_enum(mapping['operation'], 'operation', RatingSeasonRescheduleRequestV1Operation),
            extendSeconds=(_expect_int(mapping['extendSeconds'], 'extendSeconds') if 'extendSeconds' in mapping else None),
            endsAt=(_expect_str(mapping['endsAt'], 'endsAt') if 'endsAt' in mapping else None),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            reason=(_expect_str(mapping['reason'], 'reason') if 'reason' in mapping else None),
            requestId=(_expect_str(mapping['requestId'], 'requestId') if 'requestId' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['ladder'] = self.ladder
        payload['operation'] = str(self.operation)
        if self.extendSeconds is not None:
            payload['extendSeconds'] = self.extendSeconds
        if self.endsAt is not None:
            payload['endsAt'] = self.endsAt
        payload['actor'] = self.actor.to_payload()
        if self.reason is not None:
            payload['reason'] = self.reason
        if self.requestId is not None:
            payload['requestId'] = self.requestId
        return payload

@dataclass(frozen=True, slots=True)
class RatingSeasonRescheduleResponseV1:
    server: str
    season: SeasonRefV1
    previousEndsAt: str
    ended: bool

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.reschedule.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        _expect_instance(self.season, 'season', SeasonRefV1)
        _expect_str(self.previousEndsAt, 'previousEndsAt')
        _expect_bool(self.ended, 'ended')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonRescheduleResponseV1":
        mapping = _expect_mapping(payload, "RatingSeasonRescheduleResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'season', 'previousEndsAt', 'ended')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'season', 'previousEndsAt', 'ended')),
            model_name="RatingSeasonRescheduleResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            season=SeasonRefV1.from_payload(_expect_mapping(mapping['season'], 'season')),
            previousEndsAt=_expect_str(mapping['previousEndsAt'], 'previousEndsAt'),
            ended=_expect_bool(mapping['ended'], 'ended'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['season'] = self.season.to_payload()
        payload['previousEndsAt'] = self.previousEndsAt
        payload['ended'] = self.ended
        return payload

@dataclass(frozen=True, slots=True)
class RatingSeasonRescheduledV1:
    season: SeasonRefV1
    previousEndsAt: str
    actor: ActorRefV1
    server: str
    occurredAt: str
    reason: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.rescheduled'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.season, 'season', SeasonRefV1)
        _expect_str(self.previousEndsAt, 'previousEndsAt')
        _expect_instance(self.actor, 'actor', ActorRefV1)
        if self.reason is not None:
            _expect_str(self.reason, 'reason')
        _expect_str(self.server, 'server')
        _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonRescheduledV1":
        mapping = _expect_mapping(payload, "RatingSeasonRescheduledV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'season', 'previousEndsAt', 'actor', 'server', 'occurredAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'season', 'previousEndsAt', 'actor', 'reason', 'server', 'occurredAt')),
            model_name="RatingSeasonRescheduledV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            season=SeasonRefV1.from_payload(_expect_mapping(mapping['season'], 'season')),
            previousEndsAt=_expect_str(mapping['previousEndsAt'], 'previousEndsAt'),
            actor=ActorRefV1.from_payload(_expect_mapping(mapping['actor'], 'actor')),
            reason=(_expect_str(mapping['reason'], 'reason') if 'reason' in mapping else None),
            server=_expect_str(mapping['server'], 'server'),
            occurredAt=_expect_str(mapping['occurredAt'], 'occurredAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['season'] = self.season.to_payload()
        payload['previousEndsAt'] = self.previousEndsAt
        payload['actor'] = self.actor.to_payload()
        if self.reason is not None:
            payload['reason'] = self.reason
        payload['server'] = self.server
        payload['occurredAt'] = self.occurredAt
        return payload

@dataclass(frozen=True, slots=True)
class RatingSeasonStartedV1:
    season: SeasonRefV1
    server: str
    occurredAt: str
    previousSeason: int | None = None

    MESSAGE_TYPE: ClassVar[str] = 'rating.season.started'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_instance(self.season, 'season', SeasonRefV1)
        if self.previousSeason is not None:
            _expect_int(self.previousSeason, 'previousSeason')
        _expect_str(self.server, 'server')
        _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "RatingSeasonStartedV1":
        mapping = _expect_mapping(payload, "RatingSeasonStartedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'season', 'server', 'occurredAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'season', 'previousSeason', 'server', 'occurredAt')),
            model_name="RatingSeasonStartedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            season=SeasonRefV1.from_payload(_expect_mapping(mapping['season'], 'season')),
            previousSeason=(_expect_int(mapping['previousSeason'], 'previousSeason') if 'previousSeason' in mapping else None),
            server=_expect_str(mapping['server'], 'server'),
            occurredAt=_expect_str(mapping['occurredAt'], 'occurredAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['season'] = self.season.to_payload()
        if self.previousSeason is not None:
            payload['previousSeason'] = self.previousSeason
        payload['server'] = self.server
        payload['occurredAt'] = self.occurredAt
        return payload

__all__ = [
    "RatingAccountsMergeRequestV1",
    "RatingAccountsMergeResponseV1",
    "RatingPrizeGrantUpdateRequestV1",
    "RatingPrizeGrantUpdateResponseV1",
    "RatingSeasonEndedV1",
    "RatingSeasonEndingSoonV1",
    "RatingSeasonPrizesSetRequestV1",
    "RatingSeasonPrizesSetResponseV1",
    "RatingSeasonRescheduleRequestV1",
    "RatingSeasonRescheduleResponseV1",
    "RatingSeasonRescheduledV1",
    "RatingSeasonStartedV1",
    "RatingPrizeGrantUpdateRequestV1Status",
    "RatingSeasonPrizesSetRequestV1Operation",
    "RatingSeasonRescheduleRequestV1Operation",
]
