"""B5: where and in which language a reporter hears the outcome (9.1 step 14, 8.4, 8.6)."""

from __future__ import annotations

from datetime import timedelta

import pytest
from cases_fakes import NOW, member
from cases_reports_fakes import (
    REPORTER_UUID,
    SECOND_UUID,
    act,
    from_second,
    move,
    open_report,
    panel,
    report,
    reports_core,
    sanction,
)

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.i18n import t


def wakeups(service):
    return [
        (call.kwargs["notice_id"].rsplit(":", 1)[-1], call.kwargs["server"])
        for call in service.transport.publish_case_notice.call_args_list
    ]


async def reject(service, case, code="no_evidence"):
    closed, _ = await act(
        service, case, "reject", values={"reason_code": code, "reason_text": "x"}
    )
    return closed


@pytest.mark.parametrize(
    "recipient,current_server,origin_server,expected",
    [
        ("opener", "pvp", "origin", "pvp"),
        ("opener", None, "origin", "origin"),
        ("co-reporter", "arena", "second-origin", "arena"),
        ("co-reporter", None, "second-origin", "second-origin"),
    ],
)
async def test_report_outcome_server_routing(
    recipient, current_server, origin_server, expected
):
    service, _, _ = await reports_core()
    case = await open_report(
        service, server=origin_server if recipient == "opener" else "origin"
    )
    if recipient == "co-reporter":
        await reports.submitted(service, from_second(server=origin_server))
        move(service, SECOND_UUID, online_server=current_server)
        await sanction(service, case, "ban")
        assert sorted(
            w for w in wakeups(service) if w[0] == "report_resolved"
        ) == sorted(
            [
                ("report_resolved", expected),
                ("report_resolved", "hub"),
            ]
        )
    else:
        move(service, REPORTER_UUID, online_server=current_server)
        await reject(service, case)
        assert wakeups(service)[-1] == ("report_rejected", expected)


@pytest.mark.parametrize(
    "submitted,current,preference,source,expected",
    [
        ("uk", "ru", None, None, "ru"),
        ("ru", None, None, None, "ru"),
        ("ru", "auto", None, None, "ru"),
        ("uk", "ru", "en", "manual", "en"),
        ("uk", "ru", "en", "auto", "en"),
    ],
)
async def test_report_outcome_locale_precedence(
    submitted, current, preference, source, expected
):
    service, _, bot = await reports_core()
    case = await open_report(service, reporterLocale=submitted)
    assert case.origin.locale == submitted
    if preference:
        await service.store.set_locale_pref(201, preference, source=source)
    move(service, REPORTER_UUID, local_language=current)
    await reject(service, case)
    assert bot.users[201].messages == [
        t(expected, "reports-outcome-no-evidence", number=case.number)
    ]


# --- one outcome per person ----------------------------------------------------------------------


async def test_a_person_who_reported_twice_hears_the_outcome_once():
    service, db, bot = await reports_core()
    case = await open_report(service)
    service.now = lambda: NOW + timedelta(seconds=61)
    await reports.submitted(service, report("submission-again"))
    await reject(service, case, "duplicate")
    kinds = [row["kind"] for row in db["case_notices"].docs]
    assert kinds.count("report_received") == 1 and kinds.count("report_rejected") == 1
    assert len(bot.users[201].messages) == 1


async def test_a_person_who_reported_from_the_game_and_discord_hears_it_once_per_channel():
    service, db, bot = await reports_core()
    case = await open_report(service)
    # Discord member 201 is the same person as the game reporter (a linked account).
    await panel(service, bot, {"who": "-5", "what": "me too"}, user=member(201, ()))
    merged = await service.get(case.number)
    assert len(merged.reporters) == 1
    await reject(service, merged)
    kinds = [row["kind"] for row in db["case_notices"].docs]
    assert kinds.count("report_rejected") == 1
    # The form recorded the Discord client's language, and a stored preference wins.
    assert (await service.store.get_locale_pref(201)).source == "auto"
    text = t("en", "reports-outcome-no-evidence", number=case.number)
    assert bot.users[201].messages == [text]
    thread = bot.channels[int(merged.user_threads["201"])]
    assert [m.content for m in thread.messages].count(text) == 1


async def test_a_discord_opener_hears_it_in_their_thread_which_is_then_closed():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "-5", "what": "grief"}, locale="uk")
    await reject(service, case)
    thread = bot.channels[int(case.user_threads["301"])]
    text = t("en", "reports-outcome-no-evidence", number=case.number)
    assert thread.messages[-1].content == text
    assert thread.archived and thread.locked
    assert bot.users[301].messages == [text]


@pytest.mark.parametrize("code", [*reports.REASON_CODES, "banned", "muted"])
async def test_no_outcome_text_names_staff_or_describes_a_sanction(code):
    for locale in ("en", "ru", "uk"):
        key = (
            "reports-outcome-action"
            if code in {"banned", "muted"}
            else "reports-outcome-" + code.replace("_", "-")
        )
        text = t(locale, key, number=7).casefold()
        for word in ("ban", "mute", "бан", "мут", "заблок", "staff", "адмін", "админ"):
            assert word not in text, (locale, key, text)


@pytest.mark.parametrize("recipient", ["opener", "co-reporter"])
@pytest.mark.parametrize("preference", [None, "manual", "auto"])
async def test_game_reporter_linked_after_filing_gets_dm(recipient, preference):
    service, db, bot = await reports_core()
    move(service, REPORTER_UUID, discord_id=None)
    case = await open_report(service)
    uuid = REPORTER_UUID
    if recipient == "co-reporter":
        await reports.submitted(service, from_second())
        uuid = SECOND_UUID
    current = await service.get(case.number)
    party = (
        current.opener if recipient == "opener" else reports._recipients(current)[1][0]
    )
    assert not party.discord_id
    if preference:
        await service.store.set_locale_pref(301, "en", source=preference)
    move(service, uuid, discord_id="301", local_language="ru")
    # Retry a transient DM failure without repeating the game notice.
    bot.failures.arm("dm")
    await reject(service, current)
    assert not (await service.get(case.number)).progress.get("outcomes_done")
    await service.finish_close(case.number)
    await service.finish_close(case.number)
    final = await service.get(case.number)
    assert bot.users[301].messages == [
        t(
            "en" if preference else "ru",
            "reports-outcome-no-evidence",
            number=case.number,
        )
    ]
    assert final.progress["closed_done"] and not final.user_threads
    receipts = final.progress["notification_receipts"]
    receipt = next(r for r in receipts if r["key"] == f"report_rejected:{uuid}")
    assert receipt["notice"] and receipt["dm"] and not receipt.get("thread")
    assert (
        len(
            [
                r
                for r in db["case_notices"].docs
                if r["_id"] == f"{case.number}:{uuid}:report_rejected"
            ]
        )
        == 1
    )


async def test_current_player_link_does_not_replace_discord_submitter_ownership():
    service, _, bot = await reports_core()
    move(service, REPORTER_UUID, discord_id="301")
    _, case = await panel(service, bot, {"who": "-5", "what": "grief"})
    move(service, REPORTER_UUID, discord_id="302")
    await reject(service, case)
    assert len(bot.users[301].messages) == 1 and 302 not in bot.users
    final = await service.get(case.number)
    assert set(final.user_threads) == {"301"}
    thread = bot.channels[int(final.user_threads["301"])]
    assert thread.messages[-1].content == bot.users[301].messages[0]
