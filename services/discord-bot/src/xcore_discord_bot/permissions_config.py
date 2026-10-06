from __future__ import annotations

import tomllib
from pathlib import Path


def load_discord_permissions(path: str, guild_id: int) -> frozenset[str]:
    if not path:
        raise ValueError("PERMISSIONS_CONFIG_PATH is required in roles mode")
    with Path(path).open("rb") as file:
        config = tomllib.load(file)
    discord = config.get("discord", {})
    if str(discord.get("guildId", "")) != str(guild_id) or guild_id <= 0:
        raise ValueError(
            "permissions.toml discord.guildId differs from DISCORD_GUILD_ID"
        )
    bindings = discord.get("bindings", [])
    if not isinstance(bindings, list):
        raise TypeError("discord.bindings must be an array of tables")
    ids = set()
    for binding in bindings:
        role_id = binding.get("roleId")
        if not isinstance(role_id, str) or not role_id.isdecimal() or int(role_id) <= 0:
            raise ValueError(
                "discord.bindings roleId must be a positive snowflake string"
            )
        if not isinstance(binding.get("role"), str) or not binding["role"].strip():
            raise ValueError("discord.bindings role is required")
        ids.add(role_id)
    return frozenset(ids)
