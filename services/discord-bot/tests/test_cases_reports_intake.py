"""B5: reports from the game (design spec 9.1 steps 6–11, 8.8 abuse limits)."""

from __future__ import annotations

from datetime import timedelta

import pytest
from cases_fakes import NOW
from cases_reports_fakes import (
    BLACKLIST_ROLE,
    REPORTER_UUID,
    SECOND_UUID,
    TARGET_UUID,
    Guild,
    from_second,
    moderation,
    open_report,
    report,
    reports_core,
)
from xcore_protocol.generated.shared import PlayerRefV1

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.cases.model import CaseKind, CaseStatus
from xcore_discord_bot.cases.views import panel_view
from xcore_discord_bot.i18n import t
from xcore_discord_bot.runtime_consumers import case_reports_configured


def notices(db, kind=None):
    return [row for row in db["case_notices"].docs if kind in (None, row["kind"])]


def posts(bot):
    return bot.channels[200].all_threads


async def test_a_game_report_opens_a_case_and_acknowledges_the_reporter():
    service, db, bot = await reports_core()
    case = await open_report(service)
    assert case.status == CaseStatus.OPEN and case.kind == CaseKind.REPORT
    assert case.origin.source == "game" and case.origin.locale == "uk"
    assert case.dedup_key == f"report:{TARGET_UUID}"
    assert case.payload == {"reason": "griefing", "comment": "Destroyed our core"}
    assert case.evidence["context"]["same_network"] is True
    assert case.subject.player_pid == -5 and case.opener.player_pid == 0
    # The linked accounts are known, so the conflict rule can see them.
    assert case.opener.discord_id == "201" and case.subject.discord_id == "202"
    [ack] = notices(db)
    assert ack["_id"] == f"{case.number}:{REPORTER_UUID}:report_received"
    assert ack["params"] == {"number": str(case.number)}
    # Woken where the reporter is now, which is not the server the report came from.
    service.transport.publish_case_notice.assert_awaited_once_with(
        notice_id=ack["_id"], player_uuid=REPORTER_UUID, server="hub"
    )
    # An acknowledgement is in game only: the reporter is not sent a DM for it.
    assert not bot.users
    [post] = posts(bot)
    assert [tag.name for tag in post.applied_tags] == ["Report", "Open"]


async def test_a_redelivered_submission_never_creates_a_second_case_or_post():
    service, db, bot = await reports_core(cases_report_burst_limit=1)
    case = await open_report(service)
    for _ in range(3):
        await reports.submitted(service, report())
    assert len(db["cases"].docs) == 1 and len(posts(bot)) == 1
    assert (await service.get(case.number)).co_openers == ()
    # Neither the cooldown nor the burst breaker refuses the redelivery.
    assert notices(db) == notices(db, "report_received")
    assert len(notices(db)) == 1


@pytest.mark.parametrize("step", ["forum_create", "post_send"])
@pytest.mark.parametrize("after", [False, True])
async def test_redelivery_after_a_discord_failure_resumes_the_same_case(step, after):
    service, db, bot = await reports_core()
    bot.failures.arm(step, after=after)
    await reports.submitted(service, report())
    pending = await service.store.by_submission("submission-1")
    assert pending is not None and not service.creation_complete(pending)
    await reports.submitted(service, report())
    current = await service.get(pending.number)
    assert service.creation_complete(current)
    assert len(db["cases"].docs) == 1 and len(posts(bot)) == 1
    assert len(notices(db, "report_received")) == 1


async def test_a_failure_before_the_case_is_stored_reaches_the_stream_for_a_retry(
    monkeypatch,
):
    service, db, _ = await reports_core()
    original, calls = reports._sanctions, []

    async def failing_once(*args):
        calls.append(args)
        if len(calls) == 1:
            raise RuntimeError("MongoDB is unavailable")
        return await original(*args)

    monkeypatch.setattr(reports, "_sanctions", failing_once)
    with pytest.raises(RuntimeError):
        await reports.submitted(service, report())
    assert db["cases"].docs == [] and notices(db) == []
    # The retry is not refused by the cooldown the first attempt reserved.
    await reports.submitted(service, report())
    assert len(db["cases"].docs) == 1 and len(notices(db, "report_received")) == 1


