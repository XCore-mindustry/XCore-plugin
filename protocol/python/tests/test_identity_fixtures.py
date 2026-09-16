from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid


VALID_CASES = [
    (
        spec_root() / "messages" / "identity" / "player.join-leave.v1.json",
        fixtures_root() / "valid" / "identity" / "player.join-leave.v1.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.custom-nickname.changed.command.v1.json",
        fixtures_root() / "valid" / "identity" / "player.custom-nickname.changed.command.v1.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.active-badge.changed.command.v1.json",
        fixtures_root() / "valid" / "identity" / "player.active-badge.changed.command.v1.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.badge-inventory.changed.command.v1.json",
        fixtures_root() / "valid" / "identity" / "player.badge-inventory.changed.command.v1.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.badge-symbol-color-mode.changed.command.v1.json",
        fixtures_root() / "valid" / "identity" / "player.badge-symbol-color-mode.changed.command.v1.json",
    ),
]


INVALID_CASES = [
    (
        spec_root() / "messages" / "identity" / "player.join-leave.v1.json",
        fixtures_root() / "invalid" / "identity" / "player.join-leave.v1.legacy-join.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.custom-nickname.changed.command.v1.json",
        fixtures_root() / "invalid" / "identity" / "player.custom-nickname.changed.command.v1.legacy-uuid.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.active-badge.changed.command.v1.json",
        fixtures_root() / "invalid" / "identity" / "player.active-badge.changed.command.v1.legacy-uuid.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.badge-inventory.changed.command.v1.json",
        fixtures_root() / "invalid" / "identity" / "player.badge-inventory.changed.command.v1.legacy-uuid.json",
    ),
    (
        spec_root() / "messages" / "identity" / "player.badge-symbol-color-mode.changed.command.v1.json",
        fixtures_root() / "invalid" / "identity" / "player.badge-symbol-color-mode.changed.command.v1.legacy-uuid.json",
    ),
]


def test_valid_identity_fixtures_pass() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_identity_fixtures_fail() -> None:
    for schema_path, fixture_path in INVALID_CASES:
        error = assert_invalid(schema_path, fixture_path)
        assert error is not None


def test_identity_fixture_inventory_exists() -> None:
    valid_dir = fixtures_root() / "valid" / "identity"
    invalid_dir = fixtures_root() / "invalid" / "identity"

    assert valid_dir.exists()
    assert invalid_dir.exists()
    assert any(valid_dir.iterdir())
    assert any(invalid_dir.iterdir())
