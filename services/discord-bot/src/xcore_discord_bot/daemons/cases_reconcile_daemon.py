from __future__ import annotations

import asyncio
import logging

from ..cases.reconcile import reconcile_cases
from ..cases.startup import initialize_flows

logger = logging.getLogger(__name__)


class CasesReconcileDaemon:
    def __init__(self, bot, service, panel, *, interval_seconds: float = 300) -> None:
        self.bot, self.service, self.panel = bot, service, panel
        self.interval_seconds = interval_seconds
        self._task: asyncio.Task[None] | None = None
        self.ready = asyncio.Event()

    def start(self) -> None:
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(
                self._run_loop(), name="cases_reconcile_daemon"
            )

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            await asyncio.gather(self._task, return_exceptions=True)

    async def run_once(self) -> None:
        await reconcile_cases(self.service)
        await self.panel.refresh()

    async def _run_loop(self) -> None:
        await self.bot.wait_until_ready()
        try:
            await self.service.store.load_active()
        except Exception:
            # Only costs staff one extra press on a form button until the case is read.
            logger.exception("Could not preload the active cases")
        await initialize_flows(self.service)
        self.ready.set()
        while not self.bot.is_closed():
            try:
                await self.run_once()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Cases reconciliation failed")
            await asyncio.sleep(self.interval_seconds)
