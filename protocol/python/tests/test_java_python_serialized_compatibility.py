from __future__ import annotations

from xcore_protocol.generated import (
    DiscordLinkConfirmCommandV1,
    MapsListRequestV1,
    ModerationAuditAppendedV1,
    ServerHeartbeatV1,
)
from xcore_protocol.paths import fixtures_root
from xcore_protocol.schema_validation import load_json


def test_maps_list_request_fixture_remains_canonical_golden_payload() -> None:
    payload = load_json(fixtures_root() / "valid" / "maps" / "maps.list.request.v1.json")

    model = MapsListRequestV1.from_payload(payload)

    assert model.to_payload() == payload


def test_server_heartbeat_fixture_remains_canonical_golden_payload() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "server.heartbeat.v1.json")

    model = ServerHeartbeatV1.from_payload(payload)

    assert model.to_payload() == payload


def test_discord_link_confirm_fixture_remains_canonical_golden_payload() -> None:
    payload = load_json(fixtures_root() / "valid" / "discord" / "discord.link.confirm.command.v1.json")

    model = DiscordLinkConfirmCommandV1.from_payload(payload)

    assert model.to_payload() == payload


def test_moderation_audit_fixture_remains_canonical_golden_payload() -> None:
    payload = load_json(fixtures_root() / "valid" / "moderation" / "moderation.audit.appended.v1.json")

    model = ModerationAuditAppendedV1.from_payload(payload)

    assert model.to_payload() == payload
