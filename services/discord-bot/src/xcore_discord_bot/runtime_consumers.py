from __future__ import annotations

import asyncio
import logging
import secrets
from collections.abc import Awaitable, Callable
from typing import TYPE_CHECKING

from .contracts import EventType, ServerHeartbeatEvent
from .handlers_moderation import post_ban_log, post_mute_log
from .moderation_views import AdminRequestView
from .registry import server_registry
from .retry import retry_reconnect_bus
from .service_protocols import ConsumerRecoveryService, PlayerLookupService

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot
    from .contracts import (
        BanEvent,
        GameChatMessage,
        GlobalChatEvent,
        MuteEvent,
        PlayerJoinLeaveEvent,
        RawEvent,
        ServerActionEvent,
        ServerHeartbeatEvent,
    )


logger = logging.getLogger(__name__)


async def _player_nickname_for_pid(store: PlayerLookupService, pid: int) -> str:
    player = await store.find_player_by_pid(pid)
    if player is None:
        return "Unknown"

    nickname = str(player.nickname).strip()
    return nickname or "Unknown"


async def _player_pid_for_uuid(store: PlayerLookupService, uuid: str | None) -> int:
    if not uuid:
        return -1

    player = await store.find_player_by_uuid(uuid)
    if player is None:
        return -1

    return player.pid


async def consume_game_chat(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: GameChatMessage) -> None:
        channel_id = bot._channel_id_for_server(event.server, context="game chat")
        if channel_id is None:
            return

        channel = await bot._resolve_messageable_channel(
            channel_id, context="game chat"
        )
        if channel is None:
            return

        safe_author = str(event.author_name).replace("`", "")
        safe_message = str(event.message).replace("`", "")
        await channel.send(f"`{safe_author}: {safe_message}`")

    await run_consumer_forever(bot, "Game chat", bot.consume_game_chat_events, dispatch)


async def consume_global_chat(bot: "XCoreDiscordBot") -> None:
    from .bot import strip_mindustry_colors

    async def dispatch(event: GlobalChatEvent) -> None:
        channel_id = bot._channel_id_for_server(event.server, context="global chat")
        if channel_id is None:
            return

        channel = await bot._resolve_messageable_channel(
            channel_id, context="global chat"
        )
        if channel is None:
            return

        safe_author = strip_mindustry_colors(str(event.author_name).replace("`", ""))
        safe_message = strip_mindustry_colors(str(event.message).replace("`", ""))
        safe_server = str(event.server).replace("`", "")
        await channel.send(f"`[GLOBAL:{safe_server}] {safe_author}: {safe_message}`")

    await run_consumer_forever(
        bot, "Global chat", bot.consume_global_chat_events, dispatch
    )


async def consume_raw_events(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: RawEvent) -> None:
        if event.event_type in {
            EventType.HEARTBEAT,
            "org.xcore.plugin.event.SocketEvents$ServerHeartbeatEvent",
            "event.serverheartbeatevent",
        }:
            heartbeat = ServerHeartbeatEvent.from_payload(event.payload)
            server_registry.update_server(
                heartbeat.server_name,
                heartbeat.discord_channel_id,
                heartbeat.players,
                heartbeat.max_players,
                heartbeat.version,
                heartbeat.host,
                heartbeat.port,
            )
            return
        logger.warning(
            "Unhandled raw event received: type=%s payload=%s",
            event.event_type,
            event.payload,
        )

    await run_consumer_forever(
        bot, "Raw event", bot.consume_raw_events_stream, dispatch
    )


