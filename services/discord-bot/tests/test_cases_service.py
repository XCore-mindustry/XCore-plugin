from __future__ import annotations

import asyncio
from dataclasses import replace
from datetime import timedelta

import pytest
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import NOW, core, member

from xcore_discord_bot.cases.model import (
    AccessDenied,
    CaseKind,
    CaseRace,
    CaseStatus,
    Cooldown,
    InvalidTransition,
    OpenLimit,
    Origin,
    Party,
    Resolution,
)
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.cases.store import CASE_NUMBER_SEED
from xcore_discord_bot.staff_actions import InvalidDuration, MapUploadTimeout

OPENER = Party(
    player_uuid="opener-secret",
    player_pid=1,
    player_name="Reporter",
    discord_id="42",
    discord_name="Reporter",
)
SUBJECT = Party(
    player_uuid="subject-secret", player_pid=2, player_name="Subject", discord_id="77"
)


async def opened(service, *, submission="submission", source="game", **kwargs):
    return await service.open(
        kind=DUMMY_FLOW.kind,
        origin=Origin(source, submission, "ru", "server" if source == "game" else None),
        opener=OPENER,
        subject=SUBJECT,
        member=member(42, ()) if source == "discord" else None,
        **kwargs,
    )


async def test_dummy_flow_open_take_resolve_archive_and_reopen():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service, source="discord")
    assert case.status == CaseStatus.OPEN and case.progress["creation_done"]
    thread = bot.channels[int(case.discord["thread_id"])]
    user_thread = bot.channels[int(case.discord["user_thread_id"])]
    assert user_thread.private and 42 in user_thread.members
    assert case.number == CASE_NUMBER_SEED + 1
    await service.take(case.number, member())
    assert 101 in user_thread.members
    final = await service.dispatch_action(
        case.number, "resolve", member(), "en", {"note": "done"}
    )
    assert final.status == CaseStatus.RESOLVED and final.resolution.code == "muted"
    assert final.resolution.reason_text == "done"
    assert final.assignee["discord_id"] == "101"
    assert (
        thread.archived
        and thread.locked
        and user_thread.archived
        and user_thread.locked
    )
    assert thread.messages[0].view.children == []
    assert final.progress["closed_done"] and len(db["case_notices"].docs) == 1
    assert (
        bot.users[42].messages
        and user_thread.messages[-1].content == bot.users[42].messages[-1]
    )
    assert "active" not in db["cases"].docs[0]
    event_types = [e["type"] for e in final.events]
    assert all(e in event_types for e in ("created", "taken", "action", "notified"))
    reopened = await service.reopen(case.number, member(200, (20,)))
    assert (
        reopened.status == CaseStatus.OPEN
        and thread.archived is False
        and thread.locked is False
    )
    assert user_thread.archived is False and user_thread.locked is False
    assert "active" in db["cases"].docs[0]


async def test_dummy_flow_open_reject_without_take():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    result = await service.reject(
        case.number, member(), reason_code="no_evidence", reason_text="Details"
    )
    assert (
        result.status == CaseStatus.REJECTED
        and result.resolution.reason_code == "no_evidence"
    )
    assert result.assignee["discord_id"] == "101"
    assert bot.channels[int(case.discord["thread_id"])].locked


async def test_duplicate_submission_and_merged_submission_are_idempotent():
    service, db, bot = await core(flow=DUMMY_FLOW)
    first = await opened(service, dedup_key="report:subject-secret")
    second_opener = Party(player_uuid="co-secret", discord_id="43")
    second = await service.open(
        kind=DUMMY_FLOW.kind,
        origin=Origin("game", "second"),
        opener=second_opener,
        subject=SUBJECT,
        dedup_key="report:subject-secret",
    )
    retry = await service.open(
        kind=DUMMY_FLOW.kind,
        origin=Origin("game", "second"),
        opener=second_opener,
        subject=SUBJECT,
        dedup_key="report:subject-secret",
    )
    assert first.number == second.number == retry.number
    assert len(db["cases"].docs) == 1 and len(retry.co_openers) == 1
    assert len(bot.channels[200].all_threads) == 1
    assert (await service.store.by_submission("second")).number == first.number
    await service.reject(first.number, member(), reason_code="no_violation")
    after_close = await opened(
        service, submission="third", dedup_key="report:subject-secret"
    )
    assert after_close.number != first.number
    with pytest.raises(CaseRace):
        await service.reopen(first.number, member(200, (20,)))


