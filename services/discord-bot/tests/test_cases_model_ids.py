from __future__ import annotations

from dataclasses import replace
from itertools import product

import pytest
from cases_fakes import NOW, Database

from xcore_discord_bot.cases.ids import (
    MAX_NUMBER,
    case_custom_id,
    decode_custom_id,
    format_case_number,
    panel_custom_id,
    parse_case_number,
)
from xcore_discord_bot.cases.model import (
    ACTIVE_STATUSES,
    TRANSITIONS,
    Case,
    CaseKind,
    CaseStatus,
    InvalidTransition,
    Origin,
    Party,
    check_transition,
    event,
)
from xcore_discord_bot.staff_actions import Actor

EXPECTED = {
    ("creating", "open"),
    ("creating", "cancelled"),
    ("open", "taken"),
    ("open", "resolved"),
    ("open", "rejected"),
    ("open", "cancelled"),
    ("taken", "open"),
    ("taken", "resolved"),
    ("taken", "rejected"),
    ("resolved", "open"),
    ("rejected", "open"),
}


@pytest.mark.parametrize(("before", "after"), list(product(CaseStatus, repeat=2)))
def test_full_transition_table(before, after):
    if (before.value, after.value) in EXPECTED:
        check_transition(before, after)
        assert after in TRANSITIONS[before]
    else:
        with pytest.raises(InvalidTransition):
            check_transition(before, after)


@pytest.mark.parametrize(
    "verb",
    [
        "take",
        "release",
        "history",
        "reject",
        "refresh",
        "ban",
        "mute",
        "unban",
        "shorten",
        "grant",
        "accept",
        "resolve",
        "recheck",
        "subject",
        "future_verb",
    ],
)
@pytest.mark.parametrize("number", [1, 57, MAX_NUMBER])
def test_case_custom_id_round_trip(verb, number):
    custom_id = case_custom_id(verb, number)
    parsed = decode_custom_id(custom_id)
    assert (parsed.target, parsed.verb, parsed.number) == ("case", verb, number)
    assert len(custom_id) <= 100


@pytest.mark.parametrize(
    "kind", [None, CaseKind.REPORT, CaseKind.APPEAL, CaseKind.BADGE, CaseKind.COMPLAINT]
)
def test_panel_custom_id_round_trip(kind):
    parsed = decode_custom_id(panel_custom_id(kind))
    assert parsed.target == "panel" and parsed.kind == kind


@pytest.mark.parametrize(
    "custom_id",
    [
        "",
        "xc:2:case:take:1",
        "xc:1:case:take:0",
        "xc:1:case:take:-1",
        "xc:1:case:take:01",
        "xc:1:case:take:1:extra",
        "xc:1:case:Take:1",
        "xc:1:case:take:1\n",
        "xc:1:case:take:9223372036854775808",
        "xc:1:panel:open:map",
        "xc:1:panel:open:other",
        "xc:1:panel:lang:en",
        "x" * 101,
    ],
)
def test_reject_malformed_custom_id(custom_id):
    with pytest.raises(ValueError):
        decode_custom_id(custom_id)


@pytest.mark.parametrize(
    "value",
    [
        "0",
        "-1",
        "B-0",
        "#-1",
        "b 12",
        "pid 1234",
        "1234.0",
        "abc",
        "9223372036854775808",
    ],
)
def test_bad_public_ids(value):
    with pytest.raises(ValueError):
        parse_case_number(value)


def test_case_numbers_and_case_round_trip():
    assert parse_case_number(" #57 ") == 57
    assert format_case_number(57) == "#57"
    with pytest.raises(ValueError):
        parse_case_number("B-57")
    with pytest.raises(ValueError):
        case_custom_id("take", True)
    with pytest.raises(ValueError):
        panel_custom_id(CaseKind.MAP)
    case = Case(
        57,
        CaseKind.REPORT,
        CaseStatus.CREATING,
        1,
        Origin("game", "submission"),
        Party(player_uuid="u"),
        Party(player_pid=42),
        NOW,
        NOW,
        events=(event("created", Actor("1", "name"), NOW),),
    )
    assert Case.from_doc(case.to_doc()) == case
    for status in CaseStatus:
        doc = replace(case, status=status).to_doc()
        assert ("active" in doc) == (status in ACTIVE_STATUSES)
    assert "pending_action" not in case.to_doc()


async def test_fake_mongo_push_unset_partial_unique_and_rollback():
    from pymongo import ReturnDocument
    from pymongo.errors import DuplicateKeyError

    collection = Database()["cases"]
    await collection.create_index(
        [("kind", 1), ("dedup_key", 1)],
        unique=True,
        partialFilterExpression={"active": True, "dedup_key": {"$type": "string"}},
    )
    await collection.insert_one(
        {
            "_id": 1,
            "kind": "report",
            "dedup_key": "report:u",
            "active": True,
            "revision": 1,
        }
    )
    with pytest.raises(DuplicateKeyError):
        await collection.insert_one(
            {"_id": 2, "kind": "report", "dedup_key": "report:u", "active": True}
        )
    await collection.insert_one({"_id": 3, "kind": "report", "dedup_key": "report:u"})
    with pytest.raises(DuplicateKeyError):
        await collection.update_one({"_id": 3}, {"$set": {"active": True}})
    assert "active" not in (await collection.find_one({"_id": 3}))
    result = await collection.find_one_and_update(
        {"_id": 1, "revision": 1},
        {
            "$inc": {"revision": 1},
            "$push": {"events": {"type": "action"}},
            "$unset": {"active": ""},
        },
        return_document=ReturnDocument.AFTER,
    )
    assert result["revision"] == 2 and result["events"] == [{"type": "action"}]
    await collection.update_one({"_id": 3}, {"$set": {"active": True}})
