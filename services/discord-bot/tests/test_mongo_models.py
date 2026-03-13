from __future__ import annotations

from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from xcore_discord_bot.mongo_store import BanDoc, MuteDoc, PlayerDoc


def test_player_doc_allows_extra_fields_and_is_frozen() -> None:
    doc = PlayerDoc.model_validate(
        {
            "pid": 1,
            "uuid": "u-1",
            "nickname": "Nick",
            "discord_id": "123",
            "custom_field": "value",
        }
    )

    dumped = doc.model_dump(mode="python")
    assert dumped["custom_field"] == "value"

    with pytest.raises(ValidationError):
        doc.nickname = "Changed"


def test_ban_doc_and_mute_doc_frozen() -> None:
    ban = BanDoc.model_validate(
        {"uuid": "u-1", "name": "Nick", "admin_discord_id": "123"}
    )
    mute = MuteDoc.model_validate(
        {
            "uuid": "u-1",
            "name": "Nick",
            "admin_name": "mod",
            "admin_discord_id": "456",
            "reason": "spam",
            "expire_date": "2026-03-01T10:00:00+00:00",
        }
    )

    with pytest.raises(ValidationError):
        ban.name = "Other"
    with pytest.raises(ValidationError):
        mute.reason = "other"


def test_ban_doc_accepts_non_datetime_expire_date() -> None:
    doc = BanDoc.model_validate(
        {
            "uuid": "u-1",
            "name": "Nick",
            "expire_date": datetime(2026, 1, 1, tzinfo=timezone.utc),
        }
    )
    assert isinstance(doc.expire_date, datetime)

    fallback = BanDoc.model_validate(
        {
            "uuid": "u-2",
            "name": "Nick2",
            "expire_date": {"$date": {"$numberLong": "999999999999999999"}},
        }
    )
    assert isinstance(fallback.expire_date, dict)