async def test_concurrent_duplicate_reports_merge_once():
    service, db, _ = await core(flow=DUMMY_FLOW)
    results = await asyncio.gather(
        *(
            service.open(
                kind=DUMMY_FLOW.kind,
                origin=Origin("game", f"sub-{n}"),
                opener=Party(player_uuid=f"opener-{n}"),
                subject=SUBJECT,
                dedup_key="report:subject-secret",
            )
            for n in range(6)
        )
    )
    assert len({c.number for c in results}) == 1 and len(db["cases"].docs) == 1
    case = await service.get(results[0].number)
    assert len(case.co_openers) == 5


async def test_store_cas_is_atomic_with_event_and_revision():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    changed = await service.store.cas(
        case,
        set_fields={"flags": ["subject_offline"]},
        event={"at": NOW, "type": "evidence_refreshed"},
    )
    assert changed.revision == case.revision + 1
    with pytest.raises(CaseRace):
        await service.store.cas(
            case, set_fields={"flags": []}, event={"at": NOW, "type": "error"}
        )
    assert (await service.get(case.number)).flags == ("subject_offline",)
    with pytest.raises(ValueError):
        await service.store.cas(changed, set_fields={"events": []})


@pytest.mark.parametrize(
    "step",
    [
        "forum_create",
        "post_fetch",
        "post_send",
        "user_create",
        "user_add",
        "user_send",
        "card_edit",
        "thread_edit",
    ],
)
@pytest.mark.parametrize("after", [False, True])
async def test_creation_resumes_after_each_discord_failure(step, after):
    service, db, bot = await core(flow=DUMMY_FLOW)
    bot.failures.arm(step, after=after)
    case = await opened(
        service,
        source="discord",
        evidence={"chat": [{"name": "Example", "pid": 3, "text": "Chat evidence"}]},
    )
    retried = await opened(service, source="discord", evidence={"chat": []})
    assert retried.number == case.number and retried.status == CaseStatus.OPEN
    assert retried.progress["creation_done"]
    assert (
        len(db["cases"].docs) == 1
        and len(bot.channels[200].all_threads) == 1
        and len(bot.channels[100].all_threads) == 1
    )
    thread = bot.channels[int(retried.discord["thread_id"])]
    assert len([m for m in thread.messages if "Chat evidence" in m.content]) == 1
    user_thread = bot.channels[int(retried.discord["user_thread_id"])]
    assert 42 in user_thread.members and len(user_thread.messages) == 1


@pytest.mark.parametrize(
    "field",
    [
        "status",
        "progress.evidence_done",
        "discord.user_thread_id",
        "progress.created_hook_done",
        "progress.creation_done",
    ],
)
@pytest.mark.parametrize("after", [False, True])
async def test_creation_resumes_after_each_checkpoint_failure(
    monkeypatch, field, after
):
    service, db, bot = await core(flow=DUMMY_FLOW)
    original = service.store.cas
    failed = False

    async def fail_once(case, **kwargs):
        nonlocal failed
        fields = kwargs.get("set_fields") or {}
        if not failed and field in fields:
            failed = True
            if after:
                await original(case, **kwargs)
            raise RuntimeError("Lost database write response")
        return await original(case, **kwargs)

    monkeypatch.setattr(service.store, "cas", fail_once)
    case = await opened(
        service, source="discord", evidence={"chat": [{"text": "evidence"}]}
    )
    final = await opened(service, source="discord")
    assert failed and final.number == case.number and final.progress["creation_done"]
    assert (
        len(db["cases"].docs) == 1
        and len(bot.channels[200].all_threads) == 1
        and len(bot.channels[100].all_threads) == 1
    )


