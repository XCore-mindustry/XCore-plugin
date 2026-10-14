"""Executed B9 review regressions over real core/cog/UI with local services only."""

from __future__ import annotations

import asyncio
import json
import logging
from copy import deepcopy
from dataclasses import replace
from types import MethodType, SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from cases_complaints_fakes import (
    act,
    audit,
    complaint_core,
    form,
    opened,
    player,
    submit,
)
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import Interaction, core, member
from discord import app_commands

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.cases.cards import safe_text
from xcore_discord_bot.cases.flows import complaints
from xcore_discord_bot.cases.model import (
    AccessDenied,
    CaseKind,
    Origin,
    Party,
)
from xcore_discord_bot.cases.reconcile import reconcile_case, reconcile_cases
from xcore_discord_bot.cases.views import CaseActionButton
from xcore_discord_bot.cogs.cases import CasesCog

OPAQUE = "Sj9nK+MdQ/8hY2Gpwq6lTA=="
ACTOR_UUID = "lBNM+4/nAiKPxvHZG3TgWQ=="
IP4 = "203.0.113.9"
IP6 = "2001:db8::42"
HEX_UUID = "91da46c5-89a4-45d8-828b-e258d211dc2f"
RAW = f"RAW PRIVATE COMPLAINT {OPAQUE} {ACTOR_UUID} {IP4} {IP6} {HEX_UUID}"


def post_of(bot, case):
    return bot.channels[int(case.discord["thread_id"])]


def visible(post):
    return post.name + json.dumps(
        [(m.content, [e.to_dict() for e in m.embeds]) for m in post.messages]
    )


def assert_private(text, *extra):
    for secret in (OPAQUE, ACTOR_UUID, IP4, IP6, HEX_UUID, *extra):
        assert secret not in text


@pytest.mark.parametrize("accounts", [1, 2])
async def test_projection_redacts_actual_title_stale_reply_and_private_message_announcement(
    accounts,
):
    linked = [player(n) for n in range(1, accounts + 1)]
    linked[0].uuid = OPAQUE
    for p in linked:
        p.nickname = f"Player {p.uuid} {IP4} {IP6} {HEX_UUID}"
    service, _, bot = await complaint_core(players=linked)
    author = member(42, ())
    author.display_name = (
        "Complainant " + " ".join(p.uuid for p in linked) + f" {IP4} {IP6}"
    )
    await submit(
        bot,
        await form(bot, user=author),
        user=author,
        statement=f"Details {OPAQUE} {IP4} {IP6} {HEX_UUID}",
    )
    case = await service.store.by_submission("discord:700")
    assert_private(visible(post_of(bot, case)), *[p.uuid for p in linked])
    thread = bot.channels[int(case.discord["user_thread_id"])]
    message = SimpleNamespace(
        author=author,
        channel=thread,
        jump_url="https://discord.com/channels/123/456/789",
    )
    await service.threads.announce_user_message(service, message)
    assert (
        "https://discord.com/channels/123/456/789"
        in post_of(bot, case).messages[-1].content
    )
    assert_private(post_of(bot, case).messages[-1].content, *[p.uuid for p in linked])
    senior = member(101, (20,))
    senior.display_name = f"Senior {OPAQUE} {IP4} {IP6} {HEX_UUID}"
    case = await act(service, case, "take", user=senior)
    click = Interaction(bot, user=member(102, (20,)))
    await CaseActionButton(case.number, "take").callback(click)
    assert any(
        "Senior" in e[1] for e in click.events if e[0] in {"message", "followup"}
    )
    assert_private(str(click.events))
    case = await act(service, case, "resolve", values={"note": "done"}, user=senior)
    click = Interaction(bot, user=member(102, (20,)))
    await CaseActionButton(case.number, "resolve").callback(click)
    assert_private(str(click.events))
    # Stored snapshots are untouched by output privacy.
    assert case.opener.discord_name == author.display_name
    assert case.resolution.by_name == senior.display_name
    assert case.payload["statement"].startswith("Details ")


