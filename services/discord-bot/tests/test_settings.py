from __future__ import annotations

import pytest

from xcore_discord_bot.settings import Settings


def test_settings_from_env_success(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("DISCORD_BOT_TOKEN", "token")
    monkeypatch.setenv("DISCORD_ADMIN_ROLE_ID", "100")
    monkeypatch.setenv("DISCORD_PRIVATE_CHANNEL_ID", "200")
    monkeypatch.setenv(
        "SERVER_CHANNEL_MAP_JSON", '{"mini-pvp": 123, "mini-hexed": 456}'
    )

    settings = Settings.from_env()

    assert settings.discord_token == "token"
    assert settings.discord_admin_role_id == 100
    assert settings.discord_general_admin_role_id == 100
    assert settings.discord_map_reviewer_role_id == 100
    assert settings.discord_private_channel_id == 200
    assert settings.server_channel_map["mini-pvp"] == 123
    assert settings.channel_server_map[456] == "mini-hexed"


def test_settings_invalid_server_channel_json(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("DISCORD_BOT_TOKEN", "token")
    monkeypatch.setenv("DISCORD_ADMIN_ROLE_ID", "100")
    monkeypatch.setenv("DISCORD_PRIVATE_CHANNEL_ID", "200")
    monkeypatch.setenv("SERVER_CHANNEL_MAP_JSON", "[]")

    with pytest.raises(RuntimeError, match="SERVER_CHANNEL_MAP_JSON"):
        Settings.from_env()
