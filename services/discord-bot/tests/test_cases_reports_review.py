"""Executed regressions for the nine B5 GPT review findings, over real core/helpers."""

from __future__ import annotations

import asyncio
from dataclasses import replace
from datetime import timedelta
from types import SimpleNamespace

import discord
import pytest
from cases_fakes import NOW, Interaction, member
from cases_reports_fakes import (
    BLACKLIST_ROLE,
    REPORTER_IP,
    REPORTER_UUID,
    SECOND_UUID,
    STAFF,
    TARGET_IP,
    TARGET_UUID,
    act,
    from_second,
    moderation,
    move,
    open_report,
    panel,
    report,
    reports_core,
    sanction,
)
from test_cases_reports_external import audit
from test_cases_reports_panel import _RegexPlayers
from xcore_protocol.generated.shared import PlayerRefV1

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.cases.model import (
    AccessDenied,
    ActionFailed,
    CaseKind,
    CaseRace,
    CaseStatus,
    FlowDisabled,
    Party,
)
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.cases.views import CaseActionModal
from xcore_discord_bot.i18n import t
from xcore_discord_bot.moderation_logs import post_ban_log, post_mute_log
from xcore_discord_bot.mongo_store import EXACT_NAME_CANDIDATES, MongoStore


def private(bot, case, owner):
    return bot.channels[int(case.user_threads[str(owner)])]


def delete_thread(bot, thread):
    bot.channels.pop(thread.id)
    thread.parent.all_threads.remove(thread)


def receipt(case, identity, kind="report_rejected"):
    return next(
        r
        for r in case.progress["notification_receipts"]
        if r["key"] == f"{kind}:{identity}"
    )


async def reject(service, case):
    return await service.reject(case.number, member(), reason_code="no_evidence")


async def test_merge_history_and_evidence_redact_every_opaque_case_party():
    service, db, bot = await reports_core()
    case = await open_report(service)
    secrets = (
        REPORTER_UUID,
        TARGET_UUID,
        SECOND_UUID,
        TARGET_IP,
        REPORTER_IP,
        "2001:db8::42",
        "550e8400-e29b-41d4-a716-446655440000",
    )
    raw = "comment " + " ".join(secrets)
    await reports.submitted(service, from_second(comment=raw))
    case = await service.get(case.number)
    assert case.co_openers[0]["payload"]["comment"] == raw
    post = bot.channels[int(case.discord["thread_id"])]
    assert "comment" in post.messages[-1].content
    for secret in secrets:
        assert secret not in post.messages[-1].content
    await audit(db, "WARN", at=NOW - timedelta(minutes=1), actor=raw)
    db["moderation_audit"].docs[-1]["reason"] = raw
    await service.checkpoint(
        case.number,
        set_fields={
            "evidence.chat": [{"name": raw, "text": raw}],
            "subject.player_name": raw,
        },
        unset_fields=("progress.evidence_done", "progress.creation_done"),
    )
    await service.resume_creation(case.number)
    text, _ = await act(service, case, "history")
    outputs = [
        text,
        *(m.content for m in post.messages),
        str(post.messages[0].embeds[0].to_dict()),
    ]
    for output in outputs:
        assert all(secret not in output for secret in secrets)
    assert db["moderation_audit"].docs[-1]["reason"] == raw


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_case_sanction_log_redacts_raw_nickname_reason_and_actor_once(verb):
    service, db, bot = await reports_core()
    case = await open_report(service)
    await reports.submitted(service, from_second())
    raw_name = f"Target {TARGET_IP} {SECOND_UUID}"
    move(service, TARGET_UUID, nickname=raw_name)
    raw_reason = f"reason {REPORTER_UUID} {TARGET_UUID} {SECOND_UUID} {REPORTER_IP}"
    actor_uuid, actor_ip = "ZFn+OcdYThKXkqd/KCsPyA==", "203.0.113.25"
    service.player_store.database["players"].docs.append(
        {"uuid": actor_uuid, "pid": 50, "discord_id": str(STAFF), "ip": actor_ip}
    )
    staff = member()
    staff.display_name = f"Admin {SECOND_UUID} {actor_uuid} {actor_ip}"
    closed, _ = await act(
        service,
        case,
        verb,
        user=staff,
        values={
            "pid": "-5",
            "duration": "1d",
            "reason": raw_reason,
            "confirmed": "yes",
        },
    )
    assert closed.resolution.audit_id
    rows = bot.mutations["bans" if verb == "ban" else "mutes"].docs
    assert (
        len(rows) == 1
        and rows[0]["name"] == raw_name
        and rows[0]["reason"] == raw_reason
    )
    assert rows[0]["uuid"] == TARGET_UUID
    [entry] = [r for r in db["moderation_audit"].docs if r["action"] == verb.upper()]
    assert entry["reason"] == raw_reason and entry["target"]["uuid"] == TARGET_UUID
    assert entry["actor"]["name_snapshot"] == staff.display_name
    assert entry["details"]["extra"]["case_action_token"]
    assert len(bot.log.embeds) == 1
    displayed = str(bot.log.embeds[0].to_dict())
    assert all(
        s not in displayed
        for s in (
            REPORTER_UUID,
            TARGET_UUID,
            SECOND_UUID,
            TARGET_IP,
            REPORTER_IP,
            actor_uuid,
            actor_ip,
        )
    )
    assert "Target" in displayed and "reason" in displayed
    card = bot.channels[int(closed.discord["thread_id"])].messages[0].embeds[0]
    assert actor_uuid not in str(card.to_dict())
    assert (
        next(f.value for f in card.fields if f.name == t("en", "cases-assignee"))
        == f"<@{STAFF}>"
    )
    assert closed.assignee["name"] == staff.display_name


