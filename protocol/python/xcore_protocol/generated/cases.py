"""Generated canonical cases protocol models."""

from __future__ import annotations

import re
from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, ClassVar

from .shared import (
    CaseBlockAreaV1,
    CaseBlockEntryV1,
    CaseBlockSummaryV1,
    CaseChatLineV1,
    PlayerRefV1,
    CaseChatLineV1Role,
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
class CasesEvidenceRequestV1:
    server: str
    playerUuid: str
    chatLines: int | None = None
    windowSeconds: int | None = None

    MESSAGE_TYPE: ClassVar[str] = 'cases.evidence.request'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.playerUuid, 'playerUuid')
        if len(self.playerUuid) < 1:
            raise ValueError("playerUuid is too short")
        if self.chatLines is not None:
            _expect_int(self.chatLines, 'chatLines')
            if self.chatLines < 1:
                raise ValueError("chatLines must be >= 1")
        if self.windowSeconds is not None:
            _expect_int(self.windowSeconds, 'windowSeconds')
            if self.windowSeconds < 60:
                raise ValueError("windowSeconds must be >= 60")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CasesEvidenceRequestV1":
        mapping = _expect_mapping(payload, "CasesEvidenceRequestV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'playerUuid')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'playerUuid', 'chatLines', 'windowSeconds')),
            model_name="CasesEvidenceRequestV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            chatLines=(_expect_int(mapping['chatLines'], 'chatLines') if 'chatLines' in mapping else None),
            windowSeconds=(_expect_int(mapping['windowSeconds'], 'windowSeconds') if 'windowSeconds' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['playerUuid'] = self.playerUuid
        if self.chatLines is not None:
            payload['chatLines'] = self.chatLines
        if self.windowSeconds is not None:
            payload['windowSeconds'] = self.windowSeconds
        return payload

@dataclass(frozen=True, slots=True)
class CasesEvidenceResponseV1:
    server: str
    targetOnline: bool
    chat: tuple[CaseChatLineV1, ...]
    capturedAt: str
    mapName: str | None = None
    gameMode: str | None = None
    blockSummary: CaseBlockSummaryV1 | None = None

    MESSAGE_TYPE: ClassVar[str] = 'cases.evidence.response'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_bool(self.targetOnline, 'targetOnline')
        if self.mapName is not None:
            _expect_str(self.mapName, 'mapName')
        if self.gameMode is not None:
            _expect_str(self.gameMode, 'gameMode')
        if not isinstance(self.chat, tuple):
            raise TypeError("chat must be a tuple")
        for item in self.chat:
            _expect_instance(item, 'chat[]', CaseChatLineV1)
        if self.blockSummary is not None:
            _expect_instance(self.blockSummary, 'blockSummary', CaseBlockSummaryV1)
        _expect_str(self.capturedAt, 'capturedAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CasesEvidenceResponseV1":
        mapping = _expect_mapping(payload, "CasesEvidenceResponseV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'targetOnline', 'chat', 'capturedAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'targetOnline', 'mapName', 'gameMode', 'chat', 'blockSummary', 'capturedAt')),
            model_name="CasesEvidenceResponseV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            targetOnline=_expect_bool(mapping['targetOnline'], 'targetOnline'),
            mapName=(_expect_str(mapping['mapName'], 'mapName') if 'mapName' in mapping else None),
            gameMode=(_expect_str(mapping['gameMode'], 'gameMode') if 'gameMode' in mapping else None),
            chat=tuple(CaseChatLineV1.from_payload(_expect_mapping(item, 'chat[]')) for item in _expect_list(mapping['chat'], 'chat')),
            blockSummary=(CaseBlockSummaryV1.from_payload(_expect_mapping(mapping['blockSummary'], 'blockSummary')) if 'blockSummary' in mapping else None),
            capturedAt=_expect_str(mapping['capturedAt'], 'capturedAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['targetOnline'] = self.targetOnline
        if self.mapName is not None:
            payload['mapName'] = self.mapName
        if self.gameMode is not None:
            payload['gameMode'] = self.gameMode
        payload['chat'] = [item.to_payload() for item in self.chat]
        if self.blockSummary is not None:
            payload['blockSummary'] = self.blockSummary.to_payload()
        payload['capturedAt'] = self.capturedAt
        return payload

@dataclass(frozen=True, slots=True)
class CasesNoticeCommandV1:
    server: str
    noticeId: str
    playerUuid: str
    requestedAt: str | None = None

    MESSAGE_TYPE: ClassVar[str] = 'cases.notice.command'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_str(self.noticeId, 'noticeId')
        if len(self.noticeId) < 1:
            raise ValueError("noticeId is too short")
        _expect_str(self.playerUuid, 'playerUuid')
        if len(self.playerUuid) < 1:
            raise ValueError("playerUuid is too short")
        if self.requestedAt is not None:
            _expect_str(self.requestedAt, 'requestedAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CasesNoticeCommandV1":
        mapping = _expect_mapping(payload, "CasesNoticeCommandV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'server', 'noticeId', 'playerUuid')),
            allowed=frozenset(('messageType', 'messageVersion', 'server', 'noticeId', 'playerUuid', 'requestedAt')),
            model_name="CasesNoticeCommandV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            server=_expect_str(mapping['server'], 'server'),
            noticeId=_expect_str(mapping['noticeId'], 'noticeId'),
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            requestedAt=(_expect_str(mapping['requestedAt'], 'requestedAt') if 'requestedAt' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['server'] = self.server
        payload['noticeId'] = self.noticeId
        payload['playerUuid'] = self.playerUuid
        if self.requestedAt is not None:
            payload['requestedAt'] = self.requestedAt
        return payload

@dataclass(frozen=True, slots=True)
class CasesReportSubmittedV1:
    submissionId: str
    server: str
    reporter: PlayerRefV1
    target: PlayerRefV1
    targetOnline: bool
    reason: str
    chat: tuple[CaseChatLineV1, ...]
    occurredAt: str
    comment: str | None = None
    reporterLocale: str | None = None
    mapName: str | None = None
    mapFileName: str | None = None
    gameMode: str | None = None
    sameNetwork: bool | None = None
    blockSummary: CaseBlockSummaryV1 | None = None

    MESSAGE_TYPE: ClassVar[str] = 'cases.report.submitted'
    MESSAGE_VERSION: ClassVar[int] = 1
    def __post_init__(self) -> None:
        _expect_str(self.submissionId, 'submissionId')
        if len(self.submissionId) < 1:
            raise ValueError("submissionId is too short")
        _expect_str(self.server, 'server')
        if len(self.server) < 1:
            raise ValueError("server is too short")
        _expect_instance(self.reporter, 'reporter', PlayerRefV1)
        _expect_instance(self.target, 'target', PlayerRefV1)
        _expect_bool(self.targetOnline, 'targetOnline')
        _expect_str(self.reason, 'reason')
        if re.search('^[a-z][a-z0-9-]{0,31}$', self.reason) is None:
            raise ValueError("reason does not match its pattern")
        if self.comment is not None:
            _expect_str(self.comment, 'comment')
        if self.reporterLocale is not None:
            _expect_str(self.reporterLocale, 'reporterLocale')
        if self.mapName is not None:
            _expect_str(self.mapName, 'mapName')
        if self.mapFileName is not None:
            _expect_str(self.mapFileName, 'mapFileName')
        if self.gameMode is not None:
            _expect_str(self.gameMode, 'gameMode')
        if self.sameNetwork is not None:
            _expect_bool(self.sameNetwork, 'sameNetwork')
        if not isinstance(self.chat, tuple):
            raise TypeError("chat must be a tuple")
        for item in self.chat:
            _expect_instance(item, 'chat[]', CaseChatLineV1)
        if self.blockSummary is not None:
            _expect_instance(self.blockSummary, 'blockSummary', CaseBlockSummaryV1)
        _expect_str(self.occurredAt, 'occurredAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CasesReportSubmittedV1":
        mapping = _expect_mapping(payload, "CasesReportSubmittedV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('messageType', 'messageVersion', 'submissionId', 'server', 'reporter', 'target', 'targetOnline', 'reason', 'chat', 'occurredAt')),
            allowed=frozenset(('messageType', 'messageVersion', 'submissionId', 'server', 'reporter', 'target', 'targetOnline', 'reason', 'comment', 'reporterLocale', 'mapName', 'mapFileName', 'gameMode', 'sameNetwork', 'chat', 'blockSummary', 'occurredAt')),
            model_name="CasesReportSubmittedV1",
        )
        if mapping['messageType'] != cls.MESSAGE_TYPE:
            raise ValueError('messageType' + " must equal " + repr(cls.MESSAGE_TYPE))
        if mapping['messageVersion'] != cls.MESSAGE_VERSION:
            raise ValueError('messageVersion' + " must equal " + repr(cls.MESSAGE_VERSION))
        return cls(
            submissionId=_expect_str(mapping['submissionId'], 'submissionId'),
            server=_expect_str(mapping['server'], 'server'),
            reporter=PlayerRefV1.from_payload(_expect_mapping(mapping['reporter'], 'reporter')),
            target=PlayerRefV1.from_payload(_expect_mapping(mapping['target'], 'target')),
            targetOnline=_expect_bool(mapping['targetOnline'], 'targetOnline'),
            reason=_expect_str(mapping['reason'], 'reason'),
            comment=(_expect_str(mapping['comment'], 'comment') if 'comment' in mapping else None),
            reporterLocale=(_expect_str(mapping['reporterLocale'], 'reporterLocale') if 'reporterLocale' in mapping else None),
            mapName=(_expect_str(mapping['mapName'], 'mapName') if 'mapName' in mapping else None),
            mapFileName=(_expect_str(mapping['mapFileName'], 'mapFileName') if 'mapFileName' in mapping else None),
            gameMode=(_expect_str(mapping['gameMode'], 'gameMode') if 'gameMode' in mapping else None),
            sameNetwork=(_expect_bool(mapping['sameNetwork'], 'sameNetwork') if 'sameNetwork' in mapping else None),
            chat=tuple(CaseChatLineV1.from_payload(_expect_mapping(item, 'chat[]')) for item in _expect_list(mapping['chat'], 'chat')),
            blockSummary=(CaseBlockSummaryV1.from_payload(_expect_mapping(mapping['blockSummary'], 'blockSummary')) if 'blockSummary' in mapping else None),
            occurredAt=_expect_str(mapping['occurredAt'], 'occurredAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            'messageType': self.MESSAGE_TYPE,
            'messageVersion': self.MESSAGE_VERSION,
        }
        payload['submissionId'] = self.submissionId
        payload['server'] = self.server
        payload['reporter'] = self.reporter.to_payload()
        payload['target'] = self.target.to_payload()
        payload['targetOnline'] = self.targetOnline
        payload['reason'] = self.reason
        if self.comment is not None:
            payload['comment'] = self.comment
        if self.reporterLocale is not None:
            payload['reporterLocale'] = self.reporterLocale
        if self.mapName is not None:
            payload['mapName'] = self.mapName
        if self.mapFileName is not None:
            payload['mapFileName'] = self.mapFileName
        if self.gameMode is not None:
            payload['gameMode'] = self.gameMode
        if self.sameNetwork is not None:
            payload['sameNetwork'] = self.sameNetwork
        payload['chat'] = [item.to_payload() for item in self.chat]
        if self.blockSummary is not None:
            payload['blockSummary'] = self.blockSummary.to_payload()
        payload['occurredAt'] = self.occurredAt
        return payload

__all__ = [
    "CasesEvidenceRequestV1",
    "CasesEvidenceResponseV1",
    "CasesNoticeCommandV1",
    "CasesReportSubmittedV1",
]
