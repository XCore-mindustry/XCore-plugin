from __future__ import annotations

from typing import TYPE_CHECKING

from discord import Interaction

from .command_helpers import get_player_or_reply
from .dto import MERGE_KEEP_PID_TARGET
from .moderation_views import AccountMergeConfirmView
from .presentation import build_merge_preview_embed

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


async def cmd_merge_player(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    source_pid: int,
    target_pid: int,
    reason: str = "Admin merge",
    keep_pid: str = MERGE_KEEP_PID_TARGET,
) -> None:
    if source_pid == target_pid:
        await interaction.response.send_message(
            "Cannot merge an account into itself.",
            ephemeral=True,
        )
        return

    source = await get_player_or_reply(bot, interaction, source_pid)
    if source is None:
        return

    target = await get_player_or_reply(bot, interaction, target_pid)
    if target is None:
        return

    if source.uuid and source.uuid.startswith("merged:"):
        await interaction.response.send_message(
            f"Source account #{source.pid} was already merged into another account.",
            ephemeral=True,
        )
        return

    if target.uuid and target.uuid.startswith("merged:"):
        await interaction.response.send_message(
            f"Target account #{target.pid} is closed and cannot receive a merge.",
            ephemeral=True,
        )
        return

    online = [
        f"#{player.pid} ({player.nickname})"
        for player in (source, target)
        if player.online
    ]
    if online:
        await interaction.response.send_message(
            "Cannot merge while an account is online: "
            f"{', '.join(online)}. Ask the player to disconnect, then retry.",
            ephemeral=True,
        )
        return

    embed = build_merge_preview_embed(
        source=source,
        target=target,
        keep_pid=keep_pid,
        reason=reason,
    )

    view = AccountMergeConfirmView(
        requester_id=interaction.user.id,
        source_pid=source_pid,
        target_pid=target_pid,
        source_player=source,
        target_player=target,
        reason=reason,
        perform_merge=lambda **kwargs: bot.container.players.merge_player_accounts(
            **kwargs
        ),
        keep_pid=keep_pid,
    )

    await interaction.response.send_message(embed=embed, view=view)
    view.message = await interaction.original_response()
