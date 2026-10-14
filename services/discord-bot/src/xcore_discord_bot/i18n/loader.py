"""Fluent bundles for the bot, one per locale, built from ``locales/<locale>/*.ftl``.

Message ids are global within a locale: every ``.ftl`` file of a locale is added to the same
bundle, so a flow prefixes its ids with its domain (``report-``, ``appeal-``, ...). A duplicate id
across files is logged and the first file (in name order) wins.

Isolation marks: Fluent wraps every placeable in U+2068 / U+2069 unless told otherwise. All the
supported locales are left-to-right and Discord shows those marks as stray glyphs in some clients
(embed titles, button labels, select options) and counts them against length limits, so the
bundles are built with ``use_isolating=False``. Text typed by people is handed to Discord's own
bidi handling. A right-to-left locale would have to be added to ``_ISOLATING_LOCALES``.

Numbers: integers and floats passed to :func:`translate` are formatted without digit grouping, so a
case number 1234 stays ``#1234`` instead of ``#1,234``. Plural selection is unaffected. A message
that really wants grouping writes ``{ NUMBER($count, useGrouping: 1) }``.
"""

from __future__ import annotations

import logging
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal
from functools import cache
from importlib import resources

from fluent.runtime import FluentBundle, FluentResource
from fluent.runtime.types import fluent_number
from fluent.syntax import ast as ftl_ast

logger = logging.getLogger(__name__)

SUPPORTED: tuple[str, ...] = ("en", "ru", "uk")
DEFAULT = "en"

FluentArg = str | int | float | Decimal | date | datetime

_LOCALES_PACKAGE = "xcore_discord_bot.i18n"
_LOCALES_DIR = "locales"
_ISOLATING_LOCALES: frozenset[str] = frozenset()

# (kind, locale, key) already written to the log, so a hot path logs a gap once, not per call.
_reported: set[tuple[str, str, str]] = set()


@dataclass(frozen=True, slots=True)
class _Rendered:
    text: str
    errors: tuple[Exception, ...]


def read_locale_sources(locale: str) -> dict[str, str]:
    """Return ``{domain: ftl source}`` for a locale, e.g. ``{"core": "...", "reports": "..."}``.

    Read through ``importlib.resources`` so it works from an installed wheel as well as from a
    source checkout. A missing locale directory yields an empty mapping.
    """
    root = resources.files(_LOCALES_PACKAGE) / _LOCALES_DIR / locale
    if not root.is_dir():
        return {}
    files = sorted(
        (entry for entry in root.iterdir() if entry.name.endswith(".ftl")),
        key=lambda entry: entry.name,
    )
    return {
        entry.name.removesuffix(".ftl"): entry.read_text(encoding="utf-8")
        for entry in files
    }


@cache
def get_bundle(locale: str) -> FluentBundle:
    """The bundle of one supported locale; built on first use and then cached."""
    bundle = FluentBundle([locale], use_isolating=locale in _ISOLATING_LOCALES)
    sources = read_locale_sources(locale)
    if not sources:
        logger.error("No translation files found for locale %s", locale)
    seen: dict[str, str] = {}
    for domain, source in sources.items():
        resource = FluentResource(source)
        for entry in resource.body:
            if isinstance(entry, ftl_ast.Junk):
                logger.error(
                    "Invalid Fluent syntax in %s/%s.ftl: %r",
                    locale,
                    domain,
                    entry.content.strip().splitlines()[0]
                    if entry.content.strip()
                    else "",
                )
            elif isinstance(entry, ftl_ast.Message | ftl_ast.Term):
                ident = (
                    f"-{entry.id.name}"
                    if isinstance(entry, ftl_ast.Term)
                    else entry.id.name
                )
                if ident in seen:
                    logger.error(
                        "Duplicate Fluent id %s in %s/%s.ftl (already defined in %s.ftl)",
                        ident,
                        locale,
                        domain,
                        seen[ident],
                    )
                else:
                    seen[ident] = domain
        bundle.add_resource(resource)
    return bundle


def _chain(locale: str | None) -> tuple[str, ...]:
    if locale is not None and locale in SUPPORTED and locale != DEFAULT:
        return (locale, DEFAULT)
    return (DEFAULT,)


def _prepare_args(args: Mapping[str, FluentArg] | None) -> dict[str, object]:
    values: dict[str, object] = {}
    for name, value in (args or {}).items():
        if isinstance(value, int | float) and not isinstance(value, bool):
            values[name] = fluent_number(value, useGrouping=False)
        else:
            values[name] = value
    return values


def _render(locale: str, key: str, values: dict[str, object]) -> _Rendered | None:
    """Format ``key`` strictly in ``locale``; ``None`` when the locale has no such message."""
    message_id, _, attribute = key.partition(".")
    bundle = get_bundle(locale)
    if message_id.startswith("-") or not bundle.has_message(message_id):
        return None
    message = bundle.get_message(message_id)
    pattern = message.value
    if attribute:
        pattern = message.attributes.get(attribute)
    if pattern is None:
        return None
    text, errors = bundle.format_pattern(pattern, values)
    return _Rendered(text, tuple(errors))


def _report_once(kind: str, locale: str, key: str, message: str, *args: object) -> None:
    marker = (kind, locale, key)
    if marker in _reported:
        return
    _reported.add(marker)
    if kind == "missing-everywhere":
        logger.error(message, *args)
    else:
        logger.warning(message, *args)


def lookup(
    locale: str,
    key: str,
    args: Mapping[str, FluentArg] | None = None,
) -> str | None:
    """Format ``key`` in exactly ``locale``, with no fallback.

    ``None`` when the locale lacks the message or it cannot be formatted with ``args``. This is
    what the command translator needs: an English string must never be registered as a Russian
    localisation.
    """
    if locale not in SUPPORTED:
        return None
    rendered = _render(locale, key, _prepare_args(args))
    if rendered is None:
        return None
    if rendered.errors:
        _report_once(
            "format",
            locale,
            key,
            "Cannot format i18n key %r for locale %s: %s",
            key,
            locale,
            "; ".join(str(error) for error in rendered.errors),
        )
        return None
    return rendered.text


def translate(
    locale: str | None,
    key: str,
    args: Mapping[str, FluentArg] | None = None,
) -> str:
    """Format ``key``: requested locale, then English, then the key itself.

    A key may address an attribute (``"message-id.attribute"``). A message that exists but fails
    to format (a missing argument) counts as unusable in that locale and the next one is tried;
    if no locale formats cleanly the best-effort text of the first one is returned. Gaps are
    logged once per locale and key.
    """
    values = _prepare_args(args)
    chain = _chain(locale)
    best_effort: str | None = None
    for candidate in chain:
        rendered = _render(candidate, key, values)
        if rendered is None:
            continue
        if not rendered.errors:
            if candidate != chain[0]:
                _report_once(
                    "missing",
                    chain[0],
                    key,
                    "i18n key %r is missing in locale %s, using %s",
                    key,
                    chain[0],
                    candidate,
                )
            return rendered.text
        _report_once(
            "format",
            candidate,
            key,
            "Cannot format i18n key %r for locale %s: %s",
            key,
            candidate,
            "; ".join(str(error) for error in rendered.errors),
        )
        if best_effort is None:
            best_effort = rendered.text
    if best_effort is not None:
        return best_effort
    _report_once(
        "missing-everywhere",
        chain[0],
        key,
        "i18n key %r is not defined in any locale",
        key,
    )
    return key
