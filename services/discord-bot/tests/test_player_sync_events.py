from __future__ import annotations

from types import MethodType, SimpleNamespace

import pytest

from xcore_protocol.generated.chat import (
    PlayerActiveBadgeChangedCommandV1,
    PlayerBadgeInventoryChangedCommandV1,
    PlayerPasswordResetCommandV1,
)

from xcore_discord_bot.redis_bus import RedisBus


def _bus() -> RedisBus:
    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    return RedisBus(settings)


@pytest.mark.asyncio
async def test_publish_player_active_badge_changed_canonical_payload() -> None:
    bus = _bus()
    captured: dict[str, object] = {}

    async def fake_publish_for_all_servers(self, **kwargs):  # noqa: ANN001
        captured.update(kwargs)

    bus._publish_for_all_servers = MethodType(fake_publish_for_all_servers, bus)

    await bus.publish_player_active_badge_changed(
        uuid_value="uuid-7",
        active_badge="translator",
    )

    assert captured["stream_prefix"] == "xcore:cmd:player-active-badge"
    assert captured["event_type"] == "player.active-badge.changed.command"
    payload_builder = captured["payload_builder"]
    payload = payload_builder("mini-pvp")
    parsed = PlayerActiveBadgeChangedCommandV1.from_payload(payload)
    assert parsed.playerUuid == "uuid-7"
    assert parsed.activeBadge == "translator"
    assert parsed.server == "mini-pvp"


@pytest.mark.asyncio
async def test_publish_player_badge_inventory_changed_canonical_payload() -> None:
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
    assert captured["event_type"] == "player.badge-inventory.changed.command"
    payload_builder = captured["payload_builder"]
    payload = payload_builder("mini-pvp")
    parsed = PlayerBadgeInventoryChangedCommandV1.from_payload(payload)
    assert parsed.playerUuid == "uuid-7"
    assert parsed.activeBadge == "translator"
    assert parsed.unlockedBadges == ("translator", "tester")
    assert parsed.server == "mini-pvp"


@pytest.mark.asyncio
async def test_publish_player_password_reset_canonical_payload() -> None:
    bus = _bus()
    captured: dict[str, object] = {}

    async def fake_publish_for_all_servers(self, **kwargs):  # noqa: ANN001
        captured.update(kwargs)

    bus._publish_for_all_servers = MethodType(fake_publish_for_all_servers, bus)

    await bus.publish_player_password_reset(uuid_value="uuid-7")

    assert captured["stream_prefix"] == "xcore:cmd:player-password-reset"
    assert captured["event_type"] == "player.password-reset.command"
    payload_builder = captured["payload_builder"]
    payload = payload_builder("mini-pvp")
    parsed = PlayerPasswordResetCommandV1.from_payload(payload)
    assert parsed.playerUuid == "uuid-7"
    assert parsed.server == "mini-pvp"
