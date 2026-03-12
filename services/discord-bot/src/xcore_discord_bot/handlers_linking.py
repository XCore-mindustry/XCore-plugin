from __future__ import annotations

from typing import TYPE_CHECKING

import discord
from discord import Interaction

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


async def cmd_link(bot: "XCoreDiscordBot", interaction: Interaction, code: str) -> None:
    normalized_code = code.strip().upper()
    if not normalized_code:
        await interaction.response.send_message(
            "Link code is required.", ephemeral=True
        )
        return

    if not await bot._claim_mutation(
        interaction,
        operation="discord-link",
        scope=f"{interaction.user.id}:{normalized_code}",
    ):
        return

    code_doc = await bot.find_discord_link_code(normalized_code)
    if code_doc is None:
        await interaction.response.send_message("Link code not found.", ephemeral=True)
        return

    status = str(code_doc.get("status") or "")
    if status != "pending":
        await interaction.response.send_message(
            "This link code was already used or cancelled.", ephemeral=True
        )
        return

    expires_at_raw = code_doc.get("expires_at")
    expires_at = int(str(expires_at_raw)) if expires_at_raw is not None else 0
    now_ms = int((await bot.now_utc()).timestamp() * 1000)
    if expires_at > 0 and expires_at <= now_ms:
        await interaction.response.send_message(
            "This link code has expired.", ephemeral=True
        )
        return

    player = await bot.find_player_by_uuid(str(code_doc.get("player_uuid") or ""))
    if player is None or player.uuid is None:
        await interaction.response.send_message(
            "Player for this code was not found.", ephemeral=True
        )
        return

    discord_id = str(interaction.user.id)
    discord_username = interaction.user.display_name
    if player.discord_id and player.discord_id != discord_id:
        await interaction.response.send_message(
            "This Mindustry account is already linked to another Discord account.",
            ephemeral=True,
        )
        return

    await bot.publish_discord_link_confirm(
        code=normalized_code,
        player_uuid=player.uuid,
        player_pid=player.pid,
        discord_id=discord_id,
        discord_username=discord_username,
    )
    await interaction.response.send_message(
        f"Link request sent for `{player.nickname}` (`pid={player.pid}`). Return in-game in a moment.",
        ephemeral=True,
    )


async def cmd_link_status(bot: "XCoreDiscordBot", interaction: Interaction) -> None:
    discord_id = str(interaction.user.id)
    players = await bot.find_players_by_discord_id(discord_id)
    if not players:
        await interaction.response.send_message(
            "No Mindustry accounts are linked to this Discord account.",
            ephemeral=True,
        )
        return

    lines = [f"`{player.pid}` — {player.nickname}" for player in players]
    embed = discord.Embed(
        title="Linked Mindustry accounts",
        description="\n".join(lines),
        color=discord.Color.blurple(),
    )
    await interaction.response.send_message(embed=embed, ephemeral=True)


async def cmd_unlink(
    bot: "XCoreDiscordBot", interaction: Interaction, player_id: int
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    discord_id = str(interaction.user.id)
    if player.discord_id != discord_id:
        await interaction.response.send_message(
            "This player is not linked to your Discord account.",
            ephemeral=True,
        )
        return

    if player.uuid is None:
        await interaction.response.send_message(
            "Cannot unlink player: UUID is missing.",
            ephemeral=True,
        )
        return

    if not await bot._claim_mutation(
        interaction,
        operation="discord-unlink",
        scope=f"{discord_id}:{player_id}",
    ):
        return

    await bot.publish_discord_unlink(
        player_uuid=player.uuid,
        player_pid=player.pid,
        discord_id=discord_id,
        requested_by="discord",
    )
    await interaction.response.send_message(
        f"Unlink request sent for `{player.nickname}`.",
        ephemeral=True,
    )
