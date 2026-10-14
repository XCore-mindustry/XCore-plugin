from __future__ import annotations

from typing import Literal

from pydantic import Field, ValidationError, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        extra="ignore",
        populate_by_name=True,
        frozen=True,
    )

    permissions_mode: Literal["legacy", "roles"] = Field(
        default="legacy", validation_alias="PERMISSIONS_MODE"
    )
    permissions_config_path: str = Field(
        default="", validation_alias="PERMISSIONS_CONFIG_PATH"
    )
    permissions_rpc_server: str = Field(
        default="", validation_alias="PERMISSIONS_RPC_SERVER"
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
    discord_votekicks_channel_id: int = Field(
        default=0,
        validation_alias="DISCORD_VOTEKICKS_CHANNEL_ID",
    )  # 0 = disabled
    discord_seasons_channel_id: int = Field(
        default=0,
        validation_alias="DISCORD_SEASONS_CHANNEL_ID",
    )  # 0 = disabled
    discord_guild_id: int = Field(
        default=0,
        validation_alias="DISCORD_GUILD_ID",
    )  # 0 = global slash command sync (slower)
    discord_clear_stale_commands: bool = Field(
        default=False,
        validation_alias="DISCORD_CLEAR_STALE_COMMANDS",
    )
    discord_interaction_hmac_secret: str = Field(
        default="",
        validation_alias="DISCORD_INTERACTION_HMAC_SECRET",
    )
    discord_error_log_channel_id: int | None = Field(
        default=None,
        validation_alias="DISCORD_ERROR_LOG_CHANNEL_ID",
    )
    admin_reconcile_interval_seconds: int = Field(
        default=300,
        validation_alias="ADMIN_RECONCILE_INTERVAL_SECONDS",
    )

    cases_reports_enabled: bool = Field(
        default=False, validation_alias="CASES_REPORTS_ENABLED"
    )
    cases_appeals_enabled: bool = Field(
        default=False, validation_alias="CASES_APPEALS_ENABLED"
    )
    cases_badges_enabled: bool = Field(
        default=False, validation_alias="CASES_BADGES_ENABLED"
    )
    cases_complaints_enabled: bool = Field(
        default=False, validation_alias="CASES_COMPLAINTS_ENABLED"
    )
    cases_maps_enabled: bool = Field(
        default=False, validation_alias="CASES_MAPS_ENABLED"
    )
    cases_panel_channel_id: int = Field(
        default=0, validation_alias="CASES_PANEL_CHANNEL_ID"
    )
    cases_staff_forum_id: int = Field(
        default=0, validation_alias="CASES_STAFF_FORUM_ID"
    )
    cases_senior_forum_id: int = Field(
        default=0, validation_alias="CASES_SENIOR_FORUM_ID"
    )
    cases_complaint_thread_channel_id: int = Field(
        default=0, validation_alias="CASES_COMPLAINT_THREAD_CHANNEL_ID"
    )
    cases_blacklist_role_id: int = Field(
        default=0, validation_alias="CASES_BLACKLIST_ROLE_ID"
    )
    cases_staff_locale: Literal["en", "ru", "uk"] = Field(
        default="en", validation_alias="CASES_STAFF_LOCALE"
    )
    cases_open_cooldown_seconds: int = Field(
        default=60, validation_alias="CASES_OPEN_COOLDOWN_SECONDS"
    )
    cases_max_open_per_user: int = Field(
        default=3, validation_alias="CASES_MAX_OPEN_PER_USER"
    )
    cases_report_burst_limit: int = Field(
        default=20, validation_alias="CASES_REPORT_BURST_LIMIT"
    )
    cases_report_burst_window_seconds: int = Field(
        default=600, validation_alias="CASES_REPORT_BURST_WINDOW_SECONDS"
    )
    cases_appeal_cooldown_days: int = Field(
        default=14, validation_alias="CASES_APPEAL_COOLDOWN_DAYS"
    )
    cases_badges_requestable: str = Field(
        default="translator,map-maker,contributor,bug-finder,event-winner,veteran,developer",
        validation_alias="CASES_BADGES_REQUESTABLE",
    )
    cases_evidence_retention_days: int = Field(
        default=180, validation_alias="CASES_EVIDENCE_RETENTION_DAYS"
    )
    cases_notice_ttl_days: int = Field(
        default=30, validation_alias="CASES_NOTICE_TTL_DAYS"
    )
    discord_maps_forum_id: int = Field(
        default=0, validation_alias="DISCORD_MAPS_FORUM_ID"
    )
    maps_tag_waiting: str = Field(
        default="Waiting", validation_alias="MAPS_TAG_WAITING"
    )
    maps_tag_accepted: str = Field(
        default="Accepted", validation_alias="MAPS_TAG_ACCEPTED"
    )
    maps_tag_rejected: str = Field(
        default="Rejected", validation_alias="MAPS_TAG_REJECTED"
    )
    maps_ignored_tags: str = Field(
        default="Report a map,Balance a map", validation_alias="MAPS_IGNORED_TAGS"
    )
    maps_tag_profiles: str = Field(
        default="Tower Defence=tower-defense,PvP=pvp,Survival=survival,Attack=attack",
        validation_alias="MAPS_TAG_PROFILES",
    )
    maps_max_file_bytes: int = Field(
        default=4194304, validation_alias="MAPS_MAX_FILE_BYTES"
    )
    discord_map_maker_role_id: int = Field(
        default=0, validation_alias="DISCORD_MAP_MAKER_ROLE_ID"
    )

    @field_validator(
        "cases_panel_channel_id",
        "cases_staff_forum_id",
        "cases_senior_forum_id",
        "cases_complaint_thread_channel_id",
        "cases_blacklist_role_id",
        "discord_maps_forum_id",
        "discord_map_maker_role_id",
        mode="before",
    )
    @classmethod
    def _blank_case_id_as_zero(cls, value: object) -> object:
        return 0 if isinstance(value, str) and not value.strip() else value

    @property
    def cases_enabled(self) -> bool:
        return any(
            (
                self.cases_reports_enabled,
                self.cases_appeals_enabled,
                self.cases_badges_enabled,
                self.cases_complaints_enabled,
                self.cases_maps_enabled,
            )
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

    @field_validator("admin_reconcile_interval_seconds", mode="before")
    @classmethod
    def _admin_reconcile_interval_parse(cls, value: object) -> object:
        if isinstance(value, str):
            normalized = value.strip()
            if not normalized:
                return 300
            if not normalized.lstrip("-").isdigit():
                raise ValueError("ADMIN_RECONCILE_INTERVAL_SECONDS must be an integer")
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
    def _validate_fields(self) -> Settings:
        if not self.discord_token.strip():
            raise ValueError("Missing required environment variable: DISCORD_BOT_TOKEN")

        if self.rpc_timeout_ms <= 0:
            raise ValueError("RPC_TIMEOUT_MS must be > 0")

        if self.admin_reconcile_interval_seconds <= 0:
            raise ValueError("ADMIN_RECONCILE_INTERVAL_SECONDS must be > 0")

        if self.permissions_mode == "roles":
            if not self.permissions_rpc_server.strip():
                raise ValueError("PERMISSIONS_RPC_SERVER is required in roles mode")
            from .permissions_config import load_discord_permissions

            load_discord_permissions(
                self.permissions_config_path, self.discord_guild_id
            )

        return self

    @classmethod
    def from_env(cls) -> Settings:
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
