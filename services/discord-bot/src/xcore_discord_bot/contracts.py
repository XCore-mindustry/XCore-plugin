from __future__ import annotations

from json import loads
from enum import StrEnum
from typing import Any, ClassVar

from pydantic import (
    AliasChoices,
    BaseModel,
    ConfigDict,
    Field,
    ValidationError,
    field_validator,
)


class _FrozenModel(BaseModel):
    model_config: ClassVar[ConfigDict] = ConfigDict(
        frozen=True,
        extra="ignore",
        populate_by_name=True,
    )

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
    admin_discord_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("adminDiscordId", "admin_discord_id"),
    )
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
    admin_discord_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("adminDiscordId", "admin_discord_id"),
    )
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


class VoteKickParticipant(_FrozenModel):
    name: str = Field(
        validation_alias=AliasChoices("name", "nickname", "playerName", "player_name")
    )
    pid: int | None = Field(
        default=None,
        validation_alias=AliasChoices("pid", "playerPid", "player_pid"),
    )
    discord_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices("discordId", "discord_id"),
    )

    @field_validator("name")
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


class VoteKickEvent(_FrozenModel):
    target_name: str = Field(
        validation_alias=AliasChoices("targetName", "target_name", "target", "name")
    )
    target_pid: int | None = Field(
        default=None,
        validation_alias=AliasChoices("targetPid", "target_pid", "targetId", "pid"),
    )
    target_uuid: str | None = Field(
        default=None,
        validation_alias=AliasChoices("targetUuid", "target_uuid", "uuid"),
    )
    starter_name: str = Field(
        validation_alias=AliasChoices(
            "starterName",
            "starter_name",
            "starter",
            "initiatorName",
            "initiator_name",
            "adminName",
            "admin_name",
        )
    )
    starter_pid: int | None = Field(
        default=None,
        validation_alias=AliasChoices(
            "starterPid",
            "starter_pid",
            "starterId",
            "initiatorPid",
            "initiator_pid",
            "adminPid",
            "admin_pid",
        ),
    )
    starter_discord_id: str | None = Field(
        default=None,
        validation_alias=AliasChoices(
            "starterDiscordId",
            "starter_discord_id",
            "initiatorDiscordId",
            "initiator_discord_id",
            "adminDiscordId",
            "admin_discord_id",
        ),
    )
    reason: str
    votes_for: list[VoteKickParticipant] = Field(
        default_factory=list,
        validation_alias=AliasChoices("votesFor", "votes_for", "yesVotes", "yes_votes"),
    )
    votes_against: list[VoteKickParticipant] = Field(
        default_factory=list,
        validation_alias=AliasChoices(
            "votesAgainst",
            "votes_against",
            "noVotes",
            "no_votes",
        ),
    )

    @field_validator("target_name", "starter_name", "reason")
    @classmethod
    def _required_text_fields(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @field_validator("target_pid", "starter_pid", mode="before")
    @classmethod
    def _optional_pid(cls, value: Any) -> int | None:
        if value is None:
            return None
        if isinstance(value, str) and not value.strip():
            return None
        return _coerce_int(value)

    @field_validator("votes_for", "votes_against", mode="before")
    @classmethod
    def _default_participant_lists(cls, value: Any) -> list[dict[str, Any]]:
        if value is None:
            return []
        if isinstance(value, list):
            return value
        if isinstance(value, tuple):
            return list(value)
        raise ValueError("Expected participant list")

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "VoteKickEvent":
        return cls._validate_payload(
            payload,
            error_message=(
                "Invalid vote-kick payload: expected target, starter, reason, and optional vote lists"
            ),
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


class DiscordLinkStatusChangedEvent(_FrozenModel):
    player_uuid: str = Field(validation_alias=AliasChoices("playerUuid", "player_uuid"))
    player_pid: int = Field(validation_alias=AliasChoices("playerPid", "player_pid"))
    player_nickname: str = Field(
        validation_alias=AliasChoices("playerNickname", "player_nickname")
    )
    discord_id: str = Field(validation_alias=AliasChoices("discordId", "discord_id"))
    discord_username: str | None = Field(
        default=None,
        validation_alias=AliasChoices("discordUsername", "discord_username"),
    )
    action: str
    server: str
    occurred_at: int = Field(validation_alias=AliasChoices("occurredAt", "occurred_at"))

    @field_validator("player_uuid", "player_nickname", "discord_id", "action", "server")
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @field_validator("player_pid", "occurred_at", mode="before")
    @classmethod
    def _parse_int_fields(cls, value: Any) -> int:
        return _coerce_int(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "DiscordLinkStatusChangedEvent":
        return cls._validate_payload(
            payload,
            error_message="Invalid discord link status payload",
        )


class DiscordAdminAccessChangedEvent(_FrozenModel):
    player_uuid: str = Field(validation_alias=AliasChoices("playerUuid", "player_uuid"))
    player_pid: int = Field(validation_alias=AliasChoices("playerPid", "player_pid"))
    discord_id: str = Field(validation_alias=AliasChoices("discordId", "discord_id"))
    discord_username: str | None = Field(
        default=None,
        validation_alias=AliasChoices("discordUsername", "discord_username"),
    )
    admin: bool
    admin_source: str = Field(
        validation_alias=AliasChoices("adminSource", "admin_source")
    )
    requested_by: str = Field(
        validation_alias=AliasChoices("requestedBy", "requested_by")
    )
    reason: str
    server: str
    occurred_at: int = Field(validation_alias=AliasChoices("occurredAt", "occurred_at"))

    @field_validator(
        "player_uuid", "discord_id", "admin_source", "requested_by", "reason", "server"
    )
    @classmethod
    def _required_text(cls, value: str) -> str:
        return cls._require_non_empty_text(value)

    @field_validator("player_pid", "occurred_at", mode="before")
    @classmethod
    def _parse_int_fields(cls, value: Any) -> int:
        return _coerce_int(value)

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "DiscordAdminAccessChangedEvent":
        return cls._validate_payload(
            payload,
            error_message="Invalid discord admin access payload",
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
