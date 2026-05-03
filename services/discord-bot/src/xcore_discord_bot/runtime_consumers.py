from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable
from typing import TYPE_CHECKING

from .contracts import (
    ChatGlobalV1,
    ChatMessageV1,
    ServerHeartbeatV1,
)
from .handlers_moderation import post_ban_log, post_mute_log, post_vote_kick_log
from .retry import retry_reconnect_bus
from .service_protocols import ConsumerRecoveryService, PlayerLookupService

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot
    from .contracts import (
        ModerationBanCreatedV1,
        ModerationMuteCreatedV1,
        ModerationVoteKickCreatedV1,
        PlayerJoinLeaveV1,
        ServerActionV1,
    )


logger = logging.getLogger(__name__)


def _expiration_value(expiration) -> str | None:
    if expiration is None:
        return None
    expires_at = str(expiration.expiresAt or "").strip()
    return expires_at or None


def _resolve_vote_kick_starter_pid(event: "ModerationVoteKickCreatedV1") -> int | None:
    participants = [*(event.votesFor or ()), *(event.votesAgainst or ())]
    actor_name = event.actor.actorName
    actor_discord_id = str(event.actor.actorDiscordId or "").strip() or None

    for participant in participants:
        participant_discord_id = str(participant.discordId or "").strip() or None
        if actor_discord_id is not None and participant_discord_id == actor_discord_id:
            return participant.playerPid
        if participant.playerName == actor_name:
            return participant.playerPid

    return None


async def _player_pid_for_uuid(store: PlayerLookupService, uuid: str | None) -> int:
    if not uuid:
        return -1
    if uuid.startswith("legacy:"):
        return -1

    player = await store.find_player_by_uuid(uuid)
    if player is None:
        return -1

    return player.pid


async def consume_game_chat(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: ChatMessageV1) -> None:
        channel_id = bot._channel_id_for_server(event.server, context="game chat")
        if channel_id is None:
            return

        channel = await bot._resolve_messageable_channel(
            channel_id, context="game chat"
        )
        if channel is None:
            return

        safe_author = str(event.authorName).replace("`", "")
        safe_message = str(event.message).replace("`", "")
        await channel.send(f"`{safe_author}: {safe_message}`")

    await run_consumer_forever(bot, "Game chat", bot.consume_game_chat_events, dispatch)


async def consume_global_chat(bot: "XCoreDiscordBot") -> None:
    from .bot import strip_mindustry_colors

    async def dispatch(event: ChatGlobalV1) -> None:
        channel_id = bot._channel_id_for_server(event.server, context="global chat")
        if channel_id is None:
            return

        channel = await bot._resolve_messageable_channel(
            channel_id, context="global chat"
        )
        if channel is None:
            return

        safe_author = strip_mindustry_colors(str(event.authorName).replace("`", ""))
        safe_message = strip_mindustry_colors(str(event.message).replace("`", ""))
        safe_server = str(event.server).replace("`", "")
        await channel.send(f"`[GLOBAL:{safe_server}] {safe_author}: {safe_message}`")

    await run_consumer_forever(
        bot, "Global chat", bot.consume_global_chat_events, dispatch
    )


async def consume_server_heartbeats(bot: "XCoreDiscordBot") -> None:
    async def dispatch(_event: ServerHeartbeatV1) -> None:
        return None

    await run_consumer_forever(
        bot,
        "Server heartbeat",
        bot.consume_server_heartbeats_stream,
        dispatch,
    )


async def consume_join_leave(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: PlayerJoinLeaveV1) -> None:
        channel_id = bot._channel_id_for_server(event.server, context="join/leave")
        if channel_id is None:
            return

        channel = await bot._resolve_messageable_channel(
            channel_id, context="join/leave"
        )
        if channel is None:
            return

        action = "joined" if event.joined else "left"
        safe_player = str(event.playerName).replace("`", "")
        await channel.send(f"`{safe_player}` {action}")

    await run_consumer_forever(
        bot,
        "Join/leave",
        bot.consume_player_join_leave_events,
        dispatch,
    )


async def consume_server_actions(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: ServerActionV1) -> None:
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
    async def dispatch(event: ModerationBanCreatedV1) -> None:
        if not bot.bans_channel_id:
            return

        player_id = await _player_pid_for_uuid(bot, event.target.playerUuid)

        expire_dt = (
            bot._parse_iso_datetime(_expiration_value(event.expiration))
            or await bot.now_utc()
        )
        await post_ban_log(
            bot,
            pid=event.target.playerPid
            if event.target.playerPid is not None
            else player_id,
            name=event.target.playerName,
            admin_name=event.actor.actorName,
            admin_discord_id=event.actor.actorDiscordId,
            reason=event.reason,
            expire=expire_dt,
        )

    await run_consumer_forever(bot, "Ban", bot.consume_bans_stream, dispatch)


async def consume_mutes(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: ModerationMuteCreatedV1) -> None:
        if not bot.mutes_channel_id:
            return

        player_id = await _player_pid_for_uuid(bot, event.target.playerUuid)

        expire_dt = (
            bot._parse_iso_datetime(_expiration_value(event.expiration))
            or await bot.now_utc()
        )
        await post_mute_log(
            bot,
            pid=event.target.playerPid
            if event.target.playerPid is not None
            else player_id,
            name=event.target.playerName,
            admin_name=event.actor.actorName,
            admin_discord_id=event.actor.actorDiscordId,
            reason=event.reason,
            expire=expire_dt,
        )

    await run_consumer_forever(bot, "Mute", bot.consume_mutes_stream, dispatch)


async def consume_vote_kicks(bot: "XCoreDiscordBot") -> None:
    async def dispatch(event: ModerationVoteKickCreatedV1) -> None:
        if not bot.votekicks_channel_id:
            return

        await post_vote_kick_log(
            bot,
            target_name=event.target.playerName,
            target_pid=event.target.playerPid,
            starter_name=event.actor.actorName,
            starter_pid=_resolve_vote_kick_starter_pid(event),
            starter_discord_id=event.actor.actorDiscordId,
            reason=event.reason,
            votes_for=list(event.votesFor or ()),
            votes_against=list(event.votesAgainst or ()),
        )

    await run_consumer_forever(
        bot, "Vote-kick", bot.consume_vote_kicks_stream, dispatch
    )


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
