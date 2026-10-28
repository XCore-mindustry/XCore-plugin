from __future__ import annotations

from xcore_protocol.generated import (
    ActorRefV1ActorType,
    DISCORD_ACCOUNT_OPERATION_REQUEST_V1,
    DiscordAccountOperationRequestV1,
    DiscordAccountOperationRequestV1Action,
    DiscordAccountOperationResponseV1,
    DiscordAccountOperationResponseV1Status,
    DISCORD_LINK_CODE_CREATED_V1,
    DISCORD_LINK_CONFIRM_COMMAND_V1,
    DISCORD_LINK_STATUS_CHANGED_V1,
    DISCORD_UNLINK_COMMAND_V1,
    DiscordLinkCodeCreatedV1,
    DiscordLinkConfirmCommandV1,
    DiscordLinkStatusChangedV1Action,
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

    assert model.actor.to_payload() == payload["actor"]
    assert model.actor.actorType is ActorRefV1ActorType.SYSTEM
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "discord" / "discord.unlink.command.v1.json",
        model.to_payload(),
    )


def test_generated_discord_link_status_changed_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "discord" / "discord.link.status-changed.v1.json")

    model = DiscordLinkStatusChangedV1.from_payload(payload)

    assert model.action is DiscordLinkStatusChangedV1Action.LINKED
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "discord" / "discord.link.status-changed.v1.json",
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
    assert ROUTES_BY_MESSAGE[("discord.link-code-created", 1)].stream == "xcore:evt:discord:link-code"
    assert ROUTES_BY_MESSAGE[("discord.link.confirm.command", 1)].stream == "xcore:cmd:discord-link-confirm:{server}"
    assert ROUTES_BY_MESSAGE[("discord.link.status-changed", 1)].kind == "event"
    assert ROUTES_BY_MESSAGE[("discord.link.confirm.command", 1)].bindings == {"server": "payload.server"}
    # Discord roles reach the game as grants through the staff sync RPC.
    assert ("discord.admin-access.changed.command", 1) not in ROUTES_BY_MESSAGE


def test_generated_discord_account_operation_roundtrips_match_fixtures() -> None:
    for name, model_type in (
        ("discord.account.operation.request.v1.link", DiscordAccountOperationRequestV1),
        ("discord.account.operation.request.v1.unlink", DiscordAccountOperationRequestV1),
        ("discord.account.operation.response.v1", DiscordAccountOperationResponseV1),
        ("discord.account.operation.response.v1.rejected", DiscordAccountOperationResponseV1),
    ):
        payload = load_json(fixtures_root() / "valid" / "discord" / f"{name}.json")
        model = model_type.from_payload(payload)
        assert model.to_payload() == payload
        validate_instance(
            spec_root() / "messages" / "discord" / f"{model_type.MESSAGE_TYPE}.v1.json",
            model.to_payload(),
        )

    link = DiscordAccountOperationRequestV1.from_payload(
        load_json(fixtures_root() / "valid" / "discord" / "discord.account.operation.request.v1.link.json")
    )
    assert link.action is DiscordAccountOperationRequestV1Action.LINK
    assert link.code == "ABC123"
    assert link.expectedLinkedAt is None
    rejected = DiscordAccountOperationResponseV1.from_payload(
        load_json(fixtures_root() / "valid" / "discord" / "discord.account.operation.response.v1.rejected.json")
    )
    assert rejected.status is DiscordAccountOperationResponseV1Status.REJECTED


def test_generated_discord_account_operation_rejects_a_non_uuid_operation_id() -> None:
    payload = load_json(
        fixtures_root() / "invalid" / "discord" / "discord.account.operation.request.v1.bad-operation-id.json"
    )

    try:
        DiscordAccountOperationRequestV1.from_payload(payload)
    except ValueError as error:
        assert "operationId" in str(error)
    else:
        raise AssertionError("Expected a non-UUID operationId to be rejected")


def test_generated_discord_account_operation_route_is_targeted_rpc() -> None:
    route = ROUTES_BY_MESSAGE[("discord.account.operation.request", 1)]

    assert route is DISCORD_ACCOUNT_OPERATION_REQUEST_V1
    assert route.payloadType is DiscordAccountOperationRequestV1
    assert route.kind == "rpc-request"
    assert route.stream == "xcore:rpc:req:{server}"
    assert route.bindings == {"server": "payload.server"}
    assert route.ttlMs == 10000
    assert route.response is not None
    assert route.response.payloadType is DiscordAccountOperationResponseV1
    assert route.response.stream == "xcore:rpc:resp:{requester}"
