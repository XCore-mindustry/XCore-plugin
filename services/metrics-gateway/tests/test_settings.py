from __future__ import annotations

import pytest

from xcore_metrics_gateway.settings import Settings


def test_settings_defaults_are_valid() -> None:
    settings = Settings()

    assert settings.gateway_http_host == "0.0.0.0"
    assert settings.gateway_http_port == 9100
    assert settings.redis_url == "redis://127.0.0.1:6379"
    assert settings.redis_command_timeout_ms == 500
    assert settings.stale_snapshot_age_seconds == 45


def test_settings_reject_non_positive_limits(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MAX_TOTAL_SERIES", "0")

    with pytest.raises(ValueError, match="max_total_series must be > 0"):
        Settings()


def test_settings_blank_strings_fall_back_to_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("GATEWAY_HTTP_HOST", "   ")
    monkeypatch.setenv("REDIS_URL", "")

    settings = Settings()

    assert settings.gateway_http_host == "0.0.0.0"
    assert settings.redis_url == "redis://127.0.0.1:6379"
