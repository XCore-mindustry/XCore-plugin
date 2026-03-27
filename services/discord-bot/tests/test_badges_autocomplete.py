from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import pytest

from xcore_discord_bot.cogs.autocomplete import _autocomplete_badge_id


@dataclass
class _Interaction:
    client: Any = None


@pytest.mark.asyncio
async def test_autocomplete_badge_id_filters_and_formats() -> None:
    choices = await _autocomplete_badge_id(_Interaction(), "trans")

    assert len(choices) == 1
    assert choices[0].name == "Translator (translator)"
    assert choices[0].value == "translator"


@pytest.mark.asyncio
async def test_autocomplete_badge_id_excludes_system_badges() -> None:
    choices = await _autocomplete_badge_id(_Interaction(), "admin")

    assert choices == []


@pytest.mark.asyncio
async def test_autocomplete_badge_id_returns_all_grantable_badges_for_empty_query() -> (
    None
):
    choices = await _autocomplete_badge_id(_Interaction(), "")

    values = [choice.value for choice in choices]
    assert values == [
        "developer",
        "translator",
        "map-maker",
        "contributor",
        "bug-finder",
        "event-winner",
        "veteran",
    ]


@pytest.mark.asyncio
async def test_autocomplete_badge_id_finds_bug_finder() -> None:
    choices = await _autocomplete_badge_id(_Interaction(), "bug")

    assert len(choices) == 1
    assert choices[0].name == "Bug Finder (bug-finder)"
    assert choices[0].value == "bug-finder"
