from __future__ import annotations

from dataclasses import replace
from typing import Any

from ..dto import MERGE_KEEP_PID_TARGET, AccountMergeResult, PlayerRecord
from ..mongo_store import MongoStore
from ..redis_bus import RedisBus
from .rating_service import RatingService


class PlayerService:
    def __init__(
        self,
        store: MongoStore,
        bus: RedisBus,
        ratings: RatingService | None = None,
    ) -> None:
        self._store = store
        self._bus = bus
        self._ratings = ratings

    async def autocomplete_players(self, current: str) -> list[PlayerRecord]:
        return await self._store.autocomplete_players(current)

    async def count_players_by_name(self, name: str) -> int:
        return await self._store.count_players_by_name(name)

    async def search_players(
        self,
        *,
        query: str,
        limit: int = 10,
    ) -> list[PlayerRecord]:
        return await self._store.search_players(query=query, limit=limit)

    async def find_player_by_pid(self, pid: int) -> PlayerRecord | None:
        return await self._store.find_player_by_pid(pid)

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
            except Exception as error:
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

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        return await self._store.find_player_by_uuid(uuid)

    async def find_players_by_discord_id(self, discord_id: str) -> list[PlayerRecord]:
        return await self._store.find_players_by_discord_id(discord_id)

    async def find_discord_admin_players(self) -> list[PlayerRecord]:
        return await self._store.find_discord_admin_players()

    async def find_discord_link_code(self, code: str) -> dict[str, Any] | None:
        return await self._bus.get_discord_link_code(code)

    async def reset_password(self, *, uuid: str) -> bool:
        updated = await self._store.reset_password(uuid=uuid)
        if updated:
            await self.publish_player_password_reset(uuid_value=uuid)
        return updated

    async def grant_badge(self, *, uuid: str, badge_id: str) -> bool:
        return await self._store.grant_badge(uuid=uuid, badge_id=badge_id)

    async def revoke_badge(self, *, uuid: str, badge_id: str) -> bool:
        return await self._store.revoke_badge(uuid=uuid, badge_id=badge_id)

    async def publish_player_active_badge_changed(
        self,
        *,
        uuid_value: str,
        active_badge: str | None,
    ) -> None:
        await self._bus.publish_player_active_badge_changed(
            uuid_value=uuid_value,
            active_badge=active_badge,
        )

    async def publish_player_badge_inventory_changed(
        self,
        *,
        uuid_value: str,
        active_badge: str | None,
        unlocked_badges: list[str] | tuple[str, ...],
    ) -> None:
        await self._bus.publish_player_badge_inventory_changed(
            uuid_value=uuid_value,
            active_badge=active_badge,
            unlocked_badges=unlocked_badges,
        )

    async def publish_player_password_reset(self, *, uuid_value: str) -> None:
        await self._bus.publish_player_password_reset(uuid_value=uuid_value)

    async def publish_discord_link_confirm(
        self,
        *,
        code: str,
        player_uuid: str,
        player_pid: int,
        player_name: str,
        discord_id: str,
        discord_username: str,
    ) -> None:
        await self._bus.publish_discord_link_confirm(
            code=code,
            player_uuid=player_uuid,
            player_pid=player_pid,
            player_name=player_name,
            discord_id=discord_id,
            discord_username=discord_username,
        )

    async def publish_discord_unlink(
        self,
        *,
        player_uuid: str,
        player_pid: int,
        player_name: str,
        discord_id: str,
        discord_username: str,
        actor_name: str,
        actor_discord_id: str,
    ) -> None:
        await self._bus.publish_discord_unlink(
            player_uuid=player_uuid,
            player_pid=player_pid,
            player_name=player_name,
            discord_id=discord_id,
            discord_username=discord_username,
            actor_name=actor_name,
            actor_discord_id=actor_discord_id,
        )
