from __future__ import annotations

import logging
from dataclasses import dataclass
from types import SimpleNamespace
from typing import Any

import discord
import pytest
from discord import app_commands
from discord.app_commands import TranslationContext, TranslationContextLocation

from xcore_discord_bot.i18n import (
    DEFAULT,
    SUPPORTED,
    LocalePref,
    XCoreTranslator,
    auto_pref_needs_update,
    from_discord_locale,
    loader,
    normalize_locale,
    pick_dm_locale,
    pick_interaction_locale,
    pick_player_locale,
    resolve_dm_locale,
    resolve_interaction_locale,
    t,
)
from xcore_discord_bot.i18n.translator import derive_key

BIDI_MARKS = "\u2066\u2067\u2068\u2069"


@pytest.fixture
def fake_locales(monkeypatch: pytest.MonkeyPatch):
    """Replace the .ftl sources: ``fake_locales({"en": {"core": "..."}, "ru": {...}})``."""

    def install(sources: dict[str, dict[str, str]]) -> None:
        monkeypatch.setattr(
            loader, "read_locale_sources", lambda locale: dict(sources.get(locale, {}))
        )
        loader.get_bundle.cache_clear()
        loader._reported.clear()

    yield install
    loader.get_bundle.cache_clear()
    loader._reported.clear()


@pytest.fixture(autouse=True)
def _fresh_reports() -> None:
    loader._reported.clear()


# --- the fallback chain -------------------------------------------------------------------------


def test_supported_locales_and_default() -> None:
    assert SUPPORTED == ("en", "ru", "uk")
    assert DEFAULT == "en"


def test_requested_locale_wins_over_english() -> None:
    assert t("en", "button-yes") == "Yes"
    assert t("ru", "button-yes") == "Да"
    assert t("uk", "button-yes") == "Так"


@pytest.mark.parametrize("locale", [None, "fr", "", "ru-RU", "EN"])
def test_unsupported_or_missing_locale_means_english(locale: str | None) -> None:
    assert t(locale, "button-yes") == "Yes"


def test_key_missing_in_requested_locale_falls_back_to_english(
    fake_locales, caplog: pytest.LogCaptureFixture
) -> None:
    fake_locales(
        {
            "en": {"core": "only-en = English text\nboth = Both en\n"},
            "ru": {"core": "both = Оба ru\n"},
        }
    )

    with caplog.at_level(logging.WARNING, logger=loader.logger.name):
        assert t("ru", "only-en") == "English text"
        assert t("ru", "only-en") == "English text"

    assert t("ru", "both") == "Оба ru"
    warnings = [r for r in caplog.records if "only-en" in r.getMessage()]
    assert len(warnings) == 1, "a gap is logged once, not on every call"
    assert warnings[0].levelno == logging.WARNING
    assert "ru" in warnings[0].getMessage()


def test_key_missing_everywhere_returns_the_key_and_logs(
    fake_locales, caplog: pytest.LogCaptureFixture
) -> None:
    fake_locales({"en": {"core": "known = x\n"}, "ru": {"core": "known = y\n"}})

    with caplog.at_level(logging.WARNING, logger=loader.logger.name):
        assert t("ru", "no-such-key") == "no-such-key"
        assert t("ru", "no-such-key") == "no-such-key"

    errors = [r for r in caplog.records if r.levelno == logging.ERROR]
    assert len(errors) == 1
    assert "no-such-key" in errors[0].getMessage()


def test_terms_and_malformed_keys_are_not_messages() -> None:
    assert t("ru", "-brand") == "-brand"
    assert t("ru", "") == ""
    assert t("ru", "button-yes.nope") == "button-yes.nope"


def test_attribute_keys(fake_locales) -> None:
    fake_locales(
        {
            "en": {
                "core": "take = Take\n    .label = Take case\n    .hint = Hint { $n }\n"
            },
            "ru": {"core": "take = Взять\n    .label = Взять обращение\n"},
        }
    )

    assert t("ru", "take") == "Взять"
    assert t("ru", "take.label") == "Взять обращение"
    assert t("ru", "take.hint", n=3) == "Hint 3"  # missing in ru -> English attribute
    assert t("en", "take.missing") == "take.missing"


