from __future__ import annotations

from pydantic import Field, ValidationError, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        extra="ignore",
        populate_by_name=True,
        frozen=True,
    )

    discord_token: str = Field(validation_alias="DISCORD_BOT_TOKEN")
    discord_admin_role_id: int = Field(validation_alias="DISCORD_ADMIN_ROLE_ID")
    discord_general_admin_role_id: int | None = Field(
        default=None,
        validation_alias="DISCORD_GENERAL_ADMIN_ROLE_ID",
    )
    discord_map_reviewer_role_id: int | None = Field(
        default=None,
        validation_alias="DISCORD_MAP_REVIEWER_ROLE_ID",
    )
    discord_private_channel_id: int = Field(
        validation_alias="DISCORD_PRIVATE_CHANNEL_ID"
    )
    redis_url: str = Field(
        default="redis://127.0.0.1:6379",
        validation_alias="REDIS_URL",
    )
    redis_group_prefix: str = Field(
        default="xcore:cg",
        validation_alias="REDIS_GROUP_PREFIX",
    )
    redis_consumer_name: str = Field(
        default="discord-bot",
        validation_alias="REDIS_CONSUMER_NAME",
    )
    mongo_uri: str = Field(
        default="mongodb://127.0.0.1:27017",
        validation_alias="MONGO_URI",
    )
    mongo_db_name: str = Field(default="xcore", validation_alias="MONGO_DB_NAME")
    rpc_timeout_ms: int = Field(default=5000, validation_alias="RPC_TIMEOUT_MS")
    discord_bans_channel_id: int = Field(
        default=0,
        validation_alias="DISCORD_BANS_CHANNEL_ID",
    )  # 0 = disabled
    discord_mutes_channel_id: int = Field(
        default=0,
        validation_alias="DISCORD_MUTES_CHANNEL_ID",
    )  # 0 = disabled
    discord_guild_id: int = Field(
        default=0,
        validation_alias="DISCORD_GUILD_ID",
    )  # 0 = global slash command sync (slower)
    discord_interaction_hmac_secret: str = Field(
        default="",
        validation_alias="DISCORD_INTERACTION_HMAC_SECRET",
    )
    discord_error_log_channel_id: int | None = Field(
        default=None,
        validation_alias="DISCORD_ERROR_LOG_CHANNEL_ID",
    )

    @field_validator(
        "discord_general_admin_role_id",
        "discord_map_reviewer_role_id",
        "discord_error_log_channel_id",
        mode="before",
    )
    @classmethod
    def _blank_optional_id_as_none(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value

    @field_validator(
        "redis_url",
        "redis_group_prefix",
        "redis_consumer_name",
        "mongo_uri",
        "mongo_db_name",
        mode="before",
    )
    @classmethod
    def _blank_string_as_default(cls, value: object, info) -> object:
        if isinstance(value, str) and not value.strip():
            default = cls.model_fields[info.field_name].default
            return default
        return value

    @field_validator("rpc_timeout_ms", mode="before")
    @classmethod
    def _rpc_timeout_parse(cls, value: object) -> object:
        if isinstance(value, str):
            normalized = value.strip()
            if not normalized:
                return 5000
            if not normalized.lstrip("-").isdigit():
                raise ValueError("RPC_TIMEOUT_MS must be an integer")
        return value

    @property
    def server_channel_map(self) -> dict[str, int]:
        return {}

    @property
    def channel_server_map(self) -> dict[int, str]:
        return {}

    @model_validator(mode="before")
    @classmethod
    def _apply_role_id_fallbacks(cls, data: object) -> object:
        if not isinstance(data, dict):
            return data

        values = dict(data)

        def _get(field_name: str, env_name: str) -> object:
            if field_name in values:
                return values[field_name]
            return values.get(env_name)

        def _is_missing(value: object) -> bool:
            if value is None:
                return True
            return isinstance(value, str) and not value.strip()

        admin_role = _get("discord_admin_role_id", "DISCORD_ADMIN_ROLE_ID")
        if _is_missing(admin_role):
            return values

        general_role = _get(
            "discord_general_admin_role_id",
            "DISCORD_GENERAL_ADMIN_ROLE_ID",
        )
        if _is_missing(general_role):
            if "discord_general_admin_role_id" in values:
                values["discord_general_admin_role_id"] = admin_role
            else:
                values["DISCORD_GENERAL_ADMIN_ROLE_ID"] = admin_role

        reviewer_role = _get(
            "discord_map_reviewer_role_id",
            "DISCORD_MAP_REVIEWER_ROLE_ID",
        )
        if _is_missing(reviewer_role):
            if "discord_map_reviewer_role_id" in values:
                values["discord_map_reviewer_role_id"] = admin_role
            else:
                values["DISCORD_MAP_REVIEWER_ROLE_ID"] = admin_role

        return values

    @model_validator(mode="after")
    def _validate_fields(self) -> "Settings":
        if not self.discord_token.strip():
            raise ValueError("Missing required environment variable: DISCORD_BOT_TOKEN")

        if self.rpc_timeout_ms <= 0:
            raise ValueError("RPC_TIMEOUT_MS must be > 0")

        return self

    @classmethod
    def from_env(cls) -> "Settings":
        try:
            return cls()
        except ValidationError as error:
            details = error.errors()
            if not details:
                raise RuntimeError("Invalid settings") from error

            first = details[0]
            location = first.get("loc") or []
            field_name = location[0] if location else ""
            message = str(first.get("msg", "Invalid settings"))

            required_aliases = {
                "discord_token": "DISCORD_BOT_TOKEN",
                "discord_admin_role_id": "DISCORD_ADMIN_ROLE_ID",
                "discord_private_channel_id": "DISCORD_PRIVATE_CHANNEL_ID",
                "DISCORD_BOT_TOKEN": "DISCORD_BOT_TOKEN",
                "DISCORD_ADMIN_ROLE_ID": "DISCORD_ADMIN_ROLE_ID",
                "DISCORD_PRIVATE_CHANNEL_ID": "DISCORD_PRIVATE_CHANNEL_ID",
            }
            if message == "Field required" and field_name in required_aliases:
                message = (
                    "Missing required environment variable: "
                    f"{required_aliases[field_name]}"
                )
            raise RuntimeError(message) from error
