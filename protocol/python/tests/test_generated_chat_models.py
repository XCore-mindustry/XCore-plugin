from __future__ import annotations

from xcore_protocol.generated import (
    CHAT_DISCORD_INGRESS_COMMAND_V1,
    CHAT_GLOBAL_V1,
    CHAT_MESSAGE_V1,
    CHAT_PRIVATE_V1,
    PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1,
    PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1,
    PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1,
    PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1,
    PLAYER_JOIN_LEAVE_V1,
    PLAYER_PASSWORD_RESET_COMMAND_V1,
    SERVER_ACTION_V1,
    SERVER_HEARTBEAT_V1,
    ChatDiscordIngressCommandV1,
    ChatGlobalV1,
    ChatMessageV1,
    ChatPrivateV1,
    MAPS_ROUTES_BY_MESSAGE,
    PlayerActiveBadgeChangedCommandV1,
    PlayerBadgeInventoryChangedCommandV1,
    PlayerBadgeSymbolColorModeChangedCommandV1,
    PlayerCustomNicknameChangedCommandV1,
    PlayerJoinLeaveV1,
    PlayerPasswordResetCommandV1,
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


def test_generated_chat_private_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "chat.private.v1.json")

    model = ChatPrivateV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "chat" / "chat.private.v1.json",
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


def test_generated_player_custom_nickname_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "player.custom-nickname.changed.command.v1.json")

    model = PlayerCustomNicknameChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "chat" / "player.custom-nickname.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_player_active_badge_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "player.active-badge.changed.command.v1.json")

    model = PlayerActiveBadgeChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "chat" / "player.active-badge.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_player_badge_inventory_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "player.badge-inventory.changed.command.v1.json")

    model = PlayerBadgeInventoryChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "chat" / "player.badge-inventory.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_player_badge_symbol_color_mode_changed_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "player.badge-symbol-color-mode.changed.command.v1.json")

    model = PlayerBadgeSymbolColorModeChangedCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "chat" / "player.badge-symbol-color-mode.changed.command.v1.json",
        model.to_payload(),
    )


def test_generated_player_password_reset_command_roundtrip_matches_fixture() -> None:
    payload = load_json(fixtures_root() / "valid" / "chat" / "player.password-reset.command.v1.json")

    model = PlayerPasswordResetCommandV1.from_payload(payload)

    assert model.to_payload() == payload
    validate_instance(
        spec_root() / "messages" / "chat" / "player.password-reset.command.v1.json",
        model.to_payload(),
    )


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

    invalid_private_payload = load_json(fixtures_root() / "invalid" / "chat" / "chat.private.v1.snake-from.json")

    try:
        ChatPrivateV1.from_payload(invalid_private_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated private-message parsing to reject non-canonical keys")

    invalid_player_payload = load_json(
        fixtures_root() / "invalid" / "chat" / "player.custom-nickname.changed.command.v1.legacy-uuid.json"
    )

    try:
        PlayerCustomNicknameChangedCommandV1.from_payload(invalid_player_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated player-session command parsing to reject legacy uuid field")

    invalid_password_reset_payload = load_json(
        fixtures_root() / "invalid" / "chat" / "player.password-reset.command.v1.legacy-uuid.json"
    )

    try:
        PlayerPasswordResetCommandV1.from_payload(invalid_password_reset_payload)
    except ValueError as error:
        assert "missing required fields" in str(error) or "unexpected fields" in str(error)
    else:
        raise AssertionError("Expected strict generated password-reset parsing to reject legacy uuid field")


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
    assert CHAT_PRIVATE_V1.payloadType is ChatPrivateV1
    assert SERVER_ACTION_V1.payloadType is ServerActionV1
    assert PLAYER_JOIN_LEAVE_V1.payloadType is PlayerJoinLeaveV1
    assert PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1.payloadType is PlayerCustomNicknameChangedCommandV1
    assert PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1.payloadType is PlayerActiveBadgeChangedCommandV1
    assert PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1.payloadType is PlayerBadgeInventoryChangedCommandV1
    assert PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1.payloadType is PlayerBadgeSymbolColorModeChangedCommandV1
    assert PLAYER_PASSWORD_RESET_COMMAND_V1.payloadType is PlayerPasswordResetCommandV1
    assert SERVER_HEARTBEAT_V1.payloadType is ServerHeartbeatV1
    assert ROUTES_BY_MESSAGE[("chat.message", 1)].stream == "xcore:evt:chat:message"
    assert ROUTES_BY_MESSAGE[("chat.private", 1)].stream == "xcore:evt:chat:private"
    assert ROUTES_BY_MESSAGE[("player.custom-nickname.changed.command", 1)].stream == "xcore:cmd:player-custom-nickname:{server}"
    assert ROUTES_BY_MESSAGE[("player.active-badge.changed.command", 1)].stream == "xcore:cmd:player-active-badge:{server}"
    assert ROUTES_BY_MESSAGE[("player.badge-inventory.changed.command", 1)].stream == "xcore:cmd:player-badge-inventory:{server}"
    assert ROUTES_BY_MESSAGE[("player.badge-symbol-color-mode.changed.command", 1)].stream == "xcore:cmd:player-badge-symbol-color-mode:{server}"
    assert ROUTES_BY_MESSAGE[("player.password-reset.command", 1)].stream == "xcore:cmd:player-password-reset:{server}"
    assert ROUTES_BY_MESSAGE[("server.heartbeat", 1)].stream == "xcore:evt:server:heartbeat"
    assert MAPS_ROUTES_BY_MESSAGE[("maps.list.request", 1)].stream == "xcore:rpc:req:{server}"
