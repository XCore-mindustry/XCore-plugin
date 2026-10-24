from __future__ import annotations

from typing import Literal

from pydantic import Field, ValidationError, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from .permissions_config import load_discord_permissions

ROLE_FALLBACK_FIELDS = ("discord_general_admin_role_id", "discord_map_reviewer_role_id")
# Comma-separated lists, where an empty value means "none" rather than "the default".
EMPTY_LIST_FIELDS = frozenset(
    {"cases_badges_requestable", "maps_ignored_tags", "maps_tag_profiles"}
)


class Settings(BaseSettings):
    """Read from the environment; field names match the variable names ignoring case."""

    model_config = SettingsConfigDict(
        extra="ignore", frozen=True, hide_input_in_errors=True
    )

    permissions_mode: Literal["legacy", "roles"] = "legacy"
    permissions_config_path: str = ""
    permissions_rpc_server: str = ""

    discord_bot_token: str
    discord_admin_role_id: int
    # Both fall back to the admin role when unset.
    discord_general_admin_role_id: int | None = None
    discord_map_reviewer_role_id: int | None = None
    discord_private_channel_id: int
    redis_url: str = "redis://127.0.0.1:6379"
    redis_group_prefix: str = "xcore:cg"
    redis_consumer_name: str = "discord-bot"
    mongo_uri: str = "mongodb://127.0.0.1:27017"
    mongo_db_name: str = "xcore"
    rpc_timeout_ms: int = Field(default=5000, gt=0)
    # Channel ids of 0 disable the matching log.
    discord_bans_channel_id: int = 0
    discord_mutes_channel_id: int = 0
    discord_votekicks_channel_id: int = 0
    discord_seasons_channel_id: int = 0
    discord_guild_id: int = 0  # 0 = global slash command sync (slower)
    discord_clear_stale_commands: bool = False
    discord_interaction_hmac_secret: str = ""
    discord_error_log_channel_id: int | None = None
    admin_reconcile_interval_seconds: int = Field(default=300, gt=0)

    cases_reports_enabled: bool = False
    cases_appeals_enabled: bool = False
    cases_badges_enabled: bool = False
    cases_complaints_enabled: bool = False
    cases_maps_enabled: bool = False
    cases_panel_channel_id: int = 0
    cases_staff_forum_id: int = 0
    cases_senior_forum_id: int = 0
    cases_complaint_thread_channel_id: int = 0
    cases_blacklist_role_id: int = 0
    cases_staff_locale: Literal["en", "ru", "uk"] = "en"
    cases_open_cooldown_seconds: int = 60
    cases_max_open_per_user: int = 3
    cases_report_burst_limit: int = 20
    cases_report_burst_window_seconds: int = 600
    cases_appeal_cooldown_days: int = 14
    cases_badges_requestable: str = (
        "translator,map-maker,contributor,bug-finder,event-winner,veteran,developer"
    )
    cases_evidence_retention_days: int = 180
    cases_reporter_cooldown_seconds: int = 60
    cases_notice_ttl_days: int = 30
    discord_maps_forum_id: int = 0
    maps_tag_waiting: str = "Waiting"
    maps_tag_accepted: str = "Accepted"
    maps_tag_rejected: str = "Rejected"
    maps_ignored_tags: str = "Report a map,Balance a map"
    maps_tag_profiles: str = (
        "Tower Defence=tower-defense,PvP=pvp,Survival=survival,Attack=attack"
    )
    maps_max_file_bytes: int = 4194304
    discord_map_maker_role_id: int = 0

    @model_validator(mode="before")
    @classmethod
    def _apply_defaults(cls, data: object) -> object:
        if not isinstance(data, dict):
            return data
        # Compose passes an unset variable as an empty string: treat blank as unset.
        values = {
            key: value
            for key, value in data.items()
            if key in EMPTY_LIST_FIELDS
            or not (isinstance(value, str) and not value.strip())
        }
        if "discord_admin_role_id" in values:
            for name in ROLE_FALLBACK_FIELDS:
                values.setdefault(name, values["discord_admin_role_id"])
        return values

    @model_validator(mode="after")
    def _validate_roles_mode(self) -> Settings:
        if self.permissions_mode == "roles":
            if not self.permissions_rpc_server.strip():
                raise ValueError("PERMISSIONS_RPC_SERVER is required in roles mode")
            load_discord_permissions(
                self.permissions_config_path, self.discord_guild_id
            )
        return self

    @property
    def roles_mode(self) -> bool:
        return self.permissions_mode == "roles"

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

    @classmethod
    def from_env(cls) -> Settings:
        """Like ``Settings()``, but the error names the variables and leaves out their
        values: a missing field would otherwise print every setting, secrets included."""
        try:
            return cls()
        except ValidationError as error:
            problems = []
            for item in error.errors():
                name = "_".join(str(part) for part in item["loc"]).upper()
                if item["type"] == "missing":
                    problems.append(f"Missing required environment variable: {name}")
                else:
                    problems.append(f"{name}: {item['msg']}" if name else item["msg"])
            raise RuntimeError("; ".join(problems)) from None
