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

from .cogs import AdminCog, InfoCog, MapsCog
from .dto import BanRecord, MuteRecord, PlayerRecord
from .moderation_modals import StatsBanModal, StatsMuteModal
from .moderation_views import (
    AdminRequestView,
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
AdminRequestView.__name__ = "_AdminRequestView"
StatsBanModal.__name__ = "_StatsBanModal"
StatsMuteModal.__name__ = "_StatsMuteModal"
PaginatorView.__name__ = "_PaginatorView"
ServersView.__name__ = "_ServersView"

_StatsActionsView = StatsActionsView
_BanConfirmView = BanConfirmView
_MapRemoveConfirmView = MapRemoveConfirmView
_MuteUndoView = MuteUndoView
_AdminRequestView = AdminRequestView
_StatsBanModal = StatsBanModal
_StatsMuteModal = StatsMuteModal
_PaginatorView = PaginatorView
_ServersView = ServersView


class XCoreDiscordBot(commands.Bot):
    def __init__(self, settings: Settings, bus: RedisBus, store: MongoStore) -> None:
        intents = discord.Intents.default()
        intents.message_content = True
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
        self._admin_request_task: asyncio.Task[None] | None = None
        self._heartbeat_consumer_task: asyncio.Task[None] | None = None
        self._presence_task: asyncio.Task[None] | None = None
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
        name: str,
        admin_name: str,
        reason: str,
        expire_date: datetime,
    ) -> None:
        await self._store.upsert_ban(
            uuid=uuid,
            ip=ip,
            name=name,
            admin_name=admin_name,
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
        name: str,
        admin_name: str,
        reason: str,
        expire_date: datetime,
    ) -> None:
        await self._store.upsert_mute(
            uuid=uuid,
            name=name,
            admin_name=admin_name,
            reason=reason,
            expire_date=expire_date,
        )

    async def delete_mute(self, *, uuid: str) -> int:
        return await self._store.delete_mute(uuid=uuid)

    async def find_mute(self, *, uuid: str) -> MuteRecord | None:
        return await self._store.find_mute(uuid=uuid)

    async def remove_admin(self, *, uuid: str) -> bool:
        return await self._store.remove_admin(uuid=uuid)

    async def publish_remove_admin(self, *, uuid_value: str) -> None:
        await self._bus.publish_remove_admin(uuid_value=uuid_value)

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

    async def mark_admin_confirmed(self, *, uuid: str) -> None:
        await self._store.mark_admin_confirmed(uuid=uuid)

    async def publish_admin_confirm(self, *, uuid_value: str, server: str) -> None:
        await self._bus.publish_admin_confirm(uuid_value=uuid_value, server=server)

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

    async def consume_admin_requests_stream(
        self, callback: Callable[..., Awaitable[None]]
    ) -> None:
        await self._bus.consume_admin_requests(callback)

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

    async def setup_hook(self) -> None:
        await self.add_cog(InfoCog(self))
        await self.add_cog(AdminCog(self))
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
        self._admin_request_task = asyncio.create_task(
            runtime_consumers.consume_admin_requests(self),
            name="redis-admin-request-consumer",
        )
        self._heartbeat_consumer_task = asyncio.create_task(
            runtime_consumers.consume_server_heartbeats(self),
            name="redis-server-heartbeat-consumer",
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

    async def on_message(self, message: discord.Message) -> None:
        """Only used for the game chat bridge — slash commands handle all admin operations."""
        if message.author.bot:
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
            self._admin_request_task,
            self._heartbeat_consumer_task,
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
        self._admin_request_task = None
        self._heartbeat_consumer_task = None
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

    async def _finalize_admin_request_message(
        self,
        interaction: Interaction,
        status: str,
    ) -> None:
        if interaction.message is None:
            await interaction.response.send_message(status)
            return

        content = interaction.message.content
        if status not in content:
            content = f"{content}\n{status}" if content else status

        view = self._disabled_interaction_buttons_view(interaction)
        await interaction.response.edit_message(content=content, view=view)

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
    ) -> None:
        """Send an embed with Prev/Next pagination buttons via an Interaction."""
        page = 0
        embed, has_next = await fetch_page(page)
        view = _PaginatorView(
            page=page, has_prev=False, has_next=has_next, fetch_page=fetch_page
        )
        await interaction.response.send_message(embed=embed, view=view)
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
