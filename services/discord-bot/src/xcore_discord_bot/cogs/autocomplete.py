from __future__ import annotations


from discord import Interaction, app_commands

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