def test_translation_that_cannot_format_falls_back_to_english(
    fake_locales, caplog: pytest.LogCaptureFixture
) -> None:
    fake_locales(
        {
            "en": {"core": "greet = Hello, { $name }!\n"},
            "ru": {"core": "greet = Привет, { $nick }!\n"},  # wrong argument name
        }
    )

    with caplog.at_level(logging.WARNING, logger=loader.logger.name):
        assert t("ru", "greet", name="Ann") == "Hello, Ann!"

    assert any("greet" in r.getMessage() for r in caplog.records)


def test_missing_argument_gives_best_effort_text_not_an_exception(fake_locales) -> None:
    fake_locales({"en": {"core": "greet = Hello, { $name }!\n"}})

    text = t("en", "greet")

    assert text.startswith("Hello, ") and text.endswith("!")


def test_every_file_of_a_locale_lands_in_one_bundle(fake_locales) -> None:
    fake_locales(
        {
            "en": {
                "core": "from-core = A\n",
                "reports": "from-reports = B\n-term = Shared\nuses-term = { -term }!\n",
            }
        }
    )

    assert t("en", "from-core") == "A"
    assert t("en", "from-reports") == "B"
    assert t("en", "uses-term") == "Shared!"


def test_duplicate_id_across_files_is_logged_and_first_file_wins(
    fake_locales, caplog: pytest.LogCaptureFixture
) -> None:
    fake_locales({"en": {"core": "dup = first\n", "reports": "dup = second\n"}})

    with caplog.at_level(logging.ERROR, logger=loader.logger.name):
        assert t("en", "dup") == "first"

    assert any("Duplicate" in r.getMessage() for r in caplog.records)


# --- arguments, plurals, numbers ----------------------------------------------------------------


def test_arguments_are_interpolated() -> None:
    assert t("ru", "error-already-handled-by", name="Anna") == (
        "Это уже обработал пользователь Anna."
    )
    assert (
        t("uk", "language-changed", language="Українська")
        == "Мову змінено: Українська."
    )


@pytest.mark.parametrize(
    ("seconds", "expected"),
    [
        (0, "0 секунд"),
        (1, "1 секунду"),
        (2, "2 секунды"),
        (4, "4 секунды"),
        (5, "5 секунд"),
        (11, "11 секунд"),
        (12, "12 секунд"),
        (21, "21 секунду"),
        (22, "22 секунды"),
        (25, "25 секунд"),
        (101, "101 секунду"),
        (111, "111 секунд"),
    ],
)
def test_russian_plural_categories(seconds: int, expected: str) -> None:
    assert expected in t("ru", "error-cooldown", seconds=seconds)


@pytest.mark.parametrize(
    ("seconds", "expected"),
    [
        (0, "0 секунд"),
        (1, "1 секунду"),
        (3, "3 секунди"),
        (5, "5 секунд"),
        (11, "11 секунд"),
        (14, "14 секунд"),
        (21, "21 секунду"),
        (23, "23 секунди"),
        (100, "100 секунд"),
    ],
)
def test_ukrainian_plural_categories(seconds: int, expected: str) -> None:
    assert expected in t("uk", "error-cooldown", seconds=seconds)


def test_english_plural_categories() -> None:
    assert "1 more second before" in t("en", "error-cooldown", seconds=1)
    assert "2 more seconds before" in t("en", "error-cooldown", seconds=2)


def test_integers_are_not_grouped_but_still_drive_plurals(fake_locales) -> None:
    fake_locales(
        {
            "en": {
                "core": (
                    "case = Case #{ $number }\n"
                    "grouped = { NUMBER($number, useGrouping: 1) }\n"
                    "files = { $n ->\n    [one] { $n } file\n   *[other] { $n } files\n}\n"
                )
            }
        }
    )

    assert t("en", "case", number=1234) == "Case #1234"
    assert t("en", "case", number=1234567) == "Case #1234567"
    assert t("en", "grouped", number=1234).replace("\xa0", ",") == "1,234"
    assert t("en", "files", n=1) == "1 file"
    assert t("en", "files", n=1001) == "1001 files"


