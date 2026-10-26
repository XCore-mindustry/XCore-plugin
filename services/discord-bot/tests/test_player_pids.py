from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import pytest
from backends import real_bot
from discord import AppCommandOptionType

from xcore_discord_bot.cogs.autocomplete import (
    _autocomplete_player_id,
    _autocomplete_stats_player,
)
from xcore_discord_bot.cogs.info import InfoCog
from xcore_discord_bot.moderation_logs import format_vote_kick_party_value
from xcore_discord_bot.player_pids import NO_PID, is_assigned, or_none


def test_only_the_reserved_value_means_no_pid() -> None:
    assert is_assigned(12)
    assert is_assigned(0)
    assert is_assigned(-1)
    assert is_assigned(-12)
    assert not is_assigned(NO_PID)
    assert not is_assigned(None)
    assert or_none(-12) == -12
    assert or_none(NO_PID) is None


def test_vote_kick_party_shows_a_negative_pid() -> None:
    assert format_vote_kick_party_value(name="Ann", pid=-12) == "Ann (pid=-12)"
    assert format_vote_kick_party_value(name="Ann", pid=0) == "Ann (pid=0)"
    assert format_vote_kick_party_value(name="Ann", pid=None) == "Ann"


@dataclass
class _Interaction:
    client: Any


async def _interaction(*players: dict[str, Any]) -> Any:
    bot, db = await real_bot()
    for player in players:
        await db["players"].insert_one(dict(player))
    return _Interaction(client=bot)


EVENT = {"pid": -12, "nickname": "Event", "username": "br"}
ZERO = {"pid": 0, "nickname": "Zero", "username": "zero"}
NOBODY = {"pid": NO_PID, "nickname": "Nobody"}


@pytest.mark.asyncio
async def test_autocomplete_offers_zero_and_negative_pids() -> None:
    interaction = await _interaction(EVENT, ZERO, NOBODY)

    # The reserved value also starts with "-", but it is never offered.
    negative = await _autocomplete_player_id(interaction, "-")
    zero = await _autocomplete_player_id(interaction, "#0")

    assert [(choice.name, choice.value) for choice in negative] == [
        ("Event @br (#-12)", -12),
    ]
    assert [(choice.name, choice.value) for choice in zero] == [
        ("Zero @zero (#0)", 0),
    ]


def test_stats_player_option_accepts_text_with_autocomplete() -> None:
    parameter = next(p for p in InfoCog.cmd_stats.parameters if p.name == "player_id")
    assert parameter.type is AppCommandOptionType.string
    assert parameter.autocomplete
    assert not parameter.required


@pytest.mark.asyncio
async def test_stats_suggestions_use_string_pids() -> None:
    interaction = await _interaction(EVENT, ZERO)

    choices = await _autocomplete_stats_player(interaction, "@br")

    assert [(choice.name, choice.value) for choice in choices] == [
        ("Event @br (#-12)", "-12"),
    ]
