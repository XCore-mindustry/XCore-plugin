"""Merging two player accounts into one.

The merge rewrites the players, the recorded games, both accounts' bans and mutes and the
moderation audit in a single transaction.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from pymongo.asynchronous.client_session import AsyncClientSession
from pymongo.asynchronous.database import AsyncDatabase
from pymongo.errors import OperationFailure

from .dto import (
    MERGE_KEEP_PID_CHOICES,
    MERGE_KEEP_PID_SOURCE,
    MERGE_KEEP_PID_TARGET,
    AccountMergeResult,
    PlayerRecord,
)
from .mongo_store import MongoStore, PlayerDoc, to_int
from .settings import Settings
from .store_mappers import player_record_from_doc

logger = logging.getLogger(__name__)
# The server error a standalone MongoDB returns for a transaction.
_ILLEGAL_OPERATION = 20


def _to_bool(value: Any, default: bool = False) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        return value.strip().lower() in {"1", "true", "yes", "online"}
    return default


@dataclass(frozen=True)
class _MergePlan:
    source_raw: dict[str, Any]
    target_raw: dict[str, Any]
    source_uuid: str
    target_uuid: str
    keep_pid: str
    surviving_pid: int
    tombstone_pid: int
    target_updates: dict[str, Any]
    source_updates: dict[str, Any]
    discord_link_moved: bool
    discord_link_conflict: bool


class AccountMergeStore:
    def __init__(self, store: MongoStore, settings: Settings) -> None:
        self._store = store
        self._settings = settings

    async def merge_player_accounts(
        self,
        *,
        source_pid: int,
        target_pid: int,
        actor_name: str,
        actor_discord_id: str | None,
        reason: str,
        keep_pid: str = MERGE_KEEP_PID_TARGET,
    ) -> AccountMergeResult:
        normalized_keep_pid = str(keep_pid or MERGE_KEEP_PID_TARGET).strip().lower()
        if normalized_keep_pid not in MERGE_KEEP_PID_CHOICES:
            return AccountMergeResult(
                success=False,
                error=(
                    f"Unknown keep_pid value '{keep_pid}'. "
                    f"Expected one of: {', '.join(MERGE_KEEP_PID_CHOICES)}."
                ),
            )

        if source_pid == target_pid:
            return AccountMergeResult(
                success=False,
                error=f"Cannot merge account into itself (PID #{source_pid})",
            )

        db = self._store.database

        # with_transaction retries the whole callback on a transient error such as a write
        # conflict, so the players are read inside it: a retry sees what the other writer did.
        async def merge(session: AsyncClientSession) -> AccountMergeResult:
            loaded = await self._load_merge_pair(
                db, session, source_pid=source_pid, target_pid=target_pid
            )
            if isinstance(loaded, str):
                return AccountMergeResult(success=False, error=loaded)
            plan = self._plan_merge(*loaded, keep_pid=normalized_keep_pid)
            return await self._apply_merge(
                db,
                session,
                plan,
                actor_name=actor_name,
                actor_discord_id=actor_discord_id,
                reason=reason,
            )

        try:
            async with db.client.start_session() as session:
                return await session.with_transaction(merge)
        except OperationFailure as error:
            if error.code != _ILLEGAL_OPERATION:
                raise
            # A standalone server refuses the first operation of a transaction.
            logger.warning("Account merge needs a MongoDB replica set: %s", error)
            return AccountMergeResult(
                success=False,
                error=(
                    "Account merge needs MongoDB to run as a replica set. "
                    "Nothing was changed."
                ),
            )

    @staticmethod
    async def _load_merge_pair(
        db: AsyncDatabase,
        session: AsyncClientSession,
        *,
        source_pid: int,
        target_pid: int,
    ) -> tuple[dict[str, Any], dict[str, Any]] | str:
        """Both players of a merge, or why they cannot be merged."""
        source_raw = await db["players"].find_one({"pid": source_pid}, session=session)
        if source_raw is None:
            return f"Source player #{source_pid} not found in database"

        target_raw = await db["players"].find_one({"pid": target_pid}, session=session)
        if target_raw is None:
            return f"Target player #{target_pid} not found in database"

        source_uuid = str(source_raw.get("uuid") or "").strip()
        target_uuid = str(target_raw.get("uuid") or "").strip()

        if source_uuid and target_uuid and source_uuid == target_uuid:
            return "Source and target have the same UUID."

        if source_uuid.startswith("merged:"):
            return (
                f"Source player #{source_pid} was already merged into another account."
            )

        if target_uuid.startswith("merged:"):
            return (
                f"Target player #{target_pid} is a closed account "
                "and cannot receive a merge."
            )

        online_pids = [
            pid
            for pid, doc in ((source_pid, source_raw), (target_pid, target_raw))
            if _to_bool(doc.get("online"))
        ]
        if online_pids:
            online_list = ", ".join(f"#{pid}" for pid in online_pids)
            return (
                f"Cannot merge while {online_list} is online. "
                "Ask the player to disconnect, then retry."
            )

        return source_raw, target_raw

    def _plan_merge(
        self, source_raw: dict[str, Any], target_raw: dict[str, Any], *, keep_pid: str
    ) -> _MergePlan:
        """The documents a merge writes, built from both players without touching the database."""
        source_pid = to_int(source_raw.get("pid"))
        target_pid = to_int(target_raw.get("pid"))
        source_uuid = str(source_raw.get("uuid") or "").strip()

        # The UUID always comes from the merge target; only the PID is selectable.
        if keep_pid == MERGE_KEEP_PID_SOURCE:
            surviving_pid = source_pid
            tombstone_pid = target_pid
        else:
            surviving_pid = target_pid
            tombstone_pid = source_pid

        source_discord_id = str(source_raw.get("discord_id") or "").strip()
        target_discord_id = str(target_raw.get("discord_id") or "").strip()

        target_updates = self._merged_target_fields(source_raw, target_raw)
        target_updates["pid"] = surviving_pid

        source_updates: dict[str, Any] = {
            "pid": tombstone_pid,
            "uuid": f"merged:{source_uuid}",
            "total_play_time": 0,
            "description": f"Merged into PID #{surviving_pid} ({target_raw.get('nickname')})",
        }
        if source_discord_id:
            # The closed account must not keep a live Discord link, otherwise admin
            # sync and link lookups would resolve to a dead player.
            source_updates["discord_id"] = ""
            source_updates["discord_username"] = ""
            source_updates["discord_linked_at"] = 0

        return _MergePlan(
            source_raw=source_raw,
            target_raw=target_raw,
            source_uuid=source_uuid,
            target_uuid=str(target_raw.get("uuid") or "").strip(),
            keep_pid=keep_pid,
            surviving_pid=surviving_pid,
            tombstone_pid=tombstone_pid,
            target_updates=target_updates,
            source_updates=source_updates,
            discord_link_moved=bool(source_discord_id) and not target_discord_id,
            discord_link_conflict=(
                bool(source_discord_id)
                and bool(target_discord_id)
                and source_discord_id != target_discord_id
            ),
        )

    def _merged_target_fields(
        self, source_raw: dict[str, Any], target_raw: dict[str, Any]
    ) -> dict[str, Any]:
        """The surviving account's fields: the target's values, filled in from the source."""

        def first(key: str, default: Any) -> Any:
            return target_raw.get(key) or source_raw.get(key) or default

        def union(key: str) -> list[Any]:
            return sorted(
                set((target_raw.get(key) or []) + (source_raw.get(key) or []))
            )

        hexed_points = to_int(target_raw.get("hexed_points")) + to_int(
            source_raw.get("hexed_points")
        )
        hexed_rank_reqs = [0, 3, 10, 20, 25, 30]
        hexed_rank = 0
        for idx, req in enumerate(hexed_rank_reqs):
            if hexed_points >= req:
                hexed_rank = idx

        fields: dict[str, Any] = {
            "total_play_time": to_int(target_raw.get("total_play_time"))
            + to_int(source_raw.get("total_play_time")),
            "hexed_points": hexed_points,
            "hexed_rank": hexed_rank,
            "unlocked_badges": union("unlocked_badges"),
            "active_badge": first("active_badge", ""),
            "custom_nickname": first("custom_nickname", ""),
            "description": first("description", ""),
            "discord_id": str(target_raw.get("discord_id") or "").strip()
            or str(source_raw.get("discord_id") or "").strip(),
            "discord_username": first("discord_username", ""),
            "discord_linked_at": first("discord_linked_at", 0),
            "device_tokens": {
                **(source_raw.get("device_tokens") or {}),
                **(target_raw.get("device_tokens") or {}),
            },
            "device_token_hashes": union("device_token_hashes"),
            "blocked_private_uuids": union("blocked_private_uuids"),
        }
        if self._settings.permissions_mode != "roles":
            fields["is_admin"] = bool(
                target_raw.get("is_admin") or source_raw.get("is_admin")
            )
            fields["admin_source"] = first("admin_source", "NONE")
        return fields

    async def _apply_merge(
        self,
        db: AsyncDatabase,
        session: AsyncClientSession,
        plan: _MergePlan,
        *,
        actor_name: str,
        actor_discord_id: str | None,
        reason: str,
    ) -> AccountMergeResult:
        source_raw = plan.source_raw
        target_raw = plan.target_raw

        await db["players"].update_one(
            {"_id": target_raw["_id"]}, {"$set": plan.target_updates}, session=session
        )
        await db["players"].update_one(
            {"_id": source_raw["_id"]}, {"$set": plan.source_updates}, session=session
        )

        games_transferred = 0
        punishment_transfers = {"bans": False, "mutes": False}
        if plan.source_uuid and plan.target_uuid:
            match_res = await db["games_v2"].update_many(
                {"player_stats.uuid": plan.source_uuid},
                {"$set": {"player_stats.$[elem].uuid": plan.target_uuid}},
                array_filters=[{"elem.uuid": plan.source_uuid}],
                session=session,
            )
            games_transferred = to_int(match_res.modified_count)
            for collection in ("bans", "mutes"):
                punishment_transfers[collection] = await self._transfer_punishment(
                    db, session, plan, collection
                )

        audit_id = await self._store.append_moderation_audit(
            action="MERGE",
            target_uuid=plan.target_uuid,
            target_pid=plan.surviving_pid,
            target_name=str(target_raw.get("nickname") or "Unknown"),
            target_ip=str(target_raw.get("ip") or ""),
            actor_discord_id=actor_discord_id,
            actor_name=actor_name,
            reason=reason,
            occurred_at=datetime.now(UTC),
            extra={
                "merge": {
                    "keep_pid": plan.keep_pid,
                    "source_pid": source_raw.get("pid"),
                    "source_uuid": plan.source_uuid,
                    "source_name": str(source_raw.get("nickname") or "Unknown"),
                    "tombstone_pid": plan.tombstone_pid,
                    "surviving_pid": plan.surviving_pid,
                    "games_transferred": games_transferred,
                    "ban_transferred": punishment_transfers["bans"],
                    "mute_transferred": punishment_transfers["mutes"],
                    "discord_link_moved": plan.discord_link_moved,
                    "discord_link_conflict": plan.discord_link_conflict,
                }
            },
            session=session,
        )

        updated_target_raw = await db["players"].find_one(
            {"_id": target_raw["_id"]}, session=session
        )
        return AccountMergeResult(
            success=True,
            source_before=self._player_record(source_raw),
            target_before=self._player_record(target_raw),
            target_after=self._player_record(updated_target_raw),
            games_transferred=games_transferred,
            ban_transferred=punishment_transfers["bans"],
            mute_transferred=punishment_transfers["mutes"],
            audit_id=audit_id,
            keep_pid=plan.keep_pid,
            surviving_pid=plan.surviving_pid,
            tombstone_pid=plan.tombstone_pid,
            discord_link_moved=plan.discord_link_moved,
            discord_link_conflict=plan.discord_link_conflict,
        )

    async def _transfer_punishment(
        self,
        db: AsyncDatabase,
        session: AsyncClientSession,
        plan: _MergePlan,
        collection: str,
    ) -> bool:
        """Copy the closed account's ban or mute to the surviving one unless it has its own.

        Returns whether a copy was made. Both documents get the PID their account now has.
        """
        tombstone: dict[str, Any] = {"$set": {"pid": plan.tombstone_pid}}
        if collection == "bans":
            # Bans match by UUID or IP and unban deletes one ban_id, so a kept IP would
            # keep banning the surviving account after its own ban is lifted.
            tombstone["$unset"] = {"ip": ""}
        source_punishment = await db[collection].find_one(
            {"uuid": plan.source_uuid}, session=session
        )
        if source_punishment is not None:
            await db[collection].update_one(
                {"_id": source_punishment["_id"]}, tombstone, session=session
            )

        target_punishment = await db[collection].find_one(
            {"uuid": plan.target_uuid}, session=session
        )
        if target_punishment is not None:
            await db[collection].update_one(
                {"_id": target_punishment["_id"]},
                {"$set": {"pid": plan.surviving_pid}},
                session=session,
            )
            return False

        if source_punishment is None:
            return False

        transferred = dict(source_punishment)
        transferred.pop("_id", None)
        transferred["uuid"] = plan.target_uuid
        transferred["pid"] = plan.surviving_pid
        transferred["name"] = plan.target_raw.get("nickname") or "Unknown"
        transferred["reason"] = (
            f"[Merged from #{plan.source_raw.get('pid')}] "
            f"{source_punishment.get('reason', '')}"
        )
        if collection == "bans":
            # ban_id is unique and the closed account keeps its ban, so the copy needs its own.
            transferred["ban_id"] = await self._store.next_ban_id(session=session)
        await db[collection].insert_one(transferred, session=session)
        return True

    @staticmethod
    def _player_record(raw: dict[str, Any] | None) -> PlayerRecord:
        return player_record_from_doc(
            PlayerDoc.model_validate(raw).model_dump(mode="python")
        )