async def test_take_shows_assignee_mention_without_raw_staff_identifier():
    service, _, bot = await reports_core()
    case = await open_report(service)
    staff = member()
    identifier = "ZFn+OcdYThKXkqd/KCsPyA=="
    staff.display_name = f"Admin {identifier}"
    taken = await service.take(case.number, staff)
    card = bot.channels[int(taken.discord["thread_id"])].messages[0].embeds[0]
    assert identifier not in str(card.to_dict())
    assert (
        next(f.value for f in card.fields if f.name == t("en", "cases-assignee"))
        == f"<@{STAFF}>"
    )
    assert taken.assignee["name"] == staff.display_name


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_legacy_noncase_log_preserves_existing_presentation(verb):
    _, _, bot = await reports_core()
    helper = post_ban_log if verb == "ban" else post_mute_log
    raw = f"Legacy {TARGET_UUID} {TARGET_IP}"
    await helper(
        bot,
        pid=-5,
        name=raw,
        admin_name=raw,
        admin_discord_id=None,
        reason=raw,
        expire=NOW,
        **({"ban_id": 31} if verb == "ban" else {}),
    )
    assert raw in str(bot.log.embeds[0].to_dict())


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_unresolved_subject_cannot_sanction_an_already_active_report(verb):
    service, _, bot = await reports_core()
    known = await open_report(service)
    _, unknown = await panel(service, bot, {"who": "unknown", "what": "grief"})
    with pytest.raises(ActionFailed) as refused:
        await sanction(service, unknown, verb)
    assert refused.value.text == t(
        "en", "reports-error-open-report", number=known.number
    )
    current = await service.get(unknown.number)
    assert current.subject is None and current.dedup_key is None
    assert current.pending_action is None and current.actionable
    assert (
        await service.store.by_dedup(CaseKind.REPORT, f"report:{TARGET_UUID}")
    ).number == known.number
    assert not bot.mutations["bans"].docs and not bot.mutations["mutes"].docs


async def test_report_opened_on_the_player_during_a_ban_is_named(monkeypatch):
    service, _, bot = await reports_core()
    _, unknown = await panel(service, bot, {"who": "unknown", "what": "grief"})
    other = await open_report(service)
    lookup = service.store.by_dedup
    calls = []

    async def late(kind, key):
        # Every check before the write misses the other report, as if it opened just after them.
        calls.append(key)
        return await lookup(kind, key) if len(calls) > 3 else None

    monkeypatch.setattr(service.store, "by_dedup", late)
    with pytest.raises(ActionFailed) as refused:
        await sanction(service, unknown, "ban")
    assert len(calls) == 4
    assert refused.value.text == t(
        "en", "reports-error-open-report", number=other.number
    )
    assert not bot.mutations["bans"].docs


async def test_unknown_subject_claims_unique_key_in_guarded_checkpoint_before_mutation(
    monkeypatch,
):
    service, db, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "unknown", "what": "grief"})
    original = bot.store.upsert_mute

    async def checked(**kwargs):
        current = await service.get(case.number)
        assert current.pending_action and current.dedup_key == f"report:{TARGET_UUID}"
        assert current.subject.player_uuid == TARGET_UUID
        assert current.events[-1]["type"] == "subject_set"
        # The real unique index also refuses a competing game submission at this point.
        await reports.submitted(service, from_second())
        assert len(db["cases"].docs) == 1
        return await original(**kwargs)

    monkeypatch.setattr(bot.store, "upsert_mute", checked)
    closed = await sanction(service, case, "mute")
    assert (
        closed.dedup_key == f"report:{TARGET_UUID}"
        and len(bot.mutations["mutes"].docs) == 1
    )


async def test_subject_command_refuses_active_dedup_collision():
    service, _, bot = await reports_core()
    await open_report(service)
    _, unknown = await panel(service, bot, {"who": "unknown", "what": "grief"})
    with pytest.raises(CaseRace):
        await service.set_subject(
            unknown.number, member(102, (20,)), Party(player_uuid=TARGET_UUID)
        )
    assert (await service.get(unknown.number)).subject is None


