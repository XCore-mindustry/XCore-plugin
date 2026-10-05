from __future__ import annotations

from xcore_protocol.paths import fixtures_root, spec_root
from xcore_protocol.schema_validation import assert_invalid, assert_valid

VALID_MESSAGES = (
    "rating.season.started",
    "rating.season.ending-soon",
    "rating.season.ended",
    "rating.season.rescheduled",
    "rating.season.reschedule.request",
    "rating.season.reschedule.response",
    "rating.accounts.merge.request",
    "rating.accounts.merge.response",
    "rating.season.prizes.set.request",
    "rating.season.prizes.set.response",
    "rating.prize.grant.update.request",
    "rating.prize.grant.update.response",
)

VALID_CASES = [
    (
        spec_root() / "messages" / "rating" / f"{name}.v1.json",
        fixtures_root() / "valid" / "rating" / f"{name}.v1.json",
    )
    for name in VALID_MESSAGES
] + [
    (
        spec_root() / "messages" / "rating" / "rating.prize.grant.update.request.v1.json",
        fixtures_root() / "valid" / "rating" / "rating.prize.grant.update.request.v1.negative-pid.json",
    ),
]

INVALID_CASES = [
    (message, f"{message}.v1.{case}")
    for message, case in (
        ("rating.season.ended", "zero-place"),
        ("rating.season.started", "zero-season"),
        ("rating.season.reschedule.request", "unknown-operation"),
        ("rating.season.ending-soon", "missing-notice"),
        ("rating.accounts.merge.request", "empty-source"),
        ("rating.season.prizes.set.request", "unknown-operation"),
        ("rating.season.prizes.set.request", "unknown-kind"),
        ("rating.prize.grant.update.request", "unknown-status"),
        ("rating.prize.grant.update.request", "zero-place"),
        ("rating.season.reschedule.request", "extend-without-seconds"),
        ("rating.season.reschedule.request", "set-end-without-ends-at"),
        ("rating.season.prizes.set.request", "add-without-prize"),
        ("rating.season.prizes.set.request", "remove-without-place-from"),
    )
]


def test_valid_rating_fixtures_match_schemas() -> None:
    for schema_path, fixture_path in VALID_CASES:
        assert_valid(schema_path, fixture_path)


def test_invalid_rating_fixtures_are_rejected() -> None:
    for message, fixture_name in INVALID_CASES:
        assert_invalid(
            spec_root() / "messages" / "rating" / f"{message}.v1.json",
            fixtures_root() / "invalid" / "rating" / f"{fixture_name}.json",
        )
