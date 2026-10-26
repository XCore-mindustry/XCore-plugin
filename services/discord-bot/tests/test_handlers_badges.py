from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest
from backends import published, real_bot

from xcore_discord_bot.handlers_badges import cmd_badge_grant, cmd_badge_revoke


@dataclass
class _User:
    display_name: str = "admin"


@dataclass
class _Response:
    sent: list[tuple[str, bool]] = field(default_factory=list)

    async def send_message(self, text: str, *, ephemeral: bool = False) -> None:
        self.sent.append((text, ephemeral))


@dataclass
class _Interaction:
    id: int
    user: _User = field(default_factory=_User)
    response: _Response = field(default_factory=_Response)


INVENTORY = "xcore:cmd:player-badge-inventory:mini-pvp"


async def _bot(**player):
    bot, db = await real_bot()
    bot.container.servers.update_server("mini-pvp", 1, 0, 10, "v1")
    # The plugin writes both badge fields for every player.
    await db["players"].insert_one(
        {
            "_id": 1,
            "pid": 7,
            "uuid": "uuid-7",
            "nickname": "Target",
            "unlocked_badges": [],
            "active_badge": "",
            **player,
        }
    )
    return bot


async def _inventory(bot) -> list[tuple[str, str | None, list[str]]]:
    return [
        (event["playerUuid"], event["activeBadge"], event["unlockedBadges"])
        for event in await published(bot.bus, INVENTORY)
    ]


async def _claimed(bot, key: str) -> bool:
    return bool(await bot.bus.redis.exists(f"xcore:idmp:{key}"))


@pytest.mark.asyncio
async def test_cmd_badge_grant_grants_badge() -> None:
    bot = await _bot(active_badge="translator")
    interaction = _Interaction(id=11)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert await _claimed(bot, "cmd:badge-grant:11:7:translator")
    player = await bot.store.find_player_by_pid(7)
    assert player.unlocked_badges == ("translator",)
    assert await _inventory(bot) == [("uuid-7", "translator", ["translator"])]
    assert interaction.response.sent == [
        ("Granted badge `translator` to `Target`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_grant_reports_existing_badge() -> None:
    bot = await _bot(unlocked_badges=["translator"])
    interaction = _Interaction(id=12)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert await _inventory(bot) == []
    assert interaction.response.sent == [
        ("Player already has badge `translator`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_revoke_revokes_badge() -> None:
    bot = await _bot(unlocked_badges=["translator"], active_badge="translator")
    interaction = _Interaction(id=13)

    await cmd_badge_revoke(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert await _claimed(bot, "cmd:badge-revoke:13:7:translator")
    player = await bot.store.find_player_by_pid(7)
    # Revoking the worn badge also takes it off.
    assert player.unlocked_badges == () and not player.active_badge
    assert await _inventory(bot) == [("uuid-7", "", [])]
    assert interaction.response.sent == [
        ("Revoked badge `translator` from `Target`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_revoke_reports_missing_badge() -> None:
    bot = await _bot()
    interaction = _Interaction(id=14)

    await cmd_badge_revoke(cast(Any, bot), cast(Any, interaction), 7, "translator")

    assert await _inventory(bot) == []
    assert interaction.response.sent == [
        ("Player does not have badge `translator`", False)
    ]


@pytest.mark.asyncio
async def test_cmd_badge_change_rejects_unknown_badge() -> None:
    bot = await _bot()
    interaction = _Interaction(id=15)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "unknown")

    assert interaction.response.sent == [("Badge `unknown` was not found.", True)]


@pytest.mark.asyncio
async def test_cmd_badge_change_rejects_system_badge() -> None:
    bot = await _bot()
    interaction = _Interaction(id=16)

    await cmd_badge_grant(cast(Any, bot), cast(Any, interaction), 7, "admin")

    assert interaction.response.sent == [
        ("Badge `admin` cannot be granted or revoked manually.", True)
    ]