async def consume_admin_requests(bot: "XCoreDiscordBot") -> None:
    async def claim_idempotency_for_view(key: str, ttl_seconds: int) -> bool:
        return await bot.claim_idempotency(key, ttl_seconds=ttl_seconds)

    async def dispatch(pid: int, server: str) -> None:
        nickname = await _player_nickname_for_pid(bot, pid)
        request_nonce = secrets.token_hex(6)

        channel = await bot._resolve_messageable_channel(
            bot.private_channel_id,
            context="admin requests",
        )
        if channel is None:
            return

        view = AdminRequestView(
            settings=bot.settings,
            server=server,
            pid=pid,
            request_nonce=request_nonce,
            find_player_by_pid=bot.find_player_by_pid,
            claim_idempotency=claim_idempotency_for_view,
            mark_admin_confirmed=bot.mark_admin_confirmed,
            publish_admin_confirm=bot.publish_admin_confirm,
            finalize_message=bot._finalize_admin_request_message,
        )
        message = await channel.send(
            f"Admin request: **{nickname}** (`pid={pid}`) on `{server}`",
            view=view,
        )
        view.message = message

    await run_consumer_forever(
        bot, "Admin request", bot.consume_admin_requests_stream, dispatch
    )


async def consume_server_heartbeats(bot: "XCoreDiscordBot") -> None:
    async def dispatch(_event: ServerHeartbeatEvent) -> None:
        return None

    await run_consumer_forever(
        bot,
        "Server heartbeat",
        bot.consume_server_heartbeats_stream,
        dispatch,
    )


async def consume_join_leave(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: PlayerJoinLeaveEvent) -> None:
        channel_id = bot._channel_id_for_server(event.server, context="join/leave")
        if channel_id is None:
            return

        channel = await bot._resolve_messageable_channel(
            channel_id, context="join/leave"
        )
        if channel is None:
            return

        action = "joined" if event.joined else "left"
        safe_player = str(event.player_name).replace("`", "")
        await channel.send(f"`{safe_player}` {action}")

    await run_consumer_forever(
        bot,
        "Join/leave",
        bot.consume_player_join_leave_events,
        dispatch,
    )


async def consume_server_actions(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: ServerActionEvent) -> None:
        channel_id = bot._channel_id_for_server(event.server, context="server action")
        if channel_id is None:
            return

        channel = await bot._resolve_messageable_channel(
            channel_id, context="server action"
        )
        if channel is None:
            return

        safe_message = str(event.message).replace("`", "")
        await channel.send(safe_message)

    await run_consumer_forever(
        bot,
        "Server action",
        bot.consume_server_actions_events,
        dispatch,
    )


async def consume_bans(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: BanEvent) -> None:
        if not bot.bans_channel_id:
            return

        player_id = await _player_pid_for_uuid(bot, event.uuid)

        expire_dt = bot._parse_iso_datetime(event.expire_date) or await bot.now_utc()
        await post_ban_log(
            bot,
            pid=event.pid if event.pid is not None else player_id,
            name=event.name,
            admin_name=event.admin_name,
            reason=event.reason,
            expire=expire_dt,
        )

    await run_consumer_forever(bot, "Ban", bot.consume_bans_stream, dispatch)


async def consume_mutes(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: MuteEvent) -> None:
        if not bot.mutes_channel_id:
            return

        player_id = await _player_pid_for_uuid(bot, event.uuid)

        expire_dt = bot._parse_iso_datetime(event.expire_date) or await bot.now_utc()
        await post_mute_log(
            bot,
            pid=event.pid if event.pid is not None else player_id,
            name=event.name,
            admin_name=event.admin_name,
            reason=event.reason,
            expire=expire_dt,
        )

    await run_consumer_forever(bot, "Mute", bot.consume_mutes_stream, dispatch)


async def run_consumer_forever(
    bot: ConsumerRecoveryService,
    label: str,
    consume: Callable[[Callable[..., Awaitable[None]]], Awaitable[None]],
    callback: Callable[..., Awaitable[None]],
) -> None:
    while True:
        try:
            await consume(callback)
        except asyncio.CancelledError:
            raise
        except Exception as error:
            logger.exception(
                "%s consumer crashed; reconnecting in 2s: %s", label, error
            )
            await asyncio.sleep(2)
            try:
                await retry_reconnect_bus(bot.reconnect_bus)
            except Exception as connect_error:
                logger.warning("%s consumer reconnect failed: %s", label, connect_error)
                await asyncio.sleep(2)
