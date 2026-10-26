"""B5: a report whose player is banned or muted outside its card is closed as handled (9.1 step 12)."""

from __future__ import annotations

from datetime import timedelta

import pytest
from cases_fakes import NOW
from cases_reports_fakes import (
    REPORTER_UUID,
    SECOND_UUID,
    TARGET_UUID,
    from_second,
    moderation,
    open_report,
    reports_core,
    sanction,
)

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.cases.model import CaseRace, CaseStatus
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.i18n import t


def notices(db, kind):
    return {
        row["player_uuid"] for row in db["case_notices"].docs if row["kind"] == kind
    }


def note(bot, case):
    return bot.channels[int(case.discord["thread_id"])].messages[-1].content


async def audit(db, action, *, at, uuid=TARGET_UUID, case=None, actor="Slash Admin"):
    extra = {"ban_id": "44"} if action == "BAN" else {}
    if case is not None:
        extra["case"] = str(case)
    await db["moderation_audit"].insert_one(
        {
            "_id": f"{action}-{at.isoformat()}",
            "audit_id": f"audit-{action}-{len(db['moderation_audit'].docs)}",
            "action": action,
            "target": {"uuid": uuid},
            "actor": {"name_snapshot": actor, "discord_id": "909"},
            "reason": "seen on stream",
            "occurred_at": at,
            "created_at_epoch_ms": int(at.timestamp() * 1000),
            "details": {"extra": extra},
        }
    )


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_an_in_game_sanction_closes_the_report_and_tells_the_reporters(verb):
    service, db, bot = await reports_core()
    case = await open_report(service)
    await reports.submitted(service, from_second())
    event = reports.BAN_EVENT if verb == "ban" else reports.MUTE_EVENT
    await service.registry.dispatch_event(event, service, moderation(verb))
    closed = await service.get(case.number)
    assert closed.status == CaseStatus.RESOLVED
    resolution = closed.resolution
    assert resolution.code == ("banned" if verb == "ban" else "muted")
    assert resolution.reason_code == reports.EXTERNAL
    assert resolution.by_name == "Ingame Admin"
    assert resolution.ban_id == (31 if verb == "ban" else None)
    assert note(bot, closed) == t(
        "en", f"reports-external-{resolution.code}", name="Ingame Admin"
    )
    assert notices(db, "report_resolved") == {REPORTER_UUID, SECOND_UUID}
    # The reporter learns that action was taken, not who took it.
    [dm] = bot.users[201].messages
    assert dm == t("uk", "reports-outcome-action", number=case.number)
    assert "Ingame Admin" not in dm
    # Redelivered, nothing changes.
    await service.registry.dispatch_event(event, service, moderation(verb))
    assert len(bot.users[201].messages) == 1


async def test_a_sanction_from_before_the_report_was_filed_does_not_close_it():
    service, db, _ = await reports_core()
    case = await open_report(service)
    old = moderation(at=NOW - timedelta(minutes=5))
    await service.registry.dispatch_event(reports.BAN_EVENT, service, old)
    await audit(db, "BAN", at=NOW - timedelta(minutes=5))
    await reconcile_cases(service)
    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_a_sanction_of_another_player_does_not_close_it():
    service, _, _ = await reports_core()
    case = await open_report(service)
    other = moderation(uuid=SECOND_UUID)
    await service.registry.dispatch_event(reports.BAN_EVENT, service, other)
    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_a_slash_command_sanction_closes_the_report_on_the_repair_pass():
    service, db, bot = await reports_core()
    case = await open_report(service)
    service.now = lambda: NOW + timedelta(minutes=10)
    await audit(db, "MUTE", at=NOW + timedelta(minutes=2))
    await reconcile_cases(service)
    closed = await service.get(case.number)
    assert closed.status == CaseStatus.RESOLVED
    assert closed.resolution.code == "muted"
    assert closed.resolution.reason_code == reports.EXTERNAL
    assert closed.resolution.by_discord_id == "909"
    assert closed.resolution.audit_id.startswith("audit-MUTE")
    assert note(bot, closed) == t("en", "reports-external-muted", name="Slash Admin")
    assert notices(db, "report_resolved") == {REPORTER_UUID}


async def test_a_ban_while_the_report_waited_in_the_stream_still_closes_it():
    service, db, _ = await reports_core()
    # Filed at NOW, banned a minute later, and the bot only read the report ten minutes on.
    service.now = lambda: NOW + timedelta(minutes=10)
    case = await open_report(service)
    assert case.created_at > NOW + timedelta(minutes=1)
    await audit(db, "BAN", at=NOW + timedelta(minutes=1))
    await reconcile_cases(service)
    closed = await service.get(case.number)
    assert closed.status == CaseStatus.RESOLVED and closed.resolution.ban_id == 44


async def test_an_audit_naming_this_case_is_left_to_the_core_not_taken_as_external():
    service, db, _ = await reports_core()
    case = await open_report(service)
    await audit(db, "BAN", at=NOW + timedelta(minutes=1), case=case.number)
    # The flow's own look at the audit trail skips it ...
    await reports.reconcile(service, case)
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    # ... and the core's recovery closes the case from it as its own sanction.
    await reconcile_cases(service)
    closed = await service.get(case.number)
    assert closed.status == CaseStatus.RESOLVED
    assert closed.resolution.reason_code != reports.EXTERNAL


async def test_a_sanction_during_a_card_action_is_retried_not_dropped(monkeypatch):
    service, _, bot = await reports_core()
    case = await open_report(service)

    async def crash(*args, **kwargs):
        raise RuntimeError("process died")

    finish = service.actions._finish
    monkeypatch.setattr(service.actions, "_finish", crash)
    with pytest.raises(RuntimeError):
        await sanction(service, case, "mute")
    assert (await service.get(case.number)).pending_action
    # The card's own action is still in progress: the event must come back later.
    with pytest.raises(CaseRace):
        await service.registry.dispatch_event(
            reports.BAN_EVENT, service, moderation(at=NOW + timedelta(minutes=2))
        )
    monkeypatch.setattr(service.actions, "_finish", finish)
    await reconcile_cases(service)
    closed = await service.get(case.number)
    # The card's mute is what closed it, from the audit trail, without a second mute.
    assert closed.resolution.code == "muted" and closed.resolution.reason_code is None
    assert len(bot.mutations["mutes"].docs) == 1


async def test_a_closed_report_ignores_later_sanctions():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await sanction(service, case, "ban")
    sent = len(bot.users[201].messages)
    await service.registry.dispatch_event(
        reports.MUTE_EVENT, service, moderation("mute", at=NOW + timedelta(hours=1))
    )
    closed = await service.get(case.number)
    assert closed.resolution.code == "banned" and closed.resolution.reason_code is None
    assert len(bot.users[201].messages) == sent