@pytest.mark.parametrize(
    "uuid",
    [REPORTER_UUID, SECOND_UUID, TARGET_UUID],
    ids=["opener", "co_opener", "subject"],
)
@pytest.mark.parametrize(
    "verb",
    [
        "take",
        "release",
        "reject",
        "ban",
        "mute",
        "history",
        "refresh",
        "reopen",
        "subject",
    ],
)
async def test_current_actor_links_block_every_report_verb_even_for_headstaff(
    uuid, verb
):
    service, _, bot = await reports_core()
    for involved in (REPORTER_UUID, SECOND_UUID, TARGET_UUID):
        move(service, involved, discord_id=None)
    case = await open_report(service)
    await reports.submitted(service, from_second())
    if verb == "release":
        await service.take(case.number, member())
    if verb == "reopen":
        await service.reject(case.number, member(102), reason_code="duplicate")
    move(service, uuid, discord_id=str(STAFF))
    staff = member(STAFF, (10, 20))
    values = {
        "pid": "-5",
        "duration": "1d",
        "reason": "x",
        "reason_code": "abuse",
        "confirmed": "yes",
    }
    with pytest.raises(AccessDenied):
        if verb == "reopen":
            await service.reopen(case.number, staff)
        elif verb == "subject":
            await service.set_subject(
                case.number, staff, Party(player_uuid=TARGET_UUID)
            )
        else:
            await act(service, case, verb, user=staff, values=values)
    assert not bot.mutations["bans"].docs and not bot.mutations["mutes"].docs


@pytest.mark.parametrize("verb", ["ban", "mute", "reject"])
async def test_link_after_io_free_precheck_is_refused_on_real_modal_submit(verb):
    service, _, bot = await reports_core()
    move(service, TARGET_UUID, discord_id=None)
    case = await open_report(service)
    staff = member(STAFF, (10, 20))
    fields = service.precheck(case, staff, verb, "en")
    modal = CaseActionModal(
        service=service,
        number=case.number,
        verb=verb,
        requester_id=STAFF,
        locale="en",
        fields=fields,
    )
    values = {"pid": "-5", "duration": "1d", "reason": "x", "reason_code": "abuse"}
    for name, item in modal.inputs.items():
        if isinstance(item, discord.ui.Select):
            item._values = [values[name]]
        else:
            item._value = values.get(name, "")
    move(service, TARGET_UUID, discord_id=str(STAFF))
    interaction = Interaction(bot, user=staff)
    await modal.on_submit(interaction)
    assert interaction.followup.texts() == [t("en", "error-not-allowed")]
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    assert not bot.mutations["bans"].docs and not bot.mutations["mutes"].docs


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_link_between_modal_and_requester_bound_confirmation_blocks_mutation(
    verb,
):
    service, _, bot = await reports_core()
    move(service, TARGET_UUID, discord_id=None)
    case = await open_report(service)
    _, interaction = await act(
        service, case, verb, values={"pid": "-5", "duration": "1d", "reason": "x"}
    )
    view = interaction.events[-1][2]["view"]
    move(service, TARGET_UUID, discord_id=str(STAFF))
    confirmation = Interaction(bot)
    await view.children[0].callback(confirmation)
    assert confirmation.followup.texts() == [t("en", "error-not-allowed")]
    assert not bot.mutations["bans"].docs and not bot.mutations["mutes"].docs


@pytest.mark.parametrize("known", [False, True])
async def test_current_link_is_checked_after_fresh_subject_under_guard(
    monkeypatch, known
):
    service, _, bot = await reports_core()
    move(service, TARGET_UUID, discord_id=None)
    if known:
        case = await open_report(service)
    else:
        _, case = await panel(service, bot, {"who": "unknown", "what": "grief"})
    lookup = service.player_store.find_player_by_pid

    async def linking(pid):
        if (await service.get(case.number)).pending_action:
            move(service, TARGET_UUID, discord_id=str(STAFF))
        return await lookup(pid)

    monkeypatch.setattr(service.player_store, "find_player_by_pid", linking)
    with pytest.raises(AccessDenied):
        await sanction(service, case, "mute")
    assert not bot.mutations["mutes"].docs
    assert (await service.get(case.number)).pending_action is None


async def test_offline_deleted_reporter_thread_is_repaired_without_gateway_event():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "me too"})
    old = private(bot, await service.get(case.number), 301)
    delete_thread(bot, old)
    await reconcile_cases(service)
    current = await service.get(case.number)
    thread = private(bot, current, 301)
    assert thread.id != old.id and thread.members == {301}
    assert [m.content for m in thread.messages] == [
        t("en", "cases-user-thread-note", number=case.number)
    ]
    closed = await reject(service, case)
    assert closed.progress["closed_done"] and receipt(closed, "301")["thread"]


async def test_deleted_closed_thread_repair_preserves_previous_successful_dm_receipt():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "-5", "what": "grief"})
    closed = await reject(service, case)
    old = private(bot, closed, 301)
    delete_thread(bot, old)
    # No gateway event; even already completed terminal cases with owned threads are checked.
    await reconcile_cases(service)
    current = await service.get(case.number)
    new = private(bot, current, 301)
    assert new.id != old.id and new.archived and new.locked
    assert new.messages[-1].content == bot.users[301].messages[0]
    assert len(bot.users[301].messages) == 1
    assert receipt(current, "301")["dm"] and receipt(current, "301")["thread"]
    assert current.progress["closed_done"]


