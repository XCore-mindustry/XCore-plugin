from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable
from datetime import datetime
from typing import TYPE_CHECKING

from discord.abc import Messageable
from redis.exceptions import RedisError

from .cases.model import CaseKind
from .contracts import (
    ChatGlobalV1,
    ChatMessageV1,
    RatingSeasonEndedV1,
    RatingSeasonEndingSoonV1,
    RatingSeasonRescheduledV1,
    RatingSeasonStartedV1,
    ServerHeartbeatV1,
)
from .handlers_seasons import (
    on_season_ended,
    on_season_ending_soon,
    on_season_rescheduled,
    on_season_started,
)
from .moderation_logs import post_ban_log, post_mute_log, post_vote_kick_log
from .player_pids import NO_PID
from .registry import server_registry
from .retry import retry_reconnect_bus
from .utils.mindustry_colors import strip_mindustry_colors

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot
    from .contracts import (
        ModerationBanCreatedV1,
        ModerationMuteCreatedV1,
        ModerationVoteKickCreatedV1,
        PlayerJoinLeaveV1,
        ServerActionV1,
    )
    from .mongo_store import MongoStore
    from .redis_bus import RedisBus


logger = logging.getLogger(__name__)


def _expires_at(expiration) -> datetime | None:
    raw = str(expiration.expiresAt or "").strip() if expiration is not None else ""
    try:
        return datetime.fromisoformat(raw) if raw else None
    except ValueError:
        return None


def _resolve_vote_kick_starter_pid(event: ModerationVoteKickCreatedV1) -> int | None:
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


async def _player_pid_for_uuid(store: MongoStore, uuid: str | None) -> int:
    if not uuid:
        return NO_PID
    if uuid.startswith("legacy:"):
        return NO_PID

    player = await store.find_player_by_uuid(uuid)
    if player is None:
        return NO_PID

    return player.pid


async def _server_channel(
    bot: XCoreDiscordBot, server: str, *, context: str
) -> Messageable | None:
    channel_id = server_registry.get_channel_for_server(server)
    if channel_id is None:
        logger.debug("No channel registered for server=%s (%s)", server, context)
        return None
    return await bot.resolve_channel(channel_id, context=context)


async def consume_game_chat(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: ChatMessageV1) -> None:
        channel = await _server_channel(bot, event.server, context="game chat")
        if channel is None:
            return

        safe_author = strip_mindustry_colors(str(event.authorName).replace("`", ""))
        safe_message = strip_mindustry_colors(str(event.message).replace("`", ""))
        await channel.send(f"`{safe_author}: {safe_message}`")

    await run_consumer_forever(
        bot.bus, "Game chat", bot.bus.consume_game_chat, dispatch
    )


async def consume_global_chat(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: ChatGlobalV1) -> None:
        channel = await _server_channel(bot, event.server, context="global chat")
        if channel is None:
            return

        safe_author = strip_mindustry_colors(str(event.authorName).replace("`", ""))
        safe_message = strip_mindustry_colors(str(event.message).replace("`", ""))
        safe_server = str(event.server).replace("`", "")
        await channel.send(f"`[GLOBAL:{safe_server}] {safe_author}: {safe_message}`")

    await run_consumer_forever(
        bot.bus, "Global chat", bot.bus.consume_global_chat, dispatch
    )


async def consume_server_heartbeats(bot: XCoreDiscordBot) -> None:
    async def dispatch(_event: ServerHeartbeatV1) -> None:
        return None

    await run_consumer_forever(
        bot.bus,
        "Server heartbeat",
        bot.bus.consume_server_heartbeats,
        dispatch,
    )


async def consume_join_leave(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: PlayerJoinLeaveV1) -> None:
        channel = await _server_channel(bot, event.server, context="join/leave")
        if channel is None:
            return

        action = "joined" if event.joined else "left"
        safe_player = str(event.playerName).replace("`", "")
        await channel.send(f"`{safe_player}` {action}")

    await run_consumer_forever(
        bot.bus,
        "Join/leave",
        bot.bus.consume_player_join_leave,
        dispatch,
    )


async def consume_server_actions(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: ServerActionV1) -> None:
        channel = await _server_channel(bot, event.server, context="server action")
        if channel is None:
            return

        safe_message = str(event.message).replace("`", "")
        await channel.send(safe_message)

    await run_consumer_forever(
        bot.bus,
        "Server action",
        bot.bus.consume_server_actions,
        dispatch,
    )


CASE_REPORT_EVENT = "cases.report.submitted"
CASE_BAN_EVENT = "moderation.ban.created"
CASE_MUTE_EVENT = "moderation.mute.created"


def case_reports_configured(bot: XCoreDiscordBot) -> bool:
    service = bot.container.cases
    if not service.settings.cases_reports_enabled:
        return False
    return any(
        flow.kind == CaseKind.REPORT
        and callable(flow.event_hooks.get(CASE_REPORT_EVENT))
        for flow in service.registry.configured(service.settings)
    )


