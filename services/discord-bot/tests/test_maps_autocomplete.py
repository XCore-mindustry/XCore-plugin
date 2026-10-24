from __future__ import annotations

from dataclasses import dataclass
from types import SimpleNamespace
from typing import Any, cast

import pytest
from bot_fakes import make_bot, make_settings

from xcore_discord_bot.cogs.autocomplete import _autocomplete_map_file
from xcore_discord_bot.handlers_misc import get_cached_maps


class _MapsBus:
    def __init__(self) -> None:
        self.calls = 0
        self.fail = False
        self.fail_once = False
        self.maps: list[dict[str, str]] = [
            {"name": "Glacier", "file_name": "glacier.msav"},
        ]

    async def rpc_maps_list(self, server: str, timeout_ms: int) -> list[dict[str, str]]:
        self.calls += 1
        assert server == "survival"
        assert timeout_ms == 3000
        if self.fail_once:
            self.fail_once = False
            raise TimeoutError
        if self.fail:
            raise TimeoutError
        return self.maps


def _CachedMapsBot(*, _bus, _map_cache):
    bot = make_bot(bus=_bus, settings=make_settings(rpc_timeout_ms=3000))
    bot.map_cache = _map_cache
    return bot


async def test_get_cached_maps_uses_ttl(monkeypatch: pytest.MonkeyPatch) -> None:
    bus = _MapsBus()
    bot = _CachedMapsBot(_bus=bus, _map_cache={})

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
    bus = _MapsBus()
    bot = _CachedMapsBot(
        _bus=bus,
        _map_cache={
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
    bus = _MapsBus()
    bus.fail_once = True
    bot = _CachedMapsBot(_bus=bus, _map_cache={})

    async def fast_sleep(_seconds: float) -> None:
        return None

    monkeypatch.setattr("xcore_discord_bot.retry.asyncio.sleep", fast_sleep)
    monkeypatch.setattr(
        "xcore_discord_bot.handlers_misc.time.monotonic", lambda: 1000.0
    )

    maps = await get_cached_maps(cast(Any, bot), "survival")

    assert maps == [{"name": "Glacier", "file_name": "glacier.msav"}]
    assert bus.calls == 2


def _autocomplete_bot(*, maps):
    bus = _MapsBus()
    bus.maps = maps
    return make_bot(bus=bus)


@dataclass
class _Interaction:
    client: Any
    namespace: Any


async def test_autocomplete_map_file_filters_and_formats() -> None:
    interaction: Any = _Interaction(
        client=_autocomplete_bot(
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
        client=_autocomplete_bot(maps=[]),
        namespace=SimpleNamespace(),
    )

    choices = await _autocomplete_map_file(interaction, "")
    assert choices == []
