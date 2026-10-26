from __future__ import annotations

from dataclasses import dataclass
from types import SimpleNamespace
from typing import Any
from unittest.mock import create_autospec

import pytest
from backends import real_bot
from bot_fakes import make_settings

from xcore_discord_bot.cogs.autocomplete import autocomplete_map_file


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


async def _maps_bot(server: _MapsServer):
    bot, _ = await real_bot(settings=make_settings(rpc_timeout_ms=3000))
    maps_bus = bot.container.maps_bus
    maps_bus.rpc_maps_list = create_autospec(
        maps_bus.rpc_maps_list, side_effect=server.answer
    )
    return bot


def _clock(monkeypatch: pytest.MonkeyPatch, start: float) -> list[float]:
    """The catalog's clock; set ``[0]`` to move it."""
    now = [start]
    monkeypatch.setattr(
        "xcore_discord_bot.services.map_catalog.time.monotonic", lambda: now[0]
    )
    return now


@pytest.fixture
def fast_retry(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fast_sleep(_seconds: float) -> None:
        return None

    monkeypatch.setattr("xcore_discord_bot.retry.asyncio.sleep", fast_sleep)


async def test_a_map_list_is_reused_for_a_minute(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    server = _MapsServer()
    catalog = (await _maps_bot(server)).container.map_catalog
    now = _clock(monkeypatch, 1000.0)

    first = await catalog.maps("survival")
    assert first == [{"name": "Glacier", "file_name": "glacier.msav"}]
    now[0] = 1059.0
    assert await catalog.maps("survival") == first
    assert server.calls == 1

    server.maps = [{"name": "Ruins", "file_name": "ruins.msav"}]
    now[0] = 1061.0
    assert await catalog.maps("survival") == server.maps
    assert server.calls == 2


async def test_a_silent_server_keeps_its_last_map_list(
    monkeypatch: pytest.MonkeyPatch, fast_retry: None
) -> None:
    server = _MapsServer()
    server.maps = [{"name": "Gladius Arena", "file_name": "gladius.msav"}]
    catalog = (await _maps_bot(server)).container.map_catalog
    now = _clock(monkeypatch, 100.0)
    await catalog.maps("survival")

    server.fail = True
    now[0] = 1000.0
    maps = await catalog.maps("survival")

    assert maps == [{"name": "Gladius Arena", "file_name": "gladius.msav"}]
    assert server.calls == 3


async def test_a_server_that_never_answered_has_no_maps(fast_retry: None) -> None:
    server = _MapsServer()
    server.fail = True
    catalog = (await _maps_bot(server)).container.map_catalog

    assert await catalog.maps("survival") == []


async def test_a_transient_timeout_is_retried(
    monkeypatch: pytest.MonkeyPatch, fast_retry: None
) -> None:
    server = _MapsServer()
    server.fail_once = True
    catalog = (await _maps_bot(server)).container.map_catalog
    _clock(monkeypatch, 1000.0)

    maps = await catalog.maps("survival")

    assert maps == [{"name": "Glacier", "file_name": "glacier.msav"}]
    assert server.calls == 2


async def test_a_forgotten_server_is_asked_again(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    server = _MapsServer()
    catalog = (await _maps_bot(server)).container.map_catalog
    _clock(monkeypatch, 1000.0)
    await catalog.maps("survival")

    catalog.forget("survival")
    catalog.forget("unknown")
    await catalog.maps("survival")

    assert server.calls == 2


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

    choices = await autocomplete_map_file(interaction, "gla")
    assert len(choices) == 1
    assert choices[0].name == "Glacier (glacier.msav)"
    assert choices[0].value == "glacier.msav"


async def test_autocomplete_map_file_returns_empty_without_server() -> None:
    interaction: Any = _Interaction(
        client=await _autocomplete_bot(maps=[]),
        namespace=SimpleNamespace(),
    )

    choices = await autocomplete_map_file(interaction, "")
    assert choices == []
