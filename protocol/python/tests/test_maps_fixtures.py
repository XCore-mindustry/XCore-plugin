from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "maps" / "maps.list.request.v1.json",
        fixtures_root() / "valid" / "maps" / "maps.list.request.v1.json",
    ),
    (
        spec_root() / "messages" / "maps" / "maps.list.response.v1.json",
        fixtures_root() / "valid" / "maps" / "maps.list.response.v1.json",
    ),
    (
        spec_root() / "messages" / "maps" / "maps.remove.request.v1.json",
        fixtures_root() / "valid" / "maps" / "maps.remove.request.v1.json",
    ),
    (
        spec_root() / "messages" / "maps" / "maps.remove.response.v1.json",
        fixtures_root() / "valid" / "maps" / "maps.remove.response.v1.json",
    ),
    (
        spec_root() / "messages" / "maps" / "maps.load.command.v1.json",
        fixtures_root() / "valid" / "maps" / "maps.load.command.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "maps" / "maps.list.response.v1.json",
        fixtures_root()
        / "invalid"
        / "maps"
        / "maps.list.response.v1.legacy-file-name.json",
    ),
    (
        spec_root() / "messages" / "maps" / "maps.remove.request.v1.json",
        fixtures_root()
        / "invalid"
        / "maps"
        / "maps.remove.request.v1.path-filename.json",
    ),
    (
        spec_root() / "messages" / "maps" / "maps.load.command.v1.json",
        fixtures_root()
        / "invalid"
        / "maps"
        / "maps.load.command.v1.empty-files.json",
    ),
    (
        spec_root() / "messages" / "maps" / "maps.load.command.v1.json",
        fixtures_root()
        / "invalid"
        / "maps"
        / "maps.load.command.v1.bad-extension.json",
    ),
]


def test_valid_maps_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_maps_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_maps_fixture_inventory_exists() -> None:
    maps_valid_dir = fixtures_root() / "valid" / "maps"
    maps_invalid_dir = fixtures_root() / "invalid" / "maps"

    assert maps_valid_dir.exists()
    assert maps_invalid_dir.exists()
    assert any(maps_valid_dir.iterdir())
    assert any(maps_invalid_dir.iterdir())
