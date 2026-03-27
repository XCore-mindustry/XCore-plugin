from __future__ import annotations

import asyncio
import logging
import re
import secrets
import traceback
from collections.abc import Awaitable, Callable, Mapping
from datetime import datetime, timedelta
from typing import Literal

import discord
from discord import Interaction, app_commands
from discord.abc import Messageable
from discord.ext import commands

from .cogs import AdminCog, InfoCog, LinkingCog, MapsCog
from .dto import BanRecord, MuteRecord, PlayerRecord
from .moderation_modals import StatsBanModal, StatsMuteModal
from .moderation_views import (
    BanConfirmView,
    MapRemoveConfirmView,
    MuteUndoView,
    StatsActionsView,
)
from .mongo_store import MongoStore
from .presentation import build_servers_embed
from .registry import server_registry
from .redis_bus import RedisBus
from . import runtime_consumers
from .server_views import PaginatorView, ServersView
from .settings import Settings

logger = logging.getLogger(__name__)

MSG_PLAYER_NOT_FOUND = "Player not found"
MSG_DUPLICATE_MUTATION = (
    "Duplicate command ignored: this operation was already processed recently."
)
MSG_NO_ACTIVE_BAN = "No active ban found"
MSG_NO_ACTIVE_MUTE = "No active mute found"
MSG_PLAYER_UUID_MISSING = "Player UUID is missing"
PRESENCE_UPDATE_INTERVAL_SECONDS = 30
DISCORD_MODAL_TITLE_MAX = 45

MINDUSTRY_COLOR_NAMES = {
    "clear",
    "black",
    "white",
    "light_gray",
    "gray",
    "dark_gray",
    "light_grey",
    "grey",
    "dark_grey",
    "blue",
    "navy",
    "royal",
    "slate",
    "sky",
    "cyan",
    "teal",
    "green",
    "acid",
    "lime",
    "forest",
    "olive",
    "yellow",
    "gold",
    "goldenrod",
    "orange",
    "brown",
    "tan",
    "brick",
    "red",
    "scarlet",
    "crimson",
    "coral",
    "salmon",
    "pink",
    "magenta",
    "purple",
    "violet",
    "maroon",
    "accent",
}


def parse_duration(token: str, default_unit: str = "d") -> timedelta:
    normalized = token.strip().lower()
    if not normalized:
        raise ValueError("Invalid period format. Use 10m, 1h, 1d, 1w, 1y")

    factors = {
        "s": 1,
        "m": 60,
        "h": 3600,
        "d": 86400,
        "w": 604800,
        "y": 31536000,
    }

    if default_unit not in factors:
        raise ValueError("Invalid default unit")

    if normalized.isdigit():
        return timedelta(seconds=int(normalized) * factors[default_unit])

    total_seconds = 0
    consumed = 0
    for match in re.finditer(r"(\d+)([smhdwy])", normalized):
        start, end = match.span()
        if start != consumed:
            raise ValueError("Invalid period format. Use 10m, 1h, 1d, 1w, 1y")
        value = int(match.group(1))
        unit = match.group(2)
        total_seconds += value * factors[unit]
        consumed = end

    if consumed != len(normalized) or total_seconds <= 0:
        raise ValueError("Invalid period format. Use 10m, 1h, 1d, 1w, 1y")

    return timedelta(seconds=total_seconds)


def strip_mindustry_colors(text: str) -> str:
    out: list[str] = []
    i = 0
    n = len(text)

    while i < n:
        c = text[i]
        if c != "[":
            out.append(c)
            i += 1
            continue

        parsed_len = _parse_color_markup(text, i + 1, n)
        if parsed_len >= 0:
            i += parsed_len + 2
            continue

        out.append(c)
        i += 1

    return "".join(out)


def _parse_color_markup(text: str, start: int, end: int) -> int:
    if start >= end:
        return -1

    ch0 = text[start]
    if ch0 == "#":
        i = start + 1
        while i < end:
            ch = text[i]
            if ch == "]":
                if i < start + 2 or i > start + 9:
                    return -1
                return i - start
            if not (ch.isdigit() or "a" <= ch <= "f" or "A" <= ch <= "F"):
                return -1
            i += 1
        return -1

    if ch0 == "[":
        return -2

    if ch0 == "]":
        return 0

    i = start + 1
    while i < end:
        if text[i] == "]":
            name = text[start:i].lower()
            return i - start if name in MINDUSTRY_COLOR_NAMES else -1
        i += 1

    return -1


