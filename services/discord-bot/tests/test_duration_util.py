from __future__ import annotations

from datetime import timedelta

import pytest

from xcore_discord_bot.utils.duration import parse_duration


def test_parse_duration_units() -> None:
    assert parse_duration("10s") == timedelta(seconds=10)
    assert parse_duration("5m") == timedelta(minutes=5)
    assert parse_duration("2h") == timedelta(hours=2)
    assert parse_duration("1d") == timedelta(days=1)
    assert parse_duration("1w") == timedelta(weeks=1)
    assert parse_duration("1y") == timedelta(days=365)


def test_parse_duration_combined() -> None:
    assert parse_duration("1d12h") == timedelta(days=1, hours=12)
    assert parse_duration("2h30m10s") == timedelta(hours=2, minutes=30, seconds=10)


def test_parse_duration_plain_number() -> None:
    assert parse_duration("7") == timedelta(days=7)
    assert parse_duration("10", default_unit="m") == timedelta(minutes=10)


def test_parse_duration_invalid() -> None:
    with pytest.raises(ValueError, match="Invalid period format"):
        parse_duration("")

    with pytest.raises(ValueError, match="Invalid period format"):
        parse_duration("abc")

    with pytest.raises(ValueError, match="Invalid period format"):
        parse_duration("-5m")
