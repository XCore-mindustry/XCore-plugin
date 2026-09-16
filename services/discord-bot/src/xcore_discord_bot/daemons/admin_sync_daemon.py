from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING, Any

import discord
from xcore_protocol.generated.shared import ActorRefV1ActorType

if TYPE_CHECKING:
    from ..mongo_store import MongoStore
    from ..redis_bus import RedisBus
    from ..settings import Settings

logger = logging.getLogger(__name__)


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

    async def reconcile(self) -> dict[str, Any]:
        discord_admin_ids = await self.get_discord_admin_member_ids()
        linked_admin_players = await self._store.find_discord_admin_players()

        if not discord_admin_ids and linked_admin_players:
            logger.warning(
                "Skipping admin revoke because Discord admin snapshot is empty while %s linked admins exist; this is likely a cache/intents issue.",
                len(linked_admin_players),
            )
            return {
                "applied": 0,
                "revoked": 0,
                "discord_admins": 0,
                "skipped_empty_snapshot": 1,
                "applied_players": [],
                "revoked_players": [],
                "skipped": [],
            }

        applied = 0
        revoked = 0
        applied_players: list[dict[str, object]] = []
        revoked_players: list[dict[str, object]] = []
        skipped: list[dict[str, str]] = []

        processed_ids: set[str] = set()
        for player in linked_admin_players:
            discord_id = str(player.discord_id or "").strip()
            if not discord_id:
                continue

            processed_ids.add(discord_id)
            uuid_value = str(player.uuid or "").strip()
            if not uuid_value:
                skipped.append(
                    {
                        "discord_id": discord_id,
                        "player": player.nickname,
                        "reason": "linked admin account is missing UUID",
                    }
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
                revoked += 1
                revoked_players.append(
                    {
                        "discord_id": discord_id,
                        "pid": player.pid,
                        "nickname": player.nickname,
                    }
                )

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
                    {
                        "discord_id": discord_id,
                        "player": "-",
                        "reason": "no linked Mindustry accounts",
                    }
                )
                continue

            for player in players:
                uuid_value = str(player.uuid or "").strip()
                if not uuid_value:
                    skipped.append(
                        {
                            "discord_id": discord_id,
                            "player": player.nickname,
                            "reason": "linked account is missing UUID",
                        }
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
                    applied += 1
                    applied_players.append(
                        {
                            "discord_id": discord_id,
                            "pid": player.pid,
                            "nickname": player.nickname,
                        }
                    )

        applied_players.sort(
            key=lambda item: (
                str(item["discord_id"]),
                str(item["pid"]),
                str(item["nickname"]),
            )
        )
        revoked_players.sort(
            key=lambda item: (
                str(item["discord_id"]),
                str(item["pid"]),
                str(item["nickname"]),
            )
        )
        skipped.sort(
            key=lambda item: (
                str(item["discord_id"]),
                str(item["player"]),
                str(item["reason"]),
            )
        )

        return {
            "applied": applied,
            "revoked": revoked,
            "discord_admins": len(discord_admin_ids),
            "skipped_empty_snapshot": 0,
            "applied_players": applied_players,
            "revoked_players": revoked_players,
            "skipped": skipped,
        }

    async def _run_loop(self) -> None:
        await self._bot.wait_until_ready()
        while not self._bot.is_closed():
            await asyncio.sleep(self._settings.admin_reconcile_interval_seconds)
            try:
                await self.reconcile()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Failed to reconcile Discord admin access")
