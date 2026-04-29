"""Generated canonical shared protocol models."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
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


def _expect_instance(value: Any, field_name: str, expected_type: type[Any]) -> None:
    if not isinstance(value, expected_type):
        raise TypeError(f"{field_name} must be a {expected_type.__name__}")

@dataclass(frozen=True, slots=True)
class ActorRefV1:
    actorName: str
    actorDiscordId: str | None = None
    actorType: str | None = None

    def __post_init__(self) -> None:
        _expect_str(self.actorName, 'actorName')
        if self.actorDiscordId is not None:
            _expect_str(self.actorDiscordId, 'actorDiscordId')
        if self.actorType is not None:
            _expect_str(self.actorType, 'actorType')

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
            actorType=(_expect_str(mapping['actorType'], 'actorType') if 'actorType' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['actorName'] = self.actorName
        if self.actorDiscordId is not None:
            payload['actorDiscordId'] = self.actorDiscordId
        if self.actorType is not None:
            payload['actorType'] = self.actorType
        return payload

@dataclass(frozen=True, slots=True)
class DiscordIdentityRefV1:
    discordId: str
    discordUsername: str | None = None

    def __post_init__(self) -> None:
        _expect_str(self.discordId, 'discordId')
        if self.discordUsername is not None:
            _expect_str(self.discordUsername, 'discordUsername')

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

    def __post_init__(self) -> None:
        _expect_str(self.name, 'name')
        _expect_str(self.fileName, 'fileName')
        _expect_str(self.author, 'author')
        if self.width is not None:
            _expect_int(self.width, 'width')
        if self.height is not None:
            _expect_int(self.height, 'height')
        if self.fileSizeBytes is not None:
            _expect_int(self.fileSizeBytes, 'fileSizeBytes')
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

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapEntryV1":
        mapping = _expect_mapping(payload, "MapEntryV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('name', 'fileName', 'author')),
            allowed=frozenset(('name', 'fileName', 'author', 'width', 'height', 'fileSizeBytes', 'like', 'dislike', 'reputation', 'popularity', 'interest', 'gameMode')),
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
        return payload

@dataclass(frozen=True, slots=True)
class MapFileSourceV1:
    url: str
    filename: str

    def __post_init__(self) -> None:
        _expect_str(self.url, 'url')
        _expect_str(self.filename, 'filename')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "MapFileSourceV1":
        mapping = _expect_mapping(payload, "MapFileSourceV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('url', 'filename')),
            allowed=frozenset(('url', 'filename')),
            model_name="MapFileSourceV1",
        )
        return cls(
            url=_expect_str(mapping['url'], 'url'),
            filename=_expect_str(mapping['filename'], 'filename'),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['url'] = self.url
        payload['filename'] = self.filename
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
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        if self.playerName is not None:
            _expect_str(self.playerName, 'playerName')
        if self.ip is not None:
            _expect_str(self.ip, 'ip')
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
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        if self.playerName is not None:
            _expect_str(self.playerName, 'playerName')
        if self.ip is not None:
            _expect_str(self.ip, 'ip')
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
        if self.playerPid is not None:
            _expect_int(self.playerPid, 'playerPid')
        _expect_str(self.playerName, 'playerName')
        if self.ip is not None:
            _expect_str(self.ip, 'ip')

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
class VoteKickParticipantV1:
    name: str
    pid: int | None = None
    discordId: str | None = None

    def __post_init__(self) -> None:
        _expect_str(self.name, 'name')
        if self.pid is not None:
            _expect_int(self.pid, 'pid')
        if self.discordId is not None:
            _expect_str(self.discordId, 'discordId')

    @classmethod
    def from_payload(cls, payload: Mapping[str, Any]) -> "VoteKickParticipantV1":
        mapping = _expect_mapping(payload, "VoteKickParticipantV1")
        _expect_exact_keys(
            mapping,
            required=frozenset(('name',)),
            allowed=frozenset(('name', 'pid', 'discordId')),
            model_name="VoteKickParticipantV1",
        )
        return cls(
            name=_expect_str(mapping['name'], 'name'),
            pid=(_expect_int(mapping['pid'], 'pid') if 'pid' in mapping else None),
            discordId=(_expect_str(mapping['discordId'], 'discordId') if 'discordId' in mapping else None),
        )

    def to_payload(self) -> dict[str, Any]:
        payload: dict[str, Any] = {}
        payload['name'] = self.name
        if self.pid is not None:
            payload['pid'] = self.pid
        if self.discordId is not None:
            payload['discordId'] = self.discordId
        return payload

__all__ = [
    "ActorRefV1",
    "DiscordIdentityRefV1",
    "ExpirationInfoV1",
    "MapEntryV1",
    "MapFileSourceV1",
    "ModerationTargetRefV1",
    "PlayerCommandTargetV1",
    "PlayerRefV1",
    "VoteKickParticipantV1",
]