def case_reports_active(bot: XCoreDiscordBot) -> bool:
    if not case_reports_configured(bot):
        return False
    service = bot.container.cases
    return service.storage_available and service.registry.enabled(CaseKind.REPORT)


async def consume_case_reports(bot: XCoreDiscordBot) -> None:
    if not case_reports_configured(bot):
        return
    # Startup validation needs Discord readiness and may disable the flow.
    await bot.container.cases_reconcile_daemon.ready.wait()
    service = bot.container.cases

    async def dispatch(payload) -> None:
        await service.registry.dispatch_event(CASE_REPORT_EVENT, service, payload)

    async def consume(callback) -> None:
        await bot.bus.consume_case_reports(
            callback, is_active=lambda: case_reports_active(bot)
        )

    await run_consumer_forever(bot.bus, "Case report", consume, dispatch)


async def consume_bans(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: ModerationBanCreatedV1) -> None:
        if bot.settings.discord_bans_channel_id:

            async def log() -> None:
                player_id = event.target.playerPid
                if player_id is None:
                    player_id = await _player_pid_for_uuid(
                        bot.store, event.target.playerUuid
                    )
                expire_dt = _expires_at(event.expiration) or bot.store.now_utc()
                # Old events keep the existing lookup/log fallback.
                extra = {"ban_id": event.banId} if event.banId is not None else {}
                sent = await post_ban_log(
                    bot,
                    pid=player_id,
                    name=event.target.playerName,
                    admin_name=event.actor.actorName,
                    admin_discord_id=event.actor.actorDiscordId,
                    reason=event.reason,
                    expire=expire_dt,
                    **extra,
                )
                if sent is False:
                    raise RuntimeError(
                        "Configured ban log channel "
                        f"{bot.settings.discord_bans_channel_id} is unresolved"
                    )

            await bot.bus.run_ban_log_once(event, log)
        service = bot.container.cases
        if service.settings.cases_enabled and service.storage_available:
            # A hook failure reaches Redis retry/DLQ. The completed log marker
            # survives that retry, so auto-resolution can finish without another log.
            await service.registry.dispatch_event(CASE_BAN_EVENT, service, event)

    await run_consumer_forever(bot.bus, "Ban", bot.bus.consume_bans, dispatch)


async def consume_mutes(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: ModerationMuteCreatedV1) -> None:
        if bot.settings.discord_mutes_channel_id:
            player_id = await _player_pid_for_uuid(bot.store, event.target.playerUuid)
            expire_dt = _expires_at(event.expiration) or bot.store.now_utc()
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
        service = bot.container.cases
        if service.settings.cases_enabled and service.storage_available:
            try:
                await service.registry.dispatch_event(CASE_MUTE_EVENT, service, event)
            except Exception:
                # Not retried: the log above has no delivery marker and would be posted
                # again. The mute is in the audit trail, where the repair task finds it.
                logger.exception("Case hooks failed for a mute event")

    await run_consumer_forever(bot.bus, "Mute", bot.bus.consume_mutes, dispatch)


async def consume_vote_kicks(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: ModerationVoteKickCreatedV1) -> None:
        if not bot.settings.discord_votekicks_channel_id:
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
        bot.bus, "Vote-kick", bot.bus.consume_vote_kicks, dispatch
    )


async def consume_season_started(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: RatingSeasonStartedV1) -> None:
        await on_season_started(bot, event)

    await run_consumer_forever(
        bot.bus, "Season started", bot.bus.consume_season_started, dispatch
    )


async def consume_season_ending_soon(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: RatingSeasonEndingSoonV1) -> None:
        await on_season_ending_soon(bot, event)

    await run_consumer_forever(
        bot.bus, "Season ending soon", bot.bus.consume_season_ending_soon, dispatch
    )


async def consume_season_ended(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: RatingSeasonEndedV1) -> None:
        await on_season_ended(bot, event)

    await run_consumer_forever(
        bot.bus, "Season ended", bot.bus.consume_season_ended, dispatch
    )


async def consume_season_rescheduled(bot: XCoreDiscordBot) -> None:
    async def dispatch(event: RatingSeasonRescheduledV1) -> None:
        await on_season_rescheduled(bot, event)

    await run_consumer_forever(
        bot.bus, "Season rescheduled", bot.bus.consume_season_rescheduled, dispatch
    )


async def run_consumer_forever(
    bus: RedisBus,
    label: str,
    consume: Callable[[Callable[..., Awaitable[None]]], Awaitable[None]],
    callback: Callable[..., Awaitable[None]],
) -> None:
    while True:
        try:
            await consume(callback)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("%s consumer crashed; reconnecting in 2s", label)
            await asyncio.sleep(2)
            try:
                await retry_reconnect_bus(bus.reconnect)
            except (RedisError, OSError) as connect_error:
                logger.warning("%s consumer reconnect failed: %s", label, connect_error)
                await asyncio.sleep(2)
