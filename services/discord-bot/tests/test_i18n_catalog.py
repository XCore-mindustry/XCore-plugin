"""Checks over the shipped .ftl files and the code that refers to them (design spec 8.6, 8.10)."""

from __future__ import annotations

import ast
import itertools
import subprocess
import sys
import zipfile
from importlib import resources
from pathlib import Path

import pytest
from fluent.syntax import FluentParser
from fluent.syntax import ast as ftl_ast
from fluent.syntax.visitor import Visitor

from xcore_discord_bot.i18n import SUPPORTED, loader, t

PACKAGE_DIR = Path(__file__).resolve().parent.parent / "src" / "xcore_discord_bot"
BIDI_MARKS = "\u2066\u2067\u2068\u2069"


class _Variables(Visitor):
    def __init__(self) -> None:
        super().__init__()
        self.names: set[str] = set()

    def visit_VariableReference(self, node: ftl_ast.VariableReference) -> None:
        self.names.add(node.id.name)
        self.generic_visit(node)


def _parse(locale: str) -> dict[str, dict[str, ftl_ast.Message]]:
    """``{domain: {message id: Message}}`` of one locale; fails on any syntax error."""
    result: dict[str, dict[str, ftl_ast.Message]] = {}
    for domain, source in loader.read_locale_sources(locale).items():
        resource = FluentParser().parse(source)
        junk = [entry for entry in resource.body if isinstance(entry, ftl_ast.Junk)]
        assert not junk, (
            f"{locale}/{domain}.ftl has syntax errors: {[j.content for j in junk]}"
        )
        result[domain] = {
            entry.id.name: entry
            for entry in resource.body
            if isinstance(entry, ftl_ast.Message)
        }
    return result


def _variables(message: ftl_ast.Message) -> set[str]:
    visitor = _Variables()
    visitor.visit(message)
    return visitor.names


def _attributes(message: ftl_ast.Message) -> set[str]:
    return {attribute.id.name for attribute in message.attributes}


def _all_ids(locale: str) -> set[str]:
    return {mid for messages in _parse(locale).values() for mid in messages}


def test_core_domain_exists_for_every_locale() -> None:
    for locale in SUPPORTED:
        assert "core" in loader.read_locale_sources(locale)


def test_same_domain_files_in_every_locale() -> None:
    domains = {locale: set(loader.read_locale_sources(locale)) for locale in SUPPORTED}

    assert domains["ru"] == domains["en"]
    assert domains["uk"] == domains["en"]


@pytest.mark.parametrize("locale", [loc for loc in SUPPORTED if loc != "en"])
def test_key_parity_with_english_per_domain(locale: str) -> None:
    english = _parse("en")
    other = _parse(locale)

    for domain, messages in english.items():
        assert set(other[domain]) == set(messages), (
            f"{locale}/{domain}.ftl keys differ from en"
        )
        for mid, message in messages.items():
            assert _attributes(other[domain][mid]) == _attributes(message), (
                f"{locale}/{domain}.ftl: attributes of {mid} differ from en"
            )


def test_message_ids_are_unique_across_the_files_of_a_locale() -> None:
    for locale in SUPPORTED:
        seen: dict[str, str] = {}
        for domain, messages in _parse(locale).items():
            for mid in messages:
                assert mid not in seen, (
                    f"{mid} is in {locale}/{seen[mid]}.ftl and {domain}.ftl"
                )
                seen[mid] = domain


@pytest.mark.parametrize("locale", [loc for loc in SUPPORTED if loc != "en"])
def test_translations_use_the_arguments_the_english_message_declares(
    locale: str,
) -> None:
    english = _parse("en")
    other = _parse(locale)

    for domain, messages in english.items():
        for mid, message in messages.items():
            assert _variables(other[domain][mid]) == _variables(message), (
                f"{locale}/{domain}.ftl: {mid} uses different arguments than en"
            )


@pytest.mark.parametrize("locale", SUPPORTED)
def test_every_message_formats_with_its_declared_arguments(locale: str) -> None:
    for messages in _parse(locale).values():
        for mid, message in messages.items():
            names = sorted(_variables(message))
            keys = [mid] if message.value is not None else []
            keys += [f"{mid}.{attribute}" for attribute in sorted(_attributes(message))]
            for key, number in itertools.product(keys, (0, 1, 2, 5, 11, 21)):
                values = {name: number for name in names}
                rendered = loader._render(locale, key, loader._prepare_args(values))
                assert rendered is not None, f"{locale}: {key} did not resolve"
                assert not rendered.errors, f"{locale}: {key}: {rendered.errors}"
                assert not any(mark in rendered.text for mark in BIDI_MARKS)


def test_ftl_sources_contain_no_isolation_marks() -> None:
    for locale in SUPPORTED:
        for domain, source in loader.read_locale_sources(locale).items():
            assert not any(mark in source for mark in BIDI_MARKS), (
                f"{locale}/{domain}.ftl"
            )


