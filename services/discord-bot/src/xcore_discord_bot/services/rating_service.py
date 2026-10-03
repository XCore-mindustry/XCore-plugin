from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timedelta

from xcore_protocol.generated.rating import (
    RatingSeasonRescheduleRequestV1Operation,
    RatingSeasonRescheduleResponseV1,
)

from ..dto import AccountMergeResult
from ..redis_bus import RpcRejected
from ..rating_store import RatingStore
from ..rpc.mindustry_rpc import MindustryRpcClient

logger = logging.getLogger(__name__)

Operation = RatingSeasonRescheduleRequestV1Operation


@dataclass(frozen=True, slots=True)
class RatingMergeOutcome:
    merged: bool
    queued: bool = False
    error: str | None = None


class RatingService:
    """What the bot asks of the game servers about ratings: season administration and
    carrying a player's standings over when two accounts are merged.

    The bot never writes seasons or standings itself; the servers own them.
    """

    def __init__(
        self, *, store: RatingStore, rpc: MindustryRpcClient, timeout_ms: int
    ) -> None:
        self._store = store
        self._rpc = rpc
        self._timeout_ms = timeout_ms

    # ------------------------------------------------------------ season admin

    async def extend_season(
        self,
        *,
        ladder: str,
        by: timedelta,
        discord_id: str,
        actor_name: str,
        reason: str | None,
    ) -> RatingSeasonRescheduleResponseV1:
        return await self._reschedule(
            ladder,
            Operation.EXTEND,
            discord_id,
            actor_name,
            reason,
            extend_seconds=int(by.total_seconds()),
        )

    async def set_season_end(
        self,
        *,
        ladder: str,
        ends_at: datetime,
        discord_id: str,
        actor_name: str,
        reason: str | None,
    ) -> RatingSeasonRescheduleResponseV1:
        return await self._reschedule(
            ladder,
            Operation.SET_END,
            discord_id,
            actor_name,
            reason,
            ends_at=ends_at.isoformat(),
        )

    async def end_season_now(
        self,
        *,
        ladder: str,
        discord_id: str,
        actor_name: str,
        reason: str | None,
    ) -> RatingSeasonRescheduleResponseV1:
        return await self._reschedule(
            ladder, Operation.END_NOW, discord_id, actor_name, reason
        )

    async def _reschedule(
        self,
        ladder: str,
        operation: Operation,
        discord_id: str,
        actor_name: str,
        reason: str | None,
        *,
        extend_seconds: int | None = None,
        ends_at: str | None = None,
    ) -> RatingSeasonRescheduleResponseV1:
        return await self._rpc.reschedule_season(
            ladder=ladder,
            operation=operation,
            discord_id=discord_id,
            actor_name=actor_name,
            timeout_ms=self._timeout_ms,
            extend_seconds=extend_seconds,
            ends_at=ends_at,
            reason=reason,
        )

    # ----------------------------------------------------------- account merge

    async def merge_after_account_merge(
        self, result: AccountMergeResult
    ) -> RatingMergeOutcome | None:
        """Moves the closed account's standings to the surviving one.

        The account merge is already done, so a failure here must not undo it: when the
        servers cannot be reached the merge is queued and retried; when they refuse, it
        is only reported.
        """
        source = result.source_before.uuid if result.source_before else None
        target_record = result.target_after or result.target_before
        target = target_record.uuid if target_record else None
        if not source or not target or source == target:
            return None
        return await self.merge_standings(source, target)

    async def merge_standings(
        self, source_uuid: str, target_uuid: str
    ) -> RatingMergeOutcome:
        try:
            await self._rpc.merge_ratings(
                source_uuid=source_uuid,
                target_uuid=target_uuid,
                timeout_ms=self._timeout_ms,
            )
        except RpcRejected as error:
            logger.error(
                "Rating merge %s -> %s was refused: %s",
                source_uuid,
                target_uuid,
                error,
            )
            return RatingMergeOutcome(merged=False, error=str(error))
        except Exception as error:
            logger.warning(
                "Rating merge %s -> %s postponed: %s", source_uuid, target_uuid, error
            )
            await self._store.add_pending_merge(source_uuid, target_uuid)
            return RatingMergeOutcome(merged=False, queued=True, error=str(error))
        await self._store.clear_pending_merge(source_uuid, target_uuid)
        return RatingMergeOutcome(merged=True)

    async def retry_pending_merges(self) -> int:
        """Retries the queued merges; returns how many went through."""
        done = 0
        for source, target in await self._store.pending_merges():
            try:
                await self._rpc.merge_ratings(
                    source_uuid=source, target_uuid=target, timeout_ms=self._timeout_ms
                )
            except RpcRejected as error:
                # Retrying cannot change a refusal; drop it and leave it in the log.
                logger.error("Queued rating merge %s -> %s refused: %s", source, target, error)
                await self._store.clear_pending_merge(source, target)
            except Exception as error:
                logger.info("Queued rating merge %s -> %s still waiting: %s", source, target, error)
            else:
                await self._store.clear_pending_merge(source, target)
                done += 1
        return done
