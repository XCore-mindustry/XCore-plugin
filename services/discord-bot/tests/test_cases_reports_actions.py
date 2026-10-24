"""B5: staff act on a report card (9.1 step 12–14): Ban, Mute, Reject, History, Refresh."""

from __future__ import annotations

from datetime import timedelta

import pytest
from cases_fakes import NOW, member
from cases_reports_fakes import (
    REPORTER_UUID,
    SECOND_UUID,
    STAFF,
    TARGET_IP,
    TARGET_UUID,
    act,
    from_second,
    move,
    open_report,
    panel,
    reports_core,
    sanction,
)

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.cases.model import AccessDenied, ActionFailed, CaseStatus
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.i18n import t
from xcore_discord_bot.moderation_logs import post_mute_log


def notices(db, kind):
    return {
        row["player_uuid"]: row
        for row in db["case_notices"].docs
        if row["kind"] == kind
    }


def log_fields(bot, index=-1):
    return {field.name: field.value for field in bot.log.embeds[index].fields}


async def two_reporters(service):
    case = await open_report(service)
    await reports.submitted(service, from_second())
    return await service.get(case.number)


async def test_the_sanction_form_is_prefilled_with_the_player_and_case():
    service, _, _ = await reports_core()
    case = await open_report(service)
    form = {f.name: f for f in service.registry.fields_for(case, "ban", "en")}
    assert form["pid"].default == "-5"
    assert form["reason"].default == "{} · {}".format(
        t("en", "reports-reason-griefing"),
        t("en", "reports-case-reference", number=case.number),
    )


async def test_ban_from_the_card_bans_with_the_case_and_tells_every_reporter():
    service, db, bot = await reports_core()
    case = await two_reporters(service)
    closed = await sanction(service, case, "ban")
    assert closed.status == CaseStatus.RESOLVED
    resolution = closed.resolution
    [ban] = bot.mutations["bans"].docs
    assert ban["uuid"] == TARGET_UUID and ban["case_number"] == case.number
    assert resolution.code == "banned" and resolution.ban_id == ban["ban_id"]
    assert resolution.duration_ms == 86_400_000
    assert resolution.by_discord_id == str(STAFF)
    [audit] = [row for row in db["moderation_audit"].docs if row["action"] == "BAN"]
    assert audit["audit_id"] == resolution.audit_id
    extra = audit["details"]["extra"]
    assert extra["case"] == str(case.number) and extra["case_action_token"]
    assert bot.bus.kicks == [(TARGET_UUID, TARGET_IP)]
    fields = log_fields(bot)
    assert fields["Case"] == f"#{case.number}" and fields["Ban"] == f"B-{ban['ban_id']}"
    # Every reporter: a notice in game, and a DM for the one with a linked account.
    resolved = notices(db, "report_resolved")
    assert set(resolved) == {REPORTER_UUID, SECOND_UUID}
    assert resolved[SECOND_UUID]["params"] == {"number": str(case.number)}
    text = t("uk", "reports-outcome-action", number=case.number)
    assert bot.users[201].messages == [text]
    # Nobody is named and the sanction is not described.
    assert "Staff" not in text and "1d" not in text
    assert closed.assignee["discord_id"] == str(STAFF)


async def test_mute_from_the_card_mutes_with_the_case_in_its_own_log_field():
    service, db, bot = await reports_core()
    case = await open_report(service)
    closed = await sanction(service, case, "mute", duration="2h")
    assert closed.resolution.code == "muted"
    assert closed.resolution.duration_ms == 7_200_000
    [mute] = bot.mutations["mutes"].docs
    assert mute["case_number"] == case.number
    [audit] = [row for row in db["moderation_audit"].docs if row["action"] == "MUTE"]
    assert audit["details"]["extra"]["case"] == str(case.number)
    assert log_fields(bot)["Case"] == f"#{case.number}"
    assert set(notices(db, "report_resolved")) == {REPORTER_UUID}


async def test_a_mute_log_without_a_case_is_unchanged():
    _, _, bot = await reports_core()
    await post_mute_log(
        bot,
        pid=5,
        name="Player",
        admin_name="Admin",
        admin_discord_id=None,
        reason="spam",
        expire=NOW,
    )
    assert list(log_fields(bot)) == ["Violator", "Admin", "Reason", "Expires"]


@pytest.mark.parametrize("code", reports.REASON_CODES)
async def test_a_rejection_tells_every_reporter_why_by_its_code(code):
    service, db, bot = await reports_core()
    case = await two_reporters(service)
    closed, _ = await act(
        service,
        case,
        "reject",
        values={"reason_code": code, "reason_text": "Staff 101 note"},
    )
    assert closed.status == CaseStatus.REJECTED
    rejected = notices(db, "report_rejected")
    assert set(rejected) == {REPORTER_UUID, SECOND_UUID}
    # Exactly the code: the game renders its own line from it.
    assert rejected[REPORTER_UUID]["params"] == {
        "number": str(case.number),
        "reason": code,
    }
    key = "reports-outcome-" + code.replace("_", "-")
    assert bot.users[201].messages == [t("uk", key, number=case.number)]
    assert "Staff 101" not in bot.users[201].messages[0]
    assert not bot.mutations["bans"].docs and not bot.mutations["mutes"].docs


