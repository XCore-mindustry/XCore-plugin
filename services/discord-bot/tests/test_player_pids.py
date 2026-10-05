from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import pytest

from xcore_discord_bot.cogs.autocomplete import _autocomplete_player_id
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.handlers_moderation import _format_vote_kick_party_value
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
    assert _format_vote_kick_party_value(name="Ann", pid=-12) == "Ann (pid=-12)"
    assert _format_vote_kick_party_value(name="Ann", pid=0) == "Ann (pid=0)"
    assert _format_vote_kick_party_value(name="Ann", pid=None) == "Ann"


@dataclass
class _Store:
    rows: list[PlayerRecord]

    async def autocomplete_players(
        self, query: str, limit: int = 25
    ) -> list[PlayerRecord]:
        del query, limit
        return self.rows


@dataclass
class _Interaction:
    client: Any


@pytest.mark.asyncio
async def test_autocomplete_offers_zero_and_negative_pids(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("xcore_discord_bot.cogs.autocomplete.StoreService", _Store)
    interaction: Any = _Interaction(
        client=_Store(
            [
                PlayerRecord(pid=-12, nickname="Event"),
                PlayerRecord(pid=0, nickname="Zero"),
                PlayerRecord(pid=NO_PID, nickname="Nobody"),
            ]
        )
    )

    choices = await _autocomplete_player_id(interaction, "-")

    assert [(choice.name, choice.value) for choice in choices] == [
        ("Event (pid=-12)", -12),
        ("Zero (pid=0)", 0),
    ]
