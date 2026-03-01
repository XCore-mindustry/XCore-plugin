from __future__ import annotations

from xcore_discord_bot.contracts import (
    BanEvent,
    GameChatMessage,
    GlobalChatEvent,
    PlayerJoinLeaveEvent,
    RawEvent,
    ServerActionEvent,
)


def test_game_chat_message_from_payload() -> None:
    payload = {"authorName": "pizduk", "message": "yyyy", "server": "mini-pvp"}
    event = GameChatMessage.from_payload(payload)
    assert event.author_name == "pizduk"
    assert event.message == "yyyy"
    assert event.server == "mini-pvp"


def test_player_join_leave_from_payload() -> None:
    payload = {"playerName": "pizduk", "server": "mini-pvp", "join": True}
    event = PlayerJoinLeaveEvent.from_payload(payload)
    assert event.player_name == "pizduk"
    assert event.server == "mini-pvp"
    assert event.joined is True


def test_server_action_from_payload() -> None:
    payload = {"message": "Server started", "server": "mini-pvp"}
    event = ServerActionEvent.from_payload(payload)
    assert event.message == "Server started"
    assert event.server == "mini-pvp"


def test_ban_event_from_payload_snake_case() -> None:
    payload = {
        "uuid": "u-1",
        "ip": "1.2.3.4",
        "name": "pizduk",
        "admin_name": "admin",
        "reason": "rule",
        "expire_date": "2026-03-01T10:00:00+00:00",
    }
    event = BanEvent.from_payload(payload)
    assert event.uuid == "u-1"
    assert event.ip == "1.2.3.4"
    assert event.name == "pizduk"
    assert event.admin_name == "admin"
    assert event.reason == "rule"
    assert event.expire_date == "2026-03-01T10:00:00+00:00"


def test_ban_event_from_payload_camel_case_admin_and_expire() -> None:
    payload = {
        "uuid": "u-2",
        "name": "player",
        "adminName": "mod",
        "reason": "abuse",
        "expireDate": "2026-03-01T11:00:00+00:00",
    }
    event = BanEvent.from_payload(payload)
    assert event.admin_name == "mod"
    assert event.expire_date == "2026-03-01T11:00:00+00:00"


def test_global_chat_event_from_payload() -> None:
    payload = {"authorName": "pizduk", "message": "hello all", "server": "mini-pvp"}
    event = GlobalChatEvent.from_payload(payload)
    assert event.author_name == "pizduk"
    assert event.message == "hello all"
    assert event.server == "mini-pvp"


def test_raw_event_from_fields() -> None:
    fields = {
        "event_type": "event.someunknown",
        "payload_json": "{\"a\":1,\"b\":\"x\"}",
    }
    event = RawEvent.from_fields(fields)
    assert event.event_type == "event.someunknown"
    assert event.payload == {"a": 1, "b": "x"}