@pytest.mark.parametrize(
    "staff", [201, 203, 202], ids=["opener", "co_opener", "subject"]
)
async def test_a_staff_member_cannot_act_on_a_report_by_or_about_them(staff):
    service, _, bot = await reports_core()
    move(service, SECOND_UUID, discord_id="203")
    case = await two_reporters(service)
    assert case.co_openers[0]["discord_id"] == "203"
    values = {"pid": "-5", "duration": "1d", "reason": "x", "reason_code": "abuse"}
    for verb in ("take", "ban", "mute", "history", "reject"):
        with pytest.raises(AccessDenied):
            await act(service, case, verb, user=member(staff, (10,)), values=values)
    assert not bot.mutations["bans"].docs and not bot.mutations["mutes"].docs
    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_another_player_id_in_the_form_is_refused_for_a_known_subject():
    service, _, bot = await reports_core()
    case = await open_report(service)
    with pytest.raises(ActionFailed) as refused:
        await sanction(service, case, "ban", pid="7")
    assert refused.value.text == t("en", "reports-error-other-player")
    assert not bot.mutations["bans"].docs


async def test_an_unresolved_subject_is_set_by_the_player_id_of_the_ban_form():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "Nobody Known", "what": "grief"})
    assert reports.UNRESOLVED in case.flags and case.subject is None
    assert [
        a.verb for a in reports.REPORT_FLOW.actions if a.visible and not a.visible(case)
    ] == [
        "history",
        "refresh",
    ]
    closed = await sanction(service, case, "ban", pid="#-5")
    assert closed.resolution.code == "banned"
    assert closed.subject.player_uuid == TARGET_UUID
    assert reports.UNRESOLVED not in closed.flags
    assert bot.mutations["bans"].docs[0]["case_number"] == case.number


async def test_an_unresolved_subject_that_is_the_staff_member_is_refused():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "Nobody Known", "what": "grief"})
    with pytest.raises(AccessDenied):
        await sanction(service, case, "ban", pid="-5", user=member(202, (10,)))
    assert not bot.mutations["bans"].docs
    current = await service.get(case.number)
    assert current.actionable and current.pending_action is None


async def test_a_crash_after_the_ban_is_finished_from_the_audit_without_a_second_ban(
    monkeypatch,
):
    service, db, bot = await reports_core()
    case = await open_report(service)
    finish = service._finish_action
    calls = []

    async def crash(*args, **kwargs):
        calls.append(args)
        if len(calls) == 1:
            raise RuntimeError("process died")
        return await finish(*args, **kwargs)

    monkeypatch.setattr(service, "_finish_action", crash)
    with pytest.raises(RuntimeError):
        await sanction(service, case, "ban")
    stuck = await service.get(case.number)
    assert stuck.actionable and stuck.pending_action
    await reconcile_cases(service)
    closed = await service.get(case.number)
    assert closed.status == CaseStatus.RESOLVED and closed.resolution.code == "banned"
    assert len(bot.mutations["bans"].docs) == 1
    assert len([r for r in db["moderation_audit"].docs if r["action"] == "BAN"]) == 1
    assert closed.resolution.ban_id == bot.mutations["bans"].docs[0]["ban_id"]
    assert set(notices(db, "report_resolved")) == {REPORTER_UUID}


async def test_history_is_private_bounded_and_shows_no_identifiers():
    service, db, _ = await reports_core()
    for n in range(12):
        at = NOW - timedelta(days=n)
        await db["moderation_audit"].insert_one(
            {
                "_id": n,
                "audit_id": f"a{n}",
                "action": "WARN",
                "target": {"uuid": TARGET_UUID},
                "actor": {"name_snapshot": "Admin"},
                "reason": f"warning {n} from {TARGET_IP}",
                "occurred_at": at,
                "created_at_epoch_ms": int(at.timestamp() * 1000),
                "details": {"extra": {}},
            }
        )
    case = await open_report(service)
    text, _ = await act(service, case, "history")
    lines = text.splitlines()
    assert lines[0] == t("en", "reports-history-title", player="Target")
    assert len(lines) == 1 + reports.HISTORY_LIMIT and "warning 0" in lines[1]
    assert TARGET_IP not in text and TARGET_UUID not in text
    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_refresh_reads_live_evidence_and_keeps_what_the_game_sent():
    service, _, bot = await reports_core()
    case = await open_report(service)
    service.transport.rpc_case_evidence.return_value = {
        "server": "survival",
        "targetOnline": True,
        "mapName": "Fortress",
        "chat": [
            {
                "sentAt": 0,
                "playerName": "Target",
                "role": "target",
                "text": "fresh line",
            }
        ],
        "capturedAt": (NOW + timedelta(minutes=5)).isoformat(),
    }
    text, _ = await act(service, case, "refresh")
    assert text == t("en", "reports-refreshed")
    service.transport.rpc_case_evidence.assert_awaited_once_with(
        server="survival", player_uuid=TARGET_UUID
    )
    current = await service.get(case.number)
    context = current.evidence["context"]
    assert context["server"] == "survival" and context["map_name"] == "Fortress"
    assert context["same_network"] is True and context["map_file"] == "arena.msav"
    assert "block_summary" not in current.evidence
    assert current.evidence["captured_at"] == case.evidence["captured_at"]
    thread = bot.channels[int(case.discord["thread_id"])]
    assert any("fresh line" in message.content for message in thread.messages)
    # Offline: only the sanctions.
    move(service, TARGET_UUID, online_server=None)
    text, _ = await act(service, case, "refresh")
    assert text == t("en", "reports-refreshed-offline")
    assert service.transport.rpc_case_evidence.await_count == 1