async def test_projection_redacts_available_audit_actor_uuid_in_all_rows_and_followups(
    caplog,
):
    linked = player()
    linked.uuid = OPAQUE
    row = audit(
        target=OPAQUE,
        reason=RAW,
        actor={"discord_id": "99", "player_uuid": ACTOR_UUID, "name_snapshot": RAW},
    )
    rows = [row, audit(index=2, target=OPAQUE, reason=f"cross-row {ACTOR_UUID}")]
    original = deepcopy(rows)
    service, db, bot = await complaint_core(players=[linked], audits=rows)
    caplog.set_level(logging.DEBUG)
    case = await opened(service, bot, statement=RAW)
    assert ACTOR_UUID not in json.dumps(case.evidence["sanctions"]["last"], default=str)
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99", discord_name=RAW)
    )
    assert_private(visible(post_of(bot, case)))
    assert_private(json.dumps(case.evidence["subject_actions"]["last"], default=str))
    assert db["moderation_audit"].docs == original
    assert case.payload["statement"] == RAW and case.subject.discord_name == RAW
    assert_private(caplog.text)
    assert not any(record.exc_info for record in caplog.records)


def install_global_handler(bot, settings):
    bot._settings = settings
    bot._handle_app_command_error = MethodType(
        XCoreDiscordBot._handle_app_command_error, bot
    )
    bot._format_interaction_command = XCoreDiscordBot._format_interaction_command

    async def resolve(channel_id, *, context):
        assert context == "error logs"
        return bot.channels[channel_id]

    bot._resolve_messageable_channel = resolve


def slash_interaction(bot, command, number):
    user = member(101, (20,))
    user.display_name = RAW
    interaction = Interaction(bot, user=user)
    interaction.command = command
    interaction.namespace = SimpleNamespace(number=number, user=RAW)
    interaction.extras = {}
    interaction.response.is_done = lambda: bool(interaction.events)
    return interaction


@pytest.mark.parametrize(
    "verb,stage",
    [
        (verb, stage)
        for verb in ("subject", "find", "reopen")
        for stage in (
            "read",
            "cas" if verb != "find" else "authorize",
            "projection" if verb != "find" else "reply",
        )
    ]
    + [("subject", "begin"), ("subject", "persistent_read")],
)
async def test_slash_unexpected_failure_uses_real_global_private_boundary(
    verb, stage, caplog
):
    service, db, bot = await complaint_core(discord_error_log_channel_id=100)
    case = await opened(service, bot)
    if verb == "reopen":
        case = await act(service, case, "resolve", values={"note": "done"})
    install_global_handler(bot, service.settings)
    command = getattr(CasesCog, verb)
    interaction = slash_interaction(bot, command, str(case.number))
    original_get = service.get
    if stage in {"read", "persistent_read"}:
        # An unrelated cached kind must never allow unsafe diagnostics when Mongo fails.
        service.store._active[case.number] = replace(case, kind=CaseKind.REPORT)
        calls = 0

        async def fail_read(number):
            nonlocal calls
            calls += 1
            if calls == 1 or stage == "persistent_read":
                raise RuntimeError(RAW)
            return await original_get(number)

        service.get = fail_read
    elif stage == "cas":
        service.store.cas = AsyncMock(side_effect=RuntimeError(RAW))
    elif stage == "authorize":
        service.authorize = AsyncMock(side_effect=RuntimeError(RAW))
    elif stage == "begin":
        service.store.remember_interaction = AsyncMock(side_effect=RuntimeError(RAW))
    elif stage == "reply":
        send = interaction.followup.send
        calls = 0

        async def fail_reply(content, **kwargs):
            nonlocal calls
            calls += 1
            if calls == 1:
                raise RuntimeError(RAW)
            await send(content, **kwargs)

        interaction.followup.send = fail_reply
    elif stage == "projection":
        # Fail the real projection's *later read*, outside its internal safe catch.
        project = service.project if verb == "subject" else service.resume_creation

        async def fail_projection_read(number):
            async def fail_once_read(number):
                service.get = original_get
                raise RuntimeError(RAW)

            service.get = fail_once_read
            return await project(number)

        if verb == "subject":
            service.project = fail_projection_read
        else:
            service.resume_creation = fail_projection_read
    caplog.set_level(logging.ERROR)
    args = [CasesCog(bot), interaction, str(case.number)]
    if verb == "subject":
        args.append(member(99, (20,)))
    await command.callback(*args)
    assert caplog.records and all(r.exc_info is None for r in caplog.records)
    assert "RAW PRIVATE" not in caplog.text
    assert_private(caplog.text)
    texts = [e[1] for e in interaction.events if e[0] in {"message", "followup"}]
    assert len(texts) == 1 and texts[0].startswith("Command failed (ref: ")
    assert_private(str(texts))
    embeds = [e.to_dict() for m in bot.channels[100].messages for e in m.embeds]
    assert len(embeds) == 1
    assert_private(json.dumps(embeds))
    assert "RAW PRIVATE" not in json.dumps(embeds)
    assert not db["case_notices"].docs or verb == "reopen"


