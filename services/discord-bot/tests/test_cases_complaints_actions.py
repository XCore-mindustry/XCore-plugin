from __future__ import annotations

from dataclasses import replace
from unittest.mock import AsyncMock

import discord
import pytest
from cases_complaints_fakes import act, audit, complaint_core, opened, player
from cases_fakes import Interaction, member

from xcore_discord_bot.cases.flows import complaints
from xcore_discord_bot.cases.model import (
    AccessDenied,
    CaseStatus,
    InvalidForm,
    Party,
    Resolution,
)
from xcore_discord_bot.cases.notify import Outcome
from xcore_discord_bot.cases.reconcile import reconcile_case
from xcore_discord_bot.cases.views import CaseActionButton
from xcore_discord_bot.cogs.cases import CasesCog
from xcore_discord_bot.i18n import t


async def test_set_subject_user_select_records_only_matching_actor_and_accounts():
    rows = [
        audit(
            index=n, action="WARN", target=f"private-{1 + n % 2}=", reason=f"entry {n}"
        )
        for n in range(12)
    ]
    rows += [
        audit(index=20, actor="88"),
        audit(index=30, target="other"),
        audit(index=40, actor=None),
    ]
    service, _, bot = await complaint_core(players=[player(1), player(2)], audits=rows)
    case = await opened(service, bot)
    click = Interaction(bot, user=member(101, (20,)))
    await CaseActionButton(case.number, "subject").callback(click)
    view = click.events[-1][2]["view"]
    assert isinstance(view.select, discord.ui.UserSelect)
    view.select._values = [member(99, (20,))]
    await view.select.callback(Interaction(bot, user=member(101, (20,))))
    case = await service.get(case.number)
    assert case.subject.discord_id == "99" and "subject_unresolved" not in case.flags
    history = case.evidence["subject_actions"]
    assert history["total"] == 12 and len(history["last"]) == 10
    assert history["last"][0]["reason"] == "entry 11"
    assert history["last"][-1]["reason"] == "entry 2"
    fields = bot.channels[int(case.discord["thread_id"])].messages[0].embeds[0].fields
    assert any(
        "Recorded actions: 12" in f.value and "entry 11" in f.value for f in fields
    )
    assert any("existing Discord permissions" in f.value for f in fields)
    assert not bot.users


VERBS = [
    "take",
    "release",
    "subject",
    "resolve",
    "reject",
    "reopen",
    "find",
    "history",
    "refresh",
    "anything",
]


@pytest.mark.parametrize("verb", VERBS)
async def test_subject_is_denied_every_action_and_command_even_if_senior(verb):
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    case = await service.set_subject(
        case.number,
        member(101, (20,)),
        Party(discord_id="99", discord_name="Accused senior"),
    )
    with pytest.raises(AccessDenied):
        await service.authorize(case, member(99, (10, 20, 30)), verb)
    with pytest.raises(AccessDenied):
        await act(
            service,
            case,
            verb,
            user=member(99, (20,)),
            values={"note": "x", "reason_text": "x", "subject": "88"},
        )
    assert not bot.users


@pytest.mark.parametrize("roles", [(), (10,), (30,), (10, 30)])
@pytest.mark.parametrize(
    "verb", ["take", "subject", "resolve", "reject", "release", "find", "reopen"]
)
async def test_every_complaint_action_is_senior_only(roles, verb):
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    with pytest.raises(AccessDenied):
        await act(
            service,
            case,
            verb,
            user=member(99, roles),
            values={"note": "x", "reason_text": "x", "subject": "88"},
        )
    assert (await service.get(case.number)).subject is None
    assert bot.channels[int(case.discord["user_thread_id"])].members == {42}


async def test_subject_is_removed_from_assignee_and_membership_immediately_and_on_repair():
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    case = await act(service, case, "take", user=member(99, (20,)))
    thread = bot.channels[int(case.discord["user_thread_id"])]
    assert thread.members == {42, 99}
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    assert case.status == CaseStatus.OPEN and case.assignee is None
    assert "99" not in case.discord["managed_memberships"][0]["staff_ids"]
    assert thread.members == {42}
    await service.threads.reconcile_memberships(
        service, replace(case, assignee={"discord_id": "99"})
    )
    assert thread.members == {42}
    # Repair removes an invitation independently of whether the subject was the assignee.
    thread.members.add(99)
    await reconcile_case(service, case, audited=False)
    assert thread.members == {42}
    # A later subject change allows a former subject to Take and be added again.
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="88")
    )
    await act(service, case, "take", user=member(99, (20,)))
    assert thread.members == {42, 99}


async def test_subject_membership_removal_failure_retries_without_reassigning():
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    await act(service, case, "take", user=member(99, (20,)))
    bot.failures.arm("user_remove")
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    assert case.assignee is None and case.status == CaseStatus.OPEN
    with pytest.raises(AccessDenied):
        await act(service, case, "take", user=member(99, (20,)))
    await reconcile_case(service, case, audited=False)
    assert bot.channels[int(case.discord["user_thread_id"])].members == {42}


async def test_self_identification_releases_actor_without_senior_override():
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    await act(service, case, "take")
    case = await act(
        service, case, "subject", values={"subject": "101", "subject_name": "Self"}
    )
    assert case.assignee is None and case.subject.discord_id == "101"
    with pytest.raises(AccessDenied):
        await act(service, case, "resolve")
    assert bot.channels[int(case.discord["user_thread_id"])].members == {42}


