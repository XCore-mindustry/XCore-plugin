from __future__ import annotations

from unittest.mock import patch

from xcore_discord_bot.registry import LiveServerRegistry


def test_update_server_and_get_channel_for_server() -> None:
    registry = LiveServerRegistry(timeout_sec=90)

    with patch("xcore_discord_bot.registry.time.time", side_effect=[100.0, 100.0]):
        registry.update_server("alpha", 123, 4, 12, "v1", "host.local", 6567)
        channel_id = registry.get_channel_for_server("alpha")

    assert channel_id == 123


def test_update_server_stores_optional_address() -> None:
    registry = LiveServerRegistry(timeout_sec=90)

    with patch("xcore_discord_bot.registry.time.time", side_effect=[100.0, 100.0]):
        registry.update_server("alpha", 123, 4, 12, "v1", "play.xcore.fun", 6567)
        servers = registry.get_all_servers()

    assert len(servers) == 1
    assert servers[0].host == "play.xcore.fun"
    assert servers[0].port == 6567


def test_update_server_overwrites_existing_values() -> None:
    registry = LiveServerRegistry(timeout_sec=90)

    with patch(
        "xcore_discord_bot.registry.time.time", side_effect=[100.0, 140.0, 140.0]
    ):
        registry.update_server("alpha", 123, 4, 12, "v1")
        registry.update_server("alpha", 456, 8, 20, "v2")
        servers = registry.get_all_servers()

    assert len(servers) == 1
    srv = servers[0]
    assert srv.name == "alpha"
    assert srv.channel_id == 456
    assert srv.players == 8
    assert srv.max_players == 20
    assert srv.version == "v2"
    assert srv.last_seen_ts == 140.0


def test_get_server_for_channel() -> None:
    registry = LiveServerRegistry(timeout_sec=90)

    with patch("xcore_discord_bot.registry.time.time", side_effect=[100.0, 100.0]):
        registry.update_server("alpha", 123, 4, 12, "v1")
        server_name = registry.get_server_for_channel(123)

    assert server_name == "alpha"


def test_prune_removes_stale_servers() -> None:
    registry = LiveServerRegistry(timeout_sec=90)

    with patch(
        "xcore_discord_bot.registry.time.time", side_effect=[100.0, 191.0, 191.0, 191.0]
    ):
        registry.update_server("alpha", 123, 4, 12, "v1")
        registry.prune()
        channel_id = registry.get_channel_for_server("alpha")
        server_name = registry.get_server_for_channel(123)

    assert channel_id is None
    assert server_name is None
