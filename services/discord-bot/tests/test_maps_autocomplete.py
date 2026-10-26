from __future__ import annotations

from dataclasses import dataclass
from types import SimpleNamespace
from typing import Any, cast
from unittest.mock import create_autospec

import pytest
from backends import real_bot
from bot_fakes import make_settings

from xcore_discord_bot.cogs.autocomplete import _autocomplete_map_file
from xcore_discord_bot.handlers_misc import get_cached_maps


class _MapsServer:
    """The game server answering ``maps.list``; the bot's code up to the RPC is real."""

    def __init__(self) -> None:
        self.calls = 0
        self.fail = False
        self.fail_once = False
        self.maps: list[dict[str, str]] = [
            {"name": "Glacier", "file_name": "glacier.msav"},
        ]

    async def answer(self, server: str, timeout_ms: int) -> list[dict[str, str]]:
        self.calls += 1
        assert server == "survival"
        assert timeout_ms == 3000
        if self.fail_once:
            self.fail_once = False
            raise TimeoutError
        if self.fail:
            raise TimeoutError
        return self.maps


async def _maps_bot(server: _MapsServer, map_cache=None):
    bot, _ = await real_bot(settings=make_settings(rpc_timeout_ms=3000))
    maps_bus = bot.container.maps_bus
    maps_bus.rpc_maps_list = create_autospec(
        maps_bus.rpc_maps_list, side_effect=server.answer
    )
    bot.map_cache = {} if map_cache is None else map_cache
    return bot


async def test_get_cached_maps_uses_ttl(monkeypatch: pytest.MonkeyPatch) -> None:
    bus = _MapsServer()
    bot = await _maps_bot(bus)

    now = 1000.0

    def _fake_monotonic() -> float:
        return now

    monkeypatch.setattr(
        "xcore_discord_bot.handlers_misc.time.monotonic", _fake_monotonic
    )

    first = await get_cached_maps(cast(Any, bot), "survival")
    assert first == [{"name": "Glacier", "file_name": "glacier.msav"}]
    assert cast(Any, bus).calls == 1

    second = await get_cached_maps(cast(Any, bot), "survival")
    assert second == first
    assert cast(Any, bus).calls == 1

    now = 1061.0
    third = await get_cached_maps(cast(Any, bot), "survival")
    assert third == first
    assert cast(Any, bus).calls == 2


async def test_get_cached_maps_returns_stale_cache_on_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bus = _MapsServer()
    bot = await _maps_bot(
        bus,
        {
            "survival": (
                100.0,
                [{"name": "Gladius Arena", "file_name": "gladius.msav"}],
            )
        },
    )
    bus.fail = True

    async def fast_sleep(_seconds: float) -> None:
        return None

    monkeypatch.setattr("xcore_discord_bot.retry.asyncio.sleep", fast_sleep)
    monkeypatch.setattr(
        "xcore_discord_bot.handlers_misc.time.monotonic", lambda: 1000.0
    )

    maps = await get_cached_maps(cast(Any, bot), "survival")
    assert maps == [{"name": "Gladius Arena", "file_name": "gladius.msav"}]


async def test_get_cached_maps_retries_transient_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bus = _MapsServer()
    bus.fail_once = True
    bot = await _maps_bot(bus)

    async def fast_sleep(_seconds: float) -> None:
        return None

    monkeypatch.setattr("xcore_discord_bot.retry.asyncio.sleep", fast_sleep)
    monkeypatch.setattr(
        "xcore_discord_bot.handlers_misc.time.monotonic", lambda: 1000.0
    )

    maps = await get_cached_maps(cast(Any, bot), "survival")

    assert maps == [{"name": "Glacier", "file_name": "glacier.msav"}]
    assert bus.calls == 2


async def _autocomplete_bot(*, maps):
    server = _MapsServer()
    server.maps = maps
    return await _maps_bot(server)


@dataclass
class _Interaction:
    client: Any
    namespace: Any


async def test_autocomplete_map_file_filters_and_formats() -> None:
    interaction: Any = _Interaction(
        client=await _autocomplete_bot(
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


async def test_autocomplete_map_file_returns_empty_without_server() -> None:
    interaction: Any = _Interaction(
        client=await _autocomplete_bot(maps=[]),
        namespace=SimpleNamespace(),
    )

    choices = await _autocomplete_map_file(interaction, "")
    assert choices == []