async def test_creation_resumes_failed_flow_acknowledgement():
    calls = 0

    async def created(service, case):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise RuntimeError("No acknowledgement yet")

    service, _, bot = await core(flow=replace(DUMMY_FLOW, on_created=created))
    case = await opened(service)
    assert not case.progress.get("created_hook_done")
    final = await service.resume_creation(case.number)
    assert (
        final.progress["created_hook_done"]
        and calls == 2
        and len(bot.channels[200].all_threads) == 1
    )


async def test_discord_open_blacklist_cooldown_cap_and_retry():
    service, _, _ = await core(flow=DUMMY_FLOW, cases_blacklist_role_id=40)
    with pytest.raises(AccessDenied):
        await service.open(
            kind=DUMMY_FLOW.kind,
            origin=Origin("discord", "blocked"),
            opener=OPENER,
            member=member(42, (40,)),
        )
    case = await opened(service, source="discord")
    assert (await opened(service, source="discord")).number == case.number
    with pytest.raises(Cooldown):
        await opened(service, source="discord", submission="new")
    service.now = lambda: NOW + timedelta(seconds=61)
    await opened(service, source="discord", submission="new")
    service.now = lambda: NOW + timedelta(seconds=122)
    await opened(service, source="discord", submission="third")
    service.now = lambda: NOW + timedelta(seconds=183)
    with pytest.raises(OpenLimit):
        await opened(service, source="discord", submission="fourth")


async def test_thread_delete_during_action_does_not_lose_terminal_transition():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    async def action(current):
        await service.thread_deleted(int(current.discord["thread_id"]))
        return Resolution("muted")

    final = await service.execute_action(case.number, "mute", member(), action)
    assert final.status == CaseStatus.RESOLVED and final.pending_action is None
    assert any(e["type"] == "thread_deleted" for e in final.events)


def audit(number, *, action="BAN", at=NOW, **extra):
    return {
        "_id": f"audit-{number}-{action}",
        "audit_id": f"audit-{number}-{action}",
        "action": action,
        "occurred_at": at,
        "actor": {"discord_id": "101", "name_snapshot": "Staff"},
        "details": {"extra": {"case": str(number), **extra}},
    }


def event_types(case):
    return [entry["type"] for entry in case.events]


async def racing_cas(service, monkeypatch, before):
    """Let ``before()`` run between the next read of a case and its compare-and-set."""
    original = service.store.cas
    pending = [before]

    async def cas(case, **kwargs):
        if pending:
            monkeypatch.setattr(service.store, "cas", original)
            await pending.pop()()
        return await original(case, **kwargs)

    monkeypatch.setattr(service.store, "cas", cas)


async def test_release_is_for_the_assignee_or_a_senior():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    await service.take(case.number, member())
    with pytest.raises(InvalidTransition):
        await service.take(case.number, member(102))
    with pytest.raises(AccessDenied):
        await service.release(case.number, member(102))
    assert (await service.get(case.number)).assignee["discord_id"] == "101"
    result = await service.release(case.number, member(102, (10, 20)))
    assert result.status == CaseStatus.OPEN and result.assignee is None
    with pytest.raises(InvalidTransition):
        await service.release(case.number, member())


async def test_two_staff_take_at_once_and_only_one_wins(monkeypatch):
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    async def other_takes_first():
        await service.take(case.number, member(102))

    await racing_cas(service, monkeypatch, other_takes_first)
    # 101 read the open case, 102 took it, and only then 101's write arrives.
    with pytest.raises(InvalidTransition) as refused:
        await service.take(case.number, member(101))
    assert refused.value.key == "error-already-handled"
    final = await service.get(case.number)
    assert final.assignee["discord_id"] == "102"
    assert event_types(final).count("taken") == 1