async def test_global_private_boundary_without_cog_marker_and_safe_delivery_failure(
    caplog,
):
    service, _, bot = await complaint_core(discord_error_log_channel_id=100)
    case = await opened(service, bot)
    install_global_handler(bot, service.settings)
    interaction = slash_interaction(bot, CasesCog.subject, str(case.number))
    bot.channels[100].send = AsyncMock(side_effect=RuntimeError(RAW))
    caplog.set_level(logging.ERROR)
    await bot._handle_app_command_error(
        interaction,
        app_commands.CommandInvokeError(CasesCog.subject, RuntimeError(RAW)),
    )
    assert len(caplog.records) == 2 and all(r.exc_info is None for r in caplog.records)
    assert_private(caplog.text)
    assert "RAW PRIVATE" not in caplog.text


async def test_known_noncomplaint_slash_diagnostics_and_other_flow_projection_are_preserved(
    caplog,
):
    service, _, bot = await core(
        flow=DUMMY_FLOW, cases_reports_enabled=True, discord_error_log_channel_id=100
    )
    case = await service.open(
        kind=CaseKind.REPORT,
        origin=Origin("game", "legacy"),
        opener=Party(player_uuid="reporter", player_name="Reporter"),
        subject=Party(player_uuid="subject", player_name="Player"),
        payload={"account_uuids": [OPAQUE], "statement": "x" * 3000},
    )
    assert not any(m.content for m in post_of(bot, case).messages)
    install_global_handler(bot, service.settings)
    interaction = slash_interaction(bot, CasesCog.find, str(case.number))
    service.authorize = AsyncMock(side_effect=RuntimeError("KNOWN REPORT DIAGNOSTIC"))
    caplog.set_level(logging.ERROR)
    await CasesCog.find.callback(CasesCog(bot), interaction, str(case.number))
    assert "KNOWN REPORT DIAGNOSTIC" in caplog.text
    assert any(r.exc_info for r in caplog.records)
    embed = bot.channels[100].messages[-1].embeds[0]
    assert "KNOWN REPORT DIAGNOSTIC" in embed.description
    assert any(f.name == "Command" for f in embed.fields)


