from __future__ import annotations

from xcore_discord_bot.store_mappers import (
    ban_record_from_doc,
    mute_record_from_doc,
    player_record_from_doc,
)


def test_player_record_from_doc_normalizes_dirty_values() -> None:
    record = player_record_from_doc(
        {
            "pid": "42",
            "nickname": "  Vortex  ",
            "uuid": "  uuid-42 ",
            "custom_nickname": "   ",
            "total_play_time": "15",
            "pvp_rating": None,
            "hexed_rank": "3",
            "hexed_points": "9",
            "is_admin": 1,
            "admin_confirmed": 0,
        }
    )

    assert record.pid == 42
    assert record.nickname == "Vortex"
    assert record.uuid == "uuid-42"
    assert record.custom_nickname is None
    assert record.total_play_time == 15
    assert record.pvp_rating == 0
    assert record.hexed_rank == 3
    assert record.hexed_points == 9
    assert record.is_admin is True
    assert record.admin_confirmed is False


def test_player_record_from_doc_uses_safe_defaults() -> None:
    record = player_record_from_doc({})

    assert record.pid == -1
    assert record.nickname == "Unknown"
    assert record.uuid is None
    assert record.total_play_time == 0
    assert record.pvp_rating == 0


def test_ban_record_from_doc_normalizes_values() -> None:
    record = ban_record_from_doc(
        {
            "uuid": " uuid-1 ",
            "ip": " 1.2.3.4 ",
            "name": " Target ",
            "admin_name": " Admin ",
            "reason": " griefing ",
            "expire_date": 123,
        }
    )

    assert record.uuid == "uuid-1"
    assert record.ip == "1.2.3.4"
    assert record.name == "Target"
    assert record.admin_name == "Admin"
    assert record.reason == "griefing"
    assert record.expire_date == 123


def test_ban_record_from_doc_uses_safe_defaults() -> None:
    record = ban_record_from_doc({})

    assert record.name == "Unknown"
    assert record.admin_name == "Unknown"
    assert record.reason == "Not Specified"
    assert record.expire_date is None


def test_mute_record_from_doc_normalizes_values() -> None:
    record = mute_record_from_doc(
        {
            "uuid": " uuid-2 ",
            "name": " Target ",
            "admin_name": " Admin ",
            "reason": " spam ",
            "expire_date": 456,
        }
    )

    assert record.uuid == "uuid-2"
    assert record.name == "Target"
    assert record.admin_name == "Admin"
    assert record.reason == "spam"
    assert record.expire_date == 456


def test_mute_record_from_doc_uses_safe_defaults() -> None:
    record = mute_record_from_doc({})

    assert record.name == "Unknown"
    assert record.admin_name == "Unknown"
    assert record.reason == "Not Specified"
    assert record.expire_date is None
