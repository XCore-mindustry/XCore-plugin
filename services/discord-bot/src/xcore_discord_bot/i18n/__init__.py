"""Localisation of the bot: Fluent bundles, locale resolution, the command translator.

``t(locale, key, **args)`` is the one call the rest of the bot needs. See ``loader`` for the
fallback chain and number / bidi handling, ``resolve`` for choosing ``locale`` and ``translator``
for slash command names and descriptions.
"""

from __future__ import annotations

from .loader import DEFAULT, SUPPORTED, FluentArg, translate
from .resolve import (
    LocalePref,
    LocalePrefReader,
    auto_pref_needs_update,
    from_discord_locale,
    normalize_locale,
    pick_dm_locale,
    pick_interaction_locale,
    pick_player_locale,
    resolve_dm_locale,
    resolve_interaction_locale,
)
from .translator import XCoreTranslator


def t(locale: str | None, key: str, **args: FluentArg) -> str:
    """Localised text for ``key``; falls back to English, then to the key itself (logged)."""
    return translate(locale, key, args)


__all__ = [
    "DEFAULT",
    "SUPPORTED",
    "LocalePref",
    "LocalePrefReader",
    "XCoreTranslator",
    "auto_pref_needs_update",
    "from_discord_locale",
    "normalize_locale",
    "pick_dm_locale",
    "pick_interaction_locale",
    "pick_player_locale",
    "resolve_dm_locale",
    "resolve_interaction_locale",
    "t",
]