async def test_an_unrelated_write_is_not_reported_as_a_race(monkeypatch):
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service, dedup_key="report:subject-secret")

    async def second_reporter_joins():
        await service.merge(
            case.number,
            opener=Party(player_uuid="co-secret"),
            origin=Origin("game", "second"),
        )

    await racing_cas(service, monkeypatch, second_reporter_joins)
    taken = await service.take(case.number, member())
    assert taken.status == CaseStatus.TAKEN and len(taken.co_openers) == 1


async def test_two_staff_press_the_same_action_and_it_runs_once():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    calls = []

    async def ban(current):
        calls.append(current.number)
        await asyncio.sleep(0)
        return Resolution("banned")

    results = await asyncio.gather(
        service.execute_action(case.number, "ban", member(101), ban),
        service.execute_action(case.number, "ban", member(102), ban),
        return_exceptions=True,
    )
    assert calls == [case.number]
    assert [type(r) for r in results].count(CaseRace) == 1
    final = await service.get(case.number)
    assert final.status == CaseStatus.RESOLVED and final.pending_action is None
    assert event_types(final).count("action") == 1
    assert len(bot.users[42].messages) == 1


async def test_different_verbs_and_a_reject_wait_for_the_action_in_progress():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service, dedup_key="report:subject-secret")
    started, finish = asyncio.Event(), asyncio.Event()
    calls = []

    async def action(current):
        calls.append(current.number)
        started.set()
        await finish.wait()
        return Resolution("banned")

    first = asyncio.create_task(
        service.execute_action(case.number, "ban", member(), action)
    )
    await started.wait()
    with pytest.raises(CaseRace):
        await service.execute_action(case.number, "mute", member(102), action)
    with pytest.raises(CaseRace):
        await service.reject(case.number, member(102), reason_code="no_evidence")
    with pytest.raises(CaseRace):
        await service.take(case.number, member(102))
    # Another reporter joining is not a decision and is not held up.
    joined = await service.merge(
        case.number, opener=Party(player_uuid="co-secret"), origin=Origin("game", "b")
    )
    assert len(joined.co_openers) == 1 and joined.pending_action
    finish.set()
    result = await first
    assert calls == [case.number] and result.status == CaseStatus.RESOLVED
    assert result.resolution.by_discord_id == "101" and len(result.co_openers) == 1


async def test_pipeline_order_claim_action_close_notify_render():
    order = []
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    thread = bot.channels[int(case.discord["thread_id"])]
    claim, send, render = (
        service.claims.claim_idempotency,
        service.notifier.send,
        service.threads.render,
    )

    async def claiming(key, ttl_seconds=600):
        order.append("claim")
        return await claim(key, ttl_seconds)

    async def sending(*args):
        order.append("notify:" + (await service.get(case.number)).status)
        await send(*args)

    async def rendering(svc, current):
        order.append("render:" + current.status)
        await render(svc, current)

    service.claims.claim_idempotency = claiming
    service.notifier.send = sending
    service.threads.render = rendering

    async def mute(current):
        stored = await service.get(case.number)
        # The guard is stored before the side effect, and the card has not moved yet.
        assert stored.pending_action["verb"] == "mute" and stored.status == "open"
        assert not thread.archived
        order.append("action")
        return Resolution("muted", audit_id="a1", duration_ms=60_000)

    final = await service.execute_action(case.number, "mute", member(), mute)
    assert order == ["claim", "action", "notify:resolved", "render:resolved"]
    assert final.resolution.audit_id == "a1" and final.resolution.duration_ms == 60_000
    assert final.resolution.by_name == "Staff 101" and final.resolution.at == NOW
    assert event_types(final)[-3:] == ["action_started", "action", "notified"]
    assert thread.archived and thread.locked


