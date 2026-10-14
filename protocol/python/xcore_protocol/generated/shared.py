"""Generated canonical shared protocol models."""

from __future__ import annotations

import re
from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

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

class ActorRefV1ActorType(StrEnum):
    PLAYER = 'player'
    DISCORD = 'discord'
    SERVER = 'server'
    SYSTEM = 'system'
    UNKNOWN = 'unknown'

@dataclass(frozen=True, slots=True)
class ActorRefV1:
    actorName: str
    actorDiscordId: str | None = None
    actorType: ActorRefV1ActorType | None = None

    def __post_init__(self) -> None:
        _expect_str(self.actorName, 'actorName')
        if len(self.actorName) < 1:
            raise ValueError("actorName is too short")
        if self.actorDiscordId is not None:
            _expect_str(self.actorDiscordId, 'actorDiscordId')
            if len(self.actorDiscordId) < 1:
                raise ValueError("actorDiscordId is too short")
        if self.actorType is not None:
            _expect_instance(self.actorType, 'actorType', ActorRefV1ActorType)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ActorRefV1":
        mapping = _expect_mapping(payload, "ActorRefV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('actorName',)),
            allowed=frozenset(('actorName', 'actorDiscordId', 'actorType')),
            model_name="ActorRefV1",
        )
        return cls(
            actorName=_expect_str(mapping['actorName'], 'actorName'),
            actorDiscordId=(_expect_str(mapping['actorDiscordId'], 'actorDiscordId') if 'actorDiscordId' in mapping else None),
            actorType=(_expect_enum(mapping['actorType'], 'actorType', ActorRefV1ActorType) if 'actorType' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['actorName'] = self.actorName
        if self.actorDiscordId is not None:
            payload['actorDiscordId'] = self.actorDiscordId
        if self.actorType is not None:
            payload['actorType'] = str(self.actorType)
        return payload

@dataclass(frozen=True, slots=True)
class CaseBlockAreaV1:
    x1: int
    y1: int
    x2: int
    y2: int

    def __post_init__(self) -> None:
        _expect_int(self.x1, 'x1')
        _expect_int(self.y1, 'y1')
        _expect_int(self.x2, 'x2')
        _expect_int(self.y2, 'y2')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CaseBlockAreaV1":
        mapping = _expect_mapping(payload, "CaseBlockAreaV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('x1', 'y1', 'x2', 'y2')),
            allowed=frozenset(('x1', 'y1', 'x2', 'y2')),
            model_name="CaseBlockAreaV1",
        )
        return cls(
            x1=_expect_int(mapping['x1'], 'x1'),
            y1=_expect_int(mapping['y1'], 'y1'),
            x2=_expect_int(mapping['x2'], 'x2'),
            y2=_expect_int(mapping['y2'], 'y2'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['x1'] = self.x1
        payload['y1'] = self.y1
        payload['x2'] = self.x2
        payload['y2'] = self.y2
        return payload

@dataclass(frozen=True, slots=True)
class CaseBlockEntryV1:
    block: str
    built: int
    destroyed: int

    def __post_init__(self) -> None:
        _expect_str(self.block, 'block')
        _expect_int(self.built, 'built')
        _expect_int(self.destroyed, 'destroyed')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CaseBlockEntryV1":
        mapping = _expect_mapping(payload, "CaseBlockEntryV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('block', 'built', 'destroyed')),
            allowed=frozenset(('block', 'built', 'destroyed')),
            model_name="CaseBlockEntryV1",
        )
        return cls(
            block=_expect_str(mapping['block'], 'block'),
            built=_expect_int(mapping['built'], 'built'),
            destroyed=_expect_int(mapping['destroyed'], 'destroyed'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['block'] = self.block
        payload['built'] = self.built
        payload['destroyed'] = self.destroyed
        return payload

@dataclass(frozen=True, slots=True)
class CaseBlockSummaryV1:
    windowSeconds: int
    built: int
    destroyed: int
    configured: int
    truncated: bool
    topBlocks: tuple[CaseBlockEntryV1, ...]
    area: CaseBlockAreaV1 | None = None
    lastActionSecondsAgo: int | None = None

    def __post_init__(self) -> None:
        _expect_int(self.windowSeconds, 'windowSeconds')
        if self.windowSeconds < 1:
            raise ValueError("windowSeconds must be >= 1")
        _expect_int(self.built, 'built')
        if self.built < 0:
            raise ValueError("built must be >= 0")
        _expect_int(self.destroyed, 'destroyed')
        if self.destroyed < 0:
            raise ValueError("destroyed must be >= 0")
        _expect_int(self.configured, 'configured')
        if self.configured < 0:
            raise ValueError("configured must be >= 0")
        _expect_bool(self.truncated, 'truncated')
        if not isinstance(self.topBlocks, tuple):
            raise TypeError("topBlocks must be a tuple")
        for item in self.topBlocks:
            _expect_instance(item, 'topBlocks[]', CaseBlockEntryV1)
        if self.area is not None:
            _expect_instance(self.area, 'area', CaseBlockAreaV1)
        if self.lastActionSecondsAgo is not None:
            _expect_int(self.lastActionSecondsAgo, 'lastActionSecondsAgo')
            if self.lastActionSecondsAgo < 0:
                raise ValueError("lastActionSecondsAgo must be >= 0")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CaseBlockSummaryV1":
        mapping = _expect_mapping(payload, "CaseBlockSummaryV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('windowSeconds', 'built', 'destroyed', 'configured', 'truncated', 'topBlocks')),
            allowed=frozenset(('windowSeconds', 'built', 'destroyed', 'configured', 'truncated', 'topBlocks', 'area', 'lastActionSecondsAgo')),
            model_name="CaseBlockSummaryV1",
        )
        return cls(
            windowSeconds=_expect_int(mapping['windowSeconds'], 'windowSeconds'),
            built=_expect_int(mapping['built'], 'built'),
            destroyed=_expect_int(mapping['destroyed'], 'destroyed'),
            configured=_expect_int(mapping['configured'], 'configured'),
            truncated=_expect_bool(mapping['truncated'], 'truncated'),
            topBlocks=tuple(CaseBlockEntryV1.from_payload(_expect_mapping(item, 'topBlocks[]')) for item in _expect_list(mapping['topBlocks'], 'topBlocks')),
            area=(CaseBlockAreaV1.from_payload(_expect_mapping(mapping['area'], 'area')) if 'area' in mapping else None),
            lastActionSecondsAgo=(_expect_int(mapping['lastActionSecondsAgo'], 'lastActionSecondsAgo') if 'lastActionSecondsAgo' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['windowSeconds'] = self.windowSeconds
        payload['built'] = self.built
        payload['destroyed'] = self.destroyed
        payload['configured'] = self.configured
        payload['truncated'] = self.truncated
        payload['topBlocks'] = [item.to_payload() for item in self.topBlocks]
        if self.area is not None:
            payload['area'] = self.area.to_payload()
        if self.lastActionSecondsAgo is not None:
            payload['lastActionSecondsAgo'] = self.lastActionSecondsAgo
        return payload

class CaseChatLineV1Role(StrEnum):
    TARGET = 'target'
    REPORTER = 'reporter'
    OTHER = 'other'

@dataclass(frozen=True, slots=True)
class CaseChatLineV1:
    sentAt: int
    playerName: str
    role: CaseChatLineV1Role
    text: str
    playerPid: int | None = None
    team: bool | None = None

    def __post_init__(self) -> None:
        _expect_int(self.sentAt, 'sentAt')
        _expect_str(self.playerName, 'playerName')
        if len(self.playerName) < 1:
            raise ValueError("playerName is too short")
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        _expect_instance(self.role, 'role', CaseChatLineV1Role)
        _expect_str(self.text, 'text')
        if self.team is not None:
            _expect_bool(self.team, 'team')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "CaseChatLineV1":
        mapping = _expect_mapping(payload, "CaseChatLineV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('sentAt', 'playerName', 'role', 'text')),
            allowed=frozenset(('sentAt', 'playerName', 'playerPid', 'role', 'text', 'team')),
            model_name="CaseChatLineV1",
        )
        return cls(
            sentAt=_expect_int(mapping['sentAt'], 'sentAt'),
            playerName=_expect_str(mapping['playerName'], 'playerName'),
            playerPid=(_expect_int(mapping['playerPid'], 'playerPid') if 'playerPid' in mapping else None),
            role=_expect_enum(mapping['role'], 'role', CaseChatLineV1Role),
            text=_expect_str(mapping['text'], 'text'),
            team=(_expect_bool(mapping['team'], 'team') if 'team' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['sentAt'] = self.sentAt
        payload['playerName'] = self.playerName
        if self.playerPid is not None:
            payload['playerPid'] = self.playerPid
        payload['role'] = str(self.role)
        payload['text'] = self.text
        if self.team is not None:
            payload['team'] = self.team
        return payload

@dataclass(frozen=True, slots=True)
class DiscordIdentityRefV1:
    discordId: str
    discordUsername: str | None = None

    def __post_init__(self) -> None:
        _expect_str(self.discordId, 'discordId')
        if len(self.discordId) < 1:
            raise ValueError("discordId is too short")
        if self.discordUsername is not None:
            _expect_str(self.discordUsername, 'discordUsername')
            if len(self.discordUsername) < 1:
                raise ValueError("discordUsername is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "DiscordIdentityRefV1":
        mapping = _expect_mapping(payload, "DiscordIdentityRefV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('discordId',)),
            allowed=frozenset(('discordId', 'discordUsername')),
            model_name="DiscordIdentityRefV1",
        )
        return cls(
            discordId=_expect_str(mapping['discordId'], 'discordId'),
            discordUsername=(_expect_str(mapping['discordUsername'], 'discordUsername') if 'discordUsername' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['discordId'] = self.discordId
        if self.discordUsername is not None:
            payload['discordUsername'] = self.discordUsername
        return payload

@dataclass(frozen=True, slots=True)
class ExpirationInfoV1:
    expiresAt: str | None = None
    permanent: bool | None = None

    def __post_init__(self) -> None:
        if self.expiresAt is not None:
            _expect_str(self.expiresAt, 'expiresAt')
        if self.permanent is not None:
            _expect_bool(self.permanent, 'permanent')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ExpirationInfoV1":
        mapping = _expect_mapping(payload, "ExpirationInfoV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(()),
            allowed=frozenset(('expiresAt', 'permanent')),
            model_name="ExpirationInfoV1",
        )
        return cls(
            expiresAt=(_expect_str(mapping['expiresAt'], 'expiresAt') if 'expiresAt' in mapping else None),
            permanent=(_expect_bool(mapping['permanent'], 'permanent') if 'permanent' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.expiresAt is not None:
            payload['expiresAt'] = self.expiresAt
        if self.permanent is not None:
            payload['permanent'] = self.permanent
        return payload

@dataclass(frozen=True, slots=True)
class MapDiscordUploadV1:
    uploadedBy: DiscordIdentityRefV1
    uploadedAt: int
    creator: DiscordIdentityRefV1 | None = None
    uploadedByHandle: str | None = None
    creatorHandle: str | None = None

    def __post_init__(self) -> None:
        _expect_instance(self.uploadedBy, 'uploadedBy', DiscordIdentityRefV1)
        _expect_int(self.uploadedAt, 'uploadedAt')
        if self.uploadedAt < 1:
            raise ValueError("uploadedAt must be >= 1")
        if self.creator is not None:
            _expect_instance(self.creator, 'creator', DiscordIdentityRefV1)
        if self.uploadedByHandle is not None:
            _expect_str(self.uploadedByHandle, 'uploadedByHandle')
            if len(self.uploadedByHandle) < 1:
                raise ValueError("uploadedByHandle is too short")
        if self.creatorHandle is not None:
            _expect_str(self.creatorHandle, 'creatorHandle')
            if len(self.creatorHandle) < 1:
                raise ValueError("creatorHandle is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapDiscordUploadV1":
        mapping = _expect_mapping(payload, "MapDiscordUploadV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('uploadedBy', 'uploadedAt')),
            allowed=frozenset(('uploadedBy', 'uploadedAt', 'creator', 'uploadedByHandle', 'creatorHandle')),
            model_name="MapDiscordUploadV1",
        )
        return cls(
            uploadedBy=DiscordIdentityRefV1.from_payload(_expect_mapping(mapping['uploadedBy'], 'uploadedBy')),
            uploadedAt=_expect_int(mapping['uploadedAt'], 'uploadedAt'),
            creator=(DiscordIdentityRefV1.from_payload(_expect_mapping(mapping['creator'], 'creator')) if 'creator' in mapping else None),
            uploadedByHandle=(_expect_str(mapping['uploadedByHandle'], 'uploadedByHandle') if 'uploadedByHandle' in mapping else None),
            creatorHandle=(_expect_str(mapping['creatorHandle'], 'creatorHandle') if 'creatorHandle' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['uploadedBy'] = self.uploadedBy.to_payload()
        payload['uploadedAt'] = self.uploadedAt
        if self.creator is not None:
            payload['creator'] = self.creator.to_payload()
        if self.uploadedByHandle is not None:
            payload['uploadedByHandle'] = self.uploadedByHandle
        if self.creatorHandle is not None:
            payload['creatorHandle'] = self.creatorHandle
        return payload

@dataclass(frozen=True, slots=True)
class MapEntryV1:
    name: str
    fileName: str
    author: str
    width: int | None = None
    height: int | None = None
    fileSizeBytes: int | None = None
    like: int | None = None
    dislike: int | None = None
    reputation: int | None = None
    popularity: float | None = None
    interest: float | None = None
    gameMode: str | None = None
    discordUpload: MapDiscordUploadV1 | None = None

    def __post_init__(self) -> None:
        _expect_str(self.name, 'name')
        if len(self.name) < 1:
            raise ValueError("name is too short")
        _expect_str(self.fileName, 'fileName')
        if len(self.fileName) < 1:
            raise ValueError("fileName is too short")
        _expect_str(self.author, 'author')
        if len(self.author) < 1:
            raise ValueError("author is too short")
        if self.width is not None:
            _expect_int(self.width, 'width')
            if self.width < 0:
                raise ValueError("width must be >= 0")
        if self.height is not None:
            _expect_int(self.height, 'height')
            if self.height < 0:
                raise ValueError("height must be >= 0")
        if self.fileSizeBytes is not None:
            _expect_int(self.fileSizeBytes, 'fileSizeBytes')
            if self.fileSizeBytes < 0:
                raise ValueError("fileSizeBytes must be >= 0")
        if self.like is not None:
            _expect_int(self.like, 'like')
        if self.dislike is not None:
            _expect_int(self.dislike, 'dislike')
        if self.reputation is not None:
            _expect_int(self.reputation, 'reputation')
        if self.popularity is not None:
            _expect_number(self.popularity, 'popularity')
        if self.interest is not None:
            _expect_number(self.interest, 'interest')
        if self.gameMode is not None:
            _expect_str(self.gameMode, 'gameMode')
            if len(self.gameMode) < 1:
                raise ValueError("gameMode is too short")
        if self.discordUpload is not None:
            _expect_instance(self.discordUpload, 'discordUpload', MapDiscordUploadV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapEntryV1":
        mapping = _expect_mapping(payload, "MapEntryV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('name', 'fileName', 'author')),
            allowed=frozenset(('name', 'fileName', 'author', 'width', 'height', 'fileSizeBytes', 'like', 'dislike', 'reputation', 'popularity', 'interest', 'gameMode', 'discordUpload')),
            model_name="MapEntryV1",
        )
        return cls(
            name=_expect_str(mapping['name'], 'name'),
            fileName=_expect_str(mapping['fileName'], 'fileName'),
            author=_expect_str(mapping['author'], 'author'),
            width=(_expect_int(mapping['width'], 'width') if 'width' in mapping else None),
            height=(_expect_int(mapping['height'], 'height') if 'height' in mapping else None),
            fileSizeBytes=(_expect_int(mapping['fileSizeBytes'], 'fileSizeBytes') if 'fileSizeBytes' in mapping else None),
            like=(_expect_int(mapping['like'], 'like') if 'like' in mapping else None),
            dislike=(_expect_int(mapping['dislike'], 'dislike') if 'dislike' in mapping else None),
            reputation=(_expect_int(mapping['reputation'], 'reputation') if 'reputation' in mapping else None),
            popularity=(_expect_number(mapping['popularity'], 'popularity') if 'popularity' in mapping else None),
            interest=(_expect_number(mapping['interest'], 'interest') if 'interest' in mapping else None),
            gameMode=(_expect_str(mapping['gameMode'], 'gameMode') if 'gameMode' in mapping else None),
            discordUpload=(MapDiscordUploadV1.from_payload(_expect_mapping(mapping['discordUpload'], 'discordUpload')) if 'discordUpload' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['name'] = self.name
        payload['fileName'] = self.fileName
        payload['author'] = self.author
        if self.width is not None:
            payload['width'] = self.width
        if self.height is not None:
            payload['height'] = self.height
        if self.fileSizeBytes is not None:
            payload['fileSizeBytes'] = self.fileSizeBytes
        if self.like is not None:
            payload['like'] = self.like
        if self.dislike is not None:
            payload['dislike'] = self.dislike
        if self.reputation is not None:
            payload['reputation'] = self.reputation
        if self.popularity is not None:
            payload['popularity'] = float(self.popularity)
        if self.interest is not None:
            payload['interest'] = float(self.interest)
        if self.gameMode is not None:
            payload['gameMode'] = self.gameMode
        if self.discordUpload is not None:
            payload['discordUpload'] = self.discordUpload.to_payload()
        return payload

@dataclass(frozen=True, slots=True)
class MapFileSourceV1:
    url: str
    fileName: str
    discordUpload: MapDiscordUploadV1 | None = None

    def __post_init__(self) -> None:
        _expect_str(self.url, 'url')
        _expect_str(self.fileName, 'fileName')
        if re.search('^[^/\\\\]+\\.[mM][sS][aA][vV]$', self.fileName) is None:
            raise ValueError("fileName does not match its pattern")
        if self.discordUpload is not None:
            _expect_instance(self.discordUpload, 'discordUpload', MapDiscordUploadV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapFileSourceV1":
        mapping = _expect_mapping(payload, "MapFileSourceV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('url', 'fileName')),
            allowed=frozenset(('url', 'fileName', 'discordUpload')),
            model_name="MapFileSourceV1",
        )
        return cls(
            url=_expect_str(mapping['url'], 'url'),
            fileName=_expect_str(mapping['fileName'], 'fileName'),
            discordUpload=(MapDiscordUploadV1.from_payload(_expect_mapping(mapping['discordUpload'], 'discordUpload')) if 'discordUpload' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['url'] = self.url
        payload['fileName'] = self.fileName
        if self.discordUpload is not None:
            payload['discordUpload'] = self.discordUpload.to_payload()
        return payload

class MapInspectFindingV1Severity(StrEnum):
    ERROR = 'error'
    WARNING = 'warning'
    INFO = 'info'

@dataclass(frozen=True, slots=True)
class MapInspectFindingV1:
    code: str
    severity: MapInspectFindingV1Severity
    detail: str | None = None
    x: int | None = None
    y: int | None = None
    count: int | None = None

    def __post_init__(self) -> None:
        _expect_str(self.code, 'code')
        if re.search('^[a-z0-9]+(-[a-z0-9]+)*$', self.code) is None:
            raise ValueError("code does not match its pattern")
        _expect_instance(self.severity, 'severity', MapInspectFindingV1Severity)
        if self.detail is not None:
            _expect_str(self.detail, 'detail')
        if self.x is not None:
            _expect_int(self.x, 'x')
        if self.y is not None:
            _expect_int(self.y, 'y')
        if self.count is not None:
            _expect_int(self.count, 'count')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapInspectFindingV1":
        mapping = _expect_mapping(payload, "MapInspectFindingV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('code', 'severity')),
            allowed=frozenset(('code', 'severity', 'detail', 'x', 'y', 'count')),
            model_name="MapInspectFindingV1",
        )
        return cls(
            code=_expect_str(mapping['code'], 'code'),
            severity=_expect_enum(mapping['severity'], 'severity', MapInspectFindingV1Severity),
            detail=(_expect_str(mapping['detail'], 'detail') if 'detail' in mapping else None),
            x=(_expect_int(mapping['x'], 'x') if 'x' in mapping else None),
            y=(_expect_int(mapping['y'], 'y') if 'y' in mapping else None),
            count=(_expect_int(mapping['count'], 'count') if 'count' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['code'] = self.code
        payload['severity'] = str(self.severity)
        if self.detail is not None:
            payload['detail'] = self.detail
        if self.x is not None:
            payload['x'] = self.x
        if self.y is not None:
            payload['y'] = self.y
        if self.count is not None:
            payload['count'] = self.count
        return payload

@dataclass(frozen=True, slots=True)
class MapInspectInfoV1:
    name: str
    width: int
    height: int
    sha256: str
    sizeBytes: int
    author: str | None = None
    description: str | None = None
    saveVersion: int | None = None
    build: int | None = None
    gameMode: str | None = None
    profile: str | None = None
    spawnCount: int | None = None
    coresByTeam: dict[str, Any] | None = None
    waves: bool | None = None
    waveTimer: bool | None = None
    spawnGroupCount: int | None = None
    dropZoneRadius: float | None = None
    mods: tuple[str, ...] | None = None

    def __post_init__(self) -> None:
        _expect_str(self.name, 'name')
        if self.author is not None:
            _expect_str(self.author, 'author')
        if self.description is not None:
            _expect_str(self.description, 'description')
        _expect_int(self.width, 'width')
        _expect_int(self.height, 'height')
        if self.saveVersion is not None:
            _expect_int(self.saveVersion, 'saveVersion')
        if self.build is not None:
            _expect_int(self.build, 'build')
        if self.gameMode is not None:
            _expect_str(self.gameMode, 'gameMode')
        if self.profile is not None:
            _expect_str(self.profile, 'profile')
        if self.spawnCount is not None:
            _expect_int(self.spawnCount, 'spawnCount')
        if self.coresByTeam is not None:
            _expect_json_object(self.coresByTeam, 'coresByTeam', allowed_types=('integer',), allow_null=False)
        if self.waves is not None:
            _expect_bool(self.waves, 'waves')
        if self.waveTimer is not None:
            _expect_bool(self.waveTimer, 'waveTimer')
        if self.spawnGroupCount is not None:
            _expect_int(self.spawnGroupCount, 'spawnGroupCount')
        if self.dropZoneRadius is not None:
            _expect_number(self.dropZoneRadius, 'dropZoneRadius')
        if self.mods is not None:
            if not isinstance(self.mods, tuple):
                raise TypeError("mods must be a tuple")
            for item in self.mods:
                _expect_str(item, 'mods[]')
        _expect_str(self.sha256, 'sha256')
        if re.search('^[a-fA-F0-9]{64}$', self.sha256) is None:
            raise ValueError("sha256 does not match its pattern")
        _expect_int(self.sizeBytes, 'sizeBytes')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapInspectInfoV1":
        mapping = _expect_mapping(payload, "MapInspectInfoV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('name', 'width', 'height', 'sha256', 'sizeBytes')),
            allowed=frozenset(('name', 'author', 'description', 'width', 'height', 'saveVersion', 'build', 'gameMode', 'profile', 'spawnCount', 'coresByTeam', 'waves', 'waveTimer', 'spawnGroupCount', 'dropZoneRadius', 'mods', 'sha256', 'sizeBytes')),
            model_name="MapInspectInfoV1",
        )
        return cls(
            name=_expect_str(mapping['name'], 'name'),
            author=(_expect_str(mapping['author'], 'author') if 'author' in mapping else None),
            description=(_expect_str(mapping['description'], 'description') if 'description' in mapping else None),
            width=_expect_int(mapping['width'], 'width'),
            height=_expect_int(mapping['height'], 'height'),
            saveVersion=(_expect_int(mapping['saveVersion'], 'saveVersion') if 'saveVersion' in mapping else None),
            build=(_expect_int(mapping['build'], 'build') if 'build' in mapping else None),
            gameMode=(_expect_str(mapping['gameMode'], 'gameMode') if 'gameMode' in mapping else None),
            profile=(_expect_str(mapping['profile'], 'profile') if 'profile' in mapping else None),
            spawnCount=(_expect_int(mapping['spawnCount'], 'spawnCount') if 'spawnCount' in mapping else None),
            coresByTeam=(_expect_json_object(mapping['coresByTeam'], 'coresByTeam', allowed_types=('integer',), allow_null=False) if 'coresByTeam' in mapping else None),
            waves=(_expect_bool(mapping['waves'], 'waves') if 'waves' in mapping else None),
            waveTimer=(_expect_bool(mapping['waveTimer'], 'waveTimer') if 'waveTimer' in mapping else None),
            spawnGroupCount=(_expect_int(mapping['spawnGroupCount'], 'spawnGroupCount') if 'spawnGroupCount' in mapping else None),
            dropZoneRadius=(_expect_number(mapping['dropZoneRadius'], 'dropZoneRadius') if 'dropZoneRadius' in mapping else None),
            mods=(tuple(_expect_str(item, 'mods[]') for item in _expect_list(mapping['mods'], 'mods')) if 'mods' in mapping else None),
            sha256=_expect_str(mapping['sha256'], 'sha256'),
            sizeBytes=_expect_int(mapping['sizeBytes'], 'sizeBytes'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['name'] = self.name
        if self.author is not None:
            payload['author'] = self.author
        if self.description is not None:
            payload['description'] = self.description
        payload['width'] = self.width
        payload['height'] = self.height
        if self.saveVersion is not None:
            payload['saveVersion'] = self.saveVersion
        if self.build is not None:
            payload['build'] = self.build
        if self.gameMode is not None:
            payload['gameMode'] = self.gameMode
        if self.profile is not None:
            payload['profile'] = self.profile
        if self.spawnCount is not None:
            payload['spawnCount'] = self.spawnCount
        if self.coresByTeam is not None:
            payload['coresByTeam'] = dict(self.coresByTeam)
        if self.waves is not None:
            payload['waves'] = self.waves
        if self.waveTimer is not None:
            payload['waveTimer'] = self.waveTimer
        if self.spawnGroupCount is not None:
            payload['spawnGroupCount'] = self.spawnGroupCount
        if self.dropZoneRadius is not None:
            payload['dropZoneRadius'] = float(self.dropZoneRadius)
        if self.mods is not None:
            payload['mods'] = [item for item in self.mods]
        payload['sha256'] = self.sha256
        payload['sizeBytes'] = self.sizeBytes
        return payload

class MapUploadResultV1Status(StrEnum):
    INSTALLED = 'installed'
    DOWNLOAD_FAILED = 'download_failed'
    INVALID_MAP = 'invalid_map'
    SUPERSEDED = 'superseded'
    METADATA_FAILED = 'metadata_failed'
    REJECTED = 'rejected'

@dataclass(frozen=True, slots=True)
class MapUploadResultV1:
    fileName: str
    status: MapUploadResultV1Status
    detail: str | None = None

    def __post_init__(self) -> None:
        _expect_str(self.fileName, 'fileName')
        if len(self.fileName) < 1:
            raise ValueError("fileName is too short")
        _expect_instance(self.status, 'status', MapUploadResultV1Status)
        if len(self.status) < 1:
            raise ValueError("status is too short")
        if self.detail is not None:
            _expect_str(self.detail, 'detail')
            if len(self.detail) < 1:
                raise ValueError("detail is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapUploadResultV1":
        mapping = _expect_mapping(payload, "MapUploadResultV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('fileName', 'status')),
            allowed=frozenset(('fileName', 'status', 'detail')),
            model_name="MapUploadResultV1",
        )
        return cls(
            fileName=_expect_str(mapping['fileName'], 'fileName'),
            status=_expect_enum(mapping['status'], 'status', MapUploadResultV1Status),
            detail=(_expect_str(mapping['detail'], 'detail') if 'detail' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['fileName'] = self.fileName
        payload['status'] = str(self.status)
        if self.detail is not None:
            payload['detail'] = self.detail
        return payload

class MetricSampleV1Type(StrEnum):
    COUNTER = 'counter'
    GAUGE = 'gauge'
    HISTOGRAM = 'histogram'
    INFO = 'info'

@dataclass(frozen=True, slots=True)
class MetricSampleV1:
    name: str
    type: MetricSampleV1Type
    labels: dict[str, Any]
    help: str | None = None
    unit: str | None = None
    value: float | None = None
    buckets: tuple[float, ...] | None = None
    counts: tuple[int, ...] | None = None
    count: int | None = None
    sum: float | None = None

    def __post_init__(self) -> None:
        _expect_str(self.name, 'name')
        if re.search('^[a-zA-Z_:][a-zA-Z0-9_:]*$', self.name) is None:
            raise ValueError("name does not match its pattern")
        _expect_instance(self.type, 'type', MetricSampleV1Type)
        _expect_json_object(self.labels, 'labels', allowed_types=('string',), allow_null=False)
        if self.help is not None:
            _expect_str(self.help, 'help')
            if len(self.help) < 1:
                raise ValueError("help is too short")
        if self.unit is not None:
            _expect_str(self.unit, 'unit')
            if len(self.unit) < 1:
                raise ValueError("unit is too short")
        if self.value is not None:
            _expect_number(self.value, 'value')
        if self.buckets is not None:
            if not isinstance(self.buckets, tuple):
                raise TypeError("buckets must be a tuple")
            for item in self.buckets:
                _expect_number(item, 'buckets[]')
        if self.counts is not None:
            if not isinstance(self.counts, tuple):
                raise TypeError("counts must be a tuple")
            for item in self.counts:
                _expect_int(item, 'counts[]')
        if self.count is not None:
            _expect_int(self.count, 'count')
            if self.count < 0:
                raise ValueError("count must be >= 0")
        if self.sum is not None:
            _expect_number(self.sum, 'sum')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MetricSampleV1":
        mapping = _expect_mapping(payload, "MetricSampleV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('name', 'type', 'labels')),
            allowed=frozenset(('name', 'type', 'labels', 'help', 'unit', 'value', 'buckets', 'counts', 'count', 'sum')),
            model_name="MetricSampleV1",
        )
        return cls(
            name=_expect_str(mapping['name'], 'name'),
            type=_expect_enum(mapping['type'], 'type', MetricSampleV1Type),
            labels=_expect_json_object(mapping['labels'], 'labels', allowed_types=('string',), allow_null=False),
            help=(_expect_str(mapping['help'], 'help') if 'help' in mapping else None),
            unit=(_expect_str(mapping['unit'], 'unit') if 'unit' in mapping else None),
            value=(_expect_number(mapping['value'], 'value') if 'value' in mapping else None),
            buckets=(tuple(_expect_number(item, 'buckets[]') for item in _expect_list(mapping['buckets'], 'buckets')) if 'buckets' in mapping else None),
            counts=(tuple(_expect_int(item, 'counts[]') for item in _expect_list(mapping['counts'], 'counts')) if 'counts' in mapping else None),
            count=(_expect_int(mapping['count'], 'count') if 'count' in mapping else None),
            sum=(_expect_number(mapping['sum'], 'sum') if 'sum' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['name'] = self.name
        payload['type'] = str(self.type)
        payload['labels'] = dict(self.labels)
        if self.help is not None:
            payload['help'] = self.help
        if self.unit is not None:
            payload['unit'] = self.unit
        if self.value is not None:
            payload['value'] = float(self.value)
        if self.buckets is not None:
            payload['buckets'] = [float(item) for item in self.buckets]
        if self.counts is not None:
            payload['counts'] = [item for item in self.counts]
        if self.count is not None:
            payload['count'] = self.count
        if self.sum is not None:
            payload['sum'] = float(self.sum)
        return payload

@dataclass(frozen=True, slots=True)
class ModerationTargetRefV1:
    playerUuid: str | None = None
    playerPid: int | None = None
    playerName: str | None = None
    ip: str | None = None

    def __post_init__(self) -> None:
        if self.playerUuid is not None:
            _expect_str(self.playerUuid, 'playerUuid')
            if len(self.playerUuid) < 1:
                raise ValueError("playerUuid is too short")
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        if self.playerName is not None:
            _expect_str(self.playerName, 'playerName')
            if len(self.playerName) < 1:
                raise ValueError("playerName is too short")
        if self.ip is not None:
            _expect_str(self.ip, 'ip')
            if len(self.ip) < 1:
                raise ValueError("ip is too short")
        if not (self.playerUuid is not None or self.ip is not None):
            raise ValueError("At least one of playerUuid, ip must be provided")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "ModerationTargetRefV1":
        mapping = _expect_mapping(payload, "ModerationTargetRefV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(()),
            allowed=frozenset(('playerUuid', 'playerPid', 'playerName', 'ip')),
            model_name="ModerationTargetRefV1",
        )
        return cls(
            playerUuid=(_expect_str(mapping['playerUuid'], 'playerUuid') if 'playerUuid' in mapping else None),
            playerPid=(_expect_int(mapping['playerPid'], 'playerPid') if 'playerPid' in mapping else None),
            playerName=(_expect_str(mapping['playerName'], 'playerName') if 'playerName' in mapping else None),
            ip=(_expect_str(mapping['ip'], 'ip') if 'ip' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.playerUuid is not None:
            payload['playerUuid'] = self.playerUuid
        if self.playerPid is not None:
            payload['playerPid'] = self.playerPid
        if self.playerName is not None:
            payload['playerName'] = self.playerName
        if self.ip is not None:
            payload['ip'] = self.ip
        return payload

@dataclass(frozen=True, slots=True)
class PlayerCommandTargetV1:
    playerUuid: str | None = None
    playerPid: int | None = None
    playerName: str | None = None
    ip: str | None = None

    def __post_init__(self) -> None:
        if self.playerUuid is not None:
            _expect_str(self.playerUuid, 'playerUuid')
            if len(self.playerUuid) < 1:
                raise ValueError("playerUuid is too short")
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        if self.playerName is not None:
            _expect_str(self.playerName, 'playerName')
            if len(self.playerName) < 1:
                raise ValueError("playerName is too short")
        if self.ip is not None:
            _expect_str(self.ip, 'ip')
            if len(self.ip) < 1:
                raise ValueError("ip is too short")
        if not (self.playerUuid is not None or self.ip is not None):
            raise ValueError("At least one of playerUuid, ip must be provided")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerCommandTargetV1":
        mapping = _expect_mapping(payload, "PlayerCommandTargetV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(()),
            allowed=frozenset(('playerUuid', 'playerPid', 'playerName', 'ip')),
            model_name="PlayerCommandTargetV1",
        )
        return cls(
            playerUuid=(_expect_str(mapping['playerUuid'], 'playerUuid') if 'playerUuid' in mapping else None),
            playerPid=(_expect_int(mapping['playerPid'], 'playerPid') if 'playerPid' in mapping else None),
            playerName=(_expect_str(mapping['playerName'], 'playerName') if 'playerName' in mapping else None),
            ip=(_expect_str(mapping['ip'], 'ip') if 'ip' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        if self.playerUuid is not None:
            payload['playerUuid'] = self.playerUuid
        if self.playerPid is not None:
            payload['playerPid'] = self.playerPid
        if self.playerName is not None:
            payload['playerName'] = self.playerName
        if self.ip is not None:
            payload['ip'] = self.ip
        return payload

@dataclass(frozen=True, slots=True)
class PlayerRefV1:
    playerUuid: str
    playerName: str
    playerPid: int | None = None
    ip: str | None = None

    def __post_init__(self) -> None:
        _expect_str(self.playerUuid, 'playerUuid')
        if len(self.playerUuid) < 1:
            raise ValueError("playerUuid is too short")
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        _expect_str(self.playerName, 'playerName')
        if len(self.playerName) < 1:
            raise ValueError("playerName is too short")
        if self.ip is not None:
            _expect_str(self.ip, 'ip')
            if len(self.ip) < 1:
                raise ValueError("ip is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "PlayerRefV1":
        mapping = _expect_mapping(payload, "PlayerRefV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('playerUuid', 'playerName')),
            allowed=frozenset(('playerUuid', 'playerPid', 'playerName', 'ip')),
            model_name="PlayerRefV1",
        )
        return cls(
            playerUuid=_expect_str(mapping['playerUuid'], 'playerUuid'),
            playerPid=(_expect_int(mapping['playerPid'], 'playerPid') if 'playerPid' in mapping else None),
            playerName=_expect_str(mapping['playerName'], 'playerName'),
            ip=(_expect_str(mapping['ip'], 'ip') if 'ip' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['playerUuid'] = self.playerUuid
        if self.playerPid is not None:
            payload['playerPid'] = self.playerPid
        payload['playerName'] = self.playerName
        if self.ip is not None:
            payload['ip'] = self.ip
        return payload

@dataclass(frozen=True, slots=True)
class SeasonPodiumEntryV1:
    place: int
    player: PlayerRefV1
    rating: int
    league: str
    matches: int
    wins: int
    discord: DiscordIdentityRefV1 | None = None
    prizes: tuple[SeasonPrizeV1, ...] | None = None

    def __post_init__(self) -> None:
        _expect_int(self.place, 'place')
        if self.place < 1:
            raise ValueError("place must be >= 1")
        _expect_instance(self.player, 'player', PlayerRefV1)
        if self.discord is not None:
            _expect_instance(self.discord, 'discord', DiscordIdentityRefV1)
        _expect_int(self.rating, 'rating')
        _expect_str(self.league, 'league')
        if len(self.league) < 1:
            raise ValueError("league is too short")
        _expect_int(self.matches, 'matches')
        if self.matches < 0:
            raise ValueError("matches must be >= 0")
        _expect_int(self.wins, 'wins')
        if self.wins < 0:
            raise ValueError("wins must be >= 0")
        if self.prizes is not None:
            if not isinstance(self.prizes, tuple):
                raise TypeError("prizes must be a tuple")
            for item in self.prizes:
                _expect_instance(item, 'prizes[]', SeasonPrizeV1)

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SeasonPodiumEntryV1":
        mapping = _expect_mapping(payload, "SeasonPodiumEntryV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('place', 'player', 'rating', 'league', 'matches', 'wins')),
            allowed=frozenset(('place', 'player', 'discord', 'rating', 'league', 'matches', 'wins', 'prizes')),
            model_name="SeasonPodiumEntryV1",
        )
        return cls(
            place=_expect_int(mapping['place'], 'place'),
            player=PlayerRefV1.from_payload(_expect_mapping(mapping['player'], 'player')),
            discord=(DiscordIdentityRefV1.from_payload(_expect_mapping(mapping['discord'], 'discord')) if 'discord' in mapping else None),
            rating=_expect_int(mapping['rating'], 'rating'),
            league=_expect_str(mapping['league'], 'league'),
            matches=_expect_int(mapping['matches'], 'matches'),
            wins=_expect_int(mapping['wins'], 'wins'),
            prizes=(tuple(SeasonPrizeV1.from_payload(_expect_mapping(item, 'prizes[]')) for item in _expect_list(mapping['prizes'], 'prizes')) if 'prizes' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['place'] = self.place
        payload['player'] = self.player.to_payload()
        if self.discord is not None:
            payload['discord'] = self.discord.to_payload()
        payload['rating'] = self.rating
        payload['league'] = self.league
        payload['matches'] = self.matches
        payload['wins'] = self.wins
        if self.prizes is not None:
            payload['prizes'] = [item.to_payload() for item in self.prizes]
        return payload

class SeasonPrizeV1Kind(StrEnum):
    BADGE = 'badge'
    CUSTOM = 'custom'

@dataclass(frozen=True, slots=True)
class SeasonPrizeV1:
    placeFrom: int
    placeTo: int
    kind: SeasonPrizeV1Kind
    value: str
    description: str | None = None

    def __post_init__(self) -> None:
        _expect_int(self.placeFrom, 'placeFrom')
        if self.placeFrom < 1:
            raise ValueError("placeFrom must be >= 1")
        _expect_int(self.placeTo, 'placeTo')
        if self.placeTo < 1:
            raise ValueError("placeTo must be >= 1")
        _expect_instance(self.kind, 'kind', SeasonPrizeV1Kind)
        _expect_str(self.value, 'value')
        if len(self.value) < 1:
            raise ValueError("value is too short")
        if self.description is not None:
            _expect_str(self.description, 'description')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SeasonPrizeV1":
        mapping = _expect_mapping(payload, "SeasonPrizeV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('placeFrom', 'placeTo', 'kind', 'value')),
            allowed=frozenset(('placeFrom', 'placeTo', 'kind', 'value', 'description')),
            model_name="SeasonPrizeV1",
        )
        return cls(
            placeFrom=_expect_int(mapping['placeFrom'], 'placeFrom'),
            placeTo=_expect_int(mapping['placeTo'], 'placeTo'),
            kind=_expect_enum(mapping['kind'], 'kind', SeasonPrizeV1Kind),
            value=_expect_str(mapping['value'], 'value'),
            description=(_expect_str(mapping['description'], 'description') if 'description' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['placeFrom'] = self.placeFrom
        payload['placeTo'] = self.placeTo
        payload['kind'] = str(self.kind)
        payload['value'] = self.value
        if self.description is not None:
            payload['description'] = self.description
        return payload

@dataclass(frozen=True, slots=True)
class SeasonRefV1:
    ladder: str
    season: int
    name: str
    startsAt: str
    endsAt: str

    def __post_init__(self) -> None:
        _expect_str(self.ladder, 'ladder')
        if len(self.ladder) < 1:
            raise ValueError("ladder is too short")
        _expect_int(self.season, 'season')
        if self.season < 1:
            raise ValueError("season must be >= 1")
        _expect_str(self.name, 'name')
        if len(self.name) < 1:
            raise ValueError("name is too short")
        _expect_str(self.startsAt, 'startsAt')
        _expect_str(self.endsAt, 'endsAt')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SeasonRefV1":
        mapping = _expect_mapping(payload, "SeasonRefV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('ladder', 'season', 'name', 'startsAt', 'endsAt')),
            allowed=frozenset(('ladder', 'season', 'name', 'startsAt', 'endsAt')),
            model_name="SeasonRefV1",
        )
        return cls(
            ladder=_expect_str(mapping['ladder'], 'ladder'),
            season=_expect_int(mapping['season'], 'season'),
            name=_expect_str(mapping['name'], 'name'),
            startsAt=_expect_str(mapping['startsAt'], 'startsAt'),
            endsAt=_expect_str(mapping['endsAt'], 'endsAt'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['ladder'] = self.ladder
        payload['season'] = self.season
        payload['name'] = self.name
        payload['startsAt'] = self.startsAt
        payload['endsAt'] = self.endsAt
        return payload

@dataclass(frozen=True, slots=True)
class SeasonSummaryV1:
    participants: int
    matches: int

    def __post_init__(self) -> None:
        _expect_int(self.participants, 'participants')
        if self.participants < 0:
            raise ValueError("participants must be >= 0")
        _expect_int(self.matches, 'matches')
        if self.matches < 0:
            raise ValueError("matches must be >= 0")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "SeasonSummaryV1":
        mapping = _expect_mapping(payload, "SeasonSummaryV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('participants', 'matches')),
            allowed=frozenset(('participants', 'matches')),
            model_name="SeasonSummaryV1",
        )
        return cls(
            participants=_expect_int(mapping['participants'], 'participants'),
            matches=_expect_int(mapping['matches'], 'matches'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['participants'] = self.participants
        payload['matches'] = self.matches
        return payload

@dataclass(frozen=True, slots=True)
class VoteKickParticipantV1:
    playerName: str
    playerPid: int | None = None
    discordId: str | None = None

    def __post_init__(self) -> None:
        _expect_str(self.playerName, 'playerName')
        if len(self.playerName) < 1:
            raise ValueError("playerName is too short")
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        if self.discordId is not None:
            _expect_str(self.discordId, 'discordId')
            if len(self.discordId) < 1:
                raise ValueError("discordId is too short")

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "VoteKickParticipantV1":
        mapping = _expect_mapping(payload, "VoteKickParticipantV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('playerName',)),
            allowed=frozenset(('playerName', 'playerPid', 'discordId')),
            model_name="VoteKickParticipantV1",
        )
        return cls(
            playerName=_expect_str(mapping['playerName'], 'playerName'),
            playerPid=(_expect_int(mapping['playerPid'], 'playerPid') if 'playerPid' in mapping else None),
            discordId=(_expect_str(mapping['discordId'], 'discordId') if 'discordId' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['playerName'] = self.playerName
        if self.playerPid is not None:
            payload['playerPid'] = self.playerPid
        if self.discordId is not None:
            payload['discordId'] = self.discordId
        return payload

__all__ = [
    "ActorRefV1",
    "CaseBlockAreaV1",
    "CaseBlockEntryV1",
    "CaseBlockSummaryV1",
    "CaseChatLineV1",
    "DiscordIdentityRefV1",
    "ExpirationInfoV1",
    "MapDiscordUploadV1",
    "MapEntryV1",
    "MapFileSourceV1",
    "MapInspectFindingV1",
    "MapInspectInfoV1",
    "MapUploadResultV1",
    "MetricSampleV1",
    "ModerationTargetRefV1",
    "PlayerCommandTargetV1",
    "PlayerRefV1",
    "SeasonPodiumEntryV1",
    "SeasonPrizeV1",
    "SeasonRefV1",
    "SeasonSummaryV1",
    "VoteKickParticipantV1",
    "ActorRefV1ActorType",
    "CaseChatLineV1Role",
    "MapInspectFindingV1Severity",
    "MapUploadResultV1Status",
    "MetricSampleV1Type",
    "SeasonPrizeV1Kind",
]