async def test_deleted_thread_receipt_is_reset_for_game_reporter_linked_after_filing():
    service, _, bot = await reports_core()
    move(service, REPORTER_UUID, discord_id=None)
    case = await open_report(service)
    move(service, REPORTER_UUID, discord_id="301")
    await panel(service, bot, {"who": "-5", "what": "from Discord too"})
    closed = await reject(service, case)
    assert (
        receipt(closed, REPORTER_UUID)["thread"] and len(bot.users[301].messages) == 1
    )
    old = private(bot, closed, 301)
    delete_thread(bot, old)
    await reconcile_cases(service)
    repaired = await service.get(case.number)
    new = private(bot, repaired, 301)
    assert new.id != old.id and new.messages[-1].content == bot.users[301].messages[0]
    assert (
        receipt(repaired, REPORTER_UUID)["thread"] and repaired.progress["closed_done"]
    )
    assert len(bot.users[301].messages) == 1


async def test_failed_thread_fetch_keeps_work_pending_and_later_repairs_after_a_day(
    monkeypatch,
):
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "-5", "what": "grief"})
    old = private(bot, case, 301)
    delete_thread(bot, old)
    fetch = bot.fetch_channel

    async def unavailable(id):
        if id == old.id:
            raise discord.HTTPException(
                SimpleNamespace(status=503, reason="outage"), "fetch unavailable"
            )
        return await fetch(id)

    monkeypatch.setattr(bot, "fetch_channel", unavailable)
    closed = await reject(service, case)
    assert not closed.progress.get("outcomes_done") and not closed.progress.get(
        "closed_done"
    )
    assert receipt(closed, "301")["dm"] and not receipt(closed, "301").get("thread")
    service.now = lambda: NOW + timedelta(days=2)
    await reconcile_cases(service)
    assert not (await service.get(case.number)).progress.get("closed_done")
    monkeypatch.setattr(bot, "fetch_channel", fetch)
    await reconcile_cases(service)
    repaired = await service.get(case.number)
    assert repaired.progress["closed_done"] and private(bot, repaired, 301).id != old.id
    assert len(bot.users[301].messages) == 1


async def test_panel_reporter_merged_during_real_action_gets_thread_before_close(
    monkeypatch,
):
    service, _, bot = await reports_core()
    case = await open_report(service)
    started, proceed = asyncio.Event(), asyncio.Event()
    upsert = bot.store.upsert_mute

    async def blocked(**kwargs):
        assert (await service.get(case.number)).pending_action
        started.set()
        await proceed.wait()
        return await upsert(**kwargs)

    monkeypatch.setattr(bot.store, "upsert_mute", blocked)
    task = asyncio.create_task(sanction(service, case, "mute"))
    await asyncio.wait_for(started.wait(), 2)
    try:
        _, merged = await panel(service, bot, {"who": "-5", "what": "me too"})
        assert merged.number == case.number and not merged.user_threads
    finally:
        proceed.set()
    closed = await asyncio.wait_for(task, 2)
    thread = private(bot, closed, 301)
    assert thread.members == {301, STAFF} and thread.archived and thread.locked
    assert thread.messages[-1].content == t(
        "en", "reports-outcome-action", number=case.number
    )
    assert bot.users[301].messages == [thread.messages[-1].content]
    assert closed.progress["closed_done"] and closed.progress["outcomes_done"]
    assert "discord:700" in closed.progress["merged_submissions"]


async def test_broken_first_reporter_thread_does_not_block_their_dm_or_later_reporter(
    monkeypatch,
):
    service, db, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "first"})
    move(service, SECOND_UUID, discord_id="302")
    await panel(
        service, bot, {"who": "-5", "what": "second"}, user=member(302, ()), id=710
    )
    thread = private(bot, await service.get(case.number), 301)
    send = thread.send

    async def failed(*args, **kwargs):
        raise discord.HTTPException(
            SimpleNamespace(status=503, reason="outage"), "send unavailable"
        )

    monkeypatch.setattr(thread, "send", failed)
    closed = await reject(service, case)
    assert (
        bot.users[301].messages and bot.users[302].messages and bot.users[201].messages
    )
    assert receipt(closed, SECOND_UUID)["thread"] and receipt(closed, "301")["dm"]
    assert not receipt(closed, "301").get("thread") and not closed.progress.get(
        "outcomes_done"
    )
    assert {
        r["player_uuid"]
        for r in db["case_notices"].docs
        if r["kind"] == "report_rejected"
    } == {REPORTER_UUID, SECOND_UUID}
    monkeypatch.setattr(thread, "send", send)
    await reconcile_cases(service)
    repaired = await service.get(case.number)
    assert repaired.progress["closed_done"] and receipt(repaired, "301")["thread"]
    assert len(bot.users[301].messages) == len(bot.users[302].messages) == 1


async def test_failed_owned_thread_creation_still_sends_dm_and_remains_repairable():
    service, _, bot = await reports_core()
    case = await open_report(service)
    bot.failures.arm("user_create")
    await panel(service, bot, {"who": "-5", "what": "first"})
    bot.failures.arm("user_create")
    closed = await reject(service, case)
    assert bot.users[301].messages and not closed.progress.get("closed_done")
    assert not receipt(closed, "301").get("thread")
    await reconcile_cases(service)
    repaired = await service.get(case.number)
    assert (
        repaired.progress["closed_done"]
        and private(bot, repaired, 301).messages[-1].content
        == bot.users[301].messages[0]
    )


