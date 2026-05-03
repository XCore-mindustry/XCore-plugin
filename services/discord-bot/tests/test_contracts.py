from __future__ import annotations

from xcore_discord_bot.contracts import (
    ChatGlobalV1,
    ChatMessageV1,
    DiscordLinkStatusChangedV1,
    parse_chat_message_payload,
    parse_ban_payload,
    parse_discord_link_status_payload,
    parse_global_chat_payload,
    parse_player_join_leave_payload,
    parse_server_action_payload,
    PlayerJoinLeaveV1,
    ServerHeartbeatV1,
    ServerActionV1,
    parse_server_heartbeat_payload,
)


def test_game_chat_message_from_payload() -> None:
    payload = {
        "messageType": "chat.message",
        "messageVersion": 1,
        "authorName": "pizduk",
        "message": "yyyy",
        "server": "mini-pvp",
    }
    event = parse_chat_message_payload(payload)
    assert isinstance(event, ChatMessageV1)
    assert event.authorName == "pizduk"
    assert event.message == "yyyy"
    assert event.server == "mini-pvp"


def test_player_join_leave_from_payload() -> None:
    payload = {
        "messageType": "player.join-leave",
        "messageVersion": 1,
        "playerName": "pizduk",
        "server": "mini-pvp",
        "joined": True,
    }
    event = parse_player_join_leave_payload(payload)
    assert isinstance(event, PlayerJoinLeaveV1)
    assert event.playerName == "pizduk"
    assert event.server == "mini-pvp"
    assert event.joined is True


def test_server_action_from_payload() -> None:
    payload = {
        "messageType": "server.action",
        "messageVersion": 1,
        "message": "Server started",
        "server": "mini-pvp",
    }
    event = parse_server_action_payload(payload)
    assert isinstance(event, ServerActionV1)
    assert event.message == "Server started"
    assert event.server == "mini-pvp"


def test_ban_payload_from_canonical_generated_shape() -> None:
    payload = {
        "messageType": "moderation.ban.created",
        "messageVersion": 1,
        "target": {"playerUuid": "u-2", "playerPid": 12, "playerName": "player"},
        "actor": {"actorName": "mod", "actorDiscordId": "555"},
        "reason": "abuse",
        "expiration": {"expiresAt": "2026-03-01T11:00:00+00:00", "permanent": False},
    }
    event = parse_ban_payload(payload)
    assert event.target.playerPid == 12
    assert event.actor.actorName == "mod"
    assert event.actor.actorDiscordId == "555"
    assert event.expiration is not None
    assert event.expiration.expiresAt == "2026-03-01T11:00:00+00:00"


def test_global_chat_event_from_payload() -> None:
    payload = {
        "messageType": "chat.global",
        "messageVersion": 1,
        "authorName": "pizduk",
        "message": "hello all",
        "server": "mini-pvp",
    }
    event = parse_global_chat_payload(payload)
    assert isinstance(event, ChatGlobalV1)
    assert event.authorName == "pizduk"
    assert event.message == "hello all"
    assert event.server == "mini-pvp"


def test_server_heartbeat_from_payload() -> None:
    payload = {
        "messageType": "server.heartbeat",
        "messageVersion": 1,
        "serverName": "mini-pvp",
        "discordChannelId": 1234,
        "players": 4,
        "maxPlayers": 10,
        "version": "1.2.3",
    }
    event = parse_server_heartbeat_payload(payload)
    assert isinstance(event, ServerHeartbeatV1)
    assert event.serverName == "mini-pvp"
    assert event.discordChannelId == 1234
    assert event.players == 4
    assert event.maxPlayers == 10
    assert event.version == "1.2.3"


def test_discord_link_status_changed_from_payload() -> None:
    payload = {
        "messageType": "discord.link.status-changed",
        "messageVersion": 1,
        "player": {"playerUuid": "uuid-7", "playerPid": 7, "playerName": "Target"},
        "discord": {"discordId": "123456", "discordUsername": "osp54"},
        "action": "linked",
        "server": "mini-pvp",
        "occurredAt": "2026-05-02T10:20:30.123+00:00",
    }
    event = parse_discord_link_status_payload(payload)
    assert isinstance(event, DiscordLinkStatusChangedV1)
    assert event.player.playerUuid == "uuid-7"
    assert event.player.playerPid == 7
    assert event.player.playerName == "Target"
    assert event.discord.discordId == "123456"
    assert event.discord.discordUsername == "osp54"
    assert str(event.action) == "linked"
    assert event.server == "mini-pvp"
    assert event.occurredAt == "2026-05-02T10:20:30.123+00:00"
