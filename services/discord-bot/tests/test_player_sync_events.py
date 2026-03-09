from __future__ import annotations

from types import MethodType, SimpleNamespace

import pytest

from xcore_discord_bot.redis_bus import RedisBus


def _bus() -> RedisBus:
    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    return RedisBus(settings)


@pytest.mark.asyncio
async def test_publish_player_badge_inventory_changed_uses_plugin_contract() -> None:
    bus = _bus()
    captured: dict[str, object] = {}

    async def fake_publish_for_all_servers(self, **kwargs):  # noqa: ANN001
        captured.update(kwargs)

    bus._publish_for_all_servers = MethodType(fake_publish_for_all_servers, bus)

    await bus.publish_player_badge_inventory_changed(
        uuid_value="uuid-7",
        active_badge="translator",
        unlocked_badges=("translator", "tester"),
    )

    assert captured["stream_prefix"] == "xcore:cmd:player-badge-inventory"
    assert captured["event_type"] == "player.badge_inventory"
    payload_builder = captured["payload_builder"]
    payload = payload_builder("mini-pvp")
    assert payload == {
        "uuid": "uuid-7",
        "activeBadge": "translator",
        "unlockedBadges": ["translator", "tester"],
        "server": "mini-pvp",
    }


@pytest.mark.asyncio
async def test_publish_player_password_reset_uses_plugin_contract() -> None:
    bus = _bus()
    captured: dict[str, object] = {}

    async def fake_publish_for_all_servers(self, **kwargs):  # noqa: ANN001
        captured.update(kwargs)

    bus._publish_for_all_servers = MethodType(fake_publish_for_all_servers, bus)

    await bus.publish_player_password_reset(uuid_value="uuid-7")

    assert captured["stream_prefix"] == "xcore:cmd:player-password-reset"
    assert captured["event_type"] == "player.password_reset"
    payload_builder = captured["payload_builder"]
    payload = payload_builder("mini-pvp")
    assert payload == {
        "uuid": "uuid-7",
        "server": "mini-pvp",
    }