@pytest.mark.parametrize("conflicted_again", [False, True])
async def test_subject_cleanup_serializes_decision_and_current_membership_with_real_barriers(
    conflicted_again,
):
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    await act(service, case, "take", user=member(99, (20,)))
    thread = bot.channels[int(case.discord["user_thread_id"])]
    original_remove = thread.remove_user
    removing, finish_remove, attempting_b = (
        asyncio.Event(),
        asyncio.Event(),
        asyncio.Event(),
    )
    once = True

    async def paused_remove(user):
        nonlocal once
        if user.id == 99 and once:
            once = False
            removing.set()
            await finish_remove.wait()
        await original_remove(user)

    thread.remove_user = paused_remove
    a = asyncio.create_task(
        service.set_subject(case.number, member(101, (20,)), Party(discord_id="99"))
    )
    await asyncio.wait_for(removing.wait(), 2)

    async def set_b():
        attempting_b.set()
        return await service.set_subject(
            case.number, member(101, (20,)), Party(discord_id="88")
        )

    b = asyncio.create_task(set_b())
    await asyncio.wait_for(attempting_b.wait(), 2)
    assert (await service.get(case.number)).subject.discord_id == "99"
    assert not b.done()  # B's CAS cannot pass an outstanding removal of A.
    with pytest.raises(AccessDenied):
        await act(service, case, "take", user=member(99, (20,)))
    finish_remove.set()
    await asyncio.wait_for(asyncio.gather(a, b), 2)
    case = await act(service, case, "take", user=member(99, (20,)))
    assert thread.members == {42, 99}
    assert case.discord["managed_memberships"][0]["staff_ids"] == ["99"]
    assert case.discord["managed_memberships"][0]["done"]
    stale_a = replace(case, subject=Party(discord_id="99"))
    if conflicted_again:
        case = await service.set_subject(
            case.number, member(101, (20,)), Party(discord_id="99")
        )
    # A stale reconciliation input must re-read under the same lock, not eject valid A.
    await complaints.reconcile(service, stale_a)
    case = await service.get(case.number)
    if conflicted_again:
        assert thread.members == {42}
        assert case.discord["managed_memberships"][0]["staff_ids"] == []
        assert case.discord["managed_memberships"][0]["done"]
        assert case.assignee is None
    else:
        assert thread.members == {42, 99}
        assert case.discord["managed_memberships"][0]["staff_ids"] == ["99"]
        assert case.discord["managed_memberships"][0]["done"]


@pytest.mark.parametrize("subject_id", ["42", "99"])
async def test_current_complaint_subject_never_invited_as_owner_or_head_admin(
    subject_id,
):
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    if subject_id == "99":
        case = await act(service, case, "take", user=member(99, (20,)))
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id=subject_id)
    )
    await service.threads.reconcile_memberships(
        service, replace(case, assignee={"discord_id": subject_id})
    )
    await service.project(case.number)
    assert (
        int(subject_id) not in bot.channels[int(case.discord["user_thread_id"])].members
    )
    assert subject_id not in case.discord["managed_memberships"][0]["staff_ids"]
    with pytest.raises(AccessDenied):
        await service.authorize(case, member(int(subject_id), (20,)), "take")


@pytest.mark.parametrize("replace_with", ["88", "99"])
async def test_history_lost_cas_revalidates_subject_revision_before_retry(replace_with):
    service, _, bot = await complaint_core(
        audits=[
            audit(actor="99", reason="A evidence"),
            audit(actor="88", reason="B evidence"),
        ]
    )
    case = await opened(service, bot)
    original_cas = service.store.cas
    checked, release = asyncio.Event(), asyncio.Event()
    once = True

    async def paused_history_cas(current, **kwargs):
        nonlocal once
        history = (kwargs.get("set_fields") or {}).get("evidence.subject_actions")
        if history and history["subject_id"] == "99" and once:
            once = False
            checked.set()
            await release.wait()
        return await original_cas(current, **kwargs)

    service.store.cas = paused_history_cas
    old = asyncio.create_task(
        service.set_subject(case.number, member(101, (20,)), Party(discord_id="99"))
    )
    await asyncio.wait_for(checked.wait(), 2)
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="88")
    )
    if replace_with == "99":
        case = await service.set_subject(
            case.number, member(101, (20,)), Party(discord_id="99")
        )
    expected = deepcopy(case.evidence["subject_actions"])
    release.set()
    await asyncio.wait_for(old, 2)
    current = await service.get(case.number)
    assert current.evidence["subject_actions"] == expected
    assert (
        expected["subject_revision"] == current.progress["complaint_subject_revision"]
    )
    assert current.subject.discord_id == replace_with
    assert not current.progress.get("complaint_history_pending")
    # The old A hook must not append a followup after the newer B selection.
    headers = [m.content for m in post_of(bot, current).messages[1:]]
    assert all(
        ("A evidence" if replace_with == "88" else "B evidence") not in line
        for line in headers[-1:]
    )


