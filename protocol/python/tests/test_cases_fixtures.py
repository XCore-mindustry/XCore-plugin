from __future__ import annotations

from pathlib import Path

import pytest

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


@pytest.mark.parametrize("kind", ("valid", "invalid"))
def test_cases_fixture_inventory_covers_every_message(kind: str) -> None:
    for schema in (spec_root() / "messages" / "cases").glob("*.json"):
        name = schema.name.removesuffix(".json")
        assert list((fixtures_root() / kind / "cases").glob(f"{name}*.json"))


@pytest.mark.parametrize(
    "fixture_path",
    sorted((fixtures_root() / "valid" / "cases").glob("*.json")),
    ids=lambda path: path.name,
)
def test_valid_cases_fixtures_pass(fixture_path: Path) -> None:
    name = fixture_path.name.split(".v1")[0]
    assert_valid(spec_root() / "messages" / "cases" / f"{name}.v1.json", fixture_path)


@pytest.mark.parametrize(
    "fixture_path",
    sorted((fixtures_root() / "invalid" / "cases").glob("*.json")),
    ids=lambda path: path.name,
)
def test_invalid_cases_fixtures_fail(fixture_path: Path) -> None:
    name = fixture_path.name.split(".v1")[0]
    assert_invalid(spec_root() / "messages" / "cases" / f"{name}.v1.json", fixture_path)
