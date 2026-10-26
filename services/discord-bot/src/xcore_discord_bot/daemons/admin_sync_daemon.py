from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from typing import TYPE_CHECKING

import discord
from xcore_protocol.generated.shared import ActorRefV1ActorType

if TYPE_CHECKING:
    from ..mongo_store import MongoStore
    from ..redis_bus import RedisBus
    from ..settings import Settings

logger = logging.getLogger(__name__)


# Field order is the order the reconcile report lists them in.
@dataclass(frozen=True, order=True)
class ReconciledAdmin:
    discord_id: str
    pid: int
    nickname: str


@dataclass(frozen=True, order=True)
class SkippedAdmin:
    discord_id: str
    player: str
    reason: str


@dataclass(frozen=True)
class AdminReconcileResult:
    discord_admins: int
    applied: tuple[ReconciledAdmin, ...] = ()
    revoked: tuple[ReconciledAdmin, ...] = ()
    skipped: tuple[SkippedAdmin, ...] = ()
    # The Discord role looked empty while linked admins exist, so nothing was revoked.
    skipped_empty_snapshot: bool = False


class AdminSyncDaemon:
    def __init__(
        self,
        bot: discord.Client,
        store: MongoStore,
        bus: RedisBus,
        settings: Settings,
    ) -> None:
        self._bot = bot
        self._store = store
        self._bus = bus
        self._settings = settings
        self._task: asyncio.Task[None] | None = None

    def start(self) -> None:
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(self._run_loop(), name="admin_sync_daemon")

    def stop(self) -> None:
        if self._task and not self._task.done():
            self._task.cancel()

    async def get_discord_admin_member_ids(self) -> set[str]:
        guild_id = self._settings.discord_guild_id
        if guild_id <= 0:
            raise RuntimeError(
                "DISCORD_GUILD_ID must be configured for admin reconcile"
            )

        guild = self._bot.get_guild(guild_id)
        if guild is None:
            guild = await self._bot.fetch_guild(guild_id)

        role = guild.get_role(self._settings.discord_admin_role_id)
        if role is None:
            raise RuntimeError("Configured admin role was not found in the guild")

        members = getattr(role, "members", None)
        if not members:
            await guild.chunk()
            members = getattr(role, "members", [])

        return {
            str(member.id)
            for member in members
            if getattr(member, "id", None) is not None
        }

    async def reconcile(self) -> AdminReconcileResult:
        discord_admin_ids = await self.get_discord_admin_member_ids()
        linked_admin_players = await self._store.find_discord_admin_players()

        if not discord_admin_ids and linked_admin_players:
            logger.warning(
                "Skipping admin revoke because Discord admin snapshot is empty while %s linked admins exist; this is likely a cache/intents issue.",
                len(linked_admin_players),
            )
            return AdminReconcileResult(discord_admins=0, skipped_empty_snapshot=True)

        applied: list[ReconciledAdmin] = []
        revoked: list[ReconciledAdmin] = []
        skipped: list[SkippedAdmin] = []

        processed_ids: set[str] = set()
        for player in linked_admin_players:
            discord_id = str(player.discord_id or "").strip()
            if not discord_id:
                continue

            processed_ids.add(discord_id)
            uuid_value = str(player.uuid or "").strip()
            if not uuid_value:
                skipped.append(
                    SkippedAdmin(
                        discord_id,
                        player.nickname,
                        "linked admin account is missing UUID",
                    )
                )
                continue

            should_be_admin = discord_id in discord_admin_ids
            if should_be_admin:
                continue

            matched, changed = await self._store.set_admin_access(
                uuid=uuid_value,
                is_admin=False,
                admin_source="NONE",
            )
            if not matched:
                continue
            await self._bus.publish_discord_admin_access_changed(
                player_uuid=uuid_value,
                player_pid=player.pid,
                player_name=player.nickname,
                discord_id=discord_id,
                discord_username=player.discord_username,
                admin=False,
                source_name="NONE",
                source_type=ActorRefV1ActorType.SYSTEM,
                actor_name="system/reconcile",
                actor_discord_id=None,
                actor_type=ActorRefV1ActorType.SYSTEM,
                reason="discord role missing during reconcile",
            )
            if changed:
                revoked.append(ReconciledAdmin(discord_id, player.pid, player.nickname))

        for discord_id in discord_admin_ids:
            if discord_id in processed_ids:
                continue

            players = await self._store.find_players_by_discord_id(discord_id)
            if not players:
                logger.warning(
                    "Skipping admin apply for discord_id=%s during reconcile because there are no linked accounts",
                    discord_id,
                )
                skipped.append(
                    SkippedAdmin(discord_id, "-", "no linked Mindustry accounts")
                )
                continue

            for player in players:
                uuid_value = str(player.uuid or "").strip()
                if not uuid_value:
                    skipped.append(
                        SkippedAdmin(
                            discord_id,
                            player.nickname,
                            "linked account is missing UUID",
                        )
                    )
                    continue

                matched, changed = await self._store.set_admin_access(
                    uuid=uuid_value,
                    is_admin=True,
                    admin_source="DISCORD_ROLE",
                )
                if not matched:
                    continue
                await self._bus.publish_discord_admin_access_changed(
                    player_uuid=uuid_value,
                    player_pid=player.pid,
                    player_name=player.nickname,
                    discord_id=discord_id,
                    discord_username=player.discord_username,
                    admin=True,
                    source_name="DISCORD_ROLE",
                    source_type=ActorRefV1ActorType.DISCORD,
                    actor_name="system/reconcile",
                    actor_discord_id=None,
                    actor_type=ActorRefV1ActorType.SYSTEM,
                    reason="discord role present during reconcile",
                )
                if changed:
                    applied.append(
                        ReconciledAdmin(discord_id, player.pid, player.nickname)
                    )

        return AdminReconcileResult(
            discord_admins=len(discord_admin_ids),
            applied=tuple(sorted(applied)),
            revoked=tuple(sorted(revoked)),
            skipped=tuple(sorted(skipped)),
        )

    async def _run_loop(self) -> None:
        await self._bot.wait_until_ready()
        while not self._bot.is_closed():
            await asyncio.sleep(self._settings.admin_reconcile_interval_seconds)
            try:
                await self.reconcile()
            except asyncio.CancelledError:
                raise
            except Exception:  # a failed pass must not stop the loop
                logger.exception("Failed to reconcile Discord admin access")
