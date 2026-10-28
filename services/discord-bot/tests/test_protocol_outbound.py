"""Protocol-level round-trip tests for outbound command builders."""

from __future__ import annotations

from typing import Any

from xcore_protocol.generated.chat import (
    ChatDiscordIngressCommandV1,
)
from xcore_protocol.generated.discord import DiscordAccountOperationRequestV1
from xcore_protocol.generated.discord import (
    DiscordAccountOperationRequestV1Action as Action,
)
from xcore_protocol.generated.identity import (
    PlayerActiveBadgeChangedCommandV1,
    PlayerBadgeInventoryChangedCommandV1,
)
from xcore_protocol.generated.maps import (
    MapsListRequestV1,
    MapsLoadCommandV1,
    MapsRemoveRequestV1,
)
from xcore_protocol.generated.moderation import (
    ModerationKickBannedCommandV1,
    ModerationPardonCommandV1,
)
from xcore_protocol.generated.shared import MapFileSourceV1

from xcore_discord_bot.protocol_outbound import (
    build_chat_discord_ingress_command,
    build_discord_account_operation_request,
    build_maps_list_request,
    build_maps_load_command,
    build_maps_remove_request,
    build_moderation_kick_banned_command,
    build_moderation_pardon_command,
    build_player_active_badge_changed_command,
    build_player_badge_inventory_changed_command,
)


def test_moderation_kick_banned_round_trip() -> None:
    command = build_moderation_kick_banned_command(
        uuid_value="uuid-1",
        ip="192.168.0.1",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = ModerationKickBannedCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.target.playerUuid == "uuid-1"
    assert parsed.target.ip == "192.168.0.1"
    assert parsed.server == "mini-pvp"


def test_moderation_pardon_round_trip() -> None:
    command = build_moderation_pardon_command(
        uuid_value="uuid-1",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = ModerationPardonCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.target.playerUuid == "uuid-1"
    assert parsed.server == "mini-pvp"


def _account_request(**overrides: object) -> DiscordAccountOperationRequestV1:
    fields: dict[str, Any] = {
        "action": Action.LINK,
        "server": "mini-pvp",
        "source_id": 77,
        "player_uuid": "uuid-7",
        "player_pid": 7,
        "player_name": "Target",
        "discord_id": "123456",
        "discord_username": "osp54",
        "issued_at": 1_000,
        "timeout_ms": 10_000,
        "code": "ABC123",
    }
    fields.update(overrides)
    return build_discord_account_operation_request(**fields)


def test_discord_account_operation_round_trip() -> None:
    request = _account_request()

    parsed = DiscordAccountOperationRequestV1.from_payload(request.to_payload())

    assert parsed == request
    assert parsed.action is Action.LINK
    assert parsed.code == "ABC123"
    assert parsed.expectedLinkedAt is None
    assert parsed.player.playerPid == 7
    assert parsed.discord.discordUsername == "osp54"
    assert (parsed.issuedAt, parsed.expiresAt) == (1_000, 11_000)


def test_one_intent_keeps_its_operation_id_and_another_gets_a_new_one() -> None:
    first = _account_request()

    assert _account_request(issued_at=2_000).operationId == first.operationId
    assert _account_request(source_id=78).operationId != first.operationId
    assert _account_request(code="XYZ789").operationId != first.operationId
    assert _account_request(server="other").operationId != first.operationId
    unlink = _account_request(action=Action.UNLINK, code=None, expected_linked_at=5)
    relinked = _account_request(action=Action.UNLINK, code=None, expected_linked_at=6)
    assert unlink.operationId != relinked.operationId


def test_player_active_badge_changed_round_trip() -> None:
    command = build_player_active_badge_changed_command(
        uuid_value="uuid-7",
        active_badge="translator",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = PlayerActiveBadgeChangedCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.playerUuid == "uuid-7"
    assert parsed.activeBadge == "translator"
    assert parsed.server == "mini-pvp"


def test_player_badge_inventory_changed_round_trip() -> None:
    command = build_player_badge_inventory_changed_command(
        uuid_value="uuid-7",
        active_badge="translator",
        unlocked_badges=["translator", "tester"],
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = PlayerBadgeInventoryChangedCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.playerUuid == "uuid-7"
    assert parsed.activeBadge == "translator"
    assert parsed.unlockedBadges == ("translator", "tester")
    assert parsed.server == "mini-pvp"


def test_chat_discord_ingress_round_trip() -> None:
    command = build_chat_discord_ingress_command(
        author_name="mod",
        message="hello world",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = ChatDiscordIngressCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.authorName == "mod"
    assert parsed.message == "hello world"
    assert parsed.server == "mini-pvp"


def test_maps_load_round_trip() -> None:
    command = build_maps_load_command(
        server="mini-pvp",
        files=[
            {"url": "https://example/one.msav", "filename": "one.msav"},
            {"url": "https://example/two.msav", "fileName": "two.msav"},
        ],
    )
    wire = command.to_payload()
    parsed = MapsLoadCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.server == "mini-pvp"
    assert len(parsed.files) == 2
    assert parsed.files[0] == MapFileSourceV1(
        url="https://example/one.msav", fileName="one.msav"
    )
    assert parsed.files[1] == MapFileSourceV1(
        url="https://example/two.msav", fileName="two.msav"
    )


def test_maps_list_request_round_trip() -> None:
    request = build_maps_list_request(server="mini-pvp")
    wire = request.to_payload()
    parsed = MapsListRequestV1.from_payload(wire)
    assert parsed == request
    assert parsed.server == "mini-pvp"
    assert wire["messageType"] == "maps.list.request"
    assert wire["messageVersion"] == 1


def test_maps_remove_request_round_trip() -> None:
    request = build_maps_remove_request(server="mini-pvp", file_name="map-a.msav")
    wire = request.to_payload()
    parsed = MapsRemoveRequestV1.from_payload(wire)
    assert parsed == request
    assert parsed.server == "mini-pvp"
    assert parsed.fileName == "map-a.msav"
    assert wire["messageType"] == "maps.remove.request"
    assert wire["messageVersion"] == 1