async def test_slash_subject_refreshes_evidence_and_exclusion_immediately():
    service, _, bot = await complaint_core(audits=[audit()])
    case = await opened(service, bot)
    interaction = Interaction(bot, user=member(101, (20,)))
    await CasesCog.subject.callback(
        CasesCog(bot), interaction, str(case.number), member(99)
    )
    case = await service.get(case.number)
    assert (
        case.subject.discord_id == "99"
        and case.evidence["subject_actions"]["total"] == 1
    )
    with pytest.raises(AccessDenied):
        await CasesCog(bot).service.authorize(case, member(99, (20,)), "find")


async def test_subject_history_lookup_failure_reconciles_and_subject_change_hides_stale_entries():
    service, db, bot = await complaint_core(audits=[audit()])
    case = await opened(service, bot)
    original = db["moderation_audit"].count_documents
    db["moderation_audit"].count_documents = AsyncMock(
        side_effect=RuntimeError("private failure")
    )
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    assert case.subject.discord_id == "99" and "subject_actions" not in case.evidence
    db["moderation_audit"].count_documents = original
    await reconcile_case(service, case, audited=False)
    case = await service.get(case.number)
    assert case.evidence["subject_actions"]["total"] == 1
    db["moderation_audit"].count_documents = AsyncMock(
        side_effect=RuntimeError("private failure")
    )
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="88")
    )
    embed = complaints.card(case, "en")
    field = next(
        f for f in embed.fields if f.name == t("en", "complaints-subject-actions")
    )
    assert field.value == t("en", "complaints-actions-pending")


@pytest.mark.parametrize("subject", ["", "abc", "0", "-1", "999"])
async def test_invalid_subject_id_does_not_change_case(subject):
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    with pytest.raises(InvalidForm):
        await act(service, case, "subject", values={"subject": subject})
    assert (await service.get(case.number)).subject is None


async def test_modal_submit_rechecks_new_subject_and_changed_roles():
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    senior = member(99, (20,))
    click = Interaction(bot, user=senior)
    await CaseActionButton(case.number, "resolve").callback(click)
    modal = click.events[0][1]
    modal.inputs["note"]._value = "secret note"
    await service.set_subject(case.number, member(101, (20,)), Party(discord_id="99"))
    response = Interaction(bot, user=senior)
    await modal.on_submit(response)
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    assert response.followup.texts() == [t("en", "error-not-allowed")]
    await service.set_subject(case.number, member(101, (20,)), Party(discord_id="88"))
    response = Interaction(bot, user=member(99, (10,)))
    await modal.on_submit(response)
    assert response.followup.texts() == [t("en", "error-not-allowed")]


@pytest.mark.parametrize("code", ["upheld", "rejected"])
async def test_no_subject_notification_even_for_misconfigured_outcome_or_subject_as_opener(
    code,
):
    service, db, bot = await complaint_core()
    case = await opened(service, bot)
    case = await service.set_subject(
        case.number,
        member(101, (20,)),
        Party(discord_id="42", player_uuid="private-1="),
    )
    await service.notifier.send(
        service,
        case,
        Outcome(
            case.opener, "complaint_closed", "complaints-outcome", user_thread=True
        ),
    )
    assert not db["case_notices"].docs and not bot.users
    case = await service.resolve(
        case.number,
        member(101, (20,)),
        Resolution(
            code,
            reason_code="other" if code == "rejected" else None,
            reason_text="note",
        ),
    )
    assert not complaints.outcomes(case) and not bot.users
    assert bot.channels[int(case.discord["user_thread_id"])].members == set()


async def test_privacy_cleanup_precedes_failed_evidence_lookup_on_subject_change_and_repair():
    service, db, bot = await complaint_core()
    case = await opened(service, bot)
    await act(service, case, "take", user=member(99, (20,)))
    thread = bot.channels[int(case.discord["user_thread_id"])]

    async def failed_lookup(query):
        assert 99 not in thread.members
        raise RuntimeError("Unavailable history")

    db["moderation_audit"].count_documents = failed_lookup
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    assert thread.members == {42}
    thread.members.add(99)
    with pytest.raises(RuntimeError, match="Unavailable history"):
        await complaints.reconcile(service, case)
    assert thread.members == {42}


async def test_subject_actions_full_bounded_list_is_split_only_in_senior_post():
    service, _, bot = await complaint_core(
        audits=[
            audit(index=index, reason=(f"entry-{index} " + "x" * 120))
            for index in range(12)
        ]
    )
    case = await opened(service, bot)
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99", discord_name="Subject")
    )
    post = bot.channels[int(case.discord["thread_id"])]
    texts = [m.content for m in post.messages[1:]]
    assert all(len(text) <= 1800 for text in texts)
    combined = "\n".join(texts)
    assert "entry-11" in combined and "entry-2 " in combined
    assert "entry-1 " not in combined and "entry-0 " not in combined
    await complaints.subject_set(service, case)
    assert [m.content for m in post.messages[1:]] == texts
    assert all(
        "entry-" not in m.content
        for m in bot.channels[int(case.discord["user_thread_id"])].messages
    )


@pytest.mark.parametrize("subject_uuid", [None, "private-1="])
async def test_current_player_link_cannot_notify_complaint_subject(subject_uuid):
    service, db, bot = await complaint_core()
    case = await opened(service, bot)
    stale = case
    case = await service.set_subject(
        case.number,
        member(101, (20,)),
        Party(discord_id="99", player_uuid=subject_uuid),
    )
    service.players.by_uuid["private-1="].discord_id = "99"
    thread = bot.channels[int(case.discord["user_thread_id"])]
    before = len(thread.messages)
    # A stale, game-only recipient must be checked against the current subject.
    await service.notifier.send(
        service,
        stale,
        Outcome(
            Party(player_uuid="private-1="),
            "complaint_closed",
            "complaints-outcome",
            user_thread=True,
        ),
    )
    assert not db["case_notices"].docs and not bot.users
    assert len(thread.messages) == before
    assert not (await service.get(case.number)).progress.get("notification_receipts")
