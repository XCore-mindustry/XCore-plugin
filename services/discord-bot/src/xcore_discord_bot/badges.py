from __future__ import annotations

from dataclasses import dataclass
from typing import Final


@dataclass(frozen=True)
class BadgeDef:
    id: str
    label: str
    system: bool = False
    grantable: bool = True


BADGES: Final[tuple[BadgeDef, ...]] = (
    BadgeDef(id="admin", label="Admin", system=True, grantable=False),
    BadgeDef(id="developer", label="Developer"),
    BadgeDef(id="translator", label="Translator"),
    BadgeDef(id="map-maker", label="Map Maker"),
    BadgeDef(id="contributor", label="Contributor"),
    BadgeDef(id="event-winner", label="Event Winner"),
    BadgeDef(id="veteran", label="Veteran"),
)

BADGE_BY_ID: Final[dict[str, BadgeDef]] = {badge.id: badge for badge in BADGES}


def normalize_badge_id(raw: str) -> str:
    return raw.strip().lower()


def get_badge(raw: str) -> BadgeDef | None:
    normalized = normalize_badge_id(raw)
    if not normalized:
        return None
    return BADGE_BY_ID.get(normalized)


def grantable_badges() -> tuple[BadgeDef, ...]:
    return tuple(badge for badge in BADGES if badge.grantable and not badge.system)


def badge_choice_label(badge: BadgeDef) -> str:
    return f"{badge.label} ({badge.id})"
