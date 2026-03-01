from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


def _pick(source: dict, *keys: str) -> str | None:
    for key in keys:
        value = source.get(key)
        if isinstance(value, str):
            return value
    return None


class EventType(StrEnum):
    HEARTBEAT = "ServerHeartbeatEvent"


def _pick_int(source: dict, *keys: str) -> int | None:
    for key in keys:
        value = source.get(key)
        if isinstance(value, bool):
            continue
        if isinstance(value, int):
            return value
        if isinstance(value, float):
            return int(value)
        if isinstance(value, str):
            normalized = value.strip()
            if normalized and normalized.lstrip("-").isdigit():
                return int(normalized)
    return None


@dataclass(frozen=True)
class GameChatMessage:
    author_name: str
    message: str
    server: str

    @classmethod
    def from_payload(cls, payload: dict) -> "GameChatMessage":
        author_name = _pick(payload, "authorName", "author_name")
        message = _pick(payload, "message")
        server = _pick(payload, "server")

        if not author_name or not message or not server:
            raise ValueError(
                "Invalid chat payload: expected authorName, message, server"
            )

        return cls(author_name=author_name, message=message, server=server)


@dataclass(frozen=True)
class PlayerJoinLeaveEvent:
    player_name: str
    server: str
    joined: bool

    @classmethod
    def from_payload(cls, payload: dict) -> "PlayerJoinLeaveEvent":
        player_name = _pick(payload, "playerName", "player_name")
        server = _pick(payload, "server")
        joined_raw = payload.get("join")

        if isinstance(joined_raw, bool):
            joined = joined_raw
        elif isinstance(joined_raw, str):
            joined = joined_raw.strip().lower() in {"true", "1", "yes", "on"}
        elif isinstance(joined_raw, (int, float)):
            joined = joined_raw != 0
        else:
            joined = False

        if not player_name or not server:
            raise ValueError(
                "Invalid join/leave payload: expected playerName and server"
            )

        return cls(player_name=player_name, server=server, joined=joined)


@dataclass(frozen=True)
class ServerActionEvent:
    message: str
    server: str

    @classmethod
    def from_payload(cls, payload: dict) -> "ServerActionEvent":
        message = _pick(payload, "message")
        server = _pick(payload, "server")

        if not message or not server:
            raise ValueError(
                "Invalid server action payload: expected message and server"
            )

        return cls(message=message, server=server)


@dataclass(frozen=True)
class BanEvent:
    uuid: str | None
    ip: str | None
    name: str
    admin_name: str
    reason: str
    expire_date: str | None

    @classmethod
    def from_payload(cls, payload: dict) -> "BanEvent":
        uuid_value = _pick(payload, "uuid")
        ip_value = _pick(payload, "ip")
        name = _pick(payload, "name")
        admin_name = _pick(payload, "adminName", "admin_name")
        reason = _pick(payload, "reason")
        expire_date = _pick(payload, "expireDate", "expire_date")

        if not name or not admin_name or not reason:
            raise ValueError(
                "Invalid ban payload: expected name, adminName/admin_name, reason"
            )

        return cls(
            uuid=uuid_value,
            ip=ip_value,
            name=name,
            admin_name=admin_name,
            reason=reason,
            expire_date=expire_date,
        )


@dataclass(frozen=True)
class GlobalChatEvent:
    author_name: str
    message: str
    server: str

    @classmethod
    def from_payload(cls, payload: dict) -> "GlobalChatEvent":
        author_name = _pick(payload, "authorName", "author_name")
        message = _pick(payload, "message")
        server = _pick(payload, "server")

        if not author_name or not message or not server:
            raise ValueError(
                "Invalid global chat payload: expected authorName, message, server"
            )

        return cls(author_name=author_name, message=message, server=server)


@dataclass(frozen=True)
class ServerHeartbeatEvent:
    server_name: str
    discord_channel_id: int
    players: int
    max_players: int
    version: str

    @classmethod
    def from_payload(cls, payload: dict) -> "ServerHeartbeatEvent":
        server_name = _pick(payload, "serverName", "server_name")
        discord_channel_id = _pick_int(payload, "discordChannelId", "discord_channel_id")
        players = _pick_int(payload, "players")
        max_players = _pick_int(payload, "maxPlayers", "max_players")
        version = _pick(payload, "version")

        if (
            not server_name
            or discord_channel_id is None
            or players is None
            or max_players is None
            or version is None
        ):
            raise ValueError(
                "Invalid heartbeat payload: expected serverName, discordChannelId, players, maxPlayers, version"
            )

        return cls(
            server_name=server_name,
            discord_channel_id=discord_channel_id,
            players=players,
            max_players=max_players,
            version=version,
        )


@dataclass(frozen=True)
class RawEvent:
    event_type: str
    payload: dict

    @classmethod
    def from_fields(cls, fields: dict) -> "RawEvent":
        event_type = _pick(fields, "event_type")
        payload_raw = _pick(fields, "payload_json") or "{}"

        if not event_type:
            raise ValueError("Invalid raw event fields: expected event_type")

        try:
            from json import loads

            payload = loads(payload_raw)
        except Exception as error:
            raise ValueError("Invalid raw event fields: payload_json is not valid JSON") from error

        if not isinstance(payload, dict):
            raise ValueError("Invalid raw event fields: payload_json must decode to object")

        return cls(event_type=event_type, payload=payload)
