from __future__ import annotations

import asyncio
import hashlib
import hmac
import logging
import re
import secrets
from collections.abc import Awaitable, Callable, Mapping
from datetime import datetime, timedelta, timezone
import discord
from discord import Interaction, app_commands
from discord.abc import Messageable

from .contracts import (
    BanEvent,
    GameChatMessage,
    PlayerJoinLeaveEvent,
    ServerActionEvent,
)
from .contracts import EventType, GlobalChatEvent, RawEvent, ServerHeartbeatEvent
from .mongo_store import MongoStore
from .registry import server_registry
from .redis_bus import RedisBus
from .settings import Settings

logger = logging.getLogger(__name__)

HEXED_RANKS: list[dict[str, str | int]] = [
    {"name": "Newbie", "tag": "", "required": 0},
    {"name": "Regular", "tag": "\uf7e7", "required": 3},
    {"name": "Advanced", "tag": "\uf7ed", "required": 10},
    {"name": "Veteran", "tag": "\uf7ec", "required": 20},
    {"name": "Devastator", "tag": "\uf7c4", "required": 25},
    {"name": "The Legend", "tag": "\uf7c6", "required": 30},
]

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


class _PaginatorView(discord.ui.View):
    """Reusable Prev/Next paginator for embed results."""

    def __init__(
        self,
        *,
        page: int,
        has_prev: bool,
        has_next: bool,
        fetch_page: Callable[[int], Awaitable[tuple[discord.Embed, bool]]],
    ) -> None:
        super().__init__(timeout=120)
        self._page = page
        self._fetch_page = fetch_page
        self.bot_message: discord.Message | None = None
        self._prev_btn.disabled = not has_prev
        self._next_btn.disabled = not has_next

    @discord.ui.button(label="◀ Prev", style=discord.ButtonStyle.secondary)
    async def _prev_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        await self._turn(interaction, self._page - 1)

    @discord.ui.button(label="Next ▶", style=discord.ButtonStyle.secondary)
    async def _next_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        await self._turn(interaction, self._page + 1)

    async def _turn(self, interaction: Interaction, new_page: int) -> None:
        embed, has_next = await self._fetch_page(new_page)
        self._page = new_page
        self._prev_btn.disabled = new_page == 0
        self._next_btn.disabled = not has_next
        await interaction.response.edit_message(embed=embed, view=self)

    async def on_timeout(self) -> None:
        if self.bot_message is not None:
            try:
                for item in self.children:
                    if isinstance(item, discord.ui.Button):
                        item.disabled = True
                await self.bot_message.edit(view=self)
            except Exception:
                pass


