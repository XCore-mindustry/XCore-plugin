from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import pytest

from xcore_discord_bot.bot import MSG_NO_ACTIVE_BAN, MSG_NO_ACTIVE_MUTE, XCoreDiscordBot


@dataclass
class _User:
    id: int
    display_name: str


@dataclass
class _Response:
    calls: list[dict[str, Any]] = field(default_factory=list)

    async def send_message(
        self,
        content: str | None = None,
        *,
        embed: Any = None,
        ephemeral: bool = False,
    ) -> None:
        self.calls.append({"content": content, "embed": embed, "ephemeral": ephemeral})


@dataclass
class _Interaction:
    id: int
    user: _User
    response: _Response = field(default_factory=_Response)


class _Bus:
    def __init__(self) -> None:
        self.pardon_calls: list[str] = []

    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:  # noqa: ARG002
        return True

    async def publish_pardon_player(self, uuid_value: str) -> None:
        self.pardon_calls.append(uuid_value)


class _Store:
    async def find_player_by_pid(self, pid: int) -> dict[str, object] | None:
        if pid != 123:
            return None
        return {
            "pid": 123,
            "uuid": "uuid-123",
            "ip": "1.2.3.4",
            "nickname": "Vortex",
        }

    async def find_ban(self, *, uuid: str, ip: str | None) -> dict[str, object] | None:  # noqa: ARG002
        return {
            "admin_name": "mod-1",
            "reason": "griefing",
            "expire_date": datetime(2026, 12, 31, 15, 0, tzinfo=timezone.utc),
        }

    async def delete_ban(self, *, uuid: str, ip: str | None) -> int:  # noqa: ARG002
        return 1

    async def find_mute(self, *, uuid: str) -> dict[str, object] | None:  # noqa: ARG002
        return {
            "admin_name": "mod-2",
            "reason": "spam",
            "expire_date": datetime(2026, 12, 31, 16, 0, tzinfo=timezone.utc),
        }

    async def delete_mute(self, *, uuid: str) -> int:  # noqa: ARG002
        return 1


class _NoActiveStore(_Store):
    async def delete_ban(self, *, uuid: str, ip: str | None) -> int:  # noqa: ARG002
        return 0

    async def delete_mute(self, *, uuid: str) -> int:  # noqa: ARG002
        return 0


@pytest.mark.asyncio
async def test_cmd_unban_sends_rich_embed() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bus = _Bus()
    bot.__dict__["_bus"] = bus
    bot.__dict__["_store"] = _Store()

    interaction = _Interaction(id=1, user=_User(id=7, display_name="admin-x"))
    await XCoreDiscordBot._cmd_unban(bot, interaction, 123)

    assert bus.pardon_calls == ["uuid-123"]
    assert len(interaction.response.calls) == 1
    call = interaction.response.calls[0]
    assert call["content"] is None
    assert call["ephemeral"] is False

    embed = call["embed"]
    assert embed is not None
    assert embed.title == "Unbanned Vortex"
    fields = {field.name: field.value for field in embed.fields}
    assert fields["PID"] == "123"
    assert fields["Admin who banned"] == "mod-1"
    assert fields["Reason"] == "griefing"
    assert "<t:" in fields["Was set to expire"]
    assert fields["Unbanned by"] == "admin-x"


@pytest.mark.asyncio
async def test_cmd_unmute_sends_rich_embed() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_bus"] = _Bus()
    bot.__dict__["_store"] = _Store()

    interaction = _Interaction(id=2, user=_User(id=7, display_name="admin-y"))
    await XCoreDiscordBot._cmd_unmute(bot, interaction, 123)

    assert len(interaction.response.calls) == 1
    call = interaction.response.calls[0]
    embed = call["embed"]
    assert embed is not None
    assert embed.title == "Unmuted Vortex"
    fields = {field.name: field.value for field in embed.fields}
    assert fields["PID"] == "123"
    assert fields["Admin who muted"] == "mod-2"
    assert fields["Reason"] == "spam"
    assert "<t:" in fields["Was set to expire"]
    assert fields["Unmuted by"] == "admin-y"


@pytest.mark.asyncio
async def test_cmd_unban_no_active_ban_message() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_bus"] = _Bus()
    bot.__dict__["_store"] = _NoActiveStore()

    interaction = _Interaction(id=3, user=_User(id=7, display_name="admin"))
    await XCoreDiscordBot._cmd_unban(bot, interaction, 123)

    assert interaction.response.calls == [
        {"content": MSG_NO_ACTIVE_BAN, "embed": None, "ephemeral": True}
    ]


@pytest.mark.asyncio
async def test_cmd_unmute_no_active_mute_message() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_bus"] = _Bus()
    bot.__dict__["_store"] = _NoActiveStore()

    interaction = _Interaction(id=4, user=_User(id=7, display_name="admin"))
    await XCoreDiscordBot._cmd_unmute(bot, interaction, 123)

    assert interaction.response.calls == [
        {"content": MSG_NO_ACTIVE_MUTE, "embed": None, "ephemeral": True}
    ]
