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
    total_play_time: int = 0
    pvp_rating: int = 0
    hexed_rank: int = 0
    hexed_points: int = 0
    is_admin: bool = False
    admin_confirmed: bool = False
    created_at: object = None
    updated_at: object = None

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)


@dataclass(frozen=True)
class BanRecord:
    name: str
    admin_name: str
    reason: str
    expire_date: object
    uuid: str | None = None
    ip: str | None = None
    pid: int | None = None

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)


@dataclass(frozen=True)
class MuteRecord:
    name: str
    admin_name: str
    reason: str
    expire_date: object
    uuid: str | None = None

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)