async def test_a_second_reporter_joins_the_open_case_and_is_acknowledged():
    service, db, bot = await reports_core()
    case = await open_report(service)
    await reports.submitted(service, from_second(reason="cheating", comment="aimbot"))
    merged = await service.get(case.number)
    assert [row["player_uuid"] for row in merged.co_openers] == [SECOND_UUID]
    assert merged.co_openers[0]["payload"] == {
        "reason": "cheating",
        "comment": "aimbot",
    }
    assert len(db["cases"].docs) == 1 and len(posts(bot)) == 1
    acks = {row["player_uuid"]: row for row in notices(db, "report_received")}
    assert set(acks) == {REPORTER_UUID, SECOND_UUID}
    assert acks[SECOND_UUID]["params"] == {"number": str(case.number)}
    note = posts(bot)[0].messages[-1].content
    assert note == t(
        "en",
        "reports-merged",
        total=2,
        reporter="Second · " + t("en", "cases-pid", pid=7),
        reason=t("en", "reports-reason-cheating") + " — aimbot",
    )
    # Redelivered, the merge is not repeated.
    await reports.submitted(service, from_second(reason="cheating", comment="aimbot"))
    assert len((await service.get(case.number)).co_openers) == 1
    assert len(notices(db, "report_received")) == 2


async def test_the_same_reporter_reporting_again_is_one_reporter_with_one_ack():
    service, db, _ = await reports_core()
    case = await open_report(service)
    service.now = lambda: NOW + timedelta(seconds=61)
    await reports.submitted(service, report("submission-again"))
    merged = await service.get(case.number)
    assert len(merged.co_openers) == 1 and len(merged.reporters) == 1
    # One notice per reporter and case (spec 4.3 notice id).
    assert len(notices(db, "report_received")) == 1


async def test_cooldown_refuses_a_second_submission_and_tells_the_player_once():
    service, db, _ = await reports_core()
    await open_report(service)
    other_target = PlayerRefV1("other-uuid", "Other", 9)
    await reports.submitted(service, report("submission-2", target=other_target))
    [refusal] = notices(db, "report_not_accepted")
    assert refusal["_id"] == "refused:submission-2"
    assert refusal["player_uuid"] == REPORTER_UUID
    assert "case_number" not in refusal and "number" not in refusal["params"]
    assert len(db["cases"].docs) == 1
    wakeups = service.transport.publish_case_notice.await_count
    for _ in range(2):
        await reports.submitted(service, report("submission-2", target=other_target))
    assert len(notices(db, "report_not_accepted")) == 1
    assert service.transport.publish_case_notice.await_count == wakeups
    # The cooldown is per reporter: another player is not affected, and it ends.
    await reports.submitted(service, from_second("submission-3", target=other_target))
    assert len(db["cases"].docs) == 2
    service.now = lambda: NOW + timedelta(seconds=60)
    third = PlayerRefV1("third-uuid", "Third", 10)
    await reports.submitted(service, report("submission-4", target=third))
    assert len(db["cases"].docs) == 3


async def test_a_refusal_interrupted_before_its_notice_is_refused_again(monkeypatch):
    service, db, _ = await reports_core()
    await open_report(service)
    insert, calls = service.store.insert_notice, []

    async def failing_once(row):
        calls.append(row)
        if len(calls) == 1:
            raise RuntimeError("MongoDB is unavailable")
        return await insert(row)

    monkeypatch.setattr(service.store, "insert_notice", failing_once)
    other = report("submission-2", target=PlayerRefV1("other-uuid", "Other", 9))
    with pytest.raises(RuntimeError):
        await reports.submitted(service, other)
    await reports.submitted(service, other)
    assert len(db["cases"].docs) == 1
    assert [row["_id"] for row in notices(db, "report_not_accepted")] == [
        "refused:submission-2"
    ]