def test_language_names_are_the_same_in_every_locale() -> None:
    for code in SUPPORTED:
        names = {t(locale, f"language-name-{code}") for locale in SUPPORTED}
        assert len(names) == 1, (
            f"language-name-{code} must be an endonym in every locale"
        )


# --- keys referenced as literals in code --------------------------------------------------------


def _imports_t(tree: ast.Module) -> bool:
    return any(
        isinstance(node, ast.ImportFrom)
        and (node.module or "").split(".")[-1] == "i18n"
        and any(alias.name == "t" for alias in node.names)
        for node in ast.walk(tree)
    )


def _literal_keys(path: Path) -> list[tuple[int, str]]:
    """String-literal keys of ``t(locale, "key", ...)`` and ``locale_str(..., key="key")``."""
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    uses_bare_t = _imports_t(tree)
    found: list[tuple[int, str]] = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        func = node.func
        name = func.id if isinstance(func, ast.Name) else getattr(func, "attr", None)
        owner = (
            func.value.id
            if isinstance(func, ast.Attribute) and isinstance(func.value, ast.Name)
            else None
        )
        is_t = (name == "t" and uses_bare_t and isinstance(func, ast.Name)) or (
            name == "t" and owner == "i18n"
        )
        if is_t and len(node.args) >= 2:
            key = node.args[1]
            if isinstance(key, ast.Constant) and isinstance(key.value, str):
                found.append((node.lineno, key.value))
        if name == "locale_str":
            for keyword in node.keywords:
                if (
                    keyword.arg == "key"
                    and isinstance(keyword.value, ast.Constant)
                    and isinstance(keyword.value.value, str)
                ):
                    found.append((node.lineno, keyword.value.value))
    return found


def test_every_literal_key_in_code_exists_in_every_locale() -> None:
    ids = {locale: _all_ids(locale) for locale in SUPPORTED}
    checked = 0
    for path in sorted(PACKAGE_DIR.rglob("*.py")):
        for lineno, key in _literal_keys(path):
            message_id = key.partition(".")[0]
            for locale in SUPPORTED:
                assert message_id in ids[locale], (
                    f"{path.relative_to(PACKAGE_DIR)}:{lineno} uses {key!r}, missing in {locale}"
                )
            checked += 1
    assert checked >= 0  # nothing to check yet is fine; the scan itself must not crash


def test_key_scanner_finds_t_calls_and_locale_str_keys(tmp_path: Path) -> None:
    source = tmp_path / "sample.py"
    source.write_text(
        "from .i18n import t\n"
        "from discord.app_commands import locale_str\n"
        "a = t(loc, 'one')\n"
        "b = t(loc, 'two.label', n=1)\n"
        "c = t(loc, dynamic_key)\n"
        "d = locale_str('x', key='three')\n"
        "e = other.t(loc, 'ignored')\n",
        encoding="utf-8",
    )

    assert [key for _, key in _literal_keys(source)] == ["one", "two.label", "three"]


# --- the packaged files -------------------------------------------------------------------------


def test_bundles_load_through_importlib_resources() -> None:
    root = resources.files("xcore_discord_bot.i18n") / "locales"

    for locale in SUPPORTED:
        entries = [entry.name for entry in (root / locale).iterdir()]
        assert "core.ftl" in entries
        assert loader.get_bundle(locale).has_message("error-generic")


def test_loader_does_not_depend_on_the_source_tree_layout() -> None:
    source = (PACKAGE_DIR / "i18n" / "loader.py").read_text(encoding="utf-8")

    assert "__file__" not in source
    assert "pathlib" not in source


def test_bundles_load_from_a_zipped_copy_of_the_package(tmp_path: Path) -> None:
    """A wheel is a zip: the loader must read .ftl files from there, not from a directory."""
    archive = tmp_path / "bot.zip"
    i18n_dir = PACKAGE_DIR / "i18n"
    with zipfile.ZipFile(archive, "w") as bundle:
        bundle.writestr("xcore_discord_bot/__init__.py", "")
        for path in sorted(i18n_dir.rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                bundle.write(
                    path, Path("xcore_discord_bot") / path.relative_to(PACKAGE_DIR)
                )

    script = (
        "import xcore_discord_bot.i18n as i18n; "
        "print(i18n.__file__); "
        "print(i18n.t('ru', 'button-yes')); "
        "print(i18n.t('uk', 'button-yes'))"
    )
    result = subprocess.run(
        [sys.executable, "-c", script],
        env={"PYTHONPATH": str(archive), "PATH": ""},
        capture_output=True,
        text=True,
        cwd=tmp_path,
        check=False,
    )

    assert result.returncode == 0, result.stderr
    lines = result.stdout.splitlines()
    assert str(archive) in lines[0]
    assert lines[1:] == ["Да", "Так"]