async def test_legacy_terminal_owed_thread_repairs_false_completion_without_duplicate_dm():
    service, _, bot = await reports_core()
    case = await open_report(service)
    bot.failures.arm("user_create")
    await panel(service, bot, {"who": "-5", "what": "first"})
    bot.failures.arm("user_create")
    closed = await reject(service, case)
    assert not closed.user_threads and bot.users[301].messages
    await service.checkpoint(
        case.number,
        set_fields={"progress.outcomes_done": True, "progress.closed_done": True},
    )
    await reconcile_cases(service)
    repaired = await service.get(case.number)
    thread = private(bot, repaired, 301)
    assert thread.messages[-1].content == bot.users[301].messages[0]
    assert receipt(repaired, "301")["thread"] and repaired.progress["closed_done"]
    assert len(bot.users[301].messages) == 1


async def test_legacy_case_wide_invitations_are_removed_on_reopen():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "-5", "what": "grief"})
    await service.take(case.number, member())
    await reject(service, case)
    # Simulate the pre-corrections layout before any managed-membership migration.
    await service.checkpoint(
        case.number,
        set_fields={"progress.user_thread_staff": [str(STAFF)]},
        unset_fields=("discord.managed_memberships",),
    )
    await service.reopen(case.number, member(103, (20,)))
    assert private(bot, await service.get(case.number), 301).members == {301}


async def test_managed_memberships_release_reassign_role_loss_and_reopen_preserve_owners():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "one"}, user=member(301, (10,)))
    await panel(
        service, bot, {"who": "-5", "what": "two"}, user=member(302, ()), id=710
    )
    await service.take(case.number, member())
    current = await service.get(case.number)
    assert private(bot, current, 301).members == {301, STAFF}
    assert private(bot, current, 302).members == {302, STAFF}
    await service.release(case.number, member())
    current = await service.get(case.number)
    assert private(bot, current, 301).members == {301} and private(
        bot, current, 302
    ).members == {302}
    await service.take(case.number, member(102))
    bot.guild.roles_by_member[102] = ()
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert private(bot, current, 301).members == {301} and private(
        bot, current, 302
    ).members == {302}
    # Restored authorization can restore only the current assignee.
    bot.guild.roles_by_member[102] = (10,)
    await reconcile_cases(service)
    await service.reject(case.number, member(102), reason_code="no_evidence")
    await service.reopen(case.number, member(103, (20,)))
    current = await service.get(case.number)
    assert current.assignee is None
    assert private(bot, current, 301).members == {301} and private(
        bot, current, 302
    ).members == {302}
    assert all(not r["staff_ids"] for r in current.discord["managed_memberships"])


async def test_owner_who_was_also_assignee_is_never_removed_as_obsolete_staff():
    service, _, bot = await reports_core()
    _, case = await panel(
        service, bot, {"who": "-5", "what": "grief"}, user=member(301, (10,))
    )
    # A legacy inconsistent record can list an owner as invited staff; removal preserves owner.
    await service.checkpoint(
        case.number, set_fields={"progress.user_thread_staff": ["301"]}
    )
    await service.project(case.number)
    assert private(bot, await service.get(case.number), 301).members == {301}


async def test_lost_staff_add_ack_keeps_removal_intent_durable_for_role_loss():
    service, _, bot = await reports_core()
    _, case = await panel(service, bot, {"who": "-5", "what": "grief"})
    bot.failures.arm("user_add", after=True)
    await service.take(case.number, member())
    current = await service.get(case.number)
    [intent] = current.discord["managed_memberships"]
    assert intent["staff_ids"] == [str(STAFF)] and not intent["done"]
    assert private(bot, current, 301).members == {301, STAFF}
    bot.guild.roles_by_member[STAFF] = ()
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert private(bot, current, 301).members == {301}
    assert current.discord["managed_memberships"][0]["staff_ids"] == []


async def test_failed_role_lookup_removes_managed_staff_and_never_invites_linked_subject(
    monkeypatch,
):
    service, _, bot = await reports_core()
    case = await open_report(service)
    await panel(service, bot, {"who": "-5", "what": "grief"})
    await service.take(case.number, member())
    bot.guild.roles_by_member.pop(STAFF)

    async def unavailable(id):
        raise discord.HTTPException(
            SimpleNamespace(status=503, reason="outage"), "member unavailable"
        )

    fetch = bot.guild.fetch_member
    monkeypatch.setattr(bot.guild, "fetch_member", unavailable)
    await reconcile_cases(service)
    assert private(bot, await service.get(case.number), 301).members == {301}
    monkeypatch.setattr(bot.guild, "fetch_member", fetch)
    bot.guild.roles_by_member[STAFF] = (10,)
    move(service, TARGET_UUID, discord_id=str(STAFF))
    await reconcile_cases(service)
    assert private(bot, await service.get(case.number), 301).members == {301}