async def test_a_blacklisted_linked_reporter_gets_a_refusal_and_no_case():
    guild = Guild({201: (BLACKLIST_ROLE,)})
    service, db, _ = await reports_core(
        guild=guild, cases_blacklist_role_id=BLACKLIST_ROLE
    )
    await reports.submitted(service, report())
    assert db["cases"].docs == []
    [refusal] = notices(db, "report_not_accepted")
    assert refusal["params"] == {"reason": "blocked"}
    # A member who left the server is not blacklisted any more.
    guild.roles_by_member = {}
    await reports.submitted(service, report("submission-2"))
    assert guild.fetched == [201]
    assert len(db["cases"].docs) == 1


async def test_the_blacklist_check_without_the_guild_is_retried_not_skipped():
    service, db, bot = await reports_core(cases_blacklist_role_id=BLACKLIST_ROLE)
    bot.get_guild = lambda guild_id: None
    with pytest.raises(RuntimeError):
        await reports.submitted(service, report())
    assert db["cases"].docs == [] and notices(db) == []


async def test_the_burst_breaker_refuses_new_cases_alerts_once_and_still_merges():
    service, db, bot = await reports_core(cases_report_burst_limit=1)
    case = await open_report(service)
    targets = [PlayerRefV1(f"uuid-{n}", f"Player {n}", n) for n in range(3)]
    reporters = [
        PlayerRefV1(f"reporter-{n}", f"Reporter {n}", 50 + n) for n in range(3)
    ]
    for n in range(2):
        await reports.submitted(
            service, report(f"burst-{n}", target=targets[n], reporter=reporters[n])
        )
    assert len(db["cases"].docs) == 1
    assert [row["params"] for row in notices(db, "report_not_accepted")] == [
        {"reason": "burst"},
        {"reason": "burst"},
    ]
    alerts = [
        thread for thread in posts(bot) if thread.id != int(case.discord["thread_id"])
    ]
    assert [thread.name for thread in alerts] == [t("en", "reports-burst-title")]
    burst = next(row for row in db[reports.LIMITS].docs if row["_id"] == "burst")
    assert burst["refused"] == 2
    # A report on the player who already has a case joins it.
    await reports.submitted(service, report("burst-merge", reporter=reporters[2]))
    assert len((await service.get(case.number)).co_openers) == 1
    # The window ends.
    service.now = lambda: NOW + timedelta(seconds=601)
    await reports.submitted(service, from_second("after", target=targets[2]))
    assert len(db["cases"].docs) == 2


async def test_a_failed_burst_alert_does_not_hold_up_the_refusal():
    service, db, bot = await reports_core(cases_report_burst_limit=1)
    await open_report(service)
    bot.failures.arm("forum_create")
    other = PlayerRefV1("uuid-1", "Player 1", 1)
    await reports.submitted(service, from_second("burst-1", target=other))
    assert len(notices(db, "report_not_accepted")) == 1
    assert len(posts(bot)) == 1
    # The next refusal in the window posts the alert that failed.
    third = PlayerRefV1("reporter-3", "Reporter 3", 3)
    await reports.submitted(service, report("burst-2", target=other, reporter=third))
    assert len(posts(bot)) == 2


async def test_with_the_flag_off_nothing_happens_and_no_panel_button_is_shown():
    service, db, bot = await reports_core(enabled=False)
    await service.registry.dispatch_event(reports.REPORT_EVENT, service, report())
    await service.registry.dispatch_event(reports.BAN_EVENT, service, moderation())
    assert db["cases"].docs == [] and notices(db) == [] and not posts(bot)
    labels = [item.item.custom_id for item in panel_view(service.registry).children]
    assert not any("report" in label for label in labels)
    # The consumer does not start at all when the setting is off.
    bot.container.cases = service
    assert case_reports_configured(bot) is False
    service.settings = service.settings.model_copy(
        update={"cases_reports_enabled": True}
    )
    assert case_reports_configured(bot) is True
