from __future__ import annotations

from typing import TYPE_CHECKING, cast

from discord import Interaction, app_commands

from ..badges import badge_choice_label, grantable_badges
from ..handlers_misc import get_cached_maps
from ..player_pids import is_assigned
from ..utils.mindustry_colors import strip_mindustry_colors

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


async def _autocomplete_player_id(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[int]]:
    current_norm = current.strip()
    if not current_norm:
        return []

    bot = cast("XCoreDiscordBot", interaction.client)
    rows = await bot.store.autocomplete_players(current_norm, limit=25)
    choices: list[app_commands.Choice[int]] = []
    for row in rows:
        if not is_assigned(row.pid):
            continue

        nickname = strip_mindustry_colors(row.nickname).replace("`", "").strip()
        nickname = nickname or "Unknown"
        username = f" @{row.username}" if row.username else ""
        # Discord refuses a choice whose name is longer than 100 characters.
        label = f"{nickname[:60]}{username} (#{row.pid})"
        choices.append(app_commands.Choice(name=label[:100], value=row.pid))
        if len(choices) >= 25:
            break

    return choices


async def _autocomplete_stats_player(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    """Stats accepts text so Discord can offer names without integer validation."""
    choices = await _autocomplete_player_id(interaction, current)
    return [
        app_commands.Choice(name=choice.name, value=str(choice.value))
        for choice in choices
    ]


async def _autocomplete_map_file(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    bot = cast("XCoreDiscordBot", interaction.client)
    server = str(getattr(interaction.namespace, "server", "")).strip()
    if not server:
        return []

    maps = await get_cached_maps(bot, server)
    current_norm = current.strip().lower()

    choices: list[app_commands.Choice[str]] = []
    for item in maps:
        file_name = str(item.get("file_name", "")).strip()
        if not file_name:
            continue
        map_name = str(item.get("name", "Unknown")).strip() or "Unknown"

        searchable = f"{map_name} {file_name}".lower()
        if current_norm and current_norm not in searchable:
            continue

        choices.append(
            app_commands.Choice(name=f"{map_name} ({file_name})", value=file_name)
        )
        if len(choices) >= 25:
            break

    return choices


async def _autocomplete_badge_id(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    del interaction
    current_norm = current.strip().lower()

    choices: list[app_commands.Choice[str]] = []
    for badge in grantable_badges():
        searchable = f"{badge.id} {badge.label}".lower()
        if current_norm and current_norm not in searchable:
            continue
        choices.append(
            app_commands.Choice(name=badge_choice_label(badge), value=badge.id)
        )
        if len(choices) >= 25:
            break

    return choices
