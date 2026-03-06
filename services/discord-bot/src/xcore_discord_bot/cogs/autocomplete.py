from __future__ import annotations

from typing import TYPE_CHECKING

from discord import Interaction, app_commands

from ..client_protocols import SupportsCachedMaps, SupportsPlayerAutocomplete

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


async def _autocomplete_player_id(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[int]]:
    from ..bot import strip_mindustry_colors

    current_norm = current.strip()
    if not current_norm:
        return []

    bot = interaction.client
    if not isinstance(bot, SupportsPlayerAutocomplete):
        return []

    rows = await bot.autocomplete_players(current_norm, limit=25)
    choices: list[app_commands.Choice[int]] = []
    for row in rows:
        pid_raw = row.get("pid")
        if not isinstance(pid_raw, int):
            continue

        nickname_raw = row.get("nickname")
        nickname = str(nickname_raw).strip() if nickname_raw else "Unknown"
        nickname = (
            strip_mindustry_colors(nickname).replace("`", "").strip() or "Unknown"
        )
        choices.append(
            app_commands.Choice(name=f"{nickname} (pid={pid_raw})", value=pid_raw)
        )
        if len(choices) >= 25:
            break

    return choices


async def _autocomplete_map_file(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    bot = interaction.client
    server = str(getattr(interaction.namespace, "server", "")).strip()
    if not server:
        return []

    if not isinstance(bot, SupportsCachedMaps):
        return []
    maps = await bot.get_cached_maps(server)
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