async def test_history_unrelated_lost_cas_retries_current_identity_without_losing_history():
    service, _, bot = await complaint_core(audits=[audit()])
    case = await opened(service, bot)
    original_cas = service.store.cas
    once = True

    async def competing_write(current, **kwargs):
        nonlocal once
        if "evidence.subject_actions" in (kwargs.get("set_fields") or {}) and once:
            once = False
            await original_cas(current, set_fields={"payload.independent": True})
        return await original_cas(current, **kwargs)

    service.store.cas = competing_write
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    assert (
        case.payload["independent"] and case.evidence["subject_actions"]["total"] == 1
    )
    assert not case.progress.get("complaint_history_pending")


@pytest.mark.parametrize("outcome", ["resolve", "reject"])
async def test_latest_failed_history_survives_case_close_and_terminal_repair(outcome):
    service, db, bot = await complaint_core(
        audits=[
            audit(actor="99", reason="Old actor"),
            audit(actor="88", reason="Latest actor"),
        ]
    )
    case = await opened(service, bot)
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    count = db["moderation_audit"].count_documents
    db["moderation_audit"].count_documents = AsyncMock(side_effect=RuntimeError(RAW))
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="88")
    )
    assert case.progress["complaint_history_pending"]
    case = await act(
        service,
        case,
        outcome,
        values={"note": "Private note", "reason_text": "Private reason"},
    )
    assert case.progress["closed_done"] and case.progress["outcomes_done"]
    history_field = post_of(bot, case).messages[0].embeds[0].fields[-1]
    assert (
        "Old actor" not in history_field.value
        and "awaiting a refresh" in history_field.value
    )
    notices, messages = deepcopy(db["case_notices"].docs), list(bot.users[42].messages)
    db["moderation_audit"].count_documents = count
    await reconcile_cases(
        service
    )  # Must discover a fully closed, pending-history case.
    current = await service.get(case.number)
    assert current.evidence["subject_actions"]["subject_id"] == "88"
    assert current.evidence["subject_actions"]["total"] == 1
    assert current.evidence["subject_actions"]["last"][0]["reason"] == "Latest actor"
    assert not current.progress.get("complaint_history_pending")
    assert current.subject == case.subject and current.resolution == case.resolution
    assert db["case_notices"].docs == notices and bot.users[42].messages == messages
    assert set(bot.users) == {42}
    assert post_of(bot, current).archived and post_of(bot, current).locked
    assert "Latest actor" in visible(post_of(bot, current))
    assert "Latest actor" not in " ".join(
        m.content for m in bot.channels[int(case.discord["user_thread_id"])].messages
    )


