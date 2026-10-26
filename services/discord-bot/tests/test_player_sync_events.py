from __future__ import annotations

import pytest
from backends import connected_bus, published
from xcore_protocol.generated.identity import (
    PlayerBadgeInventoryChangedCommandV1,
)
from xcore_protocol.generated.security import (
    PlayerPasswordResetCommandV1,
)

from xcore_discord_bot.bus.accounts import AccountsBus
from xcore_discord_bot.bus.transport import RedisTransport
from xcore_discord_bot.registry import LiveServerRegistry


async def _bus() -> tuple[AccountsBus, RedisTransport]:
    servers = LiveServerRegistry()
    servers.update_server("mini-pvp", 1, 0, 10, "v1")
    transport = await connected_bus()
    return AccountsBus(transport, servers), transport


@pytest.mark.asyncio
async def test_publish_player_badge_inventory_changed_canonical_payload() -> None:
    bus, transport = await _bus()

    await bus.publish_player_badge_inventory_changed(
        uuid_value="uuid-7",
        active_badge="translator",
        unlocked_badges=("translator", "tester"),
    )

    stream = "xcore:cmd:player-badge-inventory:mini-pvp"
    (payload,) = await published(transport, stream)
    parsed = PlayerBadgeInventoryChangedCommandV1.from_payload(payload)
    assert parsed.playerUuid == "uuid-7"
    assert parsed.activeBadge == "translator"
    assert parsed.unlockedBadges == ("translator", "tester")
    assert parsed.server == "mini-pvp"


@pytest.mark.asyncio
async def test_publish_player_password_reset_canonical_payload() -> None:
    bus, transport = await _bus()

    await bus.publish_player_password_reset(uuid_value="uuid-7")

    stream = "xcore:cmd:player-password-reset:mini-pvp"
    (payload,) = await published(transport, stream)
    parsed = PlayerPasswordResetCommandV1.from_payload(payload)
    assert parsed.playerUuid == "uuid-7"
    assert parsed.server == "mini-pvp"
