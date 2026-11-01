from __future__ import annotations

from xcore_protocol.generated import (
    PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1,
    PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1,
    PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1,
    PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1,
    PLAYER_JOIN_LEAVE_V1,
    PlayerActiveBadgeChangedCommandV1,
    PlayerBadgeInventoryChangedCommandV1,
    PlayerBadgeSymbolColorModeChangedCommandV1,
    PlayerCustomNicknameChangedCommandV1,
    PlayerJoinLeaveV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_player_join_leave_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "identity" / "player.join-leave.v1.json")

    model = PlayerJoinLeaveV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "identity" / "player.join-leave.v1.json", model.to_payload())


def test_generated_player_custom_nickname_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "identity" / "player.custom-nickname.changed.command.v1.json")

    model = PlayerCustomNicknameChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "identity" / "player.custom-nickname.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_player_active_badge_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "identity" / "player.active-badge.changed.command.v1.json")

    model = PlayerActiveBadgeChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "identity" / "player.active-badge.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_player_badge_inventory_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "identity" / "player.badge-inventory.changed.command.v1.json")

    model = PlayerBadgeInventoryChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "identity" / "player.badge-inventory.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_player_badge_symbol_color_mode_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "identity" / "player.badge-symbol-color-mode.changed.command.v1.json")

    model = PlayerBadgeSymbolColorModeChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "identity" / "player.badge-symbol-color-mode.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_identity_models_remain_strict() -> None:
    invalid_player_payload = load_json(
        fixtures_root() / "invalid" / "identity" / "player.custom-nickname.changed.command.v1.legacy-uuid.json"
    )

    try:
        PlayerCustomNicknameChangedCommandV1.from_payload(invalid_player_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated player-session command parsing to reject legacy uuid field")


def test_generated_route_registry_includes_identity_messages() -> None:
    assert PLAYER_JOIN_LEAVE_V1.payloadType is PlayerJoinLeaveV1
    assert PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1.payloadType is PlayerCustomNicknameChangedCommandV1
    assert PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1.payloadType is PlayerActiveBadgeChangedCommandV1
    assert PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1.payloadType is PlayerBadgeInventoryChangedCommandV1
    assert PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1.payloadType is PlayerBadgeSymbolColorModeChangedCommandV1
    assert ROUTES_BY_MESSAGE[("player.join-leave", 1)].stream == "xcore:evt:player:joinleave"
    assert ROUTES_BY_MESSAGE[("player.custom-nickname.changed.command", 1)].stream == "xcore:cmd:player-custom-nickname:{server}"
    assert ROUTES_BY_MESSAGE[("player.active-badge.changed.command", 1)].stream == "xcore:cmd:player-active-badge:{server}"
    assert ROUTES_BY_MESSAGE[("player.badge-inventory.changed.command", 1)].stream == "xcore:cmd:player-badge-inventory:{server}"
    assert ROUTES_BY_MESSAGE[("player.badge-symbol-color-mode.changed.command", 1)].stream == "xcore:cmd:player-badge-symbol-color-mode:{server}"