async def test_stable_actor_uuid_or_discord_history_is_bounded_unique_and_supports_later_links():
    targets = [player(1), player(2)]
    actor1, actor2 = player(8), player(9)
    actor1.uuid, actor2.uuid = ACTOR_UUID, OPAQUE
    rows = [
        audit(
            index=n,
            action="WARN",
            target=targets[n % 2].uuid,
            reason=f"entry {n} {ACTOR_UUID} {OPAQUE}",
            actor={
                "discord_id": None,
                "player_uuid": (ACTOR_UUID if n % 2 else OPAQUE),
                "name_snapshot": RAW,
            },
        )
        for n in range(14)
    ]
    rows += [
        audit(
            index=15,
            actor={"discord_id": "99", "player_uuid": ACTOR_UUID},
            reason="matched both",
        ),
        audit(index=16, actor="99", reason="discord only"),
        audit(
            index=17,
            actor={"discord_id": None, "player_uuid": "unrelated"},
            reason="unrelated actor",
        ),
        audit(
            index=18,
            actor={"discord_id": None, "name_snapshot": "Staff 99"},
            reason="name only",
        ),
        audit(
            index=19,
            target="unrelated",
            actor={"player_uuid": ACTOR_UUID},
            reason="unrelated target",
        ),
    ]
    service, db, bot = await complaint_core(players=targets, audits=rows)
    case = await opened(service, bot)
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    assert case.evidence["subject_actions"]["total"] == 2
    service.players.by_discord["99"] = [actor1, actor2]
    collection = db["moderation_audit"]
    original_find, original_count = collection.find, collection.count_documents
    queries, limits = [], []

    def find(query):
        queries.append(deepcopy(query))
        cursor = original_find(query)
        original_limit = cursor.limit

        def limit(n):
            limits.append(n)
            return original_limit(n)

        cursor.limit = limit
        return cursor

    async def count(query):
        queries.append(deepcopy(query))
        return await original_count(query)

    collection.find, collection.count_documents = find, count
    await reconcile_case(service, case, audited=False)
    case = await service.get(case.number)
    history = case.evidence["subject_actions"]
    assert history["total"] == 16 and len(history["last"]) == 10
    assert history["last"][0]["reason"] == "discord only"
    assert history["last"][1]["reason"] == "matched both"
    assert history["last"][-1]["reason"].startswith("entry 6 ")
    assert limits == [10]
    assert all(q["target.uuid"] == {"$in": [p.uuid for p in targets]} for q in queries)
    assert all(
        q["$or"]
        == [
            {"actor.discord_id": "99"},
            {"actor.player_uuid": {"$in": [ACTOR_UUID, OPAQUE]}},
        ]
        for q in queries
    )
    assert_private(visible(post_of(bot, case)))
    assert_private(json.dumps(history["last"], default=str))
    assert db["moderation_audit"].docs == rows


async def test_subject_cleanup_precedes_linked_actor_lookup_even_on_link_lookup_failure():
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    await act(service, case, "take", user=member(99, (20,)))
    thread = bot.channels[int(case.discord["user_thread_id"])]

    async def failed_links(discord_id):
        assert discord_id == "99" and 99 not in thread.members
        raise RuntimeError(RAW)

    service.players.find_players_by_discord_id = failed_links
    case = await service.set_subject(
        case.number, member(101, (20,)), Party(discord_id="99")
    )
    assert case.progress["complaint_history_pending"] and thread.members == {42}


@pytest.mark.parametrize("offline", [False, True])
async def test_full_statement_reprojected_per_deleted_replacement_post_with_no_other_copies(
    offline,
):
    linked = player()
    linked.uuid = OPAQUE
    service, _, bot = await complaint_core(players=[linked])
    suffix = OPAQUE + f" {IP4} {IP6} {HEX_UUID} end"
    statement = ("long statement " * 220)[: 3000 - len(suffix)] + suffix
    assert len(statement) == 3000
    case = await opened(service, bot, statement=statement)
    old_post = post_of(bot, case)
    original = [m.content for m in old_post.messages[1:]]
    assert len(original) >= 2
    assert case.progress["complaint_statement_post"] == str(old_post.id)
    old_post.parent.all_threads.remove(old_post)
    del bot.channels[old_post.id]
    if not offline:
        await service.thread_deleted(old_post.id, old_post.parent_id)
    await reconcile_cases(
        service
    )  # Also executes the actual offline _repair_post path.
    case = await service.get(case.number)
    new_post = post_of(bot, case)
    assert new_post.id != old_post.id
    assert case.progress["complaint_statement_post"] == str(new_post.id)
    texts = [m.content for m in new_post.messages[1:]]
    assert texts == original and all(len(text) <= 2000 for text in texts)
    assert "".join(text.split("\n", 1)[1] for text in texts) == safe_text(
        statement, secrets=(OPAQUE,), limit=4000
    )
    assert_private(visible(new_post), "private-1=")
    await reconcile_cases(service)
    await complaints.created(service, case)
    assert [m.content for m in new_post.messages[1:]] == texts
    assert case.progress["created_hook_done"] is True
    for channel_id in (100, 200, 500, int(case.discord["user_thread_id"])):
        assert all(
            "long statement" not in m.content for m in bot.channels[channel_id].messages
        )
    assert not bot.channels[200].all_threads
