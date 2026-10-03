from __future__ import annotations

import asyncio
import logging
from typing import Any

from .. import runtime_consumers

logger = logging.getLogger(__name__)


class StreamSupervisor:
    """Supervises and manages the lifecycle of Redis stream consumer background tasks."""

    def __init__(self, bot: Any) -> None:
        self._bot = bot
        self._tasks: list[asyncio.Task[None]] = []

    def start(self) -> None:
        if self._tasks:
            return

        consumers = (
            (runtime_consumers.consume_game_chat(self._bot), "redis-chat-consumer"),
            (
                runtime_consumers.consume_global_chat(self._bot),
                "redis-global-chat-consumer",
            ),
            (
                runtime_consumers.consume_join_leave(self._bot),
                "redis-join-leave-consumer",
            ),
            (
                runtime_consumers.consume_server_actions(self._bot),
                "redis-server-action-consumer",
            ),
            (runtime_consumers.consume_bans(self._bot), "redis-ban-consumer"),
            (runtime_consumers.consume_mutes(self._bot), "redis-mute-consumer"),
            (
                runtime_consumers.consume_vote_kicks(self._bot),
                "redis-votekick-consumer",
            ),
            (
                runtime_consumers.consume_season_started(self._bot),
                "redis-season-started-consumer",
            ),
            (
                runtime_consumers.consume_season_ending_soon(self._bot),
                "redis-season-ending-soon-consumer",
            ),
            (
                runtime_consumers.consume_season_ended(self._bot),
                "redis-season-ended-consumer",
            ),
            (
                runtime_consumers.consume_season_rescheduled(self._bot),
                "redis-season-rescheduled-consumer",
            ),
            (
                runtime_consumers.consume_server_heartbeats(self._bot),
                "redis-server-heartbeat-consumer",
            ),
        )

        for coroutine, name in consumers:
            self._tasks.append(asyncio.create_task(coroutine, name=name))

    async def stop(self) -> None:
        for task in self._tasks:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
        self._tasks.clear()