# --- bidirectional isolation --------------------------------------------------------------------


@pytest.mark.parametrize("locale", SUPPORTED)
def test_no_isolation_marks_leak_into_output(locale: str) -> None:
    for name in ("Anna", "שלום", "مرحبا", "Игрок 1"):
        text = t(locale, "error-already-handled-by", name=name)
        assert name in text
        assert not any(mark in text for mark in BIDI_MARKS)


def test_isolation_is_off_for_every_supported_locale() -> None:
    for locale in SUPPORTED:
        assert loader.get_bundle(locale).use_isolating is False


# --- locale resolution --------------------------------------------------------------------------


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("ru", "ru"),
        ("uk", "uk"),
        ("en-US", "en"),
        ("en-GB", "en"),
        ("uk_UA", "uk"),
        ("RU-ru", "ru"),
        (discord.Locale.russian, "ru"),
        (discord.Locale.ukrainian, "uk"),
        (discord.Locale.british_english, "en"),
        ("de", None),
        ("auto", None),
        ("", None),
        (None, None),
        (42, None),
    ],
)
def test_normalize_locale(value: object, expected: str | None) -> None:
    assert normalize_locale(value) == expected


def test_from_discord_locale_maps_everything_else_to_english() -> None:
    assert from_discord_locale(discord.Locale.russian) == "ru"
    assert from_discord_locale(discord.Locale.ukrainian) == "uk"
    assert from_discord_locale(discord.Locale.french) == "en"
    assert from_discord_locale(discord.Locale.american_english) == "en"
    assert from_discord_locale(None) == "en"


def test_interaction_order_manual_then_interaction_locale_then_english() -> None:
    manual_uk = LocalePref("uk", "manual")
    auto_uk = LocalePref("uk", "auto")

    assert pick_interaction_locale(manual_uk, discord.Locale.russian) == "uk"
    assert pick_interaction_locale(auto_uk, discord.Locale.russian) == "ru"
    assert pick_interaction_locale(None, discord.Locale.russian) == "ru"
    assert pick_interaction_locale(None, discord.Locale.german) == "en"
    assert pick_interaction_locale(auto_uk, discord.Locale.german) == "en"
    # A manual row holding a locale the bot no longer supports is ignored.
    assert pick_interaction_locale(LocalePref("xx", "manual"), "ru") == "ru"


def test_dm_order_preference_then_player_language_then_english() -> None:
    assert pick_dm_locale(LocalePref("uk", "auto"), "ru") == "uk"
    assert pick_dm_locale(LocalePref("uk", "manual"), "ru") == "uk"
    assert pick_dm_locale(None, "ru") == "ru"
    assert pick_dm_locale(None, "uk_UA") == "uk"
    assert pick_dm_locale(None, "auto") == "en"
    assert pick_dm_locale(None, None) == "en"
    assert pick_dm_locale(LocalePref("xx", "auto"), "ru") == "ru"


def test_player_order_is_the_dm_order_started_from_the_player() -> None:
    assert pick_player_locale("ru") == "ru"
    assert pick_player_locale("ru", LocalePref("uk", "manual")) == "uk"
    assert pick_player_locale("auto", None) == "en"
    assert pick_player_locale(None, LocalePref("ru", "auto")) == "ru"


def test_auto_row_is_refreshed_but_a_manual_choice_never_overwritten() -> None:
    assert auto_pref_needs_update(None, "ru") is True
    assert auto_pref_needs_update(LocalePref("uk", "auto"), "ru") is True
    assert auto_pref_needs_update(LocalePref("ru", "auto"), "ru") is False
    assert auto_pref_needs_update(LocalePref("uk", "manual"), "ru") is False


