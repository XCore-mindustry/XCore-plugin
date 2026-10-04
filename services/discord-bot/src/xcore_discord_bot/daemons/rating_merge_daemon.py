from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING

import discord

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot
    from ..services.rating_service import RatingService, RefusedMerge

logger = logging.getLogger(__name__)

RETRY_INTERVAL_SECONDS = 60


class RatingMergeDaemon:
    """Delivers rating merges that could not reach a game server when the accounts merged."""

    def __init__(
        self,
        bot: XCoreDiscordBot,
        ratings: RatingService,
        *,
        interval_seconds: float = RETRY_INTERVAL_SECONDS,
    ) -> None:
        self._bot = bot
        self._ratings = ratings
        self._interval = interval_seconds
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(self._run_loop(), name="rating_merge_daemon")

    def stop(self) -> None:
        if self._task and not self._task.done():
            self._task.cancel()

    async def _run_loop(self) -> None:
        await self._bot.wait_until_ready()
        while not self._bot.is_closed():
            try:
                await self.run_once()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Failed to retry queued rating merges")
            await asyncio.sleep(self._interval)

    async def run_once(self) -> None:
        report = await self._ratings.retry_pending_merges()
        if report.delivered:
            logger.info("Delivered %s queued rating merge(s)", report.delivered)
        for refused in report.refused:
            await self._report_refusal(refused)

    async def _report_refusal(self, refused: RefusedMerge) -> None:
        """Tells the administrators: they were promised a retry that will not happen."""
        channel_id = self._bot.private_channel_id
        if not channel_id:
            return
        try:
            channel = await self._bot._resolve_messageable_channel(
                channel_id, context="rating merge refusal"
            )
            if channel is None:
                return
            await channel.send(
                f"⚠️ The queued rating merge `{refused.source_uuid}` → `{refused.target_uuid}` "
                f"was refused by the server and will not be retried: {refused.error}\n"
                "The ratings are still on the closed account and have to be moved by hand.",
                allowed_mentions=discord.AllowedMentions.none(),
            )
        except Exception:
            logger.exception(
                "Failed to report the refused rating merge %s -> %s",
                refused.source_uuid,
                refused.target_uuid,
            )
