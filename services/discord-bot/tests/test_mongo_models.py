from __future__ import annotations

import pytest
from pydantic import ValidationError

from xcore_discord_bot.mongo_store import BanDoc, MuteDoc, PlayerDoc


def test_player_doc_allows_extra_fields_and_is_frozen() -> None:
    doc = PlayerDoc.model_validate(
        {
            "pid": 1,
            "uuid": "u-1",
            "nickname": "Nick",
            "custom_field": "value",
        }
    )

    dumped = doc.model_dump(mode="python")
    assert dumped["custom_field"] == "value"

    with pytest.raises(ValidationError):
        doc.nickname = "Changed"


def test_ban_doc_and_mute_doc_frozen() -> None:
    ban = BanDoc.model_validate({"uuid": "u-1", "name": "Nick"})
    mute = MuteDoc.model_validate(
        {
            "uuid": "u-1",
            "name": "Nick",
            "admin_name": "mod",
            "reason": "spam",
            "expire_date": "2026-03-01T10:00:00+00:00",
        }
    )

    with pytest.raises(ValidationError):
        ban.name = "Other"
    with pytest.raises(ValidationError):
        mute.reason = "other"