@dataclass
class _Prefs:
    rows: dict[int, LocalePref]
    fail: bool = False

    async def get_locale_pref(self, discord_id: int) -> LocalePref | None:
        if self.fail:
            raise RuntimeError("store down")
        return self.rows.get(discord_id)


def _interaction(user_id: int, locale: Any) -> Any:
    return SimpleNamespace(user=SimpleNamespace(id=user_id), locale=locale)


async def test_resolve_interaction_locale_reads_the_store() -> None:
    prefs = _Prefs({1: LocalePref("uk", "manual"), 2: LocalePref("uk", "auto")})

    assert (
        await resolve_interaction_locale(_interaction(1, discord.Locale.russian), prefs)
        == "uk"
    )
    assert (
        await resolve_interaction_locale(_interaction(2, discord.Locale.russian), prefs)
        == "ru"
    )
    assert (
        await resolve_interaction_locale(_interaction(3, discord.Locale.german), prefs)
        == "en"
    )


async def test_resolve_dm_locale_reads_the_store() -> None:
    prefs = _Prefs({1: LocalePref("uk", "auto")})

    assert await resolve_dm_locale(1, prefs, "ru") == "uk"
    assert await resolve_dm_locale(2, prefs, "ru") == "ru"
    assert await resolve_dm_locale(2, prefs) == "en"


async def test_store_failure_degrades_instead_of_failing_the_interaction() -> None:
    prefs = _Prefs({}, fail=True)

    assert (
        await resolve_interaction_locale(
            _interaction(1, discord.Locale.ukrainian), prefs
        )
        == "uk"
    )
    assert await resolve_dm_locale(1, prefs, "ru") == "ru"


# --- the command translator ---------------------------------------------------------------------


async def _noop(interaction: discord.Interaction) -> None:  # pragma: no cover
    return None


def _command(name: str = "language", description: str = "Choose a language") -> Any:
    return app_commands.Command(name=name, description=description, callback=_noop)


async def _translate(
    string: app_commands.locale_str,
    locale: discord.Locale,
    location: TranslationContextLocation,
    data: Any,
) -> str | None:
    return await XCoreTranslator().translate(
        string, locale, TranslationContext(location, data)
    )


async def test_translator_localises_command_name_and_description_for_ru_and_uk() -> (
    None
):
    command = _command()
    name = app_commands.locale_str("language")
    description = app_commands.locale_str("Choose a language")
    where = TranslationContextLocation

    assert (
        await _translate(name, discord.Locale.russian, where.command_name, command)
        == "язык"
    )
    assert (
        await _translate(name, discord.Locale.ukrainian, where.command_name, command)
        == "мова"
    )
    assert (
        await _translate(
            description, discord.Locale.russian, where.command_description, command
        )
        == "Выбрать язык, на котором бот общается с вами"
    )
    assert (
        await _translate(
            description, discord.Locale.ukrainian, where.command_description, command
        )
        == "Обрати мову, якою бот спілкується з вами"
    )


@pytest.mark.parametrize(
    "locale",
    [
        discord.Locale.american_english,
        discord.Locale.british_english,
        discord.Locale.german,
        discord.Locale.french,
        discord.Locale.japanese,
    ],
)
async def test_translator_returns_none_for_every_other_locale(
    locale: discord.Locale,
) -> None:
    command = _command()

    result = await _translate(
        app_commands.locale_str("language"),
        locale,
        TranslationContextLocation.command_name,
        command,
    )

    assert result is None


async def test_translator_returns_none_for_commands_without_a_translation() -> None:
    command = _command("ban", "Ban a player")

    for location, text in (
        (TranslationContextLocation.command_name, "ban"),
        (TranslationContextLocation.command_description, "Ban a player"),
    ):
        result = await _translate(
            app_commands.locale_str(text), discord.Locale.russian, location, command
        )
        assert result is None


