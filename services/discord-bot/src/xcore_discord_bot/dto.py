from __future__ import annotations

from dataclasses import dataclass

# Which account PID survives a merge. The UUID always comes from the merge target.
MERGE_KEEP_PID_TARGET = "target"
MERGE_KEEP_PID_SOURCE = "source"
MERGE_KEEP_PID_CHOICES: tuple[str, ...] = (MERGE_KEEP_PID_TARGET, MERGE_KEEP_PID_SOURCE)


@dataclass(frozen=True)
class PlayerRecord:
    pid: int
    nickname: str
    uuid: str | None = None
    username: str | None = None
    ip: str | None = None
    last_ip: str | None = None
    custom_nickname: str | None = None
    description: str | None = None
    language: str | None = None
    translator_language: str | None = None
    total_play_time: int = 0
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
    online: bool = False
    online_since: int | None = None
    online_server: str | None = None
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
    pid: int | None = None

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)


@dataclass(frozen=True, kw_only=True)
class AuditRecordSummary:
    audit_id: str
    action: str
    target_uuid: str | None = None
    target_name: str | None = None
    actor_type: str | None = None
    actor_id: str | None = None
    actor_name: str | None = None
    reason: str | None = None
    duration_ms: int | None = None
    expires_at: object = None
    occurred_at: object = None
    created_at_epoch_ms: int = 0

    def get(self, key: str, default: object = None) -> object:
        return getattr(self, key, default)


@dataclass(frozen=True, kw_only=True)
class AccountMergeResult:
    success: bool
    error: str | None = None
    source_before: PlayerRecord | None = None
    target_before: PlayerRecord | None = None
    target_after: PlayerRecord | None = None
    games_transferred: int = 0
    ban_transferred: bool = False
    mute_transferred: bool = False
    audit_id: str | None = None
    keep_pid: str = MERGE_KEEP_PID_TARGET
    surviving_pid: int | None = None
    tombstone_pid: int | None = None
    discord_link_moved: bool = False
    discord_link_conflict: bool = False
    # None: nothing to move or not attempted. Otherwise whether the rating standings moved.
    ratings_merged: bool | None = None
    ratings_pending: bool = False
    ratings_error: str | None = None
