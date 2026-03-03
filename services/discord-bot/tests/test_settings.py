from __future__ import annotations

import pytest
from pydantic import ValidationError

from xcore_discord_bot.settings import Settings


ENV_KEYS = [
    "DISCORD_BOT_TOKEN",
    "DISCORD_ADMIN_ROLE_ID",
    "DISCORD_GENERAL_ADMIN_ROLE_ID",
    "DISCORD_MAP_REVIEWER_ROLE_ID",
    "DISCORD_PRIVATE_CHANNEL_ID",
    "DISCORD_BANS_CHANNEL_ID",
    "DISCORD_GUILD_ID",
    "DISCORD_INTERACTION_HMAC_SECRET",
    "REDIS_URL",
    "REDIS_GROUP_PREFIX",
    "REDIS_CONSUMER_NAME",
    "MONGO_URI",
    "MONGO_DB_NAME",
    "RPC_TIMEOUT_MS",
]


@pytest.fixture(autouse=True)
def clean_settings_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in ENV_KEYS:
        monkeypatch.delenv(key, raising=False)


def _set_required_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("DISCORD_BOT_TOKEN", "token")
    monkeypatch.setenv("DISCORD_ADMIN_ROLE_ID", "10")
    monkeypatch.setenv("DISCORD_PRIVATE_CHANNEL_ID", "20")


def test_settings_from_env_required_token_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("DISCORD_ADMIN_ROLE_ID", "10")
    monkeypatch.setenv("DISCORD_PRIVATE_CHANNEL_ID", "20")

    with pytest.raises(
        RuntimeError,
        match="Missing required environment variable: DISCORD_BOT_TOKEN",
    ):
        Settings.from_env()


def test_settings_from_env_role_fallbacks(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_required_env(monkeypatch)

    settings = Settings.from_env()

    assert settings.discord_admin_role_id == 10
    assert settings.discord_general_admin_role_id == 10
    assert settings.discord_map_reviewer_role_id == 10


def test_settings_from_env_blank_optional_strings_use_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _set_required_env(monkeypatch)
    monkeypatch.setenv("REDIS_URL", "   ")
    monkeypatch.setenv("REDIS_GROUP_PREFIX", "")
    monkeypatch.setenv("REDIS_CONSUMER_NAME", " ")
    monkeypatch.setenv("MONGO_URI", "")
    monkeypatch.setenv("MONGO_DB_NAME", "")

    settings = Settings.from_env()

    assert settings.redis_url == "redis://127.0.0.1:6379"
    assert settings.redis_group_prefix == "xcore:cg"
    assert settings.redis_consumer_name == "discord-bot"
    assert settings.mongo_uri == "mongodb://127.0.0.1:27017"
    assert settings.mongo_db_name == "xcore"


def test_settings_from_env_rpc_timeout_must_be_integer(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _set_required_env(monkeypatch)
    monkeypatch.setenv("RPC_TIMEOUT_MS", "abc")

    with pytest.raises(RuntimeError, match="RPC_TIMEOUT_MS must be an integer"):
        Settings.from_env()


def test_settings_from_env_rpc_timeout_positive(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _set_required_env(monkeypatch)
    monkeypatch.setenv("RPC_TIMEOUT_MS", "0")

    with pytest.raises(RuntimeError, match="RPC_TIMEOUT_MS must be > 0"):
        Settings.from_env()


def test_settings_is_frozen_model() -> None:
    settings = Settings(
        discord_token="token",
        discord_admin_role_id=10,
        discord_private_channel_id=20,
    )

    with pytest.raises(ValidationError):
        settings.discord_token = "new-token"
