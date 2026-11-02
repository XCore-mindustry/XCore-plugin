from __future__ import annotations

import logging
import secrets
import traceback

import discord
from discord import Interaction, app_commands
from discord.abc import Messageable
from discord.ext import commands

from .bus.transport import RedisTransport
from .cases._privacy import private_case_command
from .cases.redis_transport import RedisCaseTransport
from .cases.views import CaseActionButton, PanelButton
from .cogs import AdminCog, InfoCog, LinkingCog, MapsCog, SeasonsCog, SubnetCog
from .cogs.cases import CasesCog
from .command_helpers import reply_locale
from .container import ServiceContainer
from .handlers_linking import find_code_player, link_with_code
from .i18n import XCoreTranslator, resolve_dm_locale, t
from .mongo_store import MongoStore
from .settings import Settings

logger = logging.getLogger(__name__)


class XCoreDiscordBot(commands.Bot):
    def __init__(
        self, settings: Settings, bus: RedisTransport, store: MongoStore
    ) -> None:
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

        self.settings = settings
        self.bus = bus
        self.store = store
        self.container = ServiceContainer.create(
            settings, self, store=self.store, bus=self.bus
        )
        if settings.cases_enabled:
            self.container.cases.set_transport(
                RedisCaseTransport(self.container.cases_bus, self.container.rpc)
            )

    async def on_member_update(
        self, before: discord.Member, after: discord.Member
    ) -> None:
        if after.guild.id == self.settings.discord_guild_id and {
            role.id for role in before.roles
        } != {role.id for role in after.roles}:
            await self.container.staff_sync_daemon.sync_member(str(after.id))

    async def on_raw_member_remove(self, payload: discord.RawMemberRemoveEvent) -> None:
        # Raw events include removals for members absent from the member cache.
        if payload.guild_id == self.settings.discord_guild_id:
            await self.container.staff_sync_daemon.sync_member(
                str(payload.user.id), removed=True
            )

    async def set_discord_admin_role(
        self,
        *,
        discord_id: str,
        should_have_role: bool,
        reason: str,
    ) -> bool:
        guild_id = self.settings.discord_guild_id

        guild = self.get_guild(guild_id)
        if guild is None:
            guild = await self.fetch_guild(guild_id)

        try:
            member_id = int(discord_id)
        except (ValueError, TypeError):
            raise RuntimeError(f"Invalid discord_id: {discord_id}")

        member = guild.get_member(member_id)
        if member is None:
            member = await guild.fetch_member(member_id)

        role = guild.get_role(self.settings.discord_admin_role_id)
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

    async def setup_hook(self) -> None:
        await self.add_cog(InfoCog(self))
        await self.add_cog(AdminCog(self))
        await self.add_cog(LinkingCog(self))
        await self.add_cog(MapsCog(self))
        await self.add_cog(SubnetCog(self))
        await self.add_cog(SeasonsCog(self))
        if self.settings.cases_enabled:
            # /cases and /language exist only while at least one flow is switched on.
            await self.add_cog(CasesCog(self))
        # Always registered: a button left on an old card answers "not available" instead
        # of "interaction failed" after its flow is switched off.
        self.add_dynamic_items(CaseActionButton, PanelButton)
        await self.tree.set_translator(XCoreTranslator())

        @self.tree.error
        async def _on_app_command_error(
            interaction: Interaction,
            error: app_commands.AppCommandError,
        ) -> None:
            await self.handle_app_command_error(interaction, error)

        await self.bus.connect()
        await self.store.connect()
        await self.container.punishments.ensure_indexes()
        await self.container.audit.ensure_indexes()
        container = self.container
        if self.settings.cases_enabled:
            try:
                await container.cases.store.load_locale_prefs()
                await container.cases.store.ensure_indexes()
            except Exception:  # the bot still starts with cases disabled
                logger.exception(
                    "Cases storage initialization failed; flows stay disabled"
                )
                container.cases.storage_available = False
            container.cases_reconcile_daemon.start()

        self.container.stream_supervisor.start()
        self.container.presence_daemon.start()
        container.staff_sync_daemon.start()
        self.container.rating_merge_daemon.start()

        await self._sync_application_commands()

    async def _sync_application_commands(self) -> None:
        guild_obj = discord.Object(id=self.settings.discord_guild_id)
        if self.settings.discord_clear_stale_commands:
            logger.warning("Clearing stale global and guild slash commands before sync")
            # Clearing empties the tree too; put the commands back for the guild sync.
            commands = self.tree.get_commands(guild=None)
            self.tree.clear_commands(guild=None)
            await self.tree.sync()
            self.tree.clear_commands(guild=guild_obj)
            for command in commands:
                self.tree.add_command(command)
        self.tree.copy_global_to(guild=guild_obj)
        await self.tree.sync(guild=guild_obj)

    async def on_ready(self) -> None:
        logger.info("Discord bot connected as %s", self.user)
        await self.container.presence_daemon.update_presence_once()
        try:
            # The pass logs its own summary.
            await self.container.staff_sync_daemon.reconcile()
        except Exception:  # the periodic pass retries it
            logger.exception("Startup staff role sync failed")

    async def on_message(self, message: discord.Message) -> None:
        """Used for game chat bridge and DM-based account linking."""
        if message.author.bot:
            return
        await self._dispatch_case_hook("on_message", message)

        if isinstance(message.channel, discord.DMChannel):
            code = message.content.strip().upper()
            if code:
                code_doc = await self.container.accounts_bus.get_discord_link_code(code)
                if code_doc is not None:
                    player = await find_code_player(self, code_doc)
                    locale = await resolve_dm_locale(
                        message.author.id,
                        self.container.cases.store,
                        player.language if player is not None else None,
                    )
                    await message.channel.send(
                        await link_with_code(
                            self,
                            code=code,
                            code_doc=code_doc,
                            player=player,
                            source_id=message.id,
                            discord_id=str(message.author.id),
                            discord_username=message.author.display_name,
                            locale=locale,
                        )
                    )
                    return

        target_server = self.container.servers.get_server_for_channel(
            message.channel.id
        )
        if target_server is None or not message.content:
            return

        await self.container.chat_bus.publish_discord_message(
            server=target_server,
            author_name=message.author.display_name,
            message=message.content,
            source_message_id=str(message.id),
        )

    async def _dispatch_case_hook(self, hook: str, value: object) -> None:
        # With every cases flag off no flow is enabled and the registry returns at once.
        cases = self.container.cases
        await cases.registry.dispatch(hook, cases, value)

    async def on_interaction(self, interaction: Interaction) -> None:
        # An observer, not a response handler: it never delays a modal or command
        # acknowledgement. It keeps the ``auto`` language row fresh for later DMs.
        if (
            not self.settings.cases_enabled
            or interaction.type == discord.InteractionType.autocomplete
        ):
            return
        try:
            await self.container.cases.store.remember_interaction(interaction)
        except Exception:  # an observer must not fail the interaction
            logger.exception("Could not refresh Discord locale preference")

    async def on_thread_create(self, thread: discord.Thread) -> None:
        await self._dispatch_case_hook("on_thread_create", thread)

    async def on_raw_thread_delete(self, payload: discord.RawThreadDeleteEvent) -> None:
        await self._dispatch_case_hook("on_thread_delete", payload)

    async def close(self) -> None:
        await self.container.cases_reconcile_daemon.stop()
        self.container.presence_daemon.stop()
        await self.container.staff_sync_daemon.stop()
        self.container.rating_merge_daemon.stop()
        await self.container.stream_supervisor.stop()

        await self.bus.close()
        await self.store.close()
        await super().close()

    async def resolve_channel(
        self,
        channel_id: int,
        *,
        context: str,
    ) -> Messageable | None:
        channel = self.get_channel(channel_id)
        if channel is None:
            try:
                channel = await self.fetch_channel(channel_id)
            except discord.HTTPException as error:
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

    async def handle_app_command_error(  # noqa: C901
        self,
        interaction: Interaction,
        error: app_commands.AppCommandError,
    ) -> None:
        if await private_case_command(self, interaction):
            # No exception arguments, tracebacks, namespace values or actor names may leave
            # a complaint/unknown cases command. The reference is the entire diagnostic.
            error_id = secrets.token_hex(4)
            logger.error("Private cases command failed [ref=%s]", error_id)
            message = t(reply_locale(self, interaction), "command-failed", ref=error_id)
            try:
                if interaction.response.is_done():
                    await interaction.followup.send(message, ephemeral=True)
                else:
                    await interaction.response.send_message(message, ephemeral=True)
                channel_id = self.settings.discord_error_log_channel_id
                if channel_id:
                    # Avoid the ordinary channel resolver's exception-text diagnostics.
                    channel = self.get_channel(channel_id)
                    if channel is None:
                        channel = await self.fetch_channel(channel_id)
                    embed = discord.Embed(
                        title="Application Command Error",
                        description="Private cases command failed.",
                        color=discord.Color.red(),
                    )
                    embed.add_field(name="Error ID", value=error_id, inline=False)
                    await channel.send(embed=embed)
            except Exception:  # noqa: BLE001 -- logged by reference; details stay private
                logger.error("Private cases error delivery failed [ref=%s]", error_id)
            return
        if isinstance(error, app_commands.CheckFailure):
            message = str(error) or t(
                reply_locale(self, interaction), "command-missing-permissions"
            )
            if interaction.response.is_done():
                await interaction.followup.send(message, ephemeral=True)
            else:
                await interaction.response.send_message(message, ephemeral=True)
            return

        error_id = secrets.token_hex(4)
        logger.exception("Unhandled app command error [ref=%s]: %s", error_id, error)
        message = t(reply_locale(self, interaction), "command-failed", ref=error_id)
        try:
            if interaction.response.is_done():
                await interaction.followup.send(message, ephemeral=True)
            else:
                await interaction.response.send_message(message, ephemeral=True)
        except discord.HTTPException:
            logger.warning("Failed to send command error message", exc_info=True)

        error_channel_id = self.settings.discord_error_log_channel_id
        if not error_channel_id:
            return

        channel = await self.resolve_channel(
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
        except Exception:  # error reporting must not raise
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
