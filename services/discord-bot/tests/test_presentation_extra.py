from __future__ import annotations

from xcore_discord_bot.presentation import (
    as_int,
    format_ban_expire_date_from_millis,
    format_epoch_millis,
    format_hexed_rank_block,
    format_minutes,
    format_size,
)


def test_format_size_covers_bytes_kb_and_mb() -> None:
    assert format_size(512) == "512 B"
    assert format_size(2048) == "2.0 KB"
    assert format_size(3 * 1024 * 1024) == "3.0 MB"


def test_format_minutes_covers_minutes_hours_and_days() -> None:
    assert format_minutes(59) == "59m"
    assert format_minutes(61) == "1h 1m"
    assert format_minutes(1501) == "1d 1h 1m"


def test_format_epoch_millis_returns_na_for_non_positive_values() -> None:
    assert format_epoch_millis(0) == "n/a"
    assert format_epoch_millis(-1) == "n/a"
    assert format_epoch_millis("bad") == "n/a"


def test_as_int_handles_bool_float_and_numeric_string() -> None:
    assert as_int(True, default=9) == 9
    assert as_int(5.9) == 5
    assert as_int(" 42 ") == 42
    assert as_int("nope", default=7) == 7


def test_format_hexed_rank_block_clamps_rank_bounds() -> None:
    assert format_hexed_rank_block(-5, 2) == ("Newbie", "2/3 wins")

    label, progress = format_hexed_rank_block(999, 33)
    assert label.endswith("The Legend")
    assert progress == "33 wins (max rank)"


def test_format_ban_expire_date_from_millis_handles_lower_bound() -> None:
    assert format_ban_expire_date_from_millis(-62135596800001) == "Before year 1"
