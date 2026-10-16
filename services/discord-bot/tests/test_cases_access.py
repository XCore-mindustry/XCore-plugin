from __future__ import annotations

from dataclasses import replace

import pytest
from cases_fakes import NOW, member, settings

from xcore_discord_bot.cases.access import (
    check_access,
    check_opening,
    conflicted,
    roles_for,
)
from xcore_discord_bot.cases.model import (
    AccessDenied,
    Case,
    CaseKind,
    CaseStatus,
    OpeningBlocked,
    Origin,
    Party,
)


def case(kind, **kwargs):
    return Case(
        1,
        kind,
        CaseStatus.OPEN,
        1,
        Origin("game", "submission"),
        Party(player_uuid="opener", discord_id="42"),
        Party(player_uuid="subject", discord_id="77"),
        NOW,
        NOW,
        **kwargs,
    )


@pytest.mark.parametrize(
    ("kind", "verbs", "allowed"),
    [
        (
            CaseKind.REPORT,
            ("take", "release", "history", "reject", "ban", "mute", "refresh"),
            (10,),
        ),
        (
            CaseKind.APPEAL,
            ("take", "release", "history", "reject", "unban", "shorten", "refresh"),
            (10,),
        ),
        (CaseKind.BADGE, ("take", "release", "grant", "reject"), (10, 20)),
        (
            CaseKind.COMPLAINT,
            ("take", "release", "history", "reject", "resolve", "subject"),
            (20,),
        ),
        (CaseKind.MAP, ("take", "release", "accept", "reject", "recheck"), (30,)),
    ],
)
def test_role_per_kind_and_verb(kind, verbs, allowed):
    cfg = settings()
    for verb in verbs:
        expected = (
            tuple(dict.fromkeys(allowed + (20,))) if verb == "release" else allowed
        )
        assert roles_for(cfg, kind, verb) == expected
        for role in (10, 20, 30, 40):
            if role in expected:
                check_access(cfg, member(101, (role,)), case(kind), verb)
            else:
                with pytest.raises(AccessDenied):
                    check_access(cfg, member(101, (role,)), case(kind), verb)


@pytest.mark.parametrize("kind", list(CaseKind))
@pytest.mark.parametrize("verb", ["reopen", "subject", "publish"])
def test_senior_verbs(kind, verb):
    assert roles_for(settings(), kind, verb) == (20,)


def test_blacklist_only_blocks_opening():
    cfg = settings(cases_blacklist_role_id=40)
    with pytest.raises(OpeningBlocked):
        check_opening(cfg, member(1, (40,)))
    check_opening(cfg, member(1, (10,)))
    check_access(cfg, member(101, (10, 40)), case(CaseKind.REPORT), "ban")


@pytest.mark.parametrize("discord_id", ["42", "77", "43"])
@pytest.mark.parametrize(
    "verb", ["take", "release", "history", "reject", "ban", "mute", "reopen"]
)
def test_report_opener_co_opener_and_subject_cannot_act(discord_id, verb):
    report = case(CaseKind.REPORT, co_openers=({"discord_id": "43"},))
    assert conflicted(report, discord_id)
    with pytest.raises(AccessDenied):
        check_access(settings(), member(int(discord_id), (10, 20)), report, verb)


@pytest.mark.parametrize(
    "verb", ["take", "unban", "shorten", "reject", "history", "release", "reopen"]
)
def test_appeal_issuer_blocked_even_if_senior(verb):
    appeal = case(
        CaseKind.APPEAL,
        payload={"ban_snapshot": {"admin_discord_id": "101", "admin_name": "Issuer"}},
    )
    with pytest.raises(AccessDenied):
        check_access(settings(), member(101, (10, 20)), appeal, verb)
    check_access(settings(), member(102, (10, 20)), appeal, verb)


def test_appeal_name_match_unknown_issuer_and_complaint():
    appeal = case(CaseKind.APPEAL, payload={"ban_snapshot": {"admin_name": " Issuer "}})
    assert conflicted(appeal, "101", ["issuer"])
    with pytest.raises(AccessDenied):
        check_access(
            settings(), member(101, (10, 20)), appeal, "reject", linked_names=["issuer"]
        )
    assert not conflicted(appeal, "101")
    unknown = replace(appeal, payload={"ban_snapshot": {}}, flags=("issuer_unknown",))
    check_access(settings(), member(101, (10,)), unknown, "take")
    complaint = case(CaseKind.COMPLAINT)
    for verb in ("take", "reject", "resolve", "history", "subject", "reopen"):
        with pytest.raises(AccessDenied):
            check_access(settings(), member(77, (20,)), complaint, verb)
    check_access(
        settings(), member(77, (20,)), replace(complaint, subject=None), "take"
    )


def test_release_requires_assignee_or_senior():
    report = case(CaseKind.REPORT, assignee={"discord_id": "101", "name": "Staff"})
    check_access(settings(), member(), report, "release")
    with pytest.raises(AccessDenied):
        check_access(settings(), member(102), report, "release")
    check_access(settings(), member(102, (10, 20)), report, "release")
