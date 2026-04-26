from __future__ import annotations

from xcore_protocol.generated import (
    CHAT_DISCORD_INGRESS_COMMAND_V1,
    CHAT_GLOBAL_V1,
    CHAT_MESSAGE_V1,
    PLAYER_JOIN_LEAVE_V1,
    SERVER_ACTION_V1,
    SERVER_HEARTBEAT_V1,
    ChatDiscordIngressCommandV1,
    ChatGlobalV1,
    ChatMessageV1,
    MAPS_ROUTES_BY_MESSAGE,
    PlayerJoinLeaveV1,
    ROUTES_BY_MESSAGE,
    ServerActionV1,
    ServerHeartbeatV1,
)
from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import load_json, validate_instance


def test_generated_chat_message_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "chat.message.v1.json")

    model = ChatMessageV1.from_payload(payload)

    assert model == ChatMessageV1(authorName="PlayerOne", message="Hello there", server="mini-pvp")
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "chat" / "chat.message.v1.json", model.to_payload())


def test_generated_chat_global_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "chat.global.v1.json")

    model = ChatGlobalV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "chat" / "chat.global.v1.json", model.to_payload())


def test_generated_chat_discord_ingress_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "chat.discord-ingress.command.v1.json")

    model = ChatDiscordIngressCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "chat" / "chat.discord-ingress.command.v1.json",
        model.to_payload(),
    )


def test_generated_server_action_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "server.action.v1.json")

    model = ServerActionV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "chat" / "server.action.v1.json", model.to_payload())


def test_generated_player_join_leave_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "player.join-leave.v1.json")

    model = PlayerJoinLeaveV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "chat" / "player.join-leave.v1.json", model.to_payload())


def test_generated_server_heartbeat_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "server.heartbeat.v1.json")

    model = ServerHeartbeatV1.from_payload(payload)

    assert model == ServerHeartbeatV1(
        serverName="mini-pvp",
        discordChannelId=321,
        players=4,
        maxPlayers=12,
        version="146.1",
        host="play.example.com",
        port=6567,
    )
    assert model.to_payload() == payload
    validate_instance(spec_root() / "messages" / "chat" / "server.heartbeat.v1.json", model.to_payload())


def test_generated_chat_models_remain_strict() -> None:
    invalid_payload = load_json(fixtures_root() / "invalid" / "chat" / "chat.message.v1.snake-author.json")

    try:
        ChatMessageV1.from_payload(invalid_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated model parsing to reject non-canonical keys")


def test_generated_server_heartbeat_rejects_alias_fields() -> None:
    invalid_payload = load_json(
        fixtures_root() / "invalid" / "chat" / "server.heartbeat.v1.server-host-alias.json"
    )

    try:
        ServerHeartbeatV1.from_payload(invalid_payload)
    except ValueError as error:
        assert "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated heartbeat parsing to reject alias fields")


def test_generated_route_registry_includes_chat_and_heartbeat_messages() -> None:
    assert CHAT_MESSAGE_V1.payloadType is ChatMessageV1
    assert CHAT_GLOBAL_V1.payloadType is ChatGlobalV1
    assert CHAT_DISCORD_INGRESS_COMMAND_V1.payloadType is ChatDiscordIngressCommandV1
    assert SERVER_ACTION_V1.payloadType is ServerActionV1
    assert PLAYER_JOIN_LEAVE_V1.payloadType is PlayerJoinLeaveV1
    assert SERVER_HEARTBEAT_V1.payloadType is ServerHeartbeatV1
    assert ROUTES_BY_MESSAGE[("chat.message", 1)].stream == "xcore:evt:chat:message"
    assert ROUTES_BY_MESSAGE[("server.heartbeat", 1)].stream == "xcore:evt:server:heartbeat"
    assert MAPS_ROUTES_BY_MESSAGE[("maps.list.request", 1)].stream == "xcore:rpc:req:{server}"
