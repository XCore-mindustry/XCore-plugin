from __future__ import annotations

import asyncio
import logging
import time
from typing import TYPE_CHECKING

import discord
from aiohttp import ClientError
from xcore_protocol.generated.discord import (
    DiscordLinkStatusChangedV1,
    DiscordLinkStatusChangedV1Action,
)

from ..permissions_config import load_discord_permissions
from ..redis_bus import RpcFailed
from ..retry import TRANSIENT_EXCEPTIONS
from ..runtime_consumers import run_consumer_forever

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot
    from ..mongo_store import MongoStore
    from ..rpc.mindustry_rpc import MindustryRpcClient
    from ..settings import Settings

logger = logging.getLogger(__name__)


class StaffSyncDaemon:
    CHECK_INTERVAL_SECONDS = 600
    MIN_REQUEST_INTERVAL_SECONDS = 0.25

    def __init__(
        self,
        bot: XCoreDiscordBot,
        store: MongoStore,
        rpc: MindustryRpcClient,
        settings: Settings,
    ) -> None:
        self._bot = bot
        self._store = store
        self._rpc = rpc
        self._settings = settings
        self._role_ids = load_discord_permissions(
            settings.permissions_config_path, settings.discord_guild_id
        )
        self._tasks: list[asyncio.Task[None]] = []
        self._lock = asyncio.Lock()
        self._next_request_at = 0.0
        self._pass_lock = asyncio.Lock()
        self._member_versions: dict[str, int] = {}

    def start(self) -> None:
        if self._tasks:
            return
        self._tasks = [
            asyncio.create_task(self._run_loop(), name="staff_sync_daemon"),
            asyncio.create_task(
                run_consumer_forever(
                    self._bot,
                    "staff-link-status",
                    self._bot.consume_discord_link_status_changed_stream,
                    self.on_link_status,
                ),
                name="staff_link_status_consumer",
            ),
        ]

    async def stop(self) -> None:
        for task in self._tasks:
            task.cancel()
        await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()

    async def _guild(self) -> discord.Guild:
        guild = self._bot.get_guild(self._settings.discord_guild_id)
        if guild is None:
            guild = await self._bot.fetch_guild(self._settings.discord_guild_id)
        return guild

    async def _check_roles(self, guild: discord.Guild) -> bool:
        roles = await guild.fetch_roles()
        return self._role_ids.issubset({str(role.id) for role in roles})

    def _member_role_ids(self, member: discord.Member) -> tuple[str, ...]:
        # Member.roles resolves IDs through the guild cache and can silently omit
        # roles missing there. _roles contains the actual IDs in the API response.
        return tuple(
            sorted({str(role_id) for role_id in member._roles} & self._role_ids)
        )

    async def _fetch_role_ids(self, discord_id: str) -> tuple[str, ...] | None:
        try:
            guild = await self._guild()
            if not await self._check_roles(guild):
                logger.warning(
                    "Skipping staff sync: a configured Discord role is missing"
                )
                return None
            try:
                member = await guild.fetch_member(int(discord_id))
            except discord.NotFound as error:
                if error.code == 10007:
                    return ()
                raise
            return self._member_role_ids(member)
        except (
            discord.HTTPException,
            discord.RateLimited,
            ClientError,
            *TRANSIENT_EXCEPTIONS,
            ValueError,
        ) as error:
            logger.warning(
                "Skipping staff sync: could not fetch Discord member %s: %s",
                discord_id,
                error,
            )
            return None

    async def _send(
        self, player_uuid: str, discord_id: str, role_ids: tuple[str, ...]
    ) -> None:
        # Called with the request lock held, never the entire scheduled pass.
        delay = self._next_request_at - time.monotonic()
        if delay > 0:
            await asyncio.sleep(delay)
        self._next_request_at = time.monotonic() + self.MIN_REQUEST_INTERVAL_SECONDS
        await self._rpc.sync_staff(
            server=self._settings.permissions_rpc_server,
            player_uuid=player_uuid,
            discord_id=discord_id,
            role_ids=role_ids,
            timeout_ms=self._settings.rpc_timeout_ms,
        )

    async def sync_player(
        self,
        player_uuid: str,
        discord_id: str,
        *,
        removed: bool = False,
    ) -> bool:
        self._member_versions[discord_id] = self._member_versions.get(discord_id, 0) + 1
        if not player_uuid or not discord_id:
            return False
        async with self._lock:
            role_ids = () if removed else await self._fetch_role_ids(discord_id)
            if role_ids is None:
                return False
            await self._send(player_uuid, discord_id, role_ids)
            return True

    async def sync_member(
        self, discord_id: str, *, removed: bool = False
    ) -> dict[str, object]:
        synced = 0
        skipped = 0
        errors: set[str] = set()
        for player in await self._store.find_players_by_discord_id(discord_id):
            try:
                if await self.sync_player(
                    player.uuid or "", discord_id, removed=removed
                ):
                    synced += 1
                else:
                    skipped += 1
            except (RpcFailed, *TRANSIENT_EXCEPTIONS) as error:
                skipped += 1
                code = (
                    error.error_code
                    if isinstance(error, RpcFailed)
                    else "TIMEOUT_OR_CONNECTION"
                )
                if code not in errors:
                    logger.warning(
                        "Staff sync failed for Discord member %s: %s", discord_id, error
                    )
                errors.add(code)
        return {"synced": synced, "skipped_count": skipped, "errors": sorted(errors)}

    async def on_link_status(self, event: DiscordLinkStatusChangedV1) -> None:
        # The event is emitted after the plugin persists the link, so the sync's
        # ownership check sees the confirmed binding (also covers DM linking).
        await self.sync_player(
            event.player.playerUuid,
            event.discord.discordId,
            removed=event.action == DiscordLinkStatusChangedV1Action.UNLINKED,
        )

    async def reconcile(self) -> dict[str, object]:
        if self._pass_lock.locked():
            return {
                "synced": 0,
                "skipped_count": 0,
                "reason": "A game role sync pass is already running.",
            }
        async with self._pass_lock:
            synced = 0
            skipped = 0
            errors: set[str] = set()
            reason = ""
            players = await self._store.find_linked_players()
            versions = dict(self._member_versions)
            try:
                guild = await self._guild()
                if not await self._check_roles(guild):
                    reason = (
                        "A configured Discord role is missing; no accounts were synced."
                    )
                    return {
                        "synced": 0,
                        "skipped_count": len(players),
                        "reason": reason,
                    }
                # Exhaust the REST iterator before sending anything. A failed page
                # must not make absent members look like confirmed departures.
                members = {
                    str(member.id): self._member_role_ids(member)
                    async for member in guild.fetch_members(limit=None)
                }
            except (
                discord.HTTPException,
                discord.ClientException,
                discord.RateLimited,
                ClientError,
                *TRANSIENT_EXCEPTIONS,
            ) as error:
                reason = f"Could not fetch a complete Discord member snapshot: {error}"
                return {"synced": 0, "skipped_count": len(players), "reason": reason}
            finally:
                if reason:
                    logger.info(
                        "Staff sync pass: synced=0 skipped=%s reason=%s",
                        len(players),
                        reason,
                    )

            try:
                for index, player in enumerate(players):
                    discord_id = player.discord_id or ""
                    if not player.uuid or not discord_id:
                        skipped += 1
                        continue
                    try:
                        async with self._lock:
                            # An event newer than the snapshot takes precedence.
                            if self._member_versions.get(discord_id, 0) != versions.get(
                                discord_id, 0
                            ):
                                skipped += 1
                                continue
                            await self._send(
                                player.uuid, discord_id, members.get(discord_id, ())
                            )
                        synced += 1
                    except (RpcFailed, *TRANSIENT_EXCEPTIONS) as error:
                        skipped += 1
                        code = (
                            error.error_code
                            if isinstance(error, RpcFailed)
                            else "TIMEOUT_OR_CONNECTION"
                        )
                        if code not in errors:
                            logger.warning("Staff sync pass RPC failed: %s", error)
                        errors.add(code)
                        if (
                            isinstance(error, RpcFailed)
                            and code == "UNAVAILABLE"
                            and "Roles are switched off" in error.error_message
                        ):
                            skipped += len(players) - index - 1
                            reason = "Roles are switched off on the target server; the pass stopped."
                            break
                    # Yield between accounts even when the RPC/mock finishes immediately.
                    await asyncio.sleep(0)
                return {
                    "synced": synced,
                    "skipped_count": skipped,
                    "errors": sorted(errors),
                    "reason": reason,
                }
            finally:
                logger.info(
                    "Staff sync pass: synced=%s skipped=%s errors=%s reason=%s",
                    synced,
                    skipped,
                    sorted(errors),
                    reason,
                )

    async def _run_loop(self) -> None:
        await self._bot.wait_until_ready()
        delay = self.CHECK_INTERVAL_SECONDS
        while not self._bot.is_closed():
            await asyncio.sleep(delay)
            started_at = time.monotonic()
            try:
                await self.reconcile()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Failed to check linked staff accounts")
            delay = max(
                0, self.CHECK_INTERVAL_SECONDS - (time.monotonic() - started_at)
            )