@pytest.mark.parametrize("reason", ["cooldown", "burst", "blocked"])
async def test_refusal_notice_failure_does_not_change_decision_after_windows_expire(
    monkeypatch, reason
):
    service, db, bot = await reports_core(
        cases_report_burst_limit=1 if reason == "burst" else 20,
        cases_blacklist_role_id=BLACKLIST_ROLE,
    )
    await open_report(service)
    service.now = lambda: NOW + timedelta(seconds=59)
    denied = report("denied", target=PlayerRefV1("other", "Other", 9))
    if reason == "burst":
        denied = from_second("denied", target=denied.target)
    if reason == "blocked":
        bot.guild.roles_by_member[201] = (BLACKLIST_ROLE,)
    insert = service.store.insert_notice

    async def failure(row):
        if row["_id"] == "refused:denied":
            raise RuntimeError("notice insertion failed")
        return await insert(row)

    monkeypatch.setattr(service.store, "insert_notice", failure)
    with pytest.raises(RuntimeError):
        await reports.submitted(service, denied)
    decision = await db[reports.LIMITS].find_one({"_id": "decision:denied"})
    assert decision["reason"] == reason
    service.now = lambda: NOW + timedelta(seconds=75 if reason == "cooldown" else 601)
    bot.guild.roles_by_member[201] = ()
    monkeypatch.setattr(service.store, "insert_notice", insert)
    await reports.submitted(service, denied)
    assert len(db["cases"].docs) == 1
    assert (await db["case_notices"].find_one({"_id": "refused:denied"}))["params"] == {
        "reason": reason
    }


@pytest.mark.parametrize("after_ack", [False, True])
async def test_failed_decision_promotion_retains_atomic_denial_witness_before_next_submission(
    monkeypatch, after_ack
):
    service, db, _ = await reports_core()
    await open_report(service)
    service.now = lambda: NOW + timedelta(seconds=59)
    limits = db[reports.LIMITS]
    update = limits.update_one

    async def uncertain(query, changes, **kwargs):
        if query["_id"] == "decision:denied":
            if after_ack:
                await update(query, changes, **kwargs)
            raise RuntimeError("decision write acknowledgement unknown")
        return await update(query, changes, **kwargs)

    monkeypatch.setattr(limits, "update_one", uncertain)
    denied = report("denied", target=PlayerRefV1("other", "Other", 9))
    with pytest.raises(RuntimeError):
        await reports.submitted(service, denied)
    row = await limits.find_one({"_id": f"cooldown:{REPORTER_UUID}"})
    assert (
        row["decision"]["submission_id"] == "denied"
        and row["decision"]["reason"] == "cooldown"
    )
    service.now = lambda: NOW + timedelta(seconds=75)
    # Another arrival cannot overwrite the witness before its decision is acknowledged.
    with pytest.raises(RuntimeError):
        await reports.submitted(
            service, report("later", target=PlayerRefV1("later", "Later", 10))
        )
    assert (await limits.find_one({"_id": row["_id"]}))["decision"] == row["decision"]
    monkeypatch.setattr(limits, "update_one", update)
    await reports.submitted(
        service, report("later", target=PlayerRefV1("later", "Later", 10))
    )
    await reports.submitted(service, denied)
    assert (await limits.find_one({"_id": "decision:denied"}))["reason"] == "cooldown"
    assert await service.store.by_submission("denied") is None


async def test_lost_cooldown_cas_ack_recovers_admission_without_rechecking_changed_blacklist(
    monkeypatch,
):
    service, db, bot = await reports_core(cases_blacklist_role_id=BLACKLIST_ROLE)
    limits = db[reports.LIMITS]
    cas = limits.find_one_and_update

    async def uncertain(query, changes, **kwargs):
        row = await cas(query, changes, **kwargs)
        if query["_id"].startswith("cooldown:"):
            raise RuntimeError("CAS applied, acknowledgement lost")
        return row

    monkeypatch.setattr(limits, "find_one_and_update", uncertain)
    with pytest.raises(RuntimeError):
        await reports.submitted(service, report())
    bot.guild.roles_by_member[201] = (BLACKLIST_ROLE,)
    service.now = lambda: NOW + timedelta(seconds=75)
    monkeypatch.setattr(limits, "find_one_and_update", cas)
    await reports.submitted(service, report())
    assert len(db["cases"].docs) == 1
    assert not [
        r for r in db["case_notices"].docs if r["kind"] == "report_not_accepted"
    ]


async def test_disabled_direct_intake_does_not_read_or_write_storage(monkeypatch):
    service, db, _ = await reports_core(enabled=False)

    async def unexpected(*args, **kwargs):
        pytest.fail("Disabled intake must not touch storage")

    monkeypatch.setattr(service.store, "by_submission", unexpected)
    with pytest.raises(FlowDisabled):
        await reports.submitted(service, report())
    assert not db[reports.LIMITS].docs


async def test_storage_blackout_leaves_intake_pending_without_reads(monkeypatch):
    service, db, _ = await reports_core()
    service.storage_available = False

    async def unexpected(*args, **kwargs):
        pytest.fail("Unavailable storage must not be queried by intake")

    monkeypatch.setattr(service.store, "by_submission", unexpected)
    with pytest.raises(RuntimeError, match="remains pending"):
        await reports.submitted(service, report())
    assert not db[reports.LIMITS].docs and not db["case_notices"].docs


