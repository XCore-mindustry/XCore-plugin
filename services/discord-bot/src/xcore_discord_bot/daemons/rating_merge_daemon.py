from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING

import discord

if TYPE_CHECKING:
    from ..services.rating_service import RatingService

logger = logging.getLogger(__name__)

RETRY_INTERVAL_SECONDS = 60


class RatingMergeDaemon:
    """Delivers rating merges that could not reach a game server when the accounts merged."""

    def __init__(
        self,
        bot: discord.Client,
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
                done = await self._ratings.retry_pending_merges()
                if done:
                    logger.info("Delivered %s queued rating merge(s)", done)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Failed to retry queued rating merges")
            await asyncio.sleep(self._interval)
