"""``discord.app_commands.Translator`` backed by the same Fluent bundles as ``t()``.

Discord stores command localisations per locale, so only ``ru`` and ``uk`` get a value; English is
the string in the code and every other locale gets ``None`` (Discord shows the default).

Which key a string is looked up under:

1. ``locale_str("text", key="some-id")``: the explicit key wins.
2. Otherwise one derived from where the string sits (``qn`` is the qualified command name with
   spaces turned into hyphens):

   =====================  ===========================
   command / group name   ``cmd-<qn>-name``
   command / group desc.  ``cmd-<qn>-description``
   parameter name         ``cmd-<qn>-param-<param>-name``
   parameter description  ``cmd-<qn>-param-<param>-description``
   choice name            ``choice-<value>``
   =====================  ===========================

   discord.py wraps plain strings in ``locale_str`` itself, so an existing command is localised by
   adding the key to the ``.ftl`` files, with no change to the command.

A string that is missing in the locale, would not format without arguments, or breaks Discord's
limits (names: lowercase, ``[\\w-]{1,32}``; descriptions: 1-100 characters) is answered with
``None``: one bad translation must never fail the whole ``tree.sync``.
"""

from __future__ import annotations

import logging
import re
from typing import Any

import discord
from discord import app_commands
from discord.app_commands import TranslationContextLocation as Where

from .loader import DEFAULT, lookup
from .resolve import normalize_locale

logger = logging.getLogger(__name__)

_NAME = re.compile(r"[\w-]{1,32}")
_DESCRIPTION_MAX = 100
_NAME_LOCATIONS = {Where.command_name, Where.group_name, Where.parameter_name}
_DESCRIPTION_LOCATIONS = {
    Where.command_description,
    Where.group_description,
    Where.parameter_description,
}


def _slug(value: object) -> str:
    return str(value).strip().replace(" ", "-")


def derive_key(context: Any) -> str | None:
    """The key a string is looked up under when it has no explicit ``key=`` extra."""
    location = context.location
    data = context.data
    if location in (Where.command_name, Where.group_name):
        return f"cmd-{_slug(getattr(data, 'qualified_name', None) or data.name)}-name"
    if location in (Where.command_description, Where.group_description):
        return f"cmd-{_slug(data.qualified_name)}-description"
    if location in (Where.parameter_name, Where.parameter_description):
        suffix = "name" if location == Where.parameter_name else "description"
        return f"cmd-{_slug(data.command.qualified_name)}-param-{data.name}-{suffix}"
    if location == Where.choice_name:
        return f"choice-{_slug(data.value)}"
    return None


def _within_discord_limits(location: Where, text: str) -> bool:
    if location in _NAME_LOCATIONS:
        return bool(_NAME.fullmatch(text)) and text == text.lower()
    if location in _DESCRIPTION_LOCATIONS:
        return 1 <= len(text) <= _DESCRIPTION_MAX
    if location == Where.choice_name:
        return 1 <= len(text) <= _DESCRIPTION_MAX
    return True


class XCoreTranslator(app_commands.Translator):
    async def translate(
        self,
        string: app_commands.locale_str,
        locale: discord.Locale,
        context: app_commands.TranslationContextTypes,
    ) -> str | None:
        target = normalize_locale(locale)
        if target is None or target == DEFAULT:
            return None
        key = string.extras.get("key")
        if not isinstance(key, str) or not key:
            key = derive_key(context)
        if key is None:
            return None
        text = lookup(target, key)
        if text is None:
            return None
        if not _within_discord_limits(context.location, text):
            logger.warning(
                "Ignoring the %s translation of %r: %r is not valid for %s",
                target,
                key,
                text,
                context.location.name,
            )
            return None
        return text
