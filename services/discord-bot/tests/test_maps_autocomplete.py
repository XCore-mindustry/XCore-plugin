from __future__ import annotations

from dataclasses import dataclass
from types import SimpleNamespace
from typing import Any

import pytest

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.handlers_misc import get_cached_maps
from xcore_discord_bot.cogs.autocomplete import _autocomplete_map_file


class _MapsBus:
    def __init__(self) -> None:
        self.calls = 0
        self.fail = False
        self.maps: list[dict[str, str]] = [
            {"name": "Glacier", "file_name": "glacier.msav"},
        ]

    async def rpc_maps_list(self, server: str, timeout_ms: int) -> list[dict[str, str]]:
        self.calls += 1
        assert server == "survival"
        assert timeout_ms == 3000
        if self.fail:
            raise TimeoutError
        return self.maps


@pytest.mark.asyncio
async def test_get_cached_maps_uses_ttl(monkeypatch: pytest.MonkeyPatch) -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot._bus = _MapsBus()
    bot._map_cache = {}

    now = 1000.0

    def _fake_monotonic() -> float:
        return now

    monkeypatch.setattr(
        "xcore_discord_bot.handlers_misc.time.monotonic", _fake_monotonic
    )

    first = await get_cached_maps(bot, "survival")
    assert first == [{"name": "Glacier", "file_name": "glacier.msav"}]
    assert bot._bus.calls == 1

    second = await get_cached_maps(bot, "survival")
    assert second == first
    assert bot._bus.calls == 1

    now = 1061.0
    third = await get_cached_maps(bot, "survival")
    assert third == first
    assert bot._bus.calls == 2


@pytest.mark.asyncio
async def test_get_cached_maps_returns_stale_cache_on_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bot = object.__new__(XCoreDiscordBot)
    bus = _MapsBus()
    bot._bus = bus
    bot._map_cache = {
        "survival": (100.0, [{"name": "Gladius Arena", "file_name": "gladius.msav"}])
    }
    bus.fail = True

    monkeypatch.setattr(
        "xcore_discord_bot.handlers_misc.time.monotonic", lambda: 1000.0
    )

    maps = await get_cached_maps(bot, "survival")
    assert maps == [{"name": "Gladius Arena", "file_name": "gladius.msav"}]


@dataclass
class _AutocompleteBot:
    maps: list[dict[str, str]]

    async def autocomplete_players(
        self,
        query: str,
        *,
        limit: int,
    ) -> list[dict[str, object]]:
        del query, limit
        return []

    async def get_cached_maps(self, server: str) -> list[dict[str, str]]:
        assert server == "survival"
        return self.maps


@dataclass
class _Interaction:
    client: Any
    namespace: Any


@pytest.mark.asyncio
async def test_autocomplete_map_file_filters_and_formats() -> None:
    interaction = _Interaction(
        client=_AutocompleteBot(
            maps=[
                {"name": "Glacier", "file_name": "glacier.msav"},
                {"name": "Ruins", "file_name": "ruins.msav"},
                {"name": "Unknown", "file_name": ""},
            ]
        ),
        namespace=SimpleNamespace(server="survival"),
    )

    choices = await _autocomplete_map_file(interaction, "gla")
    assert len(choices) == 1
    assert choices[0].name == "Glacier (glacier.msav)"
    assert choices[0].value == "glacier.msav"


@pytest.mark.asyncio
async def test_autocomplete_map_file_returns_empty_without_server() -> None:
    interaction = _Interaction(
        client=_AutocompleteBot(maps=[]),
        namespace=SimpleNamespace(),
    )

    choices = await _autocomplete_map_file(interaction, "")
    assert choices == []