async def test_translator_does_not_register_english_text_as_a_translation(
    fake_locales,
) -> None:
    fake_locales(
        {
            "en": {"core": "cmd-ban-description = Ban a player\n"},
            "ru": {"core": ""},
        }
    )

    result = await _translate(
        app_commands.locale_str("Ban a player"),
        discord.Locale.russian,
        TranslationContextLocation.command_description,
        _command("ban", "Ban a player"),
    )

    assert result is None


async def test_translator_explicit_key_beats_derived_key(fake_locales) -> None:
    fake_locales(
        {
            "en": {"core": "special = special\n"},
            "ru": {"core": "special = особое\ncmd-ban-name = бан\n"},
        }
    )

    result = await _translate(
        app_commands.locale_str("ban", key="special"),
        discord.Locale.russian,
        TranslationContextLocation.command_name,
        _command("ban", "Ban a player"),
    )

    assert result == "особое"


async def test_translator_rejects_translations_that_would_break_the_sync(
    fake_locales, caplog: pytest.LogCaptureFixture
) -> None:
    fake_locales(
        {
            "en": {"core": ""},
            "ru": {
                "core": (
                    "cmd-ban-name = Бан Игрока\n"
                    "cmd-mute-name = мьют\n"
                    "cmd-ban-description = { $x }\n"
                    "cmd-mute-description = " + "я" * 101 + "\n"
                )
            },
        }
    )
    where = TranslationContextLocation
    ru = discord.Locale.russian

    with caplog.at_level(logging.WARNING):
        # uppercase and a space are not allowed in a command name
        assert (
            await _translate(
                app_commands.locale_str("ban"), ru, where.command_name, _command("ban")
            )
            is None
        )
        # a description over 100 characters
        assert (
            await _translate(
                app_commands.locale_str("m"),
                ru,
                where.command_description,
                _command("mute"),
            )
            is None
        )
        # a message that needs an argument cannot be a static string
        assert (
            await _translate(
                app_commands.locale_str("b"),
                ru,
                where.command_description,
                _command("ban"),
            )
            is None
        )
        # and a valid one still passes
        assert (
            await _translate(
                app_commands.locale_str("mute"),
                ru,
                where.command_name,
                _command("mute"),
            )
            == "мьют"
        )


async def test_derived_keys_for_every_location() -> None:
    where = TranslationContextLocation
    group = app_commands.Group(name="cases", description="Cases")

    @group.command(name="find", description="Find a case")
    @app_commands.describe(number="Case number")
    async def find(
        interaction: discord.Interaction, number: int
    ) -> None:  # pragma: no cover
        return None

    command = group.get_command("find")
    assert command is not None
    parameter = command.parameters[0]

    assert derive_key(TranslationContext(where.group_name, group)) == "cmd-cases-name"
    assert (
        derive_key(TranslationContext(where.group_description, group))
        == "cmd-cases-description"
    )
    assert (
        derive_key(TranslationContext(where.command_name, command))
        == "cmd-cases-find-name"
    )
    assert (
        derive_key(TranslationContext(where.command_description, command))
        == "cmd-cases-find-description"
    )
    assert (
        derive_key(TranslationContext(where.parameter_name, parameter))
        == "cmd-cases-find-param-number-name"
    )
    assert (
        derive_key(TranslationContext(where.parameter_description, parameter))
        == "cmd-cases-find-param-number-description"
    )
    choice = app_commands.Choice(name="Report", value="report")
    assert derive_key(TranslationContext(where.choice_name, choice)) == "choice-report"
    assert derive_key(TranslationContext(where.other, None)) is None


async def test_translator_through_a_real_command_payload() -> None:
    """discord.py asks for every Locale; only ru and uk may come back, as valid payload."""
    tree = app_commands.CommandTree(discord.Client(intents=discord.Intents.none()))
    command = _command()

    payload = await command.get_translated_payload(tree, XCoreTranslator())

    assert payload["name_localizations"] == {"ru": "язык", "uk": "мова"}
    assert set(payload["description_localizations"]) == {"ru", "uk"}
    assert all(
        0 < len(text) <= 100 for text in payload["description_localizations"].values()
    )