class _BanConfirmView(discord.ui.View):
    def __init__(
        self,
        *,
        bot: "XCoreDiscordBot",
        requester_id: int,
        player_id: int,
        player: Mapping[str, object],
        period: str,
        reason: str,
        duration: timedelta,
    ) -> None:
        super().__init__(timeout=120)
        self._bot = bot
        self._requester_id = requester_id
        self._player_id = player_id
        self._player = dict(player)
        self._period = period
        self._reason = reason
        self._duration = duration
        self.message: discord.Message | None = None

    @discord.ui.button(label="Yes", style=discord.ButtonStyle.success)
    async def _confirm(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if interaction.user.id != self._requester_id:
            await interaction.response.send_message(
                "Only the moderator who started this action can confirm it.",
                ephemeral=True,
            )
            return

        result = await self._bot._perform_ban(
            actor_name=interaction.user.display_name,
            player_id=self._player_id,
            period=self._period,
            reason=self._reason,
            duration=self._duration,
            player=self._player,
        )
        self._disable_all()
        await interaction.response.edit_message(content=result, view=self)

    @discord.ui.button(label="No", style=discord.ButtonStyle.danger)
    async def _cancel(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if interaction.user.id != self._requester_id:
            await interaction.response.send_message(
                "Only the moderator who started this action can cancel it.",
                ephemeral=True,
            )
            return

        self._disable_all()
        await interaction.response.edit_message(content="Ban cancelled.", view=self)

    async def on_timeout(self) -> None:
        self._disable_all()
        try:
            if self.message is not None:
                await self.message.edit(view=self)
        except Exception:
            pass

    def _disable_all(self) -> None:
        for item in self.children:
            if isinstance(item, discord.ui.Button):
                item.disabled = True


class _MapRemoveConfirmView(discord.ui.View):
    def __init__(
        self,
        *,
        bot: "XCoreDiscordBot",
        requester_id: int,
        server: str,
        file_name: str,
        request_nonce: str,
    ) -> None:
        super().__init__(timeout=120)
        self._bot = bot
        self._requester_id = requester_id
        self._server = server
        self._file_name = file_name
        self._request_nonce = request_nonce
        self.message: discord.Message | None = None

    @discord.ui.button(label="Yes", style=discord.ButtonStyle.danger)
    async def _confirm(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if interaction.user.id != self._requester_id:
            await interaction.response.send_message(
                "Only the moderator who started this action can confirm it.",
                ephemeral=True,
            )
            return

        await interaction.response.defer()
        result = await self._bot._perform_remove_map(
            server=self._server,
            file_name=self._file_name,
            request_nonce=self._request_nonce,
        )
        self._disable_all()
        if interaction.message is not None:
            await interaction.message.edit(content=result, view=self)

    @discord.ui.button(label="No", style=discord.ButtonStyle.secondary)
    async def _cancel(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if interaction.user.id != self._requester_id:
            await interaction.response.send_message(
                "Only the moderator who started this action can cancel it.",
                ephemeral=True,
            )
            return

        self._disable_all()
        await interaction.response.edit_message(
            content="Map removal cancelled.", view=self
        )

    async def on_timeout(self) -> None:
        self._disable_all()
        if self.message is None:
            return
        try:
            await self.message.edit(view=self)
        except Exception:
            pass

    def _disable_all(self) -> None:
        for item in self.children:
            if isinstance(item, discord.ui.Button):
                item.disabled = True


class XCoreDiscordBot(discord.Client):
    def __init__(self, settings: Settings, bus: RedisBus, store: MongoStore) -> None:
        intents = discord.Intents.default()
        intents.message_content = True
        intents.guild_messages = True
        intents.guilds = True
        super().__init__(intents=intents)

        self._settings = settings
        self._bus = bus
        self._store = store
        self._chat_consumer_task: asyncio.Task[None] | None = None
        self._global_chat_consumer_task: asyncio.Task[None] | None = None
        self._raw_consumer_task: asyncio.Task[None] | None = None
        self._join_leave_consumer_task: asyncio.Task[None] | None = None
        self._server_action_consumer_task: asyncio.Task[None] | None = None
        self._ban_consumer_task: asyncio.Task[None] | None = None
        self._admin_request_task: asyncio.Task[None] | None = None
        self._heartbeat_consumer_task: asyncio.Task[None] | None = None

        self.tree = app_commands.CommandTree(self)
        self._register_commands()

    def _register_commands(self) -> None:
        """Register all slash commands on the tree."""

        guild_obj = (
            discord.Object(id=self._settings.discord_guild_id)
            if self._settings.discord_guild_id
            else None
        )

        @self.tree.command(
            name="stats", description="Show player stats", guild=guild_obj
        )
        @app_commands.describe(player_id="Numeric player ID")
        async def cmd_stats(interaction: Interaction, player_id: int) -> None:
            await self._cmd_stats(interaction, player_id)

        @self.tree.command(
            name="search", description="Search players by name (admin)", guild=guild_obj
        )
        @app_commands.describe(name="Player name to search for")
        async def cmd_search(interaction: Interaction, name: str) -> None:
            await self._cmd_search(interaction, name)

        @self.tree.command(
            name="bans",
            description="List bans, optionally filtered by name (admin)",
            guild=guild_obj,
        )
        @app_commands.describe(name="Optional name filter")
        async def cmd_bans(interaction: Interaction, name: str = "") -> None:
            await self._cmd_bans(interaction, name or None)

        @self.tree.command(
            name="ban", description="Ban a player (admin)", guild=guild_obj
        )
        @app_commands.describe(
            player_id="Numeric player ID",
            period="Duration e.g. 1d, 2w, 1y",
            reason="Ban reason",
        )
        async def cmd_ban(
            interaction: Interaction,
            player_id: int,
            period: str,
            reason: str = "Not Specified",
        ) -> None:
            await self._cmd_ban(interaction, player_id, period, reason)

        @self.tree.command(
            name="unban", description="Unban a player (admin)", guild=guild_obj
        )
        @app_commands.describe(player_id="Numeric player ID")
        async def cmd_unban(interaction: Interaction, player_id: int) -> None:
            await self._cmd_unban(interaction, player_id)

        @self.tree.command(
            name="mute", description="Mute a player (admin)", guild=guild_obj
        )
        @app_commands.describe(
            player_id="Numeric player ID",
            period="Duration e.g. 10m, 1h",
            reason="Mute reason",
        )
        async def cmd_mute(
            interaction: Interaction,
            player_id: int,
            period: str,
            reason: str = "Not Specified",
        ) -> None:
            await self._cmd_mute(interaction, player_id, period, reason)

        @self.tree.command(
            name="unmute", description="Unmute a player (admin)", guild=guild_obj
        )
        @app_commands.describe(player_id="Numeric player ID")
        async def cmd_unmute(interaction: Interaction, player_id: int) -> None:
            await self._cmd_unmute(interaction, player_id)

        @self.tree.command(
            name="remove-admin",
            description="Remove admin from a player (general admin)",
            guild=guild_obj,
        )
        @app_commands.describe(player_id="Numeric player ID")
        async def cmd_remove_admin(interaction: Interaction, player_id: int) -> None:
            await self._cmd_remove_admin(interaction, player_id)

        @self.tree.command(
            name="reset-password",
            description="Reset admin password for a player (general admin)",
            guild=guild_obj,
        )
        @app_commands.describe(player_id="Numeric player ID")
        async def cmd_reset_password(interaction: Interaction, player_id: int) -> None:
            await self._cmd_reset_password(interaction, player_id)

        @self.tree.command(
            name="servers",
            description="Show all live Mindustry servers",
            guild=guild_obj,
        )
        async def cmd_servers(interaction: Interaction) -> None:
            servers = sorted(server_registry.get_all_servers(), key=lambda s: s.name)
            if not servers:
                await interaction.response.send_message(
                    "No live servers connected right now.", ephemeral=True
                )
                return

            embed = discord.Embed(title="Live Servers", color=0x00FF00)
            for s in servers:
                val = f"**Players:** {s.players}/{s.max_players}\n**Version:** {s.version}\n**Channel:** <#{s.channel_id}>"
                embed.add_field(name=s.name, value=val, inline=False)

            await interaction.response.send_message(embed=embed)

        @self.tree.command(
            name="maps", description="List maps on a server", guild=guild_obj
        )
        @app_commands.describe(server="Server name")
        @app_commands.autocomplete(server=self._autocomplete_server_name)
        async def cmd_maps(interaction: Interaction, server: str) -> None:
            await self._cmd_maps(interaction, server)

        @self.tree.command(
            name="remove-map",
            description="Remove a map from a server (map reviewer)",
            guild=guild_obj,
        )
        @app_commands.describe(
            server="Server name", file_name="Map file name (.msav) to remove"
        )
        @app_commands.autocomplete(server=self._autocomplete_server_name)
        async def cmd_remove_map(
            interaction: Interaction, server: str, file_name: str
        ) -> None:
            await self._cmd_remove_map(interaction, server, file_name)

        @self.tree.command(
            name="upload-map",
            description="Upload .msav map files to a server (map reviewer)",
            guild=guild_obj,
        )
        @app_commands.describe(
            server="Server name",
            file1="First .msav file",
            file2="Second .msav file (optional)",
            file3="Third .msav file (optional)",
        )
        @app_commands.autocomplete(server=self._autocomplete_server_name)
        async def cmd_upload_map(
            interaction: Interaction,
            server: str,
            file1: discord.Attachment,
            file2: discord.Attachment | None = None,
            file3: discord.Attachment | None = None,
        ) -> None:
            await self._cmd_upload_map(interaction, server, [file1, file2, file3])

    async def setup_hook(self) -> None:
        await self._bus.connect()
        await self._store.connect()
        self._chat_consumer_task = asyncio.create_task(
            self._consume_game_chat(), name="redis-chat-consumer"
        )
        self._global_chat_consumer_task = asyncio.create_task(
            self._consume_global_chat(), name="redis-global-chat-consumer"
        )
        self._raw_consumer_task = asyncio.create_task(
            self._consume_raw_events(), name="redis-raw-consumer"
        )
        self._join_leave_consumer_task = asyncio.create_task(
            self._consume_join_leave(), name="redis-join-leave-consumer"
        )
        self._server_action_consumer_task = asyncio.create_task(
            self._consume_server_actions(), name="redis-server-action-consumer"
        )
        self._ban_consumer_task = asyncio.create_task(
            self._consume_bans(), name="redis-ban-consumer"
        )
        self._admin_request_task = asyncio.create_task(
            self._consume_admin_requests(), name="redis-admin-request-consumer"
        )
        self._heartbeat_consumer_task = asyncio.create_task(
            self._consume_server_heartbeats(), name="redis-server-heartbeat-consumer"
        )

        if self._settings.discord_guild_id:
            guild_obj = discord.Object(id=self._settings.discord_guild_id)
            self.tree.copy_global_to(guild=guild_obj)
            await self.tree.sync(guild=guild_obj)
        else:
            await self.tree.sync()

    async def on_ready(self) -> None:
        logger.info("Discord bot connected as %s", self.user)

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

    async def on_interaction(self, interaction: Interaction) -> None:
        if interaction.type != discord.InteractionType.component:
            return

        if interaction.data is None:
            return

        custom_id = str(interaction.data.get("custom_id", ""))
        parsed = self._parse_admin_interaction_custom_id(custom_id)
        if parsed is None:
            return
        server, pid, action, request_nonce = parsed

        member = interaction.user
        role_ids = {role.id for role in getattr(member, "roles", [])}
        if self._settings.discord_admin_role_id not in role_ids:
            await interaction.response.send_message(
                "Access denied. Required role: "
                f"{self._role_mention(self._settings.discord_admin_role_id)}",
                ephemeral=True,
            )
            return

        player = await self._store.find_player_by_pid(pid)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        if action == "decline":
            confirmer = getattr(
                interaction.user, "mention", f"`{interaction.user.display_name}`"
            )
            status = (
                f"❌ Declined admin request for `{player.get('nickname', 'Unknown')}` "
                f"on `{server}` by {confirmer}"
            )
            await self._finalize_admin_request_message(interaction, status)
            return

        uuid_value = str(player.get("uuid", "")).strip()
        if not uuid_value:
            await interaction.response.send_message(
                "Player UUID is missing", ephemeral=True
            )
            return

        claim_key = f"admin-confirm:{server}:{uuid_value}:{request_nonce}"
        if not await self._bus.claim_idempotency(claim_key, ttl_seconds=600):
            await interaction.response.send_message(
                "This admin confirmation was already processed.",
                ephemeral=True,
            )
            return

        await self._store.mark_admin_confirmed(uuid=uuid_value)
        await self._bus.publish_admin_confirm(uuid_value=uuid_value, server=server)
        confirmer = getattr(
            interaction.user, "mention", f"`{interaction.user.display_name}`"
        )
        status = (
            f"✅ Confirmed admin request for `{player.get('nickname', 'Unknown')}` "
            f"on `{server}` by {confirmer}"
        )
        await self._finalize_admin_request_message(interaction, status)

    async def close(self) -> None:
        for task in (
            self._chat_consumer_task,
            self._global_chat_consumer_task,
            self._raw_consumer_task,
            self._join_leave_consumer_task,
            self._server_action_consumer_task,
            self._ban_consumer_task,
            self._admin_request_task,
            self._heartbeat_consumer_task,
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
        self._admin_request_task = None
        self._heartbeat_consumer_task = None

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

    async def _autocomplete_server_name(
        self,
        _interaction: Interaction,
        current: str,
    ) -> list[app_commands.Choice[str]]:
        current_norm = current.strip().lower()
        choices: list[app_commands.Choice[str]] = []
        for server in sorted(srv.name for srv in server_registry.get_all_servers()):
            if current_norm and current_norm not in server.lower():
                continue
            choices.append(app_commands.Choice(name=server, value=server))
            if len(choices) >= 25:
                break
        return choices

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

    async def _consume_game_chat(self) -> None:
        async def dispatch(event: GameChatMessage) -> None:
            channel_id = self._channel_id_for_server(event.server, context="game chat")
            if channel_id is None:
                return

            channel = await self._resolve_messageable_channel(
                channel_id,
                context="game chat",
            )
            if channel is None:
                return

            safe_author = str(event.author_name).replace("`", "")
            safe_message = str(event.message).replace("`", "")
            await channel.send(f"`{safe_author}: {safe_message}`")

        await self._run_consumer_forever(
            "Game chat", self._bus.consume_game_chat, dispatch
        )

    async def _consume_global_chat(self) -> None:
        async def dispatch(event: GlobalChatEvent) -> None:
            channel_id = self._channel_id_for_server(
                event.server, context="global chat"
            )
            if channel_id is None:
                return

            channel = await self._resolve_messageable_channel(
                channel_id,
                context="global chat",
            )
            if channel is None:
                return

            safe_author = str(event.author_name).replace("`", "")
            safe_message = str(event.message).replace("`", "")
            safe_server = str(event.server).replace("`", "")
            safe_author = strip_mindustry_colors(safe_author)
            safe_message = strip_mindustry_colors(safe_message)
            await channel.send(
                f"`[GLOBAL:{safe_server}] {safe_author}: {safe_message}`"
            )

        await self._run_consumer_forever(
            "Global chat", self._bus.consume_global_chat, dispatch
        )

    async def _consume_raw_events(self) -> None:
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
                )
                return
            logger.warning(
                "Unhandled raw event received: type=%s payload=%s",
                event.event_type,
                event.payload,
            )

        await self._run_consumer_forever(
            "Raw event", self._bus.consume_raw_events, dispatch
        )

    async def _consume_admin_requests(self) -> None:
        async def dispatch(pid: int, server: str) -> None:
            player = await self._store.find_player_by_pid(pid)
            nickname = player.get("nickname", "Unknown") if player else "Unknown"
            request_nonce = secrets.token_hex(6)

            channel = await self._resolve_messageable_channel(
                self._settings.discord_private_channel_id,
                context="admin requests",
            )
            if channel is None:
                return

            button = discord.ui.Button(
                style=discord.ButtonStyle.success,
                label="Confirm",
                custom_id=self._build_admin_interaction_custom_id(
                    server,
                    pid,
                    "confirm",
                    request_nonce,
                ),
            )
            decline_button = discord.ui.Button(
                style=discord.ButtonStyle.danger,
                label="Decline",
                custom_id=self._build_admin_interaction_custom_id(
                    server,
                    pid,
                    "decline",
                    request_nonce,
                ),
            )
            view = discord.ui.View(timeout=None)
            view.add_item(button)
            view.add_item(decline_button)

            await channel.send(
                f"Admin request: **{nickname}** (`pid={pid}`) on `{server}`",
                view=view,
            )

        await self._run_consumer_forever(
            "Admin request", self._bus.consume_admin_requests, dispatch
        )

    async def _consume_server_heartbeats(self) -> None:
        async def dispatch(_event: ServerHeartbeatEvent) -> None:
            return None

        await self._run_consumer_forever(
            "Server heartbeat",
            self._bus.consume_server_heartbeats,
            dispatch,
        )

    async def _consume_join_leave(self) -> None:
        async def dispatch(event: PlayerJoinLeaveEvent) -> None:
            channel_id = self._channel_id_for_server(event.server, context="join/leave")
            if channel_id is None:
                return

            channel = await self._resolve_messageable_channel(
                channel_id,
                context="join/leave",
            )
            if channel is None:
                return

            action = "joined" if event.joined else "left"
            safe_player = str(event.player_name).replace("`", "")
            await channel.send(f"`{safe_player}` {action}")

        await self._run_consumer_forever(
            "Join/leave", self._bus.consume_player_join_leave, dispatch
        )

    async def _consume_server_actions(self) -> None:
        async def dispatch(event: ServerActionEvent) -> None:
            channel_id = self._channel_id_for_server(
                event.server, context="server action"
            )
            if channel_id is None:
                return

            channel = await self._resolve_messageable_channel(
                channel_id,
                context="server action",
            )
            if channel is None:
                return

            safe_message = str(event.message).replace("`", "")
            await channel.send(safe_message)

        await self._run_consumer_forever(
            "Server action", self._bus.consume_server_actions, dispatch
        )

    async def _consume_bans(self) -> None:
        async def dispatch(event: BanEvent) -> None:
            bans_channel_id = self._settings.discord_bans_channel_id
            if not bans_channel_id:
                return

            player_id = -1
            if event.uuid:
                player = await self._store.find_player_by_uuid(event.uuid)
                if player is not None:
                    player_id = int(player.get("pid", -1))

            expire_dt = self._parse_iso_datetime(event.expire_date)
            if expire_dt is None:
                expire_dt = self._store.now_utc()

            await self._post_ban_log(
                pid=player_id,
                name=event.name,
                admin_name=event.admin_name,
                reason=event.reason,
                expire=expire_dt,
            )

        await self._run_consumer_forever("Ban", self._bus.consume_bans, dispatch)

    async def _run_consumer_forever(
        self,
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
                    "%s consumer crashed; reconnecting in 2s: %s",
                    label,
                    error,
                )

                await asyncio.sleep(2)

                try:
                    await self._bus.reconnect()
                except Exception as connect_error:
                    logger.warning(
                        "%s consumer reconnect failed: %s",
                        label,
                        connect_error,
                    )
                    await asyncio.sleep(2)

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

    async def _require_admin(self, interaction: Interaction) -> bool:
        role_ids = self._member_role_ids(interaction.user)
        if self._settings.discord_admin_role_id in role_ids:
            return True
        await interaction.response.send_message(
            "Missing permissions: required role "
            f"{self._role_mention(self._settings.discord_admin_role_id)}",
            ephemeral=True,
        )
        return False

    async def _require_general_admin(self, interaction: Interaction) -> bool:
        role_ids = self._member_role_ids(interaction.user)
        allowed = {
            self._settings.discord_general_admin_role_id,
            self._settings.discord_admin_role_id,
        }
        if role_ids & allowed:
            return True
        await interaction.response.send_message(
            "Missing permissions: required one of roles "
            f"{self._role_mention(self._settings.discord_general_admin_role_id)} or "
            f"{self._role_mention(self._settings.discord_admin_role_id)}",
            ephemeral=True,
        )
        return False

    async def _require_map_reviewer(self, interaction: Interaction) -> bool:
        role_ids = self._member_role_ids(interaction.user)
        if self._settings.discord_map_reviewer_role_id in role_ids:
            return True
        await interaction.response.send_message(
            "Missing permissions: required role "
            f"{self._role_mention(self._settings.discord_map_reviewer_role_id)}",
            ephemeral=True,
        )
        return False

    async def _claim_mutation(
        self, interaction: Interaction, *, operation: str, scope: str
    ) -> bool:
        claim_key = f"cmd:{operation}:{interaction.id}:{scope}"
        if await self._bus.claim_idempotency(claim_key, ttl_seconds=600):
            return True

        await interaction.response.send_message(
            "Duplicate command ignored: this operation was already processed recently.",
            ephemeral=True,
        )
        return False

    # ── slash command implementations ─────────────────────────────────────────

    async def _cmd_stats(self, interaction: Interaction, player_id: int) -> None:
        if not await self._require_admin(interaction):
            return

        player = await self._store.find_player_by_pid(player_id)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        nickname = str(player.get("nickname", "Unknown"))
        custom_nickname = str(player.get("custom_nickname", "")).strip()
        title = f"Player Stats • {nickname}"
        if custom_nickname:
            title = f"Player Stats • {nickname} ({custom_nickname})"

        rank_label, rank_progress = self._format_hexed_rank_block(
            rank_value=int(player.get("hexed_rank", 0)),
            points=int(player.get("hexed_points", 0)),
        )

        embed = discord.Embed(title=title, color=discord.Color.blurple())
        embed.add_field(
            name="Identity",
            value=(f"PID: `{player.get('pid', -1)}`\nNickname: `{nickname}`"),
            inline=False,
        )
        embed.add_field(
            name="Progress",
            value=(
                f"Playtime: `{self._format_minutes(int(player.get('total_play_time', 0)))}`\n"
                f"PvP rating: `{player.get('pvp_rating', 0)}`\n"
                f"Hexed rank: `{rank_label}`\n"
                f"Hexed progress: `{rank_progress}`"
            ),
            inline=False,
        )

        admin_status = "✅" if bool(player.get("is_admin", False)) else "❌"
        admin_confirmed = "✅" if bool(player.get("admin_confirmed", False)) else "❌"
        embed.add_field(
            name="Permissions",
            value=(f"Admin: {admin_status}\nAdmin confirmed: {admin_confirmed}"),
            inline=False,
        )

        created_at = self._format_epoch_millis(player.get("created_at"))
        updated_at = self._format_epoch_millis(player.get("updated_at"))
        embed.set_footer(text=f"Created: {created_at} • Updated: {updated_at}")

        await interaction.response.send_message(embed=embed)

    async def _cmd_search(self, interaction: Interaction, name: str) -> None:
        if not await self._require_admin(interaction):
            return

        page_size = 6

        async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
            rows = await self._store.search_players(name, limit=page_size, page=page)
            embed = discord.Embed(
                title=f"Search: '{name}'",
                color=discord.Color.green() if rows else discord.Color.red(),
            )
            if rows:
                for row in rows:
                    embed.add_field(
                        name=row.get("nickname", "Unknown"),
                        value=f"ID: {row.get('pid', -1)} | playtime: {row.get('total_play_time', 0)}m",
                        inline=False,
                    )
            else:
                embed.description = "Players not found."
            has_next = len(rows) == page_size
            embed.set_footer(text=f"Page {page + 1} • entries: {len(rows)}")
            return embed, has_next

        await self._send_paginated(interaction, fetch_page)

    async def _cmd_bans(
        self, interaction: Interaction, name_filter: str | None
    ) -> None:
        if not await self._require_admin(interaction):
            return

        page_size = 6

        async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
            bans = await self._store.list_bans(
                name_filter=name_filter, limit=page_size, page=page
            )
            embed = discord.Embed(
                title="Bans",
                color=discord.Color.green() if bans else discord.Color.red(),
            )
            if bans:
                for ban in bans:
                    embed.add_field(
                        name=ban.get("name", "Unknown"),
                        value=f"Admin: {ban.get('admin_name', 'Unknown')} | Reason: {ban.get('reason', 'Not Specified')}",
                        inline=False,
                    )
            else:
                embed.description = "No bans found."
            has_next = len(bans) == page_size
            embed.set_footer(text=f"Page {page + 1} • entries: {len(bans)}")
            return embed, has_next

        await self._send_paginated(interaction, fetch_page)

    async def _cmd_ban(
        self, interaction: Interaction, player_id: int, period: str, reason: str
    ) -> None:
        if not await self._require_admin(interaction):
            return

        try:
            duration = parse_duration(period)
        except ValueError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return

        player = await self._store.find_player_by_pid(player_id)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        uuid_value, ip_value = self._player_identifiers(player)
        if uuid_value is None and ip_value is None:
            await interaction.response.send_message(
                "Cannot ban player: both UUID and IP are missing in player data.",
                ephemeral=True,
            )
            return

        view = _BanConfirmView(
            bot=self,
            requester_id=interaction.user.id,
            player_id=player_id,
            player=player,
            period=period,
            reason=reason,
            duration=duration,
        )
        await interaction.response.send_message(
            f"Are you sure you want to ban `{player.get('nickname', 'Unknown')}`?",
            view=view,
        )
        view.message = await interaction.original_response()

    async def _perform_ban(
        self,
        *,
        actor_name: str,
        player_id: int,
        period: str,
        reason: str,
        duration: timedelta,
        player: Mapping[str, object],
    ) -> str:
        uuid_value, ip_value = self._player_identifiers(player)
        if uuid_value is None and ip_value is None:
            return "Cannot ban player: both UUID and IP are missing in player data."

        key_uuid = uuid_value or "none"
        key_ip = ip_value or "none"
        claim_key = f"ban-confirm:{key_uuid}:{key_ip}:{period}:{reason}"
        claimed = await self._bus.claim_idempotency(claim_key, ttl_seconds=600)
        if not claimed:
            return "This ban was already processed recently."

        expire = self._store.now_utc() + duration
        await self._store.upsert_ban(
            uuid=uuid_value or "",
            ip=ip_value,
            name=str(player.get("nickname", "Unknown")),
            admin_name=actor_name,
            reason=reason,
            expire_date=expire,
        )
        await self._bus.publish_kick_banned(
            uuid_value=uuid_value or "",
            ip=ip_value,
        )
        await self._post_ban_log(
            pid=player_id,
            name=str(player.get("nickname", "Unknown")),
            admin_name=actor_name,
            reason=reason,
            expire=expire,
        )
        return f"Banned `{player.get('nickname', 'Unknown')}` until {discord.utils.format_dt(expire, style='f')} ({discord.utils.format_dt(expire, style='R')})"

    async def _cmd_unban(self, interaction: Interaction, player_id: int) -> None:
        if not await self._require_admin(interaction):
            return

        player = await self._store.find_player_by_pid(player_id)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        uuid_value, ip_value = self._player_identifiers(player)
        if uuid_value is None and ip_value is None:
            await interaction.response.send_message(
                "Cannot unban player: both UUID and IP are missing in player data.",
                ephemeral=True,
            )
            return

        if not await self._claim_mutation(
            interaction,
            operation="unban",
            scope=str(player_id),
        ):
            return

        deleted = await self._store.delete_ban(
            uuid=uuid_value or "",
            ip=ip_value,
        )
        if deleted == 0:
            await interaction.response.send_message(
                "No active ban found", ephemeral=True
            )
            return
        if uuid_value is not None:
            await self._bus.publish_pardon_player(uuid_value=uuid_value)
        await interaction.response.send_message(
            f"Unbanned `{player.get('nickname', 'Unknown')}`"
        )

    async def _cmd_mute(
        self, interaction: Interaction, player_id: int, period: str, reason: str
    ) -> None:
        if not await self._require_admin(interaction):
            return

        try:
            duration = parse_duration(period)
        except ValueError as error:
            await interaction.response.send_message(str(error), ephemeral=True)
            return

        player = await self._store.find_player_by_pid(player_id)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        uuid_value, _ip_value = self._player_identifiers(player)
        if uuid_value is None:
            await interaction.response.send_message(
                "Cannot mute player: UUID is missing in player data.",
                ephemeral=True,
            )
            return

        if not await self._claim_mutation(
            interaction,
            operation="mute",
            scope=f"{player_id}:{period}:{reason}",
        ):
            return

        expire = self._store.now_utc() + duration
        await self._store.upsert_mute(
            uuid=uuid_value,
            name=str(player.get("nickname", "Unknown")),
            admin_name=interaction.user.display_name,
            reason=reason,
            expire_date=expire,
        )
        await interaction.response.send_message(
            f"Muted `{player.get('nickname', 'Unknown')}` until {discord.utils.format_dt(expire, style='f')} ({discord.utils.format_dt(expire, style='R')})"
        )

    async def _cmd_unmute(self, interaction: Interaction, player_id: int) -> None:
        if not await self._require_admin(interaction):
            return

        player = await self._store.find_player_by_pid(player_id)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        uuid_value, _ip_value = self._player_identifiers(player)
        if uuid_value is None:
            await interaction.response.send_message(
                "Cannot unmute player: UUID is missing in player data.",
                ephemeral=True,
            )
            return

        if not await self._claim_mutation(
            interaction,
            operation="unmute",
            scope=str(player_id),
        ):
            return

        deleted = await self._store.delete_mute(uuid=uuid_value)
        if deleted == 0:
            await interaction.response.send_message(
                "No active mute found", ephemeral=True
            )
            return
        await interaction.response.send_message(
            f"Unmuted `{player.get('nickname', 'Unknown')}`"
        )

    async def _cmd_remove_admin(self, interaction: Interaction, player_id: int) -> None:
        if not await self._require_general_admin(interaction):
            return

        player = await self._store.find_player_by_pid(player_id)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        if not await self._claim_mutation(
            interaction,
            operation="remove-admin",
            scope=str(player_id),
        ):
            return

        uuid_value, _ip_value = self._player_identifiers(player)
        if uuid_value is None:
            await interaction.response.send_message(
                "Cannot remove admin: UUID is missing in player data.",
                ephemeral=True,
            )
            return

        changed = await self._store.remove_admin(uuid=uuid_value)
        await self._bus.publish_remove_admin(uuid_value=uuid_value)
        await interaction.response.send_message(
            f"Removed admin for `{player.get('nickname', 'Unknown')}`"
            if changed
            else "No admin state was changed"
        )

    async def _cmd_reset_password(
        self, interaction: Interaction, player_id: int
    ) -> None:
        if not await self._require_general_admin(interaction):
            return

        player = await self._store.find_player_by_pid(player_id)
        if player is None:
            await interaction.response.send_message("Player not found", ephemeral=True)
            return

        if not await self._claim_mutation(
            interaction,
            operation="reset-password",
            scope=str(player_id),
        ):
            return

        uuid_value, _ip_value = self._player_identifiers(player)
        if uuid_value is None:
            await interaction.response.send_message(
                "Cannot reset password: UUID is missing in player data.",
                ephemeral=True,
            )
            return

        changed = await self._store.reset_password(uuid=uuid_value)
        if changed:
            await self._bus.publish_reload_player_data_cache()
        await interaction.response.send_message(
            f"Password reset for `{player.get('nickname', 'Unknown')}`"
            if changed
            else "Password reset did not update any row"
        )

    async def _cmd_maps(self, interaction: Interaction, server: str) -> None:
        # TODO: Task 5 will allow resolving server even if not in static map.
        # For now, we just proceed.

        await interaction.response.defer()
        try:
            maps = await self._bus.rpc_maps_list(
                server=server, timeout_ms=self._settings.rpc_timeout_ms
            )
        except TimeoutError:
            await interaction.followup.send("No response from target server (timeout).")
            return

        if not maps:
            await interaction.followup.send(f"No maps found on server `{server}`")
            return

        page_size = 15

        async def fetch_page(page: int) -> tuple[discord.Embed, bool]:
            start = page * page_size
            end = start + page_size
            chunk = maps[start:end]

            embed = discord.Embed(
                title=f"Maps on `{server}`",
                color=discord.Color.green(),
            )
            if chunk:
                lines: list[str] = []
                for item in chunk:
                    name = item.get("name", "Unknown")
                    file_name = item.get("file_name", "")
                    author = item.get("author", "Unknown")
                    width = str(item.get("width", "")).strip()
                    height = str(item.get("height", "")).strip()
                    file_size_raw = str(item.get("file_size_bytes", "")).strip()

                    file_part = f" (`{file_name}`)" if file_name else ""
                    size_part = ""
                    if file_size_raw.isdigit():
                        bytes_size = int(file_size_raw)
                        size_part = f" • {self._format_size(bytes_size)}"

                    dims_part = (
                        f" • {width}x{height}"
                        if width.isdigit() and height.isdigit()
                        else ""
                    )
                    lines.append(
                        f"- {name}{file_part} — by `{author}`{dims_part}{size_part}"
                    )
                embed.description = "\n".join(lines)
            else:
                embed.description = "No maps found."
            has_next = end < len(maps)
            total_pages = max(1, (len(maps) + page_size - 1) // page_size)
            embed.set_footer(
                text=f"Page {page + 1}/{total_pages} • total maps: {len(maps)} • entries on page: {len(chunk)}"
            )
            return embed, has_next

        first_embed, has_next = await fetch_page(0)
        view = _PaginatorView(
            page=0, has_prev=False, has_next=has_next, fetch_page=fetch_page
        )
        sent = await interaction.followup.send(embed=first_embed, view=view)
        view.bot_message = sent

    async def _cmd_remove_map(
        self, interaction: Interaction, server: str, file_name: str
    ) -> None:
        if not await self._require_map_reviewer(interaction):
            return

        normalized = file_name.strip()
        if not normalized:
            await interaction.response.send_message(
                "Map file name must not be empty.", ephemeral=True
            )
            return

        request_nonce = secrets.token_hex(6)
        view = _MapRemoveConfirmView(
            bot=self,
            requester_id=interaction.user.id,
            server=server,
            file_name=normalized,
            request_nonce=request_nonce,
        )
        await interaction.response.send_message(
            (
                "Are you sure you want to remove map file "
                f"`{normalized}` from `{server}`?"
            ),
            view=view,
        )
        view.message = await interaction.original_response()

    async def _perform_remove_map(
        self,
        *,
        server: str,
        file_name: str,
        request_nonce: str,
    ) -> str:
        claim_key = f"remove-map:{server}:{file_name}:{request_nonce}"
        if not await self._bus.claim_idempotency(claim_key, ttl_seconds=600):
            return "This map removal was already processed."

        try:
            result = await self._bus.rpc_remove_map(
                server=server,
                file_name=file_name,
                timeout_ms=self._settings.rpc_timeout_ms,
            )
        except TimeoutError:
            return "No response from target server (timeout)."

        return f"🗑️ `{server}` remove-map `{file_name}`: {result}"

    async def _cmd_upload_map(
        self,
        interaction: Interaction,
        server: str,
        attachments: list[discord.Attachment | None],
    ) -> None:
        if not await self._require_map_reviewer(interaction):
            return

        files = [
            {"url": att.url, "filename": att.filename}
            for att in attachments
            if att is not None and att.filename.lower().endswith(".msav")
        ]

        if not files:
            await interaction.response.send_message(
                "No valid .msav files attached.", ephemeral=True
            )
            return

        if not await self._claim_mutation(
            interaction,
            operation="upload-map",
            scope=f"{server}:{len(files)}",
        ):
            return

        await self._bus.publish_maps_load(server=server, files=files)
        await interaction.response.send_message(
            f"Uploaded {len(files)} map(s) to `{server}`: "
            + ", ".join(item["filename"] for item in files)
        )

    async def _post_ban_log(
        self,
        *,
        pid: int,
        name: str,
        admin_name: str,
        reason: str,
        expire: datetime,
    ) -> None:
        bans_channel_id = self._settings.discord_bans_channel_id
        if not bans_channel_id:
            return

        channel = await self._resolve_messageable_channel(
            bans_channel_id,
            context="ban logs",
        )
        if channel is None:
            return

        embed = discord.Embed(title="Ban", color=discord.Color.red())
        embed.add_field(name="ID", value=str(pid), inline=False)
        embed.add_field(name="Violator", value=name, inline=False)
        embed.add_field(name="Admin", value=admin_name, inline=False)
        embed.add_field(name="Reason", value=reason, inline=False)
        embed.add_field(
            name="Unban Date",
            value=discord.utils.format_dt(
                expire.replace(tzinfo=timezone.utc)
                if expire.tzinfo is None
                else expire,
                style="D",
            ),
            inline=False,
        )
        await channel.send(embed=embed)

    @staticmethod
    def _member_role_ids(user: object) -> set[int]:
        roles = getattr(user, "roles", None)
        if roles is None:
            return set()
        result: set[int] = set()
        for role in roles:
            role_id = getattr(role, "id", None)
            if isinstance(role_id, int):
                result.add(role_id)
        return result

    @staticmethod
    def _player_identifiers(
        player: Mapping[str, object],
    ) -> tuple[str | None, str | None]:
        uuid_raw = player.get("uuid")
        uuid_value = str(uuid_raw).strip() if uuid_raw is not None else ""

        ip_raw = player.get("ip")
        if not ip_raw:
            ip_raw = player.get("last_ip")
        ip_value = str(ip_raw).strip() if ip_raw is not None else ""

        uuid_final = uuid_value if uuid_value else None
        ip_final = ip_value if ip_value else None
        return uuid_final, ip_final

    @staticmethod
    def _role_mention(role_id: int) -> str:
        return f"<@&{role_id}>"

    @staticmethod
    def _admin_interaction_action(custom_id: str) -> str | None:
        if custom_id.endswith("_admreq"):
            return "confirm"
        if custom_id.endswith("_admreq_confirm"):
            return "confirm"
        if custom_id.endswith("_admreq_decline"):
            return "decline"
        return None

    def _build_admin_interaction_custom_id(
        self,
        server: str,
        pid: int,
        action: str,
        request_nonce: str,
    ) -> str:
        expires_at = int(datetime.now(timezone.utc).timestamp()) + 600
        payload = f"{server}:{pid}:{action}:{request_nonce}:{expires_at}"
        signature = hmac.new(
            self._interaction_hmac_secret(),
            payload.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()[:16]
        return f"admreq:{payload}:{signature}"

    def _parse_admin_interaction_custom_id(
        self,
        custom_id: str,
    ) -> tuple[str, int, str, str] | None:
        parts = custom_id.split(":")
        if len(parts) != 7 or parts[0] != "admreq":
            return None

        server = parts[1]
        pid_raw = parts[2]
        action = parts[3]
        request_nonce = parts[4]
        expires_raw = parts[5]
        signature = parts[6]

        if action not in {"confirm", "decline"}:
            return None

        try:
            pid = int(pid_raw)
            expires_at = int(expires_raw)
        except ValueError:
            return None

        if int(datetime.now(timezone.utc).timestamp()) > expires_at:
            return None

        payload = f"{server}:{pid}:{action}:{request_nonce}:{expires_at}"
        expected = hmac.new(
            self._interaction_hmac_secret(),
            payload.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()[:16]

        if not hmac.compare_digest(expected, signature):
            return None

        return server, pid, action, request_nonce

    def _interaction_hmac_secret(self) -> bytes:
        configured = (self._settings.discord_interaction_hmac_secret or "").strip()
        secret = configured if configured else self._settings.discord_token
        return secret.encode("utf-8")

    @staticmethod
    def _format_size(size_bytes: int) -> str:
        if size_bytes < 1024:
            return f"{size_bytes} B"
        if size_bytes < 1024 * 1024:
            return f"{size_bytes / 1024:.1f} KB"
        return f"{size_bytes / (1024 * 1024):.1f} MB"

    @staticmethod
    def _format_minutes(total_minutes: int) -> str:
        if total_minutes < 60:
            return f"{total_minutes}m"
        hours = total_minutes // 60
        minutes = total_minutes % 60
        if hours < 24:
            return f"{hours}h {minutes}m"
        days = hours // 24
        rem_hours = hours % 24
        return f"{days}d {rem_hours}h {minutes}m"

    @staticmethod
    def _format_epoch_millis(value: object) -> str:
        if isinstance(value, (int, float)) and value > 0:
            dt = datetime.fromtimestamp(float(value) / 1000.0, tz=timezone.utc)
            return dt.strftime("%Y-%m-%d %H:%M UTC")
        return "n/a"

    @staticmethod
    def _format_hexed_rank_block(rank_value: int, points: int) -> tuple[str, str]:
        safe_rank = max(0, min(rank_value, len(HEXED_RANKS) - 1))
        current = HEXED_RANKS[safe_rank]

        rank_name = str(current["name"])
        rank_tag = str(current["tag"])
        if rank_tag:
            rank_label = f"{rank_tag} {rank_name}"
        else:
            rank_label = rank_name

        if safe_rank + 1 < len(HEXED_RANKS):
            next_required = int(HEXED_RANKS[safe_rank + 1]["required"])
            rank_progress = f"{points}/{next_required} wins"
        else:
            rank_progress = f"{points} wins (max rank)"

        return rank_label, rank_progress

    @staticmethod
    def _parse_iso_datetime(raw: str | None) -> datetime | None:
        if raw is None:
            return None
        try:
            normalized = raw.replace("Z", "+00:00")
            return datetime.fromisoformat(normalized)
        except ValueError:
            return None
