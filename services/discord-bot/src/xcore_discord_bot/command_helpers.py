"""Replies shared by the slash-command handlers."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from datetime import timedelta
from typing import TYPE_CHECKING

import discord
from discord import Interaction

from .dto import PlayerRecord, record_identifiers
from .server_views import PaginatorView
from .utils.duration import parse_duration

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot

MSG_PLAYER_NOT_FOUND = "Player not found"
MSG_DUPLICATE_MUTATION = (
    "Duplicate command ignored: this operation was already processed recently."
)


async def claim_mutation(
    bot: XCoreDiscordBot, interaction: Interaction, *, operation: str, scope: str
) -> bool:
    claim_key = f"cmd:{operation}:{interaction.id}:{scope}"
    if await bot.bus.claim_idempotency(claim_key, ttl_seconds=600):
        return True
    await interaction.response.send_message(MSG_DUPLICATE_MUTATION, ephemeral=True)
    return False


async def get_player_or_reply(
    bot: XCoreDiscordBot, interaction: Interaction, player_id: int
) -> PlayerRecord | None:
    player = await bot.store.find_player_by_pid(player_id)
    if player is None:
        await interaction.response.send_message(MSG_PLAYER_NOT_FOUND, ephemeral=True)
    return player


async def require_player_uuid(
    interaction: Interaction, player: PlayerRecord, *, action: str
) -> str | None:
    uuid_value, _ip_value = record_identifiers(player)
    if uuid_value is None:
        await interaction.response.send_message(
            f"Cannot {action}: UUID is missing in player data.", ephemeral=True
        )
    return uuid_value


async def require_player_uuid_or_ip(
    interaction: Interaction, player: PlayerRecord, *, action: str
) -> tuple[str | None, str | None] | None:
    uuid_value, ip_value = record_identifiers(player)
    if uuid_value is None and ip_value is None:
        await interaction.response.send_message(
            f"Cannot {action}: both UUID and IP are missing in player data.",
            ephemeral=True,
        )
        return None
    return uuid_value, ip_value


async def parse_duration_or_reply(
    interaction: Interaction, period: str
) -> timedelta | None:
    try:
        return parse_duration(period)
    except ValueError as error:
        await interaction.response.send_message(str(error), ephemeral=True)
        return None


async def send_paginated(
    interaction: Interaction,
    fetch_page: Callable[[int], Awaitable[tuple[discord.Embed, bool]]],
    *,
    ephemeral: bool = False,
    allowed_mentions: discord.AllowedMentions | None = None,
) -> None:
    """Send an embed with Prev/Next pagination buttons via an Interaction."""
    embed, has_next = await fetch_page(0)
    view = PaginatorView(
        page=0, has_prev=False, has_next=has_next, fetch_page=fetch_page
    )
    extra = {} if allowed_mentions is None else {"allowed_mentions": allowed_mentions}
    await interaction.response.send_message(
        embed=embed, view=view, ephemeral=ephemeral, **extra
    )
    view.bot_message = await interaction.original_response()
