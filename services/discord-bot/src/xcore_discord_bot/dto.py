from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class PlayerRecord:
    pid: int
    nickname: str
    uuid: str | None = None
    ip: str | None = None
    last_ip: str | None = None
    custom_nickname: str | None = None
    description: str | None = None
    language: str | None = None
    translator_language: str | None = None
    total_play_time: int = 0
    pvp_rating: int = 0
    hexed_rank: int = 0
    hexed_points: int = 0
    leaderboard: bool = True
    unlocked_badges: tuple[str, ...] = ()
    active_badge: str | None = None
    blocked_private_uuids: tuple[str, ...] = ()
    is_admin: bool = False
    admin_source: str | None = None
    discord_id: str | None = None
    discord_username: str | None = None
    discord_linked_at: int | None = None
    created_at: object = None
    updated_at: object = None

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)


@dataclass(frozen=True, kw_only=True)
class BanRecord:
    name: str
    admin_name: str
    reason: str
    expire_date: object
    admin_discord_id: str | None = None
    uuid: str | None = None
    ip: str | None = None
    pid: int | None = None

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)


@dataclass(frozen=True, kw_only=True)
class MuteRecord:
    name: str
    admin_name: str
    reason: str
    expire_date: object
    admin_discord_id: str | None = None
    uuid: str | None = None

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)
