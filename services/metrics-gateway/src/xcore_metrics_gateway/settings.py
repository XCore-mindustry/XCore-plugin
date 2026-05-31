from __future__ import annotations

from pydantic import Field, ValidationError, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        extra="ignore",
        populate_by_name=True,
        frozen=True,
    )

    gateway_http_host: str = Field(
        default="0.0.0.0", validation_alias="GATEWAY_HTTP_HOST"
    )
    gateway_http_port: int = Field(default=9100, validation_alias="GATEWAY_HTTP_PORT")
    redis_url: str = Field(
        default="redis://127.0.0.1:6379", validation_alias="REDIS_URL"
    )
    redis_discovery_interval_ms: int = Field(
        default=30000, validation_alias="REDIS_DISCOVERY_INTERVAL_MS"
    )
    redis_poll_interval_ms: int = Field(
        default=3000, validation_alias="REDIS_POLL_INTERVAL_MS"
    )
    redis_scan_count: int = Field(default=100, validation_alias="REDIS_SCAN_COUNT")
    redis_mget_batch_size: int = Field(
        default=100, validation_alias="REDIS_MGET_BATCH_SIZE"
    )
    redis_command_timeout_ms: int = Field(
        default=500, validation_alias="REDIS_COMMAND_TIMEOUT_MS"
    )
    max_servers: int = Field(default=200, validation_alias="MAX_SERVERS")
    max_series_per_server: int = Field(
        default=5000, validation_alias="MAX_SERIES_PER_SERVER"
    )
    max_total_series: int = Field(default=250000, validation_alias="MAX_TOTAL_SERIES")
    max_labels_per_metric: int = Field(
        default=8, validation_alias="MAX_LABELS_PER_METRIC"
    )
    max_label_value_length: int = Field(
        default=80, validation_alias="MAX_LABEL_VALUE_LENGTH"
    )
    max_compressed_snapshot_bytes: int = Field(
        default=131072, validation_alias="MAX_COMPRESSED_SNAPSHOT_BYTES"
    )
    max_uncompressed_snapshot_bytes: int = Field(
        default=524288, validation_alias="MAX_UNCOMPRESSED_SNAPSHOT_BYTES"
    )

    @field_validator(
        "gateway_http_host",
        "redis_url",
        mode="before",
    )
    @classmethod
    def _blank_string_as_default(cls, value: object, info) -> object:
        if isinstance(value, str) and not value.strip():
            return cls.model_fields[info.field_name].default
        return value

    @model_validator(mode="after")
    def _validate_fields(self) -> "Settings":
        positive_fields = {
            "gateway_http_port": self.gateway_http_port,
            "redis_discovery_interval_ms": self.redis_discovery_interval_ms,
            "redis_poll_interval_ms": self.redis_poll_interval_ms,
            "redis_scan_count": self.redis_scan_count,
            "redis_mget_batch_size": self.redis_mget_batch_size,
            "redis_command_timeout_ms": self.redis_command_timeout_ms,
            "max_servers": self.max_servers,
            "max_series_per_server": self.max_series_per_server,
            "max_total_series": self.max_total_series,
            "max_labels_per_metric": self.max_labels_per_metric,
            "max_label_value_length": self.max_label_value_length,
            "max_compressed_snapshot_bytes": self.max_compressed_snapshot_bytes,
            "max_uncompressed_snapshot_bytes": self.max_uncompressed_snapshot_bytes,
        }
        for field_name, value in positive_fields.items():
            if value <= 0:
                raise ValueError(f"{field_name} must be > 0")
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
            raise RuntimeError(str(first.get("msg", "Invalid settings"))) from error