@pytest.mark.parametrize("error", [InvalidDuration, MapUploadTimeout, RuntimeError])
async def test_failed_staff_action_changes_nothing_and_can_be_repeated_at_once(error):
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    thread = bot.channels[int(case.discord["thread_id"])]
    calls = []

    async def fail(current):
        calls.append("fail")
        raise error("nothing was written")

    with pytest.raises(error):
        await service.execute_action(case.number, "mute", member(), fail)
    current = await service.get(case.number)
    assert current.status == CaseStatus.OPEN and current.pending_action is None
    assert current.resolution is None and current.assignee is None
    assert current.events[-1]["type"] == "error"
    assert current.events[-1]["data"] == {"verb": "mute", "error_type": error.__name__}
    # Nothing after the action ran: no notice, no DM, the post still open.
    assert not db["case_notices"].docs and 42 not in bot.users
    assert not thread.archived and not thread.locked
    # The repair task does not run the action for anyone.
    service.now = lambda: NOW + timedelta(hours=1)
    await reconcile_cases(service)
    assert calls == ["fail"]

    async def mute(current):
        calls.append("mute")
        return Resolution("muted")

    final = await service.execute_action(case.number, "mute", member(), mute)
    assert calls == ["fail", "mute"] and final.status == CaseStatus.RESOLVED


async def test_action_that_fails_after_its_sanction_was_written_closes_the_case():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    async def ban(current):
        await db["moderation_audit"].insert_one(
            {
                **audit(
                    case.number,
                    ban_id="99",
                    case_action_token=current.pending_action["token"],
                ),
                "target": {"uuid": current.subject.player_uuid},
            }
        )
        raise RuntimeError("The bans channel post failed")

    final = await service.execute_action(case.number, "ban", member(), ban)
    assert final.status == CaseStatus.RESOLVED and final.pending_action is None
    assert final.resolution.code == "banned" and final.resolution.ban_id == 99
    assert final.resolution.audit_id == f"audit-{case.number}-BAN"
    assert final.progress["closed_done"] and len(bot.users[42].messages) == 1
    assert bot.channels[int(case.discord["thread_id"])].archived


@pytest.mark.parametrize("guard", [True, False])
async def test_crash_between_staff_action_and_case_update_is_repaired(guard):
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    if guard:
        # The process died inside the action: the guard is still on the case.
        await service.store.cas(
            case,
            set_fields={
                "pending_action": {
                    "token": "dead",
                    "verb": "ban",
                    "at": NOW,
                    "actor": {"discord_id": "101", "name": "Staff"},
                }
            },
        )
    await db["moderation_audit"].insert_one(
        {**audit(case.number, ban_id="7"), "target": {"uuid": case.subject.player_uuid}}
    )
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert final.status == CaseStatus.RESOLVED and final.pending_action is None
    assert final.resolution.code == "banned" and final.resolution.ban_id == 7
    assert final.resolution.by_discord_id == "101"
    assert final.assignee["discord_id"] == "101"
    assert final.progress["closed_done"] and len(bot.users[42].messages) == 1
    assert bot.channels[int(case.discord["thread_id"])].locked
    await reconcile_cases(service)
    assert len(bot.users[42].messages) == 1


async def test_abandoned_guard_is_released_after_ten_minutes_without_rerunning():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    card = bot.channels[int(case.discord["thread_id"])].messages[0]
    await service.store.cas(
        case,
        set_fields={
            "pending_action": {
                "token": "dead",
                "verb": "ban",
                "at": NOW,
                "actor": {"discord_id": "101", "name": "Staff"},
            }
        },
    )
    await service.project(case.number)
    assert card.view.children == []
    service.now = lambda: NOW + timedelta(minutes=9)
    await reconcile_cases(service)
    assert (await service.get(case.number)).pending_action
    service.now = lambda: NOW + timedelta(minutes=10)
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert current.pending_action is None and current.status == CaseStatus.OPEN
    assert current.events[-1]["data"] == {"verb": "ban", "error_type": "TimeoutError"}
    assert [item.custom_id for item in card.view.children] == [
        f"xc:1:case:{verb}:{case.number}" for verb in ("take", "resolve", "reject")
    ]


