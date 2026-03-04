from __future__ import annotations

from typing import TYPE_CHECKING, cast

from discord import Interaction, app_commands

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


async def _autocomplete_player_id(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[int]]:
    current_norm = current.strip()
    if not current_norm:
        return []

    bot = interaction.client
    if not hasattr(bot, "_store"):
        return []
    xcore_bot = cast("XCoreDiscordBot", bot)

    rows = await xcore_bot._store.autocomplete_players(current_norm, limit=25)
    choices: list[app_commands.Choice[int]] = []
    for row in rows:
        pid_raw = row.get("pid")
        if not isinstance(pid_raw, int):
            continue

        nickname_raw = row.get("nickname")
        nickname = str(nickname_raw).strip() if nickname_raw else "Unknown"
        choices.append(
            app_commands.Choice(name=f"{nickname} (pid={pid_raw})", value=pid_raw)
        )
        if len(choices) >= 25:
            break

    return choices
