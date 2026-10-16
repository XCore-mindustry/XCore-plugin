"""Which language a message goes out in (design spec 8.6).

Everything here is a pure function over already-loaded values, plus two thin async helpers that
read one row through a Protocol. Nothing imports MongoDB or discord.py: the store behind
``discord_user_prefs`` implements :class:`LocalePrefReader`, a Discord ``Locale`` enum is accepted
through its ``.value``.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any, Protocol

from .loader import DEFAULT, SUPPORTED

logger = logging.getLogger(__name__)

SOURCE_AUTO = "auto"  # last ``interaction.locale`` seen for the user
SOURCE_MANUAL = "manual"  # chosen in the language switch


@dataclass(frozen=True, slots=True)
class LocalePref:
    """One ``discord_user_prefs`` row, reduced to what resolution reads."""

    locale: str
    source: str = SOURCE_AUTO


class LocalePrefReader(Protocol):
    async def get_locale_pref(self, discord_id: int) -> LocalePref | None: ...


def normalize_locale(value: object) -> str | None:
    """Map any locale spelling to a supported code, or ``None`` when unsupported or unusable.

    Accepts ``"ru"``, ``"uk-UA"``, ``"ru_RU"``, ``"en-GB"``, a ``discord.Locale`` member, and
    ``None``. The in-game ``"auto"`` placeholder is unusable, hence ``None``.
    """
    raw: Any = getattr(value, "value", value)
    if not isinstance(raw, str):
        return None
    base = raw.strip().replace("_", "-").split("-", 1)[0].lower()
    return base if base in SUPPORTED else None


def from_discord_locale(value: object) -> str:
    """``ru`` -> ``ru``, ``uk`` -> ``uk``, everything else -> ``en``."""
    return normalize_locale(value) or DEFAULT


def _pref_locale(pref: LocalePref | None) -> str | None:
    return normalize_locale(pref.locale) if pref is not None else None


def pick_interaction_locale(pref: LocalePref | None, discord_locale: object) -> str:
    """The user's manual choice, then ``interaction.locale``, then English.

    An ``auto`` row never wins here: the interaction's own locale is fresher.
    """
    if pref is not None and pref.source == SOURCE_MANUAL:
        manual = _pref_locale(pref)
        if manual is not None:
            return manual
    return from_discord_locale(discord_locale)


def pick_dm_locale(pref: LocalePref | None, player_language: object = None) -> str:
    """Stored preference of any source, then the linked player's in-game language, then English."""
    return _pref_locale(pref) or normalize_locale(player_language) or DEFAULT


def pick_player_locale(player_language: object, pref: LocalePref | None = None) -> str:
    """Locale for a player: the order of :func:`pick_dm_locale`, started from the player record.

    ``player_language`` is ``PlayerRecord.language``; ``pref`` is the row of the Discord account
    linked to the player, if there is one. An in-game language of ``"auto"`` is skipped.
    """
    return pick_dm_locale(pref, player_language)


def auto_pref_needs_update(pref: LocalePref | None, locale: str) -> bool:
    """Whether an interaction should write the ``auto`` row; a manual choice is never overwritten."""
    if pref is None:
        return True
    if pref.source == SOURCE_MANUAL:
        return False
    return pref.locale != locale


async def _read_pref(prefs: LocalePrefReader, discord_id: int) -> LocalePref | None:
    try:
        return await prefs.get_locale_pref(discord_id)
    except Exception:
        # A language preference is never worth failing an interaction or a DM over.
        logger.exception("Could not read the locale preference of %s", discord_id)
        return None


async def resolve_interaction_locale(interaction: Any, prefs: LocalePrefReader) -> str:
    """:func:`pick_interaction_locale` for an interaction (needs ``.user.id`` and ``.locale``)."""
    pref = await _read_pref(prefs, int(interaction.user.id))
    return pick_interaction_locale(pref, getattr(interaction, "locale", None))


async def resolve_dm_locale(
    discord_id: int,
    prefs: LocalePrefReader,
    player_language: object = None,
) -> str:
    """:func:`pick_dm_locale` for a Discord user id; reads the preference row itself."""
    return pick_dm_locale(await _read_pref(prefs, discord_id), player_language)
