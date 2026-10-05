"""The rating leagues, as the game servers draw them from a ladder rating."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class League:
    name: str
    minimum_rating: int
    color: int


# Mirrors RatingLeague in the plugin: the names, thresholds and colours players see in game.
LEAGUES: tuple[League, ...] = (
    League("Scrap", 100, 0x7E7E7E),
    League("Copper", 800, 0xD37F47),
    League("Lead", 1000, 0x8C7FA9),
    League("Graphite", 1200, 0x6E7B8C),
    League("Silicon", 1400, 0x53565C),
    League("Titanium", 1600, 0x8DA1E3),
    League("Thorium", 1800, 0xF9A3C7),
    League("Plastanium", 2000, 0xCBD97F),
    League("Phase Fabric", 2200, 0xFFD59E),
    League("Surge Alloy", 2500, 0xF3E979),
)


def league_for(rating: int) -> League:
    """The highest league the rating reaches; anything below the first one is still Scrap."""
    reached = LEAGUES[0]
    for league in LEAGUES:
        if rating < league.minimum_rating:
            break
        reached = league
    return reached


def next_league(league: League) -> League | None:
    index = LEAGUES.index(league) + 1
    return LEAGUES[index] if index < len(LEAGUES) else None
