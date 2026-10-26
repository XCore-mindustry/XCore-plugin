from __future__ import annotations

import logging
from typing import TYPE_CHECKING

import discord
from discord import Interaction
from xcore_protocol.generated.shared import ActorRefV1ActorType

from .command_helpers import claim_mutation, get_player_or_reply, reply_locale
from .i18n import t
from .utils.mindustry_colors import strip_mindustry_colors

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot

logger = logging.getLogger(__name__)


def _clean_player_name(name: str | None) -> str:
    if not name:
        return "Unknown"
    cleaned = strip_mindustry_colors(name).replace("`", "").strip()
    return cleaned or name


async def cmd_link(bot: XCoreDiscordBot, interaction: Interaction, code: str) -> None:  # noqa: C901
    locale = reply_locale(bot, interaction)
    if not code or not code.strip():
        await interaction.response.send_message(
            t(locale, "link-code-required"), ephemeral=True
        )
        return
    normalized_code = code.strip().upper()
    if not normalized_code:
        await interaction.response.send_message(
            t(locale, "link-code-required"), ephemeral=True
        )
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation="discord-link",
        scope=f"{interaction.user.id}:{normalized_code}",
        locale=locale,
    ):
        return

    code_doc = await bot.container.accounts_bus.get_discord_link_code(normalized_code)
    if code_doc is None:
        await interaction.response.send_message(
            t(locale, "link-code-not-found"), ephemeral=True
        )
        return

    expires_at_raw = code_doc.get("expires_at")
    expires_at = int(str(expires_at_raw)) if expires_at_raw is not None else 0
    now_ms = int((bot.store.now_utc()).timestamp() * 1000)
    if expires_at > 0 and expires_at <= now_ms:
        await interaction.response.send_message(
            t(locale, "link-code-expired"), ephemeral=True
        )
        return

    player_uuid = str(code_doc.get("playerUuid") or code_doc.get("player_uuid") or "")
    player = await bot.store.find_player_by_uuid(player_uuid)
    if player is None or player.uuid is None:
        await interaction.response.send_message(
            t(locale, "link-player-not-found"), ephemeral=True
        )
        return

    discord_id = str(interaction.user.id)
    discord_username = interaction.user.display_name
    if player.discord_id and player.discord_id != discord_id:
        await interaction.response.send_message(t(locale, "link-taken"), ephemeral=True)
        return

    await bot.container.accounts_bus.publish_discord_link_confirm(
        code=normalized_code,
        player_uuid=player.uuid,
        player_pid=player.pid,
        player_name=player.nickname,
        discord_id=discord_id,
        discord_username=discord_username,
    )

    is_admin = False
    try:
        if not bot.settings.roles_mode:
            discord_admin_ids = (
                await bot.container.admin_sync_daemon.get_discord_admin_member_ids()
            )
            if discord_id in discord_admin_ids:
                await bot.store.set_admin_access(
                    uuid=player.uuid,
                    is_admin=True,
                    admin_source="DISCORD_ROLE",
                )
                await bot.container.accounts_bus.publish_discord_admin_access_changed(
                    player_uuid=player.uuid,
                    player_pid=player.pid,
                    player_name=player.nickname,
                    discord_id=discord_id,
                    discord_username=discord_username,
                    admin=True,
                    source_name="DISCORD_ROLE",
                    source_type=ActorRefV1ActorType.DISCORD,
                    actor_name=discord_username,
                    actor_discord_id=discord_id,
                    actor_type=ActorRefV1ActorType.DISCORD,
                    reason="discord admin linked account",
                )
                is_admin = True
    except Exception:  # the periodic admin reconcile grants it later
        logger.exception("Failed to grant instant admin access after link")

    await interaction.response.send_message(
        t(
            locale,
            "link-done-admin" if is_admin else "link-done",
            name=_clean_player_name(player.nickname),
            pid=player.pid,
        ),
        ephemeral=True,
    )


async def cmd_link_status(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    locale = reply_locale(bot, interaction)
    discord_id = str(interaction.user.id)
    players = await bot.store.find_players_by_discord_id(discord_id)
    if not players:
        await interaction.response.send_message(
            t(locale, "link-status-none"), ephemeral=True
        )
        return

    lines = [
        f"`{player.pid}` — {_clean_player_name(player.nickname)}" for player in players
    ]
    embed = discord.Embed(
        title=t(locale, "link-status-title"),
        description="\n".join(lines),
        color=discord.Color.blurple(),
    )
    await interaction.response.send_message(embed=embed, ephemeral=True)


async def cmd_unlink(
    bot: XCoreDiscordBot, interaction: Interaction, player_id: int
) -> None:
    locale = reply_locale(bot, interaction)
    player = await get_player_or_reply(bot, interaction, player_id, locale=locale)
    if player is None:
        return

    discord_id = str(interaction.user.id)
    if player.discord_id != discord_id:
        await interaction.response.send_message(
            t(locale, "unlink-not-yours"), ephemeral=True
        )
        return

    if player.uuid is None:
        await interaction.response.send_message(
            t(locale, "unlink-no-uuid"), ephemeral=True
        )
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation="discord-unlink",
        scope=f"{discord_id}:{player_id}",
        locale=locale,
    ):
        return

    await bot.container.accounts_bus.publish_discord_unlink(
        player_uuid=player.uuid,
        player_pid=player.pid,
        player_name=player.nickname,
        discord_id=discord_id,
        discord_username=interaction.user.display_name,
        actor_name=interaction.user.display_name,
        actor_discord_id=str(interaction.user.id),
    )
    await interaction.response.send_message(
        t(locale, "unlink-sent", name=_clean_player_name(player.nickname)),
        ephemeral=True,
    )
