from __future__ import annotations

from xcore_protocol.generated import (
    ROUTES_BY_MESSAGE,
    SECURITY_PERMISSIONS_CHANGED_V1,
    SECURITY_STAFF_RESET_PASSWORD_REQUEST_V1,
    SECURITY_STAFF_SYNC_REQUEST_V1,
    SecurityPermissionsChangedV1,
    SecurityStaffResetPasswordRequestV1,
    SecurityStaffResetPasswordResponseV1,
    SecurityStaffSyncRequestV1,
    SecurityStaffSyncResponseV1,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_security_models_remain_strict() -> None:
    invalid_payload = load_json(
        fixtures_root() / "invalid" / "security" / "security.permissions.changed.v1.legacy-uuid.json"
    )

    try:
        SecurityPermissionsChangedV1.from_payload(invalid_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated parsing to reject the legacy uuid field")


def test_password_reset_travels_only_as_the_staff_rpc() -> None:
    # A reset is stored with the player's grants; no separate command is broadcast.
    assert ("player.password-reset.command", 1) not in ROUTES_BY_MESSAGE


PERMISSION_MODELS = (
    ("security.permissions.changed", "security.permissions.changed.v1", SecurityPermissionsChangedV1),
    ("security.permissions.changed", "security.permissions.changed.v1.minimal", SecurityPermissionsChangedV1),
    ("security.staff.sync.request", "security.staff.sync.request.v1", SecurityStaffSyncRequestV1),
    ("security.staff.sync.request", "security.staff.sync.request.v1.left-guild", SecurityStaffSyncRequestV1),
    ("security.staff.sync.response", "security.staff.sync.response.v1", SecurityStaffSyncResponseV1),
    ("security.staff.reset-password.request", "security.staff.reset-password.request.v1", SecurityStaffResetPasswordRequestV1),
    ("security.staff.reset-password.response", "security.staff.reset-password.response.v1", SecurityStaffResetPasswordResponseV1),
)


def test_generated_permission_models_roundtrip_match_fixtures() -> None:
    for message, fixture, model_type in PERMISSION_MODELS:
        payload = load_json(fixtures_root() / "valid" / "security" / f"{fixture}.json")

        model = model_type.from_payload(payload)

        assert model.to_payload() == payload, fixture
        validate_instance(spec_root() / "messages" / "security" / f"{message}.v1.json", model.to_payload())


def test_staff_sync_keeps_an_empty_complete_snapshot_apart_from_a_missing_one() -> None:
    payload = load_json(fixtures_root() / "valid" / "security" / "security.staff.sync.request.v1.left-guild.json")

    model = SecurityStaffSyncRequestV1.from_payload(payload)

    assert list(model.roleIds) == []
    assert model.complete is True

    incomplete = load_json(
        fixtures_root() / "invalid" / "security" / "security.staff.sync.request.v1.missing-complete.json"
    )
    try:
        SecurityStaffSyncRequestV1.from_payload(incomplete)
    except ValueError as error:
        assert "missing required fields" in str(error)
    else:
        raise AssertionError("A sync request that does not say whether it is complete must be rejected")


def test_staff_sync_rejects_duplicate_role_ids() -> None:
    duplicate = load_json(
        fixtures_root() / "invalid" / "security" / "security.staff.sync.request.v1.duplicate-role.json"
    )
    try:
        SecurityStaffSyncRequestV1.from_payload(duplicate)
    except ValueError as error:
        assert "roleIds must not contain duplicate items" in str(error)
    else:
        raise AssertionError("A sync request that names a role twice must be rejected")


def test_generated_route_registry_includes_permission_messages() -> None:
    assert SECURITY_PERMISSIONS_CHANGED_V1.payloadType is SecurityPermissionsChangedV1
    assert SECURITY_STAFF_SYNC_REQUEST_V1.payloadType is SecurityStaffSyncRequestV1
    assert SECURITY_STAFF_RESET_PASSWORD_REQUEST_V1.payloadType is SecurityStaffResetPasswordRequestV1
    assert ROUTES_BY_MESSAGE[("security.permissions.changed", 1)].stream == "xcore:evt:security:permissions-changed"
    assert ROUTES_BY_MESSAGE[("security.staff.sync.request", 1)].stream == "xcore:rpc:req:{server}"
    assert ROUTES_BY_MESSAGE[("security.staff.reset-password.request", 1)].stream == "xcore:rpc:req:{server}"
