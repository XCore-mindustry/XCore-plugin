from __future__ import annotations

import os
from dataclasses import dataclass


def _required_env(name: str) -> str:
    value = os.getenv(name)
    if value is None or not value.strip():
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value.strip()


def _optional_env(name: str, default: str) -> str:
    value = os.getenv(name)
    if value is None:
        return default
    value = value.strip()
    return value if value else default




@dataclass(frozen=True)
class Settings:
    discord_token: str
    discord_admin_role_id: int
    discord_general_admin_role_id: int
    discord_map_reviewer_role_id: int
    discord_private_channel_id: int
    redis_url: str
    redis_group_prefix: str
    redis_consumer_name: str
    mongo_uri: str
    mongo_db_name: str
    rpc_timeout_ms: int
    discord_bans_channel_id: int = 0  # 0 = disabled
    discord_guild_id: int = 0  # 0 = global slash command sync (slower)
    discord_interaction_hmac_secret: str = ""
    @property
    def server_channel_map(self) -> dict[str, int]:
        return {}

    @property
    def channel_server_map(self) -> dict[int, str]:
        return {}


    @classmethod
    def from_env(cls) -> "Settings":
        rpc_timeout_raw = _optional_env("RPC_TIMEOUT_MS", "5000")
        try:
            rpc_timeout_ms = int(rpc_timeout_raw)
        except ValueError as error:
            raise RuntimeError("RPC_TIMEOUT_MS must be an integer") from error

        if rpc_timeout_ms <= 0:
            raise RuntimeError("RPC_TIMEOUT_MS must be > 0")

        discord_admin_role_id = int(_required_env("DISCORD_ADMIN_ROLE_ID"))
        discord_general_admin_role_id = int(
            _optional_env("DISCORD_GENERAL_ADMIN_ROLE_ID", str(discord_admin_role_id))
        )
        discord_map_reviewer_role_id = int(
            _optional_env("DISCORD_MAP_REVIEWER_ROLE_ID", str(discord_admin_role_id))
        )
        discord_private_channel_id = int(_required_env("DISCORD_PRIVATE_CHANNEL_ID"))
        discord_bans_channel_id = int(
            _optional_env("DISCORD_BANS_CHANNEL_ID", "0")
        )

        return cls(
            discord_token=_required_env("DISCORD_BOT_TOKEN"),
            discord_admin_role_id=discord_admin_role_id,
            discord_general_admin_role_id=discord_general_admin_role_id,
            discord_map_reviewer_role_id=discord_map_reviewer_role_id,
            discord_private_channel_id=discord_private_channel_id,
            discord_bans_channel_id=discord_bans_channel_id,
            discord_guild_id=int(_optional_env("DISCORD_GUILD_ID", "0")),
            redis_url=_optional_env("REDIS_URL", "redis://127.0.0.1:6379"),
            redis_group_prefix=_optional_env("REDIS_GROUP_PREFIX", "xcore:cg"),
            redis_consumer_name=_optional_env("REDIS_CONSUMER_NAME", "discord-bot"),
            mongo_uri=_optional_env("MONGO_URI", "mongodb://127.0.0.1:27017"),
            mongo_db_name=_optional_env("MONGO_DB_NAME", "xcore"),
            rpc_timeout_ms=rpc_timeout_ms,
            discord_interaction_hmac_secret=_optional_env("DISCORD_INTERACTION_HMAC_SECRET", ""),
        )
