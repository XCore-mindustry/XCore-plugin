from __future__ import annotations

from xcore_protocol.generated import (
    DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1,
    DISCORD_LINK_CODE_CREATED_V1,
    DISCORD_LINK_CONFIRM_COMMAND_V1,
    DISCORD_LINK_STATUS_CHANGED_V1,
    DISCORD_UNLINK_COMMAND_V1,
    DiscordAdminAccessChangedCommandV1,
    DiscordLinkCodeCreatedV1,
    DiscordLinkConfirmCommandV1,
    DiscordLinkStatusChangedV1,
    DiscordUnlinkCommandV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_discord_link_confirm_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "discord" / "discord.link.confirm.command.v1.json")

    model = DiscordLinkConfirmCommandV1.from_payload(payload)

    assert model.code == payload["code"]
    assert model.player.to_payload() == payload["player"]
    assert model.discord.to_payload() == payload["discord"]
    assert model.server == payload["server"]
    assert model.confirmedAt == payload["confirmedAt"]
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "discord" / "discord.link.confirm.command.v1.json",
        model.to_payload(),
    )


def test_generated_discord_link_code_created_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "discord" / "discord.link-code-created.v1.json")

    model = DiscordLinkCodeCreatedV1.from_payload(payload)

    assert model.code == payload["code"]
    assert model.player.to_payload() == payload["player"]
    assert model.server == payload["server"]
    assert model.createdAt == payload["createdAt"]
    assert model.expiresAt == payload["expiresAt"]
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "discord" / "discord.link-code-created.v1.json",
        model.to_payload(),
    )


def test_generated_discord_unlink_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "discord" / "discord.unlink.command.v1.json")

    model = DiscordUnlinkCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "discord" / "discord.unlink.command.v1.json",
        model.to_payload(),
    )


def test_generated_discord_link_status_changed_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "discord" / "discord.link.status-changed.v1.json")

    model = DiscordLinkStatusChangedV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "discord" / "discord.link.status-changed.v1.json",
        model.to_payload(),
    )


def test_generated_discord_admin_access_changed_roundtrip_matches_fixture() -> None:
    payload = load_json(
        fixtures_root() / "valid" / "discord" / "discord.admin-access.changed.command.v1.json"
    )

    model = DiscordAdminAccessChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "discord" / "discord.admin-access.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_discord_models_remain_strict() -> None:
    invalid_payload = load_json(
        fixtures_root() / "invalid" / "discord" / "discord.unlink.command.v1.missing-discord.json"
    )

    try:
        DiscordUnlinkCommandV1.from_payload(invalid_payload)
    except ValueError as error:
        assert "missing required fields" in str(error)
    else:
        raise AssertionError("Expected strict generated model parsing to reject missing nested fields")


def test_generated_discord_route_registry_matches_expected_messages() -> None:
    assert DISCORD_LINK_CODE_CREATED_V1.payloadType is DiscordLinkCodeCreatedV1
    assert DISCORD_LINK_CONFIRM_COMMAND_V1.payloadType is DiscordLinkConfirmCommandV1
    assert DISCORD_UNLINK_COMMAND_V1.payloadType is DiscordUnlinkCommandV1
    assert DISCORD_LINK_STATUS_CHANGED_V1.payloadType is DiscordLinkStatusChangedV1
    assert DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1.payloadType is DiscordAdminAccessChangedCommandV1
    assert ROUTES_BY_MESSAGE[("discord.link-code-created", 1)].stream == "xcore:evt:discord:link-code"
    assert ROUTES_BY_MESSAGE[("discord.link.confirm.command", 1)].stream == "xcore:cmd:discord-link-confirm:{server}"
    assert ROUTES_BY_MESSAGE[("discord.link.status-changed", 1)].kind == "event"
