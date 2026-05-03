"""Protocol-level round-trip tests for outbound command builders."""

from __future__ import annotations

from xcore_protocol.generated.chat import (
    ChatDiscordIngressCommandV1,
    PlayerActiveBadgeChangedCommandV1,
    PlayerBadgeInventoryChangedCommandV1,
    PlayerPasswordResetCommandV1,
)
from xcore_protocol.generated.discord import (
    DiscordAdminAccessChangedCommandV1,
    DiscordLinkConfirmCommandV1,
    DiscordUnlinkCommandV1,
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
    build_discord_admin_access_changed_command,
    build_discord_link_confirm_command,
    build_discord_unlink_command,
    build_maps_list_request,
    build_maps_load_command,
    build_maps_remove_request,
    build_moderation_kick_banned_command,
    build_moderation_pardon_command,
    build_player_active_badge_changed_command,
    build_player_badge_inventory_changed_command,
    build_player_password_reset_command,
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


def test_discord_link_confirm_round_trip() -> None:
    command = build_discord_link_confirm_command(
        code="ABC123",
        player_uuid="uuid-7",
        player_pid=7,
        player_name="Target",
        discord_id="123456",
        discord_username="osp54",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = DiscordLinkConfirmCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.code == "ABC123"
    assert parsed.player.playerUuid == "uuid-7"
    assert parsed.player.playerPid == 7
    assert parsed.player.playerName == "Target"
    assert parsed.discord.discordId == "123456"
    assert parsed.discord.discordUsername == "osp54"
    assert parsed.server == "mini-pvp"


def test_discord_unlink_round_trip() -> None:
    command = build_discord_unlink_command(
        player_uuid="uuid-7",
        player_pid=7,
        player_name="Target",
        discord_id="123456",
        discord_username="osp54",
        actor_name="boss",
        actor_discord_id="42",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = DiscordUnlinkCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.player.playerUuid == "uuid-7"
    assert parsed.player.playerPid == 7
    assert parsed.player.playerName == "Target"
    assert parsed.discord.discordId == "123456"
    assert parsed.discord.discordUsername == "osp54"
    assert parsed.actor.actorName == "boss"
    assert parsed.actor.actorDiscordId == "42"
    assert parsed.server == "mini-pvp"


def test_discord_admin_access_changed_system_actor_round_trip() -> None:
    from xcore_protocol.generated.shared import ActorRefV1ActorType

    command = build_discord_admin_access_changed_command(
        player_uuid="uuid-7",
        player_pid=7,
        player_name="Target",
        discord_id="123456",
        discord_username=None,
        admin=False,
        source_name="NONE",
        source_type=ActorRefV1ActorType.SYSTEM,
        actor_name="system/reconcile",
        actor_discord_id=None,
        actor_type=ActorRefV1ActorType.SYSTEM,
        reason="discord role missing during reconcile",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = DiscordAdminAccessChangedCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.player.playerUuid == "uuid-7"
    assert parsed.player.playerPid == 7
    assert parsed.player.playerName == "Target"
    assert parsed.admin is False
    assert parsed.source.actorName == "NONE"
    assert parsed.source.actorType == ActorRefV1ActorType.SYSTEM
    assert parsed.actor.actorName == "system/reconcile"
    assert parsed.actor.actorType == ActorRefV1ActorType.SYSTEM
    assert parsed.reason == "discord role missing during reconcile"
    assert parsed.server == "mini-pvp"


def test_discord_admin_access_changed_discord_actor_round_trip() -> None:
    from xcore_protocol.generated.shared import ActorRefV1ActorType

    command = build_discord_admin_access_changed_command(
        player_uuid="uuid-7",
        player_pid=7,
        player_name="Target",
        discord_id="123456",
        discord_username="discord-user",
        admin=True,
        source_name="DISCORD_ROLE",
        source_type=ActorRefV1ActorType.DISCORD,
        actor_name="boss",
        actor_discord_id="42",
        actor_type=ActorRefV1ActorType.DISCORD,
        reason="/admin add",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = DiscordAdminAccessChangedCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.player.playerName == "Target"
    assert parsed.admin is True
    assert parsed.source.actorName == "DISCORD_ROLE"
    assert parsed.source.actorType == ActorRefV1ActorType.DISCORD
    assert parsed.actor.actorName == "boss"
    assert parsed.actor.actorType == ActorRefV1ActorType.DISCORD
    assert parsed.reason == "/admin add"
    assert parsed.server == "mini-pvp"


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


def test_player_password_reset_round_trip() -> None:
    command = build_player_password_reset_command(
        uuid_value="uuid-7",
        server="mini-pvp",
    )
    wire = command.to_payload()
    parsed = PlayerPasswordResetCommandV1.from_payload(wire)
    assert parsed == command
    assert parsed.playerUuid == "uuid-7"
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