async def test_audit_recovery_maps_each_kind_and_ignores_foreign_records():
    cases = [
        ("appeal", "UNBAN", {"ban_id": "5"}, "unbanned"),
        ("appeal", "NOTE", {"kind": "ban_shortened"}, "shortened"),
        ("badge", "NOTE", {"kind": "badge_granted"}, "granted"),
        ("report", "MUTE", {}, "muted"),
    ]
    for kind, action, extra, code in cases:
        flow = replace(DUMMY_FLOW, kind=CaseKind(kind), outcomes=None)
        service, db, _ = await core(flow=flow)
        case = await service.open(
            kind=kind, origin=Origin("game", "s"), opener=OPENER, subject=SUBJECT
        )
        # A note that is not a sanction, and a record of another case, close nothing.
        await db["moderation_audit"].insert_one(
            audit(case.number, action="NOTE", kind="comment")
        )
        await db["moderation_audit"].insert_one(
            {**audit(case.number + 1, action=action, **extra), "_id": "other"}
        )
        await reconcile_cases(service)
        assert (await service.get(case.number)).status == CaseStatus.OPEN
        await db["moderation_audit"].insert_one(
            {**audit(case.number, action=action, **extra), "_id": "own"}
        )
        await reconcile_cases(service)
        final = await service.get(case.number)
        assert final.status == CaseStatus.RESOLVED and final.resolution.code == code


async def test_resolution_must_belong_to_the_kind():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    async def wrong(current):
        return Resolution("granted")

    with pytest.raises(ValueError):
        await service.execute_action(case.number, "grant", member(), wrong)
    current = await service.get(case.number)
    assert current.status == CaseStatus.OPEN and current.pending_action is None


async def test_redelivered_creation_is_one_case_one_post_and_costs_nothing_more():
    service, db, bot = await core(flow=DUMMY_FLOW)
    first, second = await asyncio.gather(
        opened(service, source="discord"), opened(service, source="discord")
    )
    assert first.number == second.number and len(db["cases"].docs) == 1
    assert len(bot.channels[200].all_threads) == 1
    assert len(bot.channels[100].all_threads) == 1
    # A clean creation never walks the forum looking for a post to adopt.
    assert not [call for call in bot.calls if call[0] == "archived_threads"]
    revision = (await service.get(first.number)).revision
    bot.calls.clear()
    third = await opened(service, source="discord")
    await service.resume_creation(first.number)
    await reconcile_cases(service)
    assert third.number == first.number and bot.calls == []
    assert (await service.get(first.number)).revision == revision
    assert event_types(third).count("created") == 1


async def test_only_a_retry_looks_for_a_post_to_adopt():
    service, _, bot = await core(flow=DUMMY_FLOW)
    bot.failures.arm("forum_create", after=True)
    case = await opened(service)
    assert case.status == CaseStatus.CREATING and case.progress["post_attempted"]
    assert case.events[-1]["data"] == {"step": "creation", "error_type": "RuntimeError"}
    bot.channels[200].all_threads[0].archived = True
    final = await service.resume_creation(case.number)
    assert final.status == CaseStatus.OPEN and len(bot.channels[200].all_threads) == 1
    assert ("archived_threads", 200) in bot.calls


async def test_repeated_failure_is_recorded_once():
    service, _, bot = await core(flow=DUMMY_FLOW)
    bot.failures.arm("forum_create")
    case = await opened(service)
    bot.failures.arm("forum_create")
    await service.resume_creation(case.number)
    current = await service.get(case.number)
    assert event_types(current).count("error") == 1