StatsActionsView.__name__ = "_StatsActionsView"
BanConfirmView.__name__ = "_BanConfirmView"
MapRemoveConfirmView.__name__ = "_MapRemoveConfirmView"
MuteUndoView.__name__ = "_MuteUndoView"
StatsBanModal.__name__ = "_StatsBanModal"
StatsMuteModal.__name__ = "_StatsMuteModal"
PaginatorView.__name__ = "_PaginatorView"
ServersView.__name__ = "_ServersView"

_StatsActionsView = StatsActionsView
_BanConfirmView = BanConfirmView
_MapRemoveConfirmView = MapRemoveConfirmView
_MuteUndoView = MuteUndoView
_StatsBanModal = StatsBanModal
_StatsMuteModal = StatsMuteModal
_PaginatorView = PaginatorView
_ServersView = ServersView


class XCoreDiscordBot(commands.Bot):
    def __init__(self, settings: Settings, bus: RedisBus, store: MongoStore) -> None:
        intents = discord.Intents.default()
        intents.message_content = True
        intents.members = True
        intents.guild_messages = True
        intents.guilds = True
        super().__init__(
            command_prefix=commands.when_mentioned,
            intents=intents,
            allowed_mentions=discord.AllowedMentions.none(),
        )

        self._settings = settings
        self._bus = bus
        self._store = store
        self._chat_consumer_task: asyncio.Task[None] | None = None
        self._global_chat_consumer_task: asyncio.Task[None] | None = None
        self._raw_consumer_task: asyncio.Task[None] | None = None
        self._join_leave_consumer_task: asyncio.Task[None] | None = None
        self._server_action_consumer_task: asyncio.Task[None] | None = None
        self._ban_consumer_task: asyncio.Task[None] | None = None
        self._mute_consumer_task: asyncio.Task[None] | None = None
        self._votekick_consumer_task: asyncio.Task[None] | None = None
        self._heartbeat_consumer_task: asyncio.Task[None] | None = None
        self._presence_task: asyncio.Task[None] | None = None
        self._admin_reconcile_task: asyncio.Task[None] | None = None
        self._presence_rotation_index = 0
        self._map_cache: dict[str, tuple[float, list[dict[str, str]]]] = {}

    @property
    def settings(self) -> Settings:
        return self._settings

    @property
    def rpc_timeout_ms(self) -> int:
        return self._settings.rpc_timeout_ms

    @property
    def private_channel_id(self) -> int:
        return self._settings.discord_private_channel_id

    @property
    def bans_channel_id(self) -> int:
        return self._settings.discord_bans_channel_id

    @property
    def mutes_channel_id(self) -> int:
        settings = getattr(self, "_settings", None)
        if settings is None:
            return 0
        return settings.discord_mutes_channel_id

    @property
    def votekicks_channel_id(self) -> int:
        settings = getattr(self, "_settings", None)
        if settings is None:
            return 0
        return settings.discord_votekicks_channel_id

    async def autocomplete_players(
        self,
        query: str,
        *,
        limit: int,
    ) -> list[PlayerRecord]:
        return await self._store.autocomplete_players(query, limit=limit)

    async def get_cached_maps(self, server: str) -> list[dict[str, str]]:
        from .handlers_misc import get_cached_maps

        return await get_cached_maps(self, server)

    async def count_players_by_name(self, name: str) -> int:
        return await self._store.count_players_by_name(name)

    async def search_players(
        self,
        name: str,
        *,
        limit: int,
        page: int,
    ) -> list[PlayerRecord]:
        return await self._store.search_players(name, limit=limit, page=page)

    async def count_bans(self, *, name_filter: str | None) -> int:
        return await self._store.count_bans(name_filter=name_filter)

    async def list_bans(
        self,
        *,
        name_filter: str | None,
        limit: int,
        page: int,
    ) -> list[BanRecord]:
        return await self._store.list_bans(
            name_filter=name_filter, limit=limit, page=page
        )

    async def rpc_maps_list(
        self, *, server: str, timeout_ms: int
    ) -> list[dict[str, str]]:
        return await self._bus.rpc_maps_list(server=server, timeout_ms=timeout_ms)

    async def claim_idempotency(self, key: str, *, ttl_seconds: int = 600) -> bool:
        return await self._bus.claim_idempotency(key, ttl_seconds=ttl_seconds)

    async def rpc_remove_map(
        self,
        *,
        server: str,
        file_name: str,
        timeout_ms: int,
    ) -> str:
        return await self._bus.rpc_remove_map(
            server=server,
            file_name=file_name,
            timeout_ms=timeout_ms,
        )

    async def publish_maps_load(
        self,
        *,
        server: str,
        files: list[dict[str, str]],
    ) -> None:
        await self._bus.publish_maps_load(server=server, files=files)

    async def now_utc(self) -> datetime:
        return self._store.now_utc()

    async def upsert_ban(
        self,
        *,
        uuid: str,
        ip: str | None,
        pid: int | None,
        name: str,
        admin_name: str,
        admin_discord_id: str | None,
        reason: str,
        expire_date: datetime,
    ) -> None:
        await self._store.upsert_ban(
            uuid=uuid,
            ip=ip,
            pid=pid,
            name=name,
            admin_name=admin_name,
            admin_discord_id=admin_discord_id,
            reason=reason,
            expire_date=expire_date,
        )

    async def publish_kick_banned(self, *, uuid_value: str, ip: str | None) -> None:
        await self._bus.publish_kick_banned(uuid_value=uuid_value, ip=ip)

    async def find_ban(
        self,
        *,
        uuid: str,
        ip: str | None,
    ) -> BanRecord | None:
        return await self._store.find_ban(uuid=uuid, ip=ip)

    async def delete_ban(self, *, uuid: str, ip: str | None) -> int:
        return await self._store.delete_ban(uuid=uuid, ip=ip)

    async def publish_pardon_player(self, *, uuid_value: str) -> None:
        await self._bus.publish_pardon_player(uuid_value=uuid_value)

    async def upsert_mute(
        self,
        *,
        uuid: str,
        pid: int | None,
        name: str,
        admin_name: str,
        admin_discord_id: str | None,
        reason: str,
        expire_date: datetime,
    ) -> None:
        await self._store.upsert_mute(
            uuid=uuid,
            pid=pid,
            name=name,
            admin_name=admin_name,
            admin_discord_id=admin_discord_id,
            reason=reason,
            expire_date=expire_date,
        )

    async def delete_mute(self, *, uuid: str) -> int:
        return await self._store.delete_mute(uuid=uuid)

    async def find_mute(self, *, uuid: str) -> MuteRecord | None:
        return await self._store.find_mute(uuid=uuid)

    async def set_admin_access(
        self, *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        return await self._store.set_admin_access(
            uuid=uuid, is_admin=is_admin, admin_source=admin_source
        )

    async def publish_discord_admin_access_changed(
        self,
        *,
        player_uuid: str,
        player_pid: int,
        discord_id: str,
        discord_username: str | None,
        admin: bool,
        admin_source: str,
        requested_by: str,
        reason: str,
    ) -> None:
        await self._bus.publish_discord_admin_access_changed(
            player_uuid=player_uuid,
            player_pid=player_pid,
            discord_id=discord_id,
            discord_username=discord_username,
            admin=admin,
            admin_source=admin_source,
            requested_by=requested_by,
            reason=reason,
        )

    async def reset_password(self, *, uuid: str) -> bool:
        return await self._store.reset_password(uuid=uuid)

    async def grant_badge(self, *, uuid: str, badge_id: str) -> bool:
        return await self._store.grant_badge(uuid=uuid, badge_id=badge_id)

    async def revoke_badge(self, *, uuid: str, badge_id: str) -> bool:
        return await self._store.revoke_badge(uuid=uuid, badge_id=badge_id)

    async def publish_player_active_badge_changed(
        self, *, uuid_value: str, active_badge: str | None
    ) -> None:
        await self._bus.publish_player_active_badge_changed(
            uuid_value=uuid_value,
            active_badge=active_badge,
        )

    async def publish_player_badge_inventory_changed(
        self,
        *,
        uuid_value: str,
        active_badge: str | None,
        unlocked_badges: list[str] | tuple[str, ...],
    ) -> None:
        await self._bus.publish_player_badge_inventory_changed(
            uuid_value=uuid_value,
            active_badge=active_badge,
            unlocked_badges=unlocked_badges,
        )

    async def publish_player_password_reset(self, *, uuid_value: str) -> None:
        await self._bus.publish_player_password_reset(uuid_value=uuid_value)

    async def find_player_by_pid(self, pid: int) -> PlayerRecord | None:
        return await self._store.find_player_by_pid(pid)

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        return await self._store.find_player_by_uuid(uuid)

    async def find_discord_link_code(self, code: str) -> dict[str, object] | None:
        return await self._bus.get_discord_link_code(code)

    async def find_players_by_discord_id(self, discord_id: str) -> list[PlayerRecord]:
        return await self._store.find_players_by_discord_id(discord_id)

    async def find_discord_admin_players(self) -> list[PlayerRecord]:
        return await self._store.find_discord_admin_players()

    async def set_discord_admin_role(
        self,
        *,
        discord_id: str,
        should_have_role: bool,
        reason: str,
    ) -> bool:
        guild_id = self._settings.discord_guild_id
        if guild_id <= 0:
            raise RuntimeError(
                "DISCORD_GUILD_ID must be configured for /admin add/remove"
            )

        guild = self.get_guild(guild_id)
        if guild is None:
            guild = await self.fetch_guild(guild_id)

        member = guild.get_member(int(discord_id))
        if member is None:
            member = await guild.fetch_member(int(discord_id))

        role = guild.get_role(self._settings.discord_admin_role_id)
        if role is None:
            raise RuntimeError("Configured admin role was not found in the guild")

        has_role = any(
            getattr(existing_role, "id", None) == role.id
            for existing_role in member.roles
        )
        if should_have_role:
            if has_role:
                return False
            await member.add_roles(role, reason=reason)
            return True

        if not has_role:
            return False
        await member.remove_roles(role, reason=reason)
        return True

    async def get_discord_admin_member_ids(self) -> set[str]:
        guild_id = self._settings.discord_guild_id
        if guild_id <= 0:
            raise RuntimeError(
                "DISCORD_GUILD_ID must be configured for admin reconcile"
            )

        guild = self.get_guild(guild_id)
        if guild is None:
            guild = await self.fetch_guild(guild_id)

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

    async def reconcile_discord_admin_access(self) -> dict[str, object]:
        discord_admin_ids = await self.get_discord_admin_member_ids()
        linked_admin_players = await self.find_discord_admin_players()

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

            matched, changed = await self.set_admin_access(
                uuid=uuid_value,
                is_admin=False,
                admin_source="NONE",
            )
            if not matched:
                continue
            await self.publish_discord_admin_access_changed(
                player_uuid=uuid_value,
                player_pid=player.pid,
                discord_id=discord_id,
                discord_username=player.discord_username,
                admin=False,
                admin_source="NONE",
                requested_by="system/reconcile",
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

            players = await self.find_players_by_discord_id(discord_id)
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

                matched, changed = await self.set_admin_access(
                    uuid=uuid_value,
                    is_admin=True,
                    admin_source="DISCORD_ROLE",
                )
                if not matched:
                    continue
                await self.publish_discord_admin_access_changed(
                    player_uuid=uuid_value,
                    player_pid=player.pid,
                    discord_id=discord_id,
                    discord_username=player.discord_username,
                    admin=True,
                    admin_source="DISCORD_ROLE",
                    requested_by="system/reconcile",
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
                int(item["pid"]),
                str(item["nickname"]),
            )
        )
        revoked_players.sort(
            key=lambda item: (
                str(item["discord_id"]),
                int(item["pid"]),
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

    async def _admin_reconcile_loop(self) -> None:
        await self.wait_until_ready()
        while not self.is_closed():
            await asyncio.sleep(self._settings.admin_reconcile_interval_seconds)
            try:
                await self.reconcile_discord_admin_access()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Failed to reconcile Discord admin access")

    async def publish_discord_link_confirm(
        self,
        *,
        code: str,
        player_uuid: str,
        player_pid: int,
        discord_id: str,
        discord_username: str,
    ) -> None:
        await self._bus.publish_discord_link_confirm(
            code=code,
            player_uuid=player_uuid,
            player_pid=player_pid,
            discord_id=discord_id,
            discord_username=discord_username,
        )

    async def publish_discord_unlink(
        self,
        *,
        player_uuid: str,
        player_pid: int,
        discord_id: str,
        requested_by: str,
    ) -> None:
        await self._bus.publish_discord_unlink(
            player_uuid=player_uuid,
            player_pid=player_pid,
            discord_id=discord_id,
            requested_by=requested_by,
        )

    async def reconnect_bus(self) -> None:
        await self._bus.reconnect()

    async def consume_game_chat_events(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_game_chat(callback)

    async def consume_global_chat_events(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_global_chat(callback)

    async def consume_raw_events_stream(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_raw_events(callback)

    async def consume_server_heartbeats_stream(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_server_heartbeats(callback)

    async def consume_player_join_leave_events(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_player_join_leave(callback)

    async def consume_server_actions_events(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_server_actions(callback)

    async def consume_bans_stream(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_bans(callback)

    async def consume_mutes_stream(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_mutes(callback)

    async def consume_vote_kicks_stream(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_vote_kicks(callback)

    async def consume_discord_link_status_changed_stream(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_discord_link_status_changed(callback)

    async def setup_hook(self) -> None:
        await self.add_cog(InfoCog(self))
        await self.add_cog(AdminCog(self))
        await self.add_cog(LinkingCog(self))
        await self.add_cog(MapsCog(self))

        @self.tree.error
        async def _on_app_command_error(
            interaction: Interaction,
            error: app_commands.AppCommandError,
        ) -> None:
            await self._handle_app_command_error(interaction, error)

        await self._bus.connect()
        await self._store.connect()
        self._chat_consumer_task = asyncio.create_task(
            runtime_consumers.consume_game_chat(self), name="redis-chat-consumer"
        )
        self._global_chat_consumer_task = asyncio.create_task(
            runtime_consumers.consume_global_chat(self),
            name="redis-global-chat-consumer",
        )
        self._raw_consumer_task = asyncio.create_task(
            runtime_consumers.consume_raw_events(self), name="redis-raw-consumer"
        )
        self._join_leave_consumer_task = asyncio.create_task(
            runtime_consumers.consume_join_leave(self),
            name="redis-join-leave-consumer",
        )
        self._server_action_consumer_task = asyncio.create_task(
            runtime_consumers.consume_server_actions(self),
            name="redis-server-action-consumer",
        )
        self._ban_consumer_task = asyncio.create_task(
            runtime_consumers.consume_bans(self), name="redis-ban-consumer"
        )
        self._mute_consumer_task = asyncio.create_task(
            runtime_consumers.consume_mutes(self), name="redis-mute-consumer"
        )
        self._votekick_consumer_task = asyncio.create_task(
            runtime_consumers.consume_vote_kicks(self), name="redis-votekick-consumer"
        )
        self._heartbeat_consumer_task = asyncio.create_task(
            runtime_consumers.consume_server_heartbeats(self),
            name="redis-server-heartbeat-consumer",
        )
        self._admin_reconcile_task = asyncio.create_task(
            self._admin_reconcile_loop(),
            name="discord-admin-reconcile",
        )
        self._presence_task = asyncio.create_task(
            self._update_presence_loop(), name="discord-presence-updater"
        )

        if self._settings.discord_guild_id:
            guild_obj = discord.Object(id=self._settings.discord_guild_id)
            self.tree.copy_global_to(guild=guild_obj)
            await self.tree.sync(guild=guild_obj)
        else:
            await self.tree.sync()

    async def on_ready(self) -> None:
        logger.info("Discord bot connected as %s", self.user)
        await self._update_presence_once()
        try:
            result = await self.reconcile_discord_admin_access()
            logger.info(
                "Startup admin reconcile complete: applied=%s revoked=%s discord_admins=%s",
                result["applied"],
                result["revoked"],
                result["discord_admins"],
            )
        except Exception:
            logger.exception("Startup admin reconcile failed")

    async def on_message(self, message: discord.Message) -> None:
        """Used for game chat bridge and DM-based account linking."""
        if message.author.bot:
            return

        if isinstance(message.channel, discord.DMChannel):
            code = message.content.strip().upper()
            if code:
                code_doc = await self.find_discord_link_code(code)
                player = None
                if code_doc is not None:
                    player = await self.find_player_by_uuid(
                        str(
                            code_doc.get("playerUuid")
                            or code_doc.get("player_uuid")
                            or ""
                        )
                    )
                if (
                    player is not None
                    and player.uuid is not None
                    and code_doc is not None
                ):
                    if player.discord_id and player.discord_id != str(
                        message.author.id
                    ):
                        await message.channel.send(
                            "This Mindustry account is already linked to another Discord account."
                        )
                        return

                    await self.publish_discord_link_confirm(
                        code=code,
                        player_uuid=player.uuid,
                        player_pid=player.pid,
                        discord_id=str(message.author.id),
                        discord_username=message.author.display_name,
                    )
                    await message.channel.send(
                        f"Link request sent for `{player.nickname}` (`pid={player.pid}`)."
                    )
                    return

        target_server = server_registry.get_server_for_channel(message.channel.id)
        if target_server is None:
            return

        await self._bus.publish_discord_message(
            server=target_server,
            author_name=message.author.display_name,
            message=message.content,
            source_message_id=str(message.id),
        )

    async def close(self) -> None:
        for task in (
            self._chat_consumer_task,
            self._global_chat_consumer_task,
            self._raw_consumer_task,
            self._join_leave_consumer_task,
            self._server_action_consumer_task,
            self._ban_consumer_task,
            self._mute_consumer_task,
            self._votekick_consumer_task,
            self._heartbeat_consumer_task,
            self._admin_reconcile_task,
            self._presence_task,
        ):
            if task is not None:
                task.cancel()
                try:
                    await task
                except asyncio.CancelledError:
                    pass

        self._chat_consumer_task = None
        self._global_chat_consumer_task = None
        self._raw_consumer_task = None
        self._join_leave_consumer_task = None
        self._server_action_consumer_task = None
        self._ban_consumer_task = None
        self._mute_consumer_task = None
        self._votekick_consumer_task = None
        self._heartbeat_consumer_task = None
        self._admin_reconcile_task = None
        self._presence_task = None

        await self._bus.close()
        await self._store.close()
        await super().close()

    async def _resolve_messageable_channel(
        self,
        channel_id: int,
        *,
        context: str,
    ) -> Messageable | None:
        channel = self.get_channel(channel_id)
        if channel is None:
            try:
                channel = await self.fetch_channel(channel_id)
            except Exception as error:
                logger.warning(
                    "Cannot fetch Discord channel %s for %s: %s",
                    channel_id,
                    context,
                    error,
                )
                return None

        if not isinstance(channel, Messageable):
            return None

        return channel

    def _channel_id_for_server(self, server: str, *, context: str) -> int | None:
        channel_id = server_registry.get_channel_for_server(server)
        if channel_id is None:
            logger.debug("No channel registered for server=%s (%s)", server, context)
        return channel_id

    @staticmethod
    def _get_live_servers():
        return server_registry.get_all_servers()

    @staticmethod
    def _sort_live_servers(servers, mode: Literal["players", "name"]):
        if mode == "name":
            return sorted(servers, key=lambda s: s.name.lower())
        return sorted(servers, key=lambda s: (-s.players, s.name.lower()))

    def _build_servers_embed_for_mode(
        self,
        mode: Literal["players", "name"],
    ) -> discord.Embed:
        servers = self._sort_live_servers(self._get_live_servers(), mode)
        return build_servers_embed(servers, sort_mode=mode)

    def _build_presence_activity(self) -> discord.Activity:
        servers = self._get_live_servers()
        if not servers:
            return discord.Activity(
                type=discord.ActivityType.watching,
                name="silence on servers...",
            )

        total_players = sum(server.players for server in servers)
        server_count = len(servers)
        templates: tuple[tuple[discord.ActivityType, str], ...] = (
            (discord.ActivityType.watching, "{players} players on XCore"),
            (discord.ActivityType.playing, "Mindustry | {servers} servers"),
        )
        activity_type, template = templates[
            self._presence_rotation_index % len(templates)
        ]
        self._presence_rotation_index += 1
        return discord.Activity(
            type=activity_type,
            name=template.format(players=total_players, servers=server_count),
        )

    async def _update_presence_once(self) -> None:
        await self.change_presence(activity=self._build_presence_activity())

    async def _update_presence_loop(self) -> None:
        await self.wait_until_ready()
        while not self.is_closed():
            await asyncio.sleep(PRESENCE_UPDATE_INTERVAL_SECONDS)
            try:
                await self._update_presence_once()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Failed to update Discord presence")

    @staticmethod
    def _disabled_interaction_buttons_view(
        interaction: Interaction,
    ) -> discord.ui.View | None:
        if interaction.message is None:
            return None

        try:
            view = discord.ui.View.from_message(interaction.message)
        except Exception:
            return None

        changed = False
        for item in view.children:
            if isinstance(item, discord.ui.Button) and not item.disabled:
                item.disabled = True
                changed = True

        return view if changed else view

    # ── helpers ──────────────────────────────────────────────────────────────

    async def _send_paginated(
        self,
        interaction: Interaction,
        fetch_page: Callable[[int], Awaitable[tuple[discord.Embed, bool]]],
        *,
        ephemeral: bool = False,
        allowed_mentions: discord.AllowedMentions | None = None,
    ) -> None:
        """Send an embed with Prev/Next pagination buttons via an Interaction."""
        page = 0
        embed, has_next = await fetch_page(page)
        view = _PaginatorView(
            page=page, has_prev=False, has_next=has_next, fetch_page=fetch_page
        )
        if allowed_mentions is None:
            await interaction.response.send_message(
                embed=embed,
                view=view,
                ephemeral=ephemeral,
            )
        else:
            await interaction.response.send_message(
                embed=embed,
                view=view,
                ephemeral=ephemeral,
                allowed_mentions=allowed_mentions,
            )
        view.bot_message = await interaction.original_response()

    async def _handle_app_command_error(
        self,
        interaction: Interaction,
        error: app_commands.AppCommandError,
    ) -> None:
        if isinstance(error, app_commands.CheckFailure):
            message = str(error) or "Missing permissions"
            if interaction.response.is_done():
                await interaction.followup.send(message, ephemeral=True)
            else:
                await interaction.response.send_message(message, ephemeral=True)
            return

        error_id = secrets.token_hex(4)
        logger.exception("Unhandled app command error [ref=%s]: %s", error_id, error)
        message = (
            f"Command failed (ref: {error_id}). "
            "Report this to an admin if the issue persists."
        )
        try:
            if interaction.response.is_done():
                await interaction.followup.send(message, ephemeral=True)
            else:
                await interaction.response.send_message(message, ephemeral=True)
        except discord.HTTPException:
            logger.warning("Failed to send command error message", exc_info=True)

        error_channel_id = self._settings.discord_error_log_channel_id
        if not error_channel_id:
            return

        channel = await self._resolve_messageable_channel(
            error_channel_id,
            context="error logs",
        )
        if channel is None:
            return

        command_text = self._format_interaction_command(interaction)
        user_name = getattr(interaction.user, "display_name", "Unknown")
        user_id = getattr(interaction.user, "id", "unknown")
        tb_text = "".join(
            traceback.format_exception(type(error), error, error.__traceback__)
        )
        if not tb_text.strip():
            tb_text = repr(error)
        if len(tb_text) > 4096:
            tb_text = tb_text[:4093] + "..."

        embed = discord.Embed(
            title="Application Command Error",
            color=discord.Color.red(),
            description=tb_text,
            timestamp=discord.utils.utcnow(),
        )
        embed.add_field(name="Error ID", value=error_id, inline=False)
        embed.add_field(name="Command", value=command_text, inline=False)
        embed.add_field(name="User", value=f"{user_name} (id={user_id})", inline=False)
        try:
            await channel.send(embed=embed)
        except Exception:
            logger.warning("Failed to send error log embed", exc_info=True)

    @staticmethod
    def _format_interaction_command(interaction: Interaction) -> str:
        command_name = "unknown"
        command = getattr(interaction, "command", None)
        if command is not None:
            command_name = getattr(command, "qualified_name", None) or getattr(
                command, "name", "unknown"
            )

        namespace = getattr(interaction, "namespace", None)
        if namespace is None:
            return f"/{command_name}"

        namespace_dict = getattr(namespace, "__dict__", {})
        if not namespace_dict:
            return f"/{command_name}"

        parts: list[str] = []
        for key in sorted(namespace_dict.keys()):
            value = namespace_dict[key]
            if value is None:
                continue
            rendered = str(value).replace("`", "")
            if len(rendered) > 100:
                rendered = f"{rendered[:97]}..."
            parts.append(f"{key}={rendered}")

        if not parts:
            return f"/{command_name}"
        return f"/{command_name} " + " ".join(parts)

    async def _claim_mutation(
        self, interaction: Interaction, *, operation: str, scope: str
    ) -> bool:
        claim_key = f"cmd:{operation}:{interaction.id}:{scope}"
        if await self.claim_idempotency(claim_key, ttl_seconds=600):
            return True

        await interaction.response.send_message(MSG_DUPLICATE_MUTATION, ephemeral=True)
        return False

    async def _reply_player_not_found(self, interaction: Interaction) -> None:
        await interaction.response.send_message(MSG_PLAYER_NOT_FOUND, ephemeral=True)

    async def _get_player_or_reply(
        self, interaction: Interaction, player_id: int
    ) -> PlayerRecord | None:
        player = await self.find_player_by_pid(player_id)
        if player is None:
            await self._reply_player_not_found(interaction)
            return None
        return player

    async def _reply_missing_uuid_for_action(
        self,
        interaction: Interaction,
        *,
        action: str,
    ) -> None:
        await interaction.response.send_message(
            f"Cannot {action}: UUID is missing in player data.",
            ephemeral=True,
        )

    async def _require_player_uuid(
        self,
        interaction: Interaction,
        player: PlayerRecord,
        *,
        action: str,
    ) -> str | None:
        uuid_value, _ip_value = self._player_identifiers(player)
        if uuid_value is None:
            await self._reply_missing_uuid_for_action(interaction, action=action)
            return None
        return uuid_value

    async def _require_player_uuid_or_ip(
        self,
        interaction: Interaction,
        player: PlayerRecord,
        *,
        action: str,
    ) -> tuple[str | None, str | None] | None:
        uuid_value, ip_value = self._player_identifiers(player)
        if uuid_value is None and ip_value is None:
            await interaction.response.send_message(
                f"Cannot {action}: both UUID and IP are missing in player data.",
                ephemeral=True,
            )
            return None
        return uuid_value, ip_value

    @staticmethod
    def _player_name(player: PlayerRecord) -> str:
        return player.nickname

    async def _parse_duration_or_reply(
        self,
        interaction: Interaction,
        period: str,
    ) -> timedelta | None:
        try:
            return parse_duration(period)
        except ValueError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return None

    @staticmethod
    def _doc_value(
        doc: Mapping[str, object] | None,
        key: str,
        *,
        default: str,
    ) -> str:
        return str((doc or {}).get(key) or default)

    def _build_moderation_reversal_embed(
        self,
        *,
        action_label: str,
        subject_name: str,
        player_id: int,
        previous_actor_label: str,
        previous_actor_value: str,
        reason: str,
        expire_value: object,
        actor_label: str,
        actor_name: str,
        format_expire_date,
    ) -> discord.Embed:
        embed = discord.Embed(
            title=f"{action_label} {subject_name}",
            color=discord.Color.green(),
        )
        embed.add_field(name="PID", value=str(player_id), inline=False)
        embed.add_field(
            name=previous_actor_label,
            value=previous_actor_value,
            inline=False,
        )
        embed.add_field(name="Reason", value=reason, inline=False)
        embed.add_field(
            name="Was set to expire",
            value=format_expire_date(expire_value),
            inline=False,
        )
        embed.add_field(name=actor_label, value=actor_name, inline=False)
        return embed

    @staticmethod
    def _player_identifiers(
        player: PlayerRecord,
    ) -> tuple[str | None, str | None]:
        uuid_value = str(player.uuid).strip() if player.uuid is not None else ""

        ip_raw = player.ip or player.last_ip
        ip_value = str(ip_raw).strip() if ip_raw is not None else ""

        uuid_final = uuid_value if uuid_value else None
        ip_final = ip_value if ip_value else None
        return uuid_final, ip_final

    @staticmethod
    def _parse_iso_datetime(raw: str | None) -> datetime | None:
        if raw is None:
            return None
        try:
            normalized = raw.replace("Z", "+00:00")
            return datetime.fromisoformat(normalized)
        except ValueError:
            return None
