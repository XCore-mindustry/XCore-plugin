from __future__ import annotations


from discord import Interaction, app_commands

from ..badges import badge_choice_label, grantable_badges
from ..service_protocols import BusService, StoreService


async def _autocomplete_player_id(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[int]]:
    from ..bot import strip_mindustry_colors

    current_norm = current.strip()
    if not current_norm:
        return []

    store = interaction.client
    if not isinstance(store, StoreService):
        return []

    rows = await store.autocomplete_players(current_norm, limit=25)
    choices: list[app_commands.Choice[int]] = []
    for row in rows:
        if row.pid < 0:
            continue

        nickname = strip_mindustry_colors(row.nickname).replace("`", "").strip()
        nickname = nickname or "Unknown"
        choices.append(
            app_commands.Choice(name=f"{nickname} (pid={row.pid})", value=row.pid)
        )
        if len(choices) >= 25:
            break

    return choices


async def _autocomplete_map_file(
    interaction: Interaction,
    current: str,
) -> list[app_commands.Choice[str]]:
    bus = interaction.client
    server = str(getattr(interaction.namespace, "server", "")).strip()
    if not server:
        return []

    if not isinstance(bus, BusService):
        return []
    maps = await bus.get_cached_maps(server)
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
