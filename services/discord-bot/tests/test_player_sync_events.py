from __future__ import annotations

from types import MethodType, SimpleNamespace

import pytest
from xcore_protocol.generated.identity import (
    PlayerBadgeInventoryChangedCommandV1,
)
from xcore_protocol.generated.security import (
    PlayerPasswordResetCommandV1,
)

from xcore_discord_bot.bus.accounts import AccountsBus
from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.registry import LiveServerRegistry


def _bus() -> RedisTransport:
    settings = SimpleNamespace(
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="discord-bot",
    )
    return RedisTransport(settings)


@pytest.mark.asyncio
async def test_publish_player_badge_inventory_changed_canonical_payload() -> None:
    transport = _bus()
    bus = AccountsBus(transport, LiveServerRegistry())
    captured: dict[str, object] = {}

    async def fake_publish_for_all_servers(self, **kwargs):
        captured.update(kwargs)

    transport.publish_to_all_servers = MethodType(
        fake_publish_for_all_servers, transport
    )

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
    transport = _bus()
    bus = AccountsBus(transport, LiveServerRegistry())
    captured: dict[str, object] = {}

    async def fake_publish_for_all_servers(self, **kwargs):
        captured.update(kwargs)

    transport.publish_to_all_servers = MethodType(
        fake_publish_for_all_servers, transport
    )

    await bus.publish_player_password_reset(uuid_value="uuid-7")

    assert captured["stream_prefix"] == "xcore:cmd:player-password-reset"
    assert captured["event_type"] == "player.password-reset.command"
    payload_builder = captured["payload_builder"]
    payload = payload_builder("mini-pvp")
    parsed = PlayerPasswordResetCommandV1.from_payload(payload)
    assert parsed.playerUuid == "uuid-7"
    assert parsed.server == "mini-pvp"
