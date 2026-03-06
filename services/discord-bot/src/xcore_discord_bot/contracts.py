from __future__ import annotations

from json import loads
from enum import StrEnum
from typing import Any

from pydantic import (
    AliasChoices,
    BaseModel,
    ConfigDict,
    Field,
    ValidationError,
    field_validator,
)


class _FrozenModel(BaseModel):
    model_config: ConfigDict = {
        "frozen": True,
        "extra": "ignore",
        "populate_by_name": True,
    }

    @staticmethod
    def _require_non_empty_text(value: str) -> str:
        if not value:
            raise ValueError("Expected non-empty string")
        return value

    @classmethod
    def _validate_payload(
        cls,
        payload: dict[str, Any],
        *,
        error_message: str,
    ):
        try:
            return cls.model_validate(payload)
        except ValidationError as error:
            raise ValueError(error_message) from error


class EventType(StrEnum):
    HEARTBEAT = "ServerHeartbeatEvent"


def _coerce_int(value: Any) -> int:
    if isinstance(value, bool):
        raise ValueError("Expected integer value")
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str):
        normalized = value.strip()
        if normalized and normalized.lstrip("-").isdigit():
            return int(normalized)
    raise ValueError("Expected integer value")


class GameChatMessage(_FrozenModel):
    author_name: str = Field(validation_alias=AliasChoices("authorName", "author_name"))
    message: str
    server: str

    @field_validator("author_name", "message", "server")
    @classmethod
    def _not_blank(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "GameChatMessage":
        return cls._validate_payload(
            payload,
            error_message="Invalid chat payload: expected authorName, message, server",
        )


class PlayerJoinLeaveEvent(_FrozenModel):
    player_name: str = Field(validation_alias=AliasChoices("playerName", "player_name"))
    server: str
    joined: bool = Field(validation_alias="join")

    @field_validator("player_name", "server")
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @field_validator("joined", mode="before")
    @classmethod
    def _parse_joined(cls, value: Any) -> bool:
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            return value.strip().lower() in {"true", "1", "yes", "on"}
        if isinstance(value, (int, float)):
            return value != 0
        return False

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "PlayerJoinLeaveEvent":
        return cls._validate_payload(
            payload,
            error_message="Invalid join/leave payload: expected playerName and server",
        )


class ServerActionEvent(_FrozenModel):
    message: str
    server: str

    @field_validator("message", "server")
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "ServerActionEvent":
        return cls._validate_payload(
            payload,
            error_message="Invalid server action payload: expected message and server",
        )


class BanEvent(_FrozenModel):
    pid: int | None = Field(
        default=None,
        validation_alias=AliasChoices("pid", "playerPid", "player_pid"),
    )
    uuid: str | None = None
    ip: str | None = None
    name: str
    admin_name: str = Field(validation_alias=AliasChoices("adminName", "admin_name"))
    reason: str
    expire_date: str | None = Field(
        default=None,
        validation_alias=AliasChoices("expireDate", "expire_date"),
    )

    @field_validator("name", "admin_name", "reason")
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @field_validator("pid", mode="before")
    @classmethod
    def _optional_int(cls, value: Any) -> int | None:
        if value is None:
            return None
        if isinstance(value, str) and not value.strip():
            return None
        return _coerce_int(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "BanEvent":
        return cls._validate_payload(
            payload,
            error_message="Invalid ban payload: expected name, adminName/admin_name, reason",
        )


class MuteEvent(_FrozenModel):
    pid: int | None = Field(
        default=None,
        validation_alias=AliasChoices("pid", "playerPid", "player_pid"),
    )
    uuid: str | None = None
    name: str
    admin_name: str = Field(validation_alias=AliasChoices("adminName", "admin_name"))
    reason: str
    expire_date: str | None = Field(
        default=None,
        validation_alias=AliasChoices("expireDate", "expire_date"),
    )

    @field_validator("name", "admin_name", "reason")
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @field_validator("pid", mode="before")
    @classmethod
    def _optional_int(cls, value: Any) -> int | None:
        if value is None:
            return None
        if isinstance(value, str) and not value.strip():
            return None
        return _coerce_int(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "MuteEvent":
        return cls._validate_payload(
            payload,
            error_message="Invalid mute payload: expected name, adminName/admin_name, reason",
        )


class GlobalChatEvent(_FrozenModel):
    author_name: str = Field(validation_alias=AliasChoices("authorName", "author_name"))
    message: str
    server: str

    @field_validator("author_name", "message", "server")
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "GlobalChatEvent":
        return cls._validate_payload(
            payload,
            error_message="Invalid global chat payload: expected authorName, message, server",
        )


class ServerHeartbeatEvent(_FrozenModel):
    server_name: str = Field(validation_alias=AliasChoices("serverName", "server_name"))
    discord_channel_id: int = Field(
        validation_alias=AliasChoices("discordChannelId", "discord_channel_id")
    )
    players: int
    max_players: int = Field(validation_alias=AliasChoices("maxPlayers", "max_players"))
    version: str
    host: str | None = Field(
        default=None, validation_alias=AliasChoices("host", "serverHost", "server_host")
    )
    port: int | None = Field(
        default=None, validation_alias=AliasChoices("port", "serverPort", "server_port")
    )

    @field_validator("server_name", "version")
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @field_validator("discord_channel_id", "players", "max_players", mode="before")
    @classmethod
    def _parse_int_fields(cls, value: Any) -> int:
        return _coerce_int(value)

    @field_validator("host", mode="before")
    @classmethod
    def _parse_host(cls, value: Any) -> str | None:
        if value is None:
            return None
        if isinstance(value, str):
            normalized = value.strip()
            return normalized if normalized else None
        return str(value)

    @field_validator("port", mode="before")
    @classmethod
    def _parse_optional_port(cls, value: Any) -> int | None:
        if value is None:
            return None
        if isinstance(value, str) and not value.strip():
            return None
        return _coerce_int(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "ServerHeartbeatEvent":
        return cls._validate_payload(
            payload,
            error_message=(
                "Invalid heartbeat payload: expected serverName, discordChannelId, players, maxPlayers, version"
            ),
        )


class RawEvent(_FrozenModel):
    event_type: str
    payload: dict[str, Any]

    @classmethod
    def from_fields(cls, fields: dict[str, Any]) -> "RawEvent":
        event_type_raw = fields.get("event_type")
        if not isinstance(event_type_raw, str) or not event_type_raw:
            raise ValueError("Invalid raw event fields: expected event_type")

        payload_raw = fields.get("payload_json", "{}")
        if not isinstance(payload_raw, str):
            payload_raw = str(payload_raw)

        try:
            payload = loads(payload_raw)
        except Exception as error:
            raise ValueError(
                "Invalid raw event fields: payload_json is not valid JSON"
            ) from error

        if not isinstance(payload, dict):
            raise ValueError(
                "Invalid raw event fields: payload_json must decode to object"
            )

        return cls(event_type=event_type_raw, payload=payload)