async def test_set_subject_is_senior_only_and_blocks_the_new_subject():
    flow = replace(DUMMY_FLOW, kind=CaseKind.COMPLAINT, outcomes=None)
    service, _, _ = await core(flow=flow)
    case = await service.open(
        kind="complaint",
        origin=Origin("discord", "c"),
        opener=Party(discord_id="42", discord_name="Member"),
        flags=("subject_unresolved",),
        member=member(42, ()),
    )
    assert case.flags == ("subject_unresolved",)
    with pytest.raises(AccessDenied):
        await service.set_subject(case.number, member(101), Party(discord_id="201"))
    updated = await service.set_subject(
        case.number, member(200, (20,)), Party(discord_id="201", discord_name="Admin")
    )
    assert updated.subject.discord_id == "201" and updated.flags == ()
    assert updated.events[-1]["type"] == "subject_set"
    with pytest.raises(AccessDenied):
        await service.take(case.number, member(201, (20,)))
    assert (await service.take(case.number, member(200, (20,)))).status == "taken"


async def test_reopen_runs_the_close_again_and_keeps_history():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service, source="discord")
    await service.reject(case.number, member(), reason_code="no_evidence")
    [rejected] = db["case_notices"].docs
    rejected["delivered_at"] = NOW
    with pytest.raises(AccessDenied):
        await service.reopen(case.number, member())
    reopened = await service.reopen(case.number, member(200, (20,)))
    assert reopened.resolution is None and reopened.closed_at is None
    assert reopened.assignee is None and reopened.progress["creation_done"]
    with pytest.raises(InvalidTransition):
        await service.reopen(case.number, member(200, (20,)))
    final = await service.dispatch_action(
        case.number, "resolve", member(102), "en", {"note": "second look"}
    )
    assert final.status == CaseStatus.RESOLVED and final.progress["closed_done"]
    assert len(bot.users[42].messages) == 2 and len(db["cases"].docs) == 1
    # The second decision reaches the game although the first notice was delivered.
    [notice] = db["case_notices"].docs
    assert notice["kind"] == "report_resolved" and notice["delivered_at"] is None
    assert service._locks == {}
    assert event_types(final).count("rejected") == 1
    assert event_types(final).index("reopened") < event_types(final).index("action")


async def test_cancel_deletes_nothing():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    thread = bot.channels[int(case.discord["thread_id"])]
    cancelled = await service.cancel(case.number, reason="test")
    assert cancelled.status == CaseStatus.CANCELLED and cancelled.closed_at == NOW
    assert len(db["cases"].docs) == 1 and "active" not in db["cases"].docs[0]
    assert thread in bot.channels[200].all_threads and len(thread.messages) == 1
    assert thread.messages[0].view.children == [] and thread.locked
    with pytest.raises(CaseRace):
        await service.execute_action(case.number, "ban", member(), lambda current: None)


@pytest.mark.parametrize(
    "change", ["wrong_verb", "wrong_target", "missing_target", "before_guard"]
)
async def test_legacy_pending_recovery_requires_verb_target_and_pending_cutoff(change):
    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    pending_at = NOW + timedelta(seconds=1)
    await service.store.cas(
        case,
        set_fields={
            "pending_action": {
                "token": "legacy-guard",
                "verb": "mute" if change == "wrong_verb" else "ban",
                "at": pending_at,
                "actor": {"discord_id": "101", "name": "Staff"},
            }
        },
    )
    row = {
        **audit(case.number, at=pending_at),
        "target": {"uuid": case.subject.player_uuid},
    }
    if change == "wrong_target":
        row["target"]["uuid"] = "other"
    elif change == "missing_target":
        del row["target"]
    elif change == "before_guard":
        row["occurred_at"] = NOW
    await db["moderation_audit"].insert_one(row)
    assert not await service.recover_action(case.number)
    assert (await service.get(case.number)).pending_action


async def test_legacy_recovery_with_started_verb_cannot_override_another_action():
    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    await service.store.cas(
        case, event={"type": "action_started", "at": NOW, "data": {"verb": "mute"}}
    )
    await db["moderation_audit"].insert_one(audit(case.number))
    assert not await service.recover_action(case.number)
