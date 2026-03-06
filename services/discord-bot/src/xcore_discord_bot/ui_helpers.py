from __future__ import annotations

import discord
from discord import Interaction


def disable_view_buttons(view: discord.ui.View) -> None:
    for item in view.children:
        if isinstance(item, discord.ui.Button):
            item.disabled = True


async def safe_edit_view_message(
    message: discord.Message | None,
    *,
    view: discord.ui.View | None,
) -> None:
    if message is None:
        return
    try:
        await message.edit(view=view)
    except Exception:
        pass


async def ensure_requester_action_allowed(
    interaction: Interaction,
    *,
    requester_id: int,
    denied_message: str,
) -> bool:
    if interaction.user.id == requester_id:
        return True

    await interaction.response.send_message(denied_message, ephemeral=True)
    return False
