from __future__ import annotations

from jsonschema import ValidationError

from xcore_protocol.generated import (
    MODERATION_AUDIT_APPENDED_V1,
    MODERATION_BAN_CREATED_V1,
    MODERATION_KICK_BANNED_COMMAND_V1,
    MODERATION_MUTE_CREATED_V1,
    MODERATION_PARDON_COMMAND_V1,
    MODERATION_VOTE_KICK_CREATED_V1,
    ModerationAuditAppendedV1,
    ModerationBanCreatedV1,
    ModerationKickBannedCommandV1,
    ModerationMuteCreatedV1,
    ModerationPardonCommandV1,
    ModerationVoteKickCreatedV1,
    ROUTES_BY_MESSAGE,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_moderation_ban_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.ban.created.v1.json")

    model = ModerationBanCreatedV1.from_payload(payload)

    assert model.target.to_payload() == payload["target"]
    assert model.actor.to_payload() == payload["actor"]
    assert model.reason == payload["reason"]
    assert model.expiration is not None
    assert model.expiration.to_payload() == payload["expiration"]
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "moderation" / "moderation.ban.created.v1.json", model.to_payload())


def test_generated_moderation_mute_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.mute.created.v1.json")

    model = ModerationMuteCreatedV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "moderation" / "moderation.mute.created.v1.json", model.to_payload())


def test_generated_moderation_vote_kick_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.vote-kick.created.v1.json")

    model = ModerationVoteKickCreatedV1.from_payload(payload)

    assert model.target.to_payload() == payload["target"]
    assert model.starter.to_payload() == payload["starter"]
    assert [item.to_payload() for item in model.votesFor or ()] == payload["votesFor"]
    assert [item.to_payload() for item in model.votesAgainst or ()] == payload["votesAgainst"]
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.vote-kick.created.v1.json",
        model.to_payload(),
    )


def test_generated_moderation_kick_banned_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.kick-banned.command.v1.json")

    model = ModerationKickBannedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.kick-banned.command.v1.json",
        model.to_payload(),
    )
    assert model.target.playerUuid == payload["target"]["playerUuid"]
    assert model.target.playerName is None


def test_generated_moderation_kick_banned_accepts_ip_only_target() -> None:
    payload = load_json(
        fixtures_root() / "valid" / "moderation" / "moderation.kick-banned.command.v1.ip-only-target.json"
    )

    model = ModerationKickBannedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.kick-banned.command.v1.json",
        model.to_payload(),
    )
    assert model.target.playerUuid is None
    assert model.target.ip == payload["target"]["ip"]


def test_generated_moderation_pardon_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.pardon.command.v1.json")

    model = ModerationPardonCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.pardon.command.v1.json",
        model.to_payload(),
    )
    assert model.target.playerUuid == payload["target"]["playerUuid"]
    assert model.target.playerName is None


def test_generated_moderation_pardon_accepts_ip_only_target() -> None:
    payload = load_json(
        fixtures_root() / "valid" / "moderation" / "moderation.pardon.command.v1.ip-only-target.json"
    )

    model = ModerationPardonCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.pardon.command.v1.json",
        model.to_payload(),
    )
    assert model.target.playerUuid is None
    assert model.target.ip == payload["target"]["ip"]


def test_generated_moderation_audit_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.audit.appended.v1.json")

    model = ModerationAuditAppendedV1.from_payload(payload)

    assert model.entryType == payload["entryType"]
    assert model.target.to_payload() == payload["target"]
    assert model.actor.to_payload() == payload["actor"]
    assert model.details == payload["details"]
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.audit.appended.v1.json",
        model.to_payload(),
    )


def test_generated_moderation_audit_accepts_ip_only_target() -> None:
    payload = load_json(
        fixtures_root() / "valid" / "moderation" / "moderation.audit.appended.v1.ip-only-target.json"
    )

    model = ModerationAuditAppendedV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.audit.appended.v1.json",
        model.to_payload(),
    )
    assert model.target.playerUuid is None
    assert model.target.ip == payload["target"]["ip"]


def test_generated_moderation_audit_roundtrip_preserves_nullable_details_values() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.audit.appended.v1.json")
    payload["details"]["note"] = None

    model = ModerationAuditAppendedV1.from_payload(payload)

    assert model.details == payload["details"]
    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "moderation" / "moderation.audit.appended.v1.json",
        model.to_payload(),
    )


def test_moderation_audit_schema_rejects_invalid_occurred_at_format() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.audit.appended.v1.json")
    payload["occurredAt"] = "not-a-date-time"

    try:
        validate_instance(
            spec_root() / "messages" / "moderation" / "moderation.audit.appended.v1.json",
            payload,
        )
    except ValidationError as error:
        assert error.validator == "format"
    else:
        raise AssertionError("Expected schema validation to enforce date-time format")


def test_generated_moderation_models_remain_strict() -> None:
    invalid_payload = load_json(
        fixtures_root() / "invalid" / "moderation" / "moderation.vote-kick.created.v1.bad-votes.json"
    )

    try:
        ModerationVoteKickCreatedV1.from_payload(invalid_payload)
    except TypeError as error:
        assert "votesFor must be a list" in str(error)
    else:
        raise AssertionError("Expected strict moderation parsing to reject invalid vote payloads")


def test_generated_moderation_command_models_reject_missing_uuid_and_ip() -> None:
    invalid_kick_payload = load_json(
        fixtures_root() / "invalid" / "moderation" / "moderation.kick-banned.command.v1.missing-target-identity.json"
    )

    try:
        ModerationKickBannedCommandV1.from_payload(invalid_kick_payload)
    except ValueError as error:
        assert "At least one of playerUuid, ip must be provided" in str(error)
    else:
        raise AssertionError("Expected strict moderation command parsing to reject missing target identity")


def test_generated_moderation_route_registry_matches_expected_messages() -> None:
    assert MODERATION_BAN_CREATED_V1.payloadType is ModerationBanCreatedV1
    assert MODERATION_MUTE_CREATED_V1.payloadType is ModerationMuteCreatedV1
    assert MODERATION_VOTE_KICK_CREATED_V1.payloadType is ModerationVoteKickCreatedV1
    assert MODERATION_KICK_BANNED_COMMAND_V1.payloadType is ModerationKickBannedCommandV1
    assert MODERATION_PARDON_COMMAND_V1.payloadType is ModerationPardonCommandV1
    assert MODERATION_AUDIT_APPENDED_V1.payloadType is ModerationAuditAppendedV1
    assert ROUTES_BY_MESSAGE[("moderation.kick-banned.command", 1)].stream == "xcore:cmd:kick-banned:{server}"
    assert ROUTES_BY_MESSAGE[("moderation.audit.appended", 1)].kind == "event"
