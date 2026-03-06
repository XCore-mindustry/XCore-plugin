from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING

import discord

from .moderation_modals import StatsBanModal, StatsMuteModal
from .handlers_moderation import cmd_ban, cmd_mute

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


def create_stats_ban_modal(
    bot: "XCoreDiscordBot",
    *,
    player_id: int,
    player: Mapping[str, object],
) -> discord.ui.Modal:
    return StatsBanModal(
        player_id=player_id,
        player=player,
        on_submit_ban=lambda interaction, pid, period, reason: cmd_ban(
            bot,
            interaction,
            pid,
            period,
            reason,
        ),
    )


def create_stats_mute_modal(
    bot: "XCoreDiscordBot",
    *,
    player_id: int,
    player: Mapping[str, object],
) -> discord.ui.Modal:
    return StatsMuteModal(
        player_id=player_id,
        player=player,
        on_submit_mute=lambda interaction, pid, period, reason: cmd_mute(
            bot,
            interaction,
            pid,
            period,
            reason,
        ),
    )
