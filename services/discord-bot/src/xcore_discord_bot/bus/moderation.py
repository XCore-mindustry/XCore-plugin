"""Moderation: ban, mute and vote-kick events, kick and pardon commands, and the once-per-delivery ban log."""

from __future__ import annotations

import asyncio
import hashlib
from collections.abc import Awaitable, Callable

from ..contracts import (
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
    ModerationVoteKickCreatedV1,
    parse_ban_payload,
    parse_mute_payload,
    parse_vote_kick_payload,
)
from ..protocol_outbound import (
    build_moderation_kick_banned_command,
    build_moderation_pardon_command,
)
from ..registry import LiveServerRegistry
from .transport import RedisTransport


class ModerationBus:
    def __init__(self, transport: RedisTransport, servers: LiveServerRegistry) -> None:
        self._transport = transport
        # Commands for every server go to the ones that sent a heartbeat lately.
        self._servers = servers
        self._ban_log_lock = asyncio.Lock()

    async def consume_bans(
        self, callback: Callable[[ModerationBanCreatedV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:moderation:ban",
            group_suffix="discord-ban",
            parse_payload=parse_ban_payload,
            callback=callback,
        )

    async def consume_mutes(
        self, callback: Callable[[ModerationMuteCreatedV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:moderation:mute",
            group_suffix="discord-mute",
            parse_payload=parse_mute_payload,
            callback=callback,
        )

    async def consume_vote_kicks(
        self, callback: Callable[[ModerationVoteKickCreatedV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:moderation:votekick",
            group_suffix="discord-votekick",
            parse_payload=parse_vote_kick_payload,
            callback=callback,
        )

    async def publish_kick_banned(self, uuid_value: str, ip: str | None) -> None:
        await self._transport.publish_to_all_servers(
            servers=self._servers.names(),
            stream_prefix="xcore:cmd:kick-banned",
            event_type="moderation.kick-banned.command",
            ttl_ms=120_000,
            idempotency_prefix="moderation.kick_banned",
            payload_builder=lambda server: build_moderation_kick_banned_command(
                uuid_value=uuid_value,
                ip=ip,
                server=server,
            ).to_payload(),
        )

    async def publish_pardon_player(self, uuid_value: str) -> None:
        await self._transport.publish_to_all_servers(
            servers=self._servers.names(),
            stream_prefix="xcore:cmd:pardon-player",
            event_type="moderation.pardon.command",
            ttl_ms=120_000,
            idempotency_prefix="moderation.pardon",
            payload_builder=lambda server: build_moderation_pardon_command(
                uuid_value=uuid_value,
                server=server,
            ).to_payload(),
        )

    async def run_ban_log_once(
        self,
        event: ModerationBanCreatedV1,
        callback: Callable[[], Awaitable[None]],
    ) -> None:
        """Checkpoint only the log for this stream delivery, not the case callback.

        Mark only after success so a failed Discord send is retryable. As with stream
        ACKs, a process death between sending and recording completion can redeliver.
        Direct calls without stream metadata have no checkpoint: payload equality
        cannot distinguish redelivery from independent legacy bans.
        """
        delivery = self._transport.current_delivery()
        if delivery is None or delivery[0] is not event:
            await callback()
            return
        digest = hashlib.sha256(delivery[1].encode()).hexdigest()
        key = f"ban-event-log:{digest}"
        async with self._ban_log_lock:
            if await self._transport.redis.exists(f"xcore:idmp:{key}"):
                return
            await callback()
            await self._transport.claim_idempotency(key, ttl_seconds=604_800)
