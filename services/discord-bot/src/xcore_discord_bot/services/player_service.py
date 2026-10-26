from __future__ import annotations

from dataclasses import replace

from ..dto import MERGE_KEEP_PID_TARGET, AccountMergeResult
from ..mongo_store import MongoStore
from ..redis_bus import RedisBus
from ..rpc.mindustry_rpc import MindustryRpcClient
from ..settings import Settings
from .rating_service import RatingService


class PlayerService:
    def __init__(
        self,
        store: MongoStore,
        bus: RedisBus,
        ratings: RatingService | None = None,
        settings: Settings | None = None,
        rpc: MindustryRpcClient | None = None,
    ) -> None:
        self._store = store
        self._bus = bus
        self._ratings = ratings
        self._settings = settings
        self._rpc = rpc

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
        result = await self._store.merge_player_accounts(
            source_pid=source_pid,
            target_pid=target_pid,
            actor_name=actor_name,
            actor_discord_id=actor_discord_id,
            reason=reason,
            keep_pid=keep_pid,
        )
        kick_error: Exception | None = None
        if result.success and result.source_before and result.source_before.uuid:
            try:
                await self._bus.publish_kick_banned(
                    uuid_value=result.source_before.uuid,
                    ip=None,
                )
            except Exception as error:  # noqa: BLE001 -- re-raised after the ratings below
                # The accounts are merged already; a kick that could not be sent must not
                # leave the ratings behind unattempted and unqueued.
                kick_error = error
        if result.success and self._ratings is not None:
            outcome = await self._ratings.merge_after_account_merge(result)
            if outcome is not None:
                result = replace(
                    result,
                    ratings_merged=outcome.merged,
                    ratings_pending=outcome.queued,
                    ratings_error=outcome.error,
                )
        if kick_error is not None:
            raise kick_error
        return result

    async def reset_password(self, *, uuid: str) -> bool:
        if self._settings is not None and self._settings.permissions_mode == "roles":
            assert self._rpc is not None
            response = await self._rpc.reset_staff_password(
                server=self._settings.permissions_rpc_server,
                player_uuid=uuid,
                timeout_ms=self._settings.rpc_timeout_ms,
            )
            return response.changed
        updated = await self._store.reset_password(uuid=uuid)
        if updated:
            await self._bus.publish_player_password_reset(uuid_value=uuid)
        return updated