async def test_first_recipient_dm_failure_does_not_block_later_notices_threads_or_dms():
    service, db, bot = await reports_core()
    case = await open_report(service)
    move(service, SECOND_UUID, discord_id="302")
    await panel(service, bot, {"who": "-5", "what": "second"}, user=member(302, ()))
    bot.failures.arm("dm")
    closed = await reject(service, case)
    assert bot.users[302].messages and receipt(closed, SECOND_UUID)["thread"]
    assert not receipt(closed, REPORTER_UUID).get("dm") and not closed.progress.get(
        "outcomes_done"
    )
    assert {
        r["player_uuid"]
        for r in db["case_notices"].docs
        if r["kind"] == "report_rejected"
    } == {REPORTER_UUID, SECOND_UUID}
    await reconcile_cases(service)
    assert (await service.get(case.number)).progress["closed_done"]
    assert len(bot.users[302].messages) == 1 and len(bot.users[201].messages) == 1


async def test_first_recipient_notice_failure_does_not_block_dm_or_later_recipient(
    monkeypatch,
):
    service, _, bot = await reports_core()
    case = await open_report(service)
    move(service, SECOND_UUID, discord_id="302")
    await panel(service, bot, {"who": "-5", "what": "second"}, user=member(302, ()))
    insert = service.store.insert_notice

    async def failing(row):
        if row["_id"] == f"{case.number}:{REPORTER_UUID}:report_rejected":
            raise RuntimeError("first notice failed")
        return await insert(row)

    monkeypatch.setattr(service.store, "insert_notice", failing)
    closed = await reject(service, case)
    assert bot.users[201].messages and bot.users[302].messages
    assert receipt(closed, REPORTER_UUID)["dm"] and not receipt(
        closed, REPORTER_UUID
    ).get("notice")
    assert (
        receipt(closed, SECOND_UUID)["notice"]
        and receipt(closed, SECOND_UUID)["thread"]
    )
    assert not closed.progress.get("closed_done")
    monkeypatch.setattr(service.store, "insert_notice", insert)
    await reconcile_cases(service)
    assert (await service.get(case.number)).progress["closed_done"]
    assert len(bot.users[201].messages) == len(bot.users[302].messages) == 1


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_actual_base64_party_identifiers_are_redacted_in_case_logs_and_confirmation(
    verb,
):
    service, db, bot = await reports_core()
    ids = (
        "L8/JxAFpSGOOu6kD8WwyVA==",
        "QN+LpSqDSQehxjwAgP0veA==",
        "Bw/oPEzyQ5G+GdT4EYmqWQ==",
    )
    for row, uuid in zip(
        service.player_store.database["players"].docs, ids, strict=True
    ):
        row["uuid"] = uuid
    first = report(
        reporter=PlayerRefV1(ids[0], "Reporter", 0),
        target=PlayerRefV1(ids[1], "Target", -5),
    )
    await reports.submitted(service, first)
    await reports.submitted(
        service,
        report(
            "second",
            reporter=PlayerRefV1(ids[2], "Second", 7),
            target=first.target,
            comment="all " + " ".join(ids),
        ),
    )
    case = await service.store.by_submission(first.submissionId)
    service.player_store.database["players"].docs[1]["nickname"] = "Target " + ids[0]
    values = {"pid": "-5", "duration": "1d", "reason": "raw " + " ".join(ids)}
    _, asked = await act(service, case, verb, values=values)
    assert all(uuid not in " ".join(asked.followup.texts()) for uuid in ids)
    closed, _ = await act(service, case, verb, values={**values, "confirmed": "yes"})
    assert closed.resolution.audit_id and len(bot.log.embeds) == 1
    assert all(uuid not in str(bot.log.embeds[0].to_dict()) for uuid in ids)
    assert db["moderation_audit"].docs[-1]["reason"] == values["reason"]


@pytest.mark.parametrize("verb", ["ban", "mute"])
@pytest.mark.parametrize("unknown", [None, "not-a-date", "2026-10-14T01:00:00"])
@pytest.mark.parametrize("reopened", [False, True])
async def test_unknown_external_time_never_uses_processing_time(
    verb, unknown, reopened
):
    service, db, _ = await reports_core()
    case = await open_report(service)
    await audit(db, verb.upper(), at=NOW - timedelta(minutes=1))
    if reopened:
        await reject(service, case)
        service.now = lambda: NOW + timedelta(minutes=5)
        case = await service.reopen(case.number, member(103, (20,)))
        await audit(db, verb.upper(), at=NOW + timedelta(minutes=1))
    service.now = lambda: NOW + timedelta(hours=1)
    await reports.sanctioned(service, replace(moderation(verb), occurredAt=unknown))
    assert (await service.get(case.number)).status == CaseStatus.OPEN


