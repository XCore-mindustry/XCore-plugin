from __future__ import annotations

from xcore_protocol.generated import (
    PLAYER_PASSWORD_RESET_COMMAND_V1,
    PlayerPasswordResetCommandV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_player_password_reset_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "security" / "player.password-reset.command.v1.json")

    model = PlayerPasswordResetCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "security" / "player.password-reset.command.v1.json",
        model.to_payload(),
    )


def test_generated_security_models_remain_strict() -> None:
    invalid_password_reset_payload = load_json(
        fixtures_root() / "invalid" / "security" / "player.password-reset.command.v1.legacy-uuid.json"
    )

    try:
        PlayerPasswordResetCommandV1.from_payload(invalid_password_reset_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated password-reset parsing to reject legacy uuid field")


def test_generated_route_registry_includes_security_messages() -> None:
    assert PLAYER_PASSWORD_RESET_COMMAND_V1.payloadType is PlayerPasswordResetCommandV1
    assert ROUTES_BY_MESSAGE[("player.password-reset.command", 1)].stream == "xcore:cmd:player-password-reset:{server}"
