from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest

from xcore_discord_bot.handlers_misc import cmd_maps, perform_remove_map


@dataclass
class _DeferredResponse:
    deferred: bool = False

    async def defer(self) -> None:
        self.deferred = True


@dataclass
class _Followup:
    messages: list[dict[str, Any]] = field(default_factory=list)

    async def send(
        self, content: str | None = None, *, embed: Any = None, view: Any = None
    ):
        message = {"content": content, "embed": embed, "view": view}
        self.messages.append(message)
        return message


@dataclass
class _Interaction:
    response: _DeferredResponse = field(default_factory=_DeferredResponse)
    followup: _Followup = field(default_factory=_Followup)


class _MapsBot:
    def __init__(
        self, maps: list[dict[str, str]] | None = None, *, fail: bool = False
    ) -> None:
        self.maps = maps or []
        self.fail = fail
        self.rpc_timeout_ms = 4321

    async def rpc_maps_list(
        self, *, server: str, timeout_ms: int
    ) -> list[dict[str, str]]:
        assert server == "mini-pvp"
        assert timeout_ms == 4321
        if self.fail:
            raise TimeoutError
        return self.maps


class _RemoveMapBot:
    def __init__(self, *, claim: bool = True, fail: bool = False) -> None:
        self.claim = claim
        self.fail = fail
        self.rpc_timeout_ms = 9876
        self.claim_keys: list[tuple[str, int]] = []
        self.remove_calls: list[tuple[str, str, int]] = []

    async def claim_idempotency(self, key: str, *, ttl_seconds: int = 600) -> bool:
        self.claim_keys.append((key, ttl_seconds))
        return self.claim

    async def rpc_remove_map(
        self, *, server: str, file_name: str, timeout_ms: int
    ) -> str:
        self.remove_calls.append((server, file_name, timeout_ms))
        if self.fail:
            raise TimeoutError
        return "removed"


@pytest.mark.asyncio
async def test_cmd_maps_reports_timeout() -> None:
    bot = _MapsBot(fail=True)
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    assert interaction.response.deferred is True
    assert interaction.followup.messages == [
        {
            "content": "No response from target server (timeout).",
            "embed": None,
            "view": None,
        }
    ]


@pytest.mark.asyncio
async def test_cmd_maps_reports_empty_server_map_list() -> None:
    bot = _MapsBot(maps=[])
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    assert interaction.response.deferred is True
    assert interaction.followup.messages == [
        {"content": "No maps found on server `mini-pvp`", "embed": None, "view": None}
    ]


@pytest.mark.asyncio
async def test_perform_remove_map_returns_duplicate_message() -> None:
    bot = _RemoveMapBot(claim=False)

    result = await perform_remove_map(
        bot,
        server="mini-pvp",
        file_name="arena.msav",
        request_nonce="nonce-1",
    )

    assert result == "This map removal was already processed."
    assert bot.claim_keys == [("remove-map:mini-pvp:arena.msav:nonce-1", 600)]
    assert bot.remove_calls == []


@pytest.mark.asyncio
async def test_perform_remove_map_returns_timeout_message() -> None:
    bot = _RemoveMapBot(claim=True, fail=True)

    result = await perform_remove_map(
        bot,
        server="mini-pvp",
        file_name="arena.msav",
        request_nonce="nonce-2",
    )

    assert result == "No response from target server (timeout)."
    assert bot.remove_calls == [("mini-pvp", "arena.msav", 9876)]