@pytest.mark.parametrize("verb", ["ban", "mute"])
async def test_unknown_external_time_can_close_only_from_matching_fresh_audit(verb):
    service, db, _ = await reports_core()
    case = await open_report(service)
    await audit(db, "MUTE" if verb == "ban" else "BAN", at=NOW + timedelta(minutes=1))
    event = replace(moderation(verb), occurredAt=None)
    await reports.sanctioned(service, event)
    assert (await service.get(case.number)).actionable
    await audit(db, verb.upper(), at=NOW + timedelta(minutes=2), uuid=SECOND_UUID)
    await reports.sanctioned(service, event)
    assert (await service.get(case.number)).actionable
    await audit(db, verb.upper(), at=NOW + timedelta(minutes=3))
    if verb == "ban":
        # Event B-31 cannot be evidenced by an unrelated B-44 audit.
        await reports.sanctioned(service, event)
        assert (await service.get(case.number)).actionable
        db["moderation_audit"].docs[-1]["details"]["extra"]["ban_id"] = "31"
    await reports.sanctioned(service, event)
    closed = await service.get(case.number)
    assert closed.resolution.audit_id == db["moderation_audit"].docs[-1]["audit_id"]
    assert closed.resolution.at == NOW + timedelta(minutes=3)


@pytest.mark.parametrize("path", ["event", "audit"])
@pytest.mark.parametrize("race", ["pause", "cas"])
async def test_external_close_cannot_cross_reject_reopen_revision_boundary(
    monkeypatch, path, race
):
    service, db, _ = await reports_core()
    case = await open_report(service)
    if path == "audit":
        await audit(db, "BAN", at=NOW + timedelta(minutes=1))
    interleaved = False

    async def reopen_boundary():
        nonlocal interleaved
        interleaved = True
        await reject(service, case)
        service.now = lambda: NOW + timedelta(minutes=5)
        await service.reopen(case.number, member(103, (20,)))

    if race == "pause":
        close = service.close_validated

        async def paused(validated, resolution):
            if not interleaved:
                await reopen_boundary()
            return await close(validated, resolution)

        monkeypatch.setattr(service, "close_validated", paused)
    else:
        cas = service.store.cas

        async def raced(validated, **kwargs):
            fields = kwargs.get("set_fields") or {}
            if fields.get("status") == CaseStatus.RESOLVED and not interleaved:
                await reopen_boundary()
            return await cas(validated, **kwargs)

        monkeypatch.setattr(service.store, "cas", raced)
    with pytest.raises(CaseRace):
        if path == "event":
            await reports.sanctioned(service, moderation())
        else:
            await reports.reconcile(service, case)
    current = await service.get(case.number)
    assert (
        interleaved and current.status == CaseStatus.OPEN and current.resolution is None
    )
    assert current.pending_action is None
    # A later retry must revalidate, and cannot close the reopened report with the old sanction.
    await reports.sanctioned(service, moderation())
    await reports.reconcile(service, await service.get(case.number))
    assert (await service.get(case.number)).status == CaseStatus.OPEN


@pytest.mark.parametrize("path", ["event", "audit"])
async def test_external_close_preserves_competing_pending_attempt_guard(
    monkeypatch, path
):
    service, db, _ = await reports_core()
    case = await open_report(service)
    await audit(db, "BAN", at=NOW + timedelta(minutes=1))
    close = service.close_validated

    async def guarded(validated, resolution):
        await service.checkpoint(
            case.number,
            set_fields={
                "pending_action": {
                    "token": "competing-attempt",
                    "verb": "mute",
                    "at": NOW,
                    "actor": {},
                }
            },
        )
        return await close(validated, resolution)

    monkeypatch.setattr(service, "close_validated", guarded)
    with pytest.raises(CaseRace):
        if path == "event":
            await reports.sanctioned(service, moderation())
        else:
            await reports.reconcile(service, case)
    current = await service.get(case.number)
    assert current.actionable and current.pending_action["token"] == "competing-attempt"


@pytest.mark.parametrize("count,expected", [(9, []), (8, ["bob"]), (0, ["bob"])])
async def test_exact_name_saturation_never_proves_unique_after_false_positive_filter(
    count, expected
):
    rows = [
        {"uuid": f"vip-{n}", "pid": n, "nickname": "[VIP]Bob"} for n in range(count)
    ]
    rows += [{"uuid": "bob", "pid": -5, "nickname": "Bob"}]
    if count == 9:
        rows += [{"uuid": "coloured-bob", "pid": 0, "nickname": "[red]Bob"}]
    collection = _RegexPlayers(rows)
    store = MongoStore(SimpleNamespace())
    store._db = {"players": collection}
    found = await store.find_players_by_exact_name("Bob")
    assert [p.uuid for p in found] == expected
    assert collection.limits == [EXACT_NAME_CANDIDATES]
    if found:
        assert found[0].pid == -5


async def test_saturated_ambiguous_name_still_returns_two_candidates_and_panel_is_unresolved():
    rows = [{"uuid": f"bob-{n}", "pid": n, "nickname": "[red]Bob"} for n in range(11)]
    store = MongoStore(SimpleNamespace())
    store._db = {"players": _RegexPlayers(rows)}
    assert len(await store.find_players_by_exact_name("Bob")) == 2
    service, _, bot = await reports_core()
    service.player_store.find_players_by_exact_name = store.find_players_by_exact_name
    _, case = await panel(service, bot, {"who": "Bob", "what": "grief"})
    assert case.subject is None and reports.UNRESOLVED in case.flags
