from __future__ import annotations

from xcore_protocol.generated import (
    CHAT_DISCORD_INGRESS_COMMAND_V1,
    CHAT_GLOBAL_V1,
    CHAT_MESSAGE_V1,
    CHAT_PRIVATE_V1,
    ChatDiscordIngressCommandV1,
    ChatGlobalV1,
    ChatMessageV1,
    ChatPrivateV1,
    ROUTES_BY_MESSAGE,
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


def test_generated_route_registry_includes_chat_messages() -> None:
    assert CHAT_MESSAGE_V1.payloadType is ChatMessageV1
    assert CHAT_GLOBAL_V1.payloadType is ChatGlobalV1
    assert CHAT_DISCORD_INGRESS_COMMAND_V1.payloadType is ChatDiscordIngressCommandV1
    assert CHAT_PRIVATE_V1.payloadType is ChatPrivateV1
    assert ROUTES_BY_MESSAGE[("chat.message", 1)].stream == "xcore:evt:chat:message"
    assert ROUTES_BY_MESSAGE[("chat.global", 1)].stream == "xcore:evt:chat:global"
    assert ROUTES_BY_MESSAGE[("chat.private", 1)].stream == "xcore:evt:chat:private"
    assert ROUTES_BY_MESSAGE[("chat.discord-ingress.command", 1)].bindings == {"server": "payload.server"}
