from __future__ import annotations

from dataclasses import replace
from datetime import timedelta
from types import SimpleNamespace

import discord
import pytest
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import NOW, core, member
from test_cases_service import opened

from xcore_discord_bot.cases.model import (
    CaseKind,
    CaseStatus,
    Origin,
    Party,
    Resolution,
)
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.cases.startup import initialize_flows, validate_flow
from xcore_discord_bot.cases.threads import FORUM_TAGS
from xcore_discord_bot.settings import Settings


async def test_startup_flags_off_does_no_discord_work_and_registers_no_enabled_flow():
    service, _, _ = await core(flow=DUMMY_FLOW)

    async def fail(_):
        pytest.fail("Disabled flows must not resolve channels")

    service.threads.channel = fail
    await initialize_flows(service)
    assert not service.registry.active_flows()


async def test_startup_accepts_safe_staff_setup_and_creates_tags_by_name():
    service, _, bot = await core(
        flow=DUMMY_FLOW, tags=False, cases_reports_enabled=True
    )
    forum = bot.channels[200]
    forum.available_tags = [discord.ForumTag(name="Owner's tag")]
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.REPORT)
    assert [tag.name for tag in forum.available_tags] == ["Owner's tag", *FORUM_TAGS]
    assert await validate_flow(service, CaseKind.REPORT) == []
    assert len(forum.available_tags) == len(FORUM_TAGS) + 1


async def test_startup_needs_manage_channels_only_to_create_missing_tags():
    service, _, bot = await core(flow=DUMMY_FLOW, cases_reports_enabled=True)
    forum = bot.channels[200]
    permissions = discord.Permissions.all()
    permissions.manage_channels = False
    forum.role_permissions[999] = permissions
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.REPORT)
    forum.available_tags = [tag for tag in forum.available_tags if tag.name != "Taken"]
    await initialize_flows(service)
    reasons = service.registry.disabled_reasons[CaseKind.REPORT]
    assert not service.registry.enabled(CaseKind.REPORT)
    assert len(reasons) == 1 and "Taken" in reasons[0] and "Open" not in reasons[0]


async def test_one_misconfigured_flow_does_not_disable_the_others():
    service, _, _ = await core(
        flow=DUMMY_FLOW,
        cases_reports_enabled=True,
        cases_complaints_enabled=True,
        cases_senior_forum_id=0,
    )
    service.registry.register(replace(DUMMY_FLOW, kind=CaseKind.COMPLAINT))
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.REPORT)
    assert not service.registry.enabled(CaseKind.COMPLAINT)
    assert service.registry.disabled_reasons[CaseKind.COMPLAINT] == (
        "CASES_SENIOR_FORUM_ID is not configured",
    )
    assert [flow.kind for flow in service.registry.active_flows()] == [CaseKind.REPORT]


@pytest.mark.parametrize("key", ["cases_staff_forum_id", "cases_panel_channel_id"])
async def test_startup_missing_ids_disable_only_affected_flow(key, caplog):
    service, _, _ = await core(flow=DUMMY_FLOW, cases_reports_enabled=True)
    service.settings = service.settings.model_copy(update={key: 0})
    service.threads.settings = service.settings
    await initialize_flows(service)
    assert not service.registry.enabled(CaseKind.REPORT)
    assert key.upper() in str(service.registry.disabled_reasons[CaseKind.REPORT])
    assert "disabled" in caplog.text


async def test_requested_but_uninstalled_flow_does_not_crash():
    service, _, _ = await core(cases_reports_enabled=True)
    await initialize_flows(service)
    assert service.registry.disabled_reasons[CaseKind.REPORT] == (
        "Flow module is not installed",
    )


@pytest.mark.parametrize(
    "problem",
    [
        "wrong_type",
        "public_staff",
        "no_admin_visibility",
        "missing_bot_permission",
        "wrong_guild",
        "bad_thread_parent",
    ],
)
async def test_startup_refuses_unsafe_or_wrong_channels(problem):
    service, _, bot = await core(flow=DUMMY_FLOW, cases_reports_enabled=True)
    forum = bot.channels[200]
    if problem == "wrong_type":
        forum.type = discord.ChannelType.text
    elif problem == "public_staff":
        forum.role_permissions[1] = discord.Permissions(view_channel=True)
    elif problem == "no_admin_visibility":
        forum.role_permissions[10] = discord.Permissions.none()
    elif problem == "missing_bot_permission":
        perms = discord.Permissions.all()
        perms.manage_threads = False
        forum.role_permissions[999] = perms
    elif problem == "wrong_guild":
        service.settings = service.settings.model_copy(update={"discord_guild_id": 321})
    else:
        bot.channels[100].type = discord.ChannelType.forum
    await initialize_flows(service)
    assert (
        not service.registry.enabled(CaseKind.REPORT)
        and service.registry.disabled_reasons[CaseKind.REPORT]
    )


async def test_startup_channel_fetch_error_and_storage_error_fail_closed():
    service, _, _ = await core(flow=DUMMY_FLOW, cases_reports_enabled=True)

    async def unavailable(_):
        raise RuntimeError("Discord unavailable")

    service.threads.channel = unavailable
    await initialize_flows(service)
    assert not service.registry.enabled(CaseKind.REPORT)

    async def missing(_):
        raise discord.NotFound(SimpleNamespace(status=404, reason="Not Found"), "gone")

    service.threads.channel = missing
    await initialize_flows(service)
    assert service.registry.disabled_reasons[CaseKind.REPORT] == (
        "CASES_STAFF_FORUM_ID: the channel does not exist or the bot cannot see it",
    )
    service.storage_available = False
    await initialize_flows(service)
    assert service.registry.disabled_reasons[CaseKind.REPORT] == (
        "Cases storage is unavailable",
    )


async def test_reconcile_waits_one_minute_adopts_post_and_cancels_at_24_hours():
    service, _, bot = await core(flow=DUMMY_FLOW)
    bot.failures.arm("forum_create", after=True)
    case = await opened(service)
    assert case.status == CaseStatus.CREATING
    await reconcile_cases(service)
    assert (await service.get(case.number)).status == CaseStatus.CREATING
    service.now = lambda: NOW + timedelta(minutes=2)
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert final.status == CaseStatus.OPEN and final.progress["creation_done"]
    assert len(bot.channels[200].all_threads) == 1
    bot.failures.arm("forum_create")
    abandoned = await opened(service, submission="abandon")
    service.now = lambda: NOW + timedelta(hours=25)
    await reconcile_cases(service)
    assert (await service.get(abandoned.number)).status == CaseStatus.CANCELLED


async def test_reconcile_unarchives_open_posts_and_repairs_deleted_post():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    old = bot.channels[int(case.discord["thread_id"])]
    old.archived = True
    await reconcile_cases(service)
    assert old.archived is False
    old.locked = True
    await reconcile_cases(service)
    assert old.locked is False
    assert {tag.name for tag in old.applied_tags} == {"Report", "Open"}
    bot.channels.pop(old.id)
    bot.channels[200].all_threads.remove(old)
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert (
        current.discord["thread_id"] != case.discord["thread_id"]
        and current.status == CaseStatus.OPEN
    )
    assert any(e["type"] == "thread_deleted" for e in current.events)


async def test_reconcile_finishes_partial_close_and_clears_old_chat_only():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(
        service,
        evidence={
            "chat": [{"text": "Retain until deadline"}],
            "context": {"map_name": "Keep context"},
        },
    )
    bot.failures.arm("card_edit")
    await service.reject(case.number, member(), reason_code="no_evidence")
    closed = await service.get(case.number)
    assert not closed.progress.get("closed_done")
    await reconcile_cases(service)
    assert (await service.get(case.number)).progress["closed_done"]
    service.now = lambda: NOW + timedelta(days=179)
    await reconcile_cases(service)
    assert (await service.get(case.number)).evidence["chat"]
    service.now = lambda: NOW + timedelta(days=180)
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert (
        current.evidence["chat"] == []
        and current.evidence["context"]["map_name"] == "Keep context"
    )


async def test_reconcile_ignores_old_audit_after_reopen():
    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    await service.reject(case.number, member(), reason_code="no_violation")
    service.now = lambda: NOW + timedelta(hours=1)
    await service.reopen(case.number, member(200, (20,)))
    await db["moderation_audit"].insert_one(
        {
            "_id": "old",
            "action": "BAN",
            "occurred_at": NOW,
            "details": {"extra": {"case": str(case.number)}},
        }
    )
    await reconcile_cases(service)
    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_map_reconcile_hooks_and_author_post_tags_are_preserved():
    calls = []

    async def retry(service, case):
        calls.append(case.number)

    flow = replace(
        DUMMY_FLOW, kind=CaseKind.MAP, panel=None, outcomes=None, reconcile=retry
    )
    service, _, bot = await core(flow=flow)
    forum = bot.channels[400]
    gameplay = discord.ForumTag(name="PvP")
    forum.available_tags = [
        gameplay,
        *(discord.ForumTag(name=name) for name in ("Waiting", "Accepted", "Rejected")),
    ]
    created = await forum.create_thread(
        name="Author title",
        embed=discord.Embed(title="Original author content"),
        view=None,
    )
    created.thread.owner_id = 42
    created.thread.applied_tags = [gameplay]
    case = await service.open(
        kind=CaseKind.MAP,
        member=member(42, ()),
        origin=Origin("forum", f"map:{created.thread.id}", "uk"),
        opener=Party(discord_id="42"),
        discord={"forum_id": "400", "thread_id": str(created.thread.id)},
        payload={"pending_badge": True},
    )
    await service.checkpoint(case.number, set_fields={"flags": ["deep_checks_pending"]})
    await reconcile_cases(service)
    assert calls == [case.number]
    assert created.thread.name == "Author title" and len(created.thread.messages) == 2
    assert {tag.name for tag in created.thread.applied_tags} == {"PvP", "Waiting"}
    await service.resolve(case.number, member(101, (30,)), Resolution("accepted"))
    assert {tag.name for tag in created.thread.applied_tags} == {"PvP", "Accepted"}
    # The author's public post stays open and readable after an accept (9.5).
    assert not created.thread.locked and not created.thread.archived
    assert created.thread.messages[1].view.children == []
    calls.clear()
    await reconcile_cases(service)
    assert calls == [case.number]
    calls.clear()
    service.now = lambda: NOW + timedelta(days=31)
    await reconcile_cases(service)
    assert calls == []


async def test_map_post_deleted_is_cancelled_and_bot_post_is_repaired():
    flow = replace(DUMMY_FLOW, kind=CaseKind.MAP, panel=None, outcomes=None)
    service, _, bot = await core(flow=flow)
    created = await bot.channels[400].create_thread(
        name="Map", embed=discord.Embed(title="author"), view=None
    )
    case = await service.open(
        kind=CaseKind.MAP,
        member=member(42, ()),
        origin=Origin("forum", f"map:{created.thread.id}"),
        opener=Party(discord_id="42"),
        discord={"forum_id": "400", "thread_id": str(created.thread.id)},
    )
    await service.registry.dispatch(
        "on_thread_delete",
        service,
        SimpleNamespace(thread_id=created.thread.id, parent_id=400),
    )
    cancelled = await service.get(case.number)
    assert cancelled.status == CaseStatus.CANCELLED
    assert cancelled.events[-1]["data"] == {"reason": "map_post_deleted"}
    assert "thread_id" not in cancelled.discord


async def test_rejected_map_post_is_locked_but_never_archived():
    flow = replace(DUMMY_FLOW, kind=CaseKind.MAP, panel=None, outcomes=None)
    service, _, bot = await core(flow=flow)
    created = await bot.channels[400].create_thread(
        name="Map", embed=discord.Embed(title="author"), view=None
    )
    case = await service.open(
        kind=CaseKind.MAP,
        origin=Origin("forum", f"map:{created.thread.id}"),
        opener=Party(discord_id="42"),
        discord={"forum_id": "400", "thread_id": str(created.thread.id)},
    )
    assert case.status == CaseStatus.OPEN and len(bot.channels[400].all_threads) == 1
    await service.reject(case.number, member(101, (30,)), reason_code="broken")
    assert created.thread.locked and not created.thread.archived


async def test_deleted_threads_are_matched_by_parent_before_any_database_read():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service, source="discord")
    old_user_thread = int(case.discord["user_thread_id"])
    by_thread = service.store.by_thread

    async def forbidden(_):
        pytest.fail("A thread of another channel is not a case thread")

    service.store.by_thread = forbidden
    await service.registry.dispatch(
        "on_thread_delete", service, SimpleNamespace(thread_id=1, parent_id=987)
    )
    service.store.by_thread = by_thread
    bot.channels[100].all_threads.remove(bot.channels.pop(old_user_thread))
    await service.registry.dispatch(
        "on_thread_delete",
        service,
        SimpleNamespace(thread_id=old_user_thread, parent_id=100),
    )
    current = await service.get(case.number)
    assert "user_thread_id" not in current.discord and current.status == "open"
    await reconcile_cases(service)
    repaired = await service.get(case.number)
    new_thread = bot.channels[int(repaired.discord["user_thread_id"])]
    assert new_thread.id != old_user_thread and 42 in new_thread.members
    assert repaired.progress["creation_done"]
    # Nothing was created twice on the staff side.
    assert len(bot.channels[200].all_threads) == 1


async def test_reconcile_is_silent_without_flows_and_for_healthy_cases():
    service, _, bot = await core(flow=DUMMY_FLOW)
    open_case = await opened(service, source="discord")
    closed = await opened(service, submission="other")
    await service.reject(closed.number, member(), reason_code="no_evidence")
    revisions = [(await service.get(c.number)).revision for c in (open_case, closed)]
    bot.calls.clear()
    await reconcile_cases(service)
    await reconcile_cases(service)
    assert bot.calls == []
    assert revisions == [
        (await service.get(c.number)).revision for c in (open_case, closed)
    ]
    service.registry.disable(DUMMY_FLOW.kind)

    async def forbidden(*args, **kwargs):
        pytest.fail("No enabled flow: the task reads nothing")

    service.store.list_cases = forbidden
    await reconcile_cases(service)


async def test_reconcile_keeps_going_after_one_broken_case():
    service, _, bot = await core(flow=DUMMY_FLOW)
    first = await opened(service)
    second = await opened(service, submission="second")
    threads = [bot.channels[int(c.discord["thread_id"])] for c in (first, second)]
    for thread in threads:
        thread.archived = True
    project = service.project

    async def broken(number):
        if number == first.number:
            raise RuntimeError("Discord said no")
        await project(number)

    service.project = broken
    await reconcile_cases(service)
    assert threads[0].archived and not threads[1].archived


async def test_close_failures_stay_pending_after_a_day_and_finish_when_repaired():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    render = service.threads.render
    attempts = []

    async def failing(svc, current):
        attempts.append(current.status)
        raise RuntimeError("The post cannot be edited")

    service.threads.render = failing
    await service.reject(case.number, member(), reason_code="no_evidence")
    closed = await service.get(case.number)
    assert closed.status == CaseStatus.REJECTED and closed.progress["outcomes_done"]
    assert not closed.progress.get("closed_done") and len(bot.users[42].messages) == 1
    service.now = lambda: NOW + timedelta(hours=23)
    await reconcile_cases(service)
    assert len(attempts) == 2 and len(bot.users[42].messages) == 1
    service.now = lambda: NOW + timedelta(hours=24)
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert not final.progress.get("closed_done") and len(attempts) == 3
    assert final.events[-1]["data"] == {
        "step": "closing",
        "error_type": "ExceptionGroup",
    }
    assert [e["type"] for e in final.events].count("error") == 1
    service.threads.render = render
    await reconcile_cases(service)
    assert len(attempts) == 3 and len(db["cases"].docs) == 1
    assert (await service.get(case.number)).progress["closed_done"]
    assert len(bot.users[42].messages) == 1


async def test_daemon_start_preloads_cases_checks_flows_and_survives_a_bad_cycle():
    from xcore_discord_bot.cases.panel import CasePanel
    from xcore_discord_bot.daemons.cases_reconcile_daemon import CasesReconcileDaemon

    service, _, bot = await core(flow=DUMMY_FLOW, cases_reports_enabled=True)
    case = await opened(service)
    # A fresh process: nothing cached, nothing enabled yet.
    service.store._active.clear()
    service.registry.disable(DUMMY_FLOW.kind)
    cycles = []

    async def ready():
        return None

    async def run_once():
        cycles.append(service.registry.enabled(DUMMY_FLOW.kind))
        if len(cycles) == 1:
            raise RuntimeError("MongoDB hiccup")

    bot.wait_until_ready = ready
    bot.is_closed = lambda: len(cycles) >= 2
    daemon = CasesReconcileDaemon(
        bot, service, CasePanel(service, bot), interval_seconds=0
    )
    daemon.run_once = run_once
    await daemon._run_loop()
    assert daemon.ready.is_set() and cycles == [True, True]
    assert service.store.peek(case.number).number == case.number


async def test_daemon_run_once_reconciles_and_refreshes_a_published_panel():
    from xcore_discord_bot.cases.panel import CasePanel
    from xcore_discord_bot.daemons.cases_reconcile_daemon import CasesReconcileDaemon

    service, _, bot = await core(flow=DUMMY_FLOW)
    panel = CasePanel(service, bot)
    daemon = CasesReconcileDaemon(bot, service, panel)
    await daemon.run_once()
    # Nothing is published that an administrator did not publish.
    assert bot.channels[100].messages == []
    await panel.publish()
    case = await opened(service)
    bot.channels[int(case.discord["thread_id"])].archived = True
    bot.calls.clear()
    await daemon.run_once()
    assert not bot.channels[int(case.discord["thread_id"])].archived
    assert not [call for call in bot.calls if call[0] == "panel_edit"]
    service.registry.disable(DUMMY_FLOW.kind)
    await daemon.run_once()
    assert bot.channels[100].messages[0].view.children == []
    assert len(bot.channels[100].messages) == 1


async def test_reconcile_paginates_beyond_first_200_rows():
    service, db, _ = await core(flow=DUMMY_FLOW)
    template = await opened(service)
    for n in range(2, 203):
        await db["cases"].insert_one(
            replace(
                template,
                number=n,
                origin=Origin("game", f"sub-{n}"),
                status=CaseStatus.CANCELLED,
                discord={},
            ).to_doc()
        )
    old = replace(
        template,
        number=203,
        origin=Origin("game", "old"),
        status=CaseStatus.REJECTED,
        discord={},
        closed_at=NOW - timedelta(days=181),
        evidence={"chat": [{"text": "old"}]},
        progress={"closed_done": True},
    )
    await db["cases"].insert_one(old.to_doc())
    await reconcile_cases(service)
    assert (await service.get(203)).evidence["chat"] == []


@pytest.mark.parametrize(
    "field",
    [
        "CASES_PANEL_CHANNEL_ID",
        "CASES_STAFF_FORUM_ID",
        "CASES_SENIOR_FORUM_ID",
        "CASES_COMPLAINT_THREAD_CHANNEL_ID",
        "CASES_BLACKLIST_ROLE_ID",
        "DISCORD_MAPS_FORUM_ID",
        "DISCORD_MAP_MAKER_ROLE_ID",
    ],
)
def test_blank_new_channel_role_ids_are_zero(field):
    cfg = Settings(
        DISCORD_BOT_TOKEN="fake",
        DISCORD_ADMIN_ROLE_ID=10,
        DISCORD_PRIVATE_CHANNEL_ID=20,
        **{field: " "},
    )
    assert getattr(cfg, field.lower()) == 0 and cfg.cases_enabled is False


async def test_token_completion_is_filtered_before_limit_despite_later_noise():
    from test_cases_service import audit

    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    async def interrupted(claimed):
        token = claimed.pending_action["token"]
        await db["moderation_audit"].insert_one(
            {
                **audit(case.number, ban_id="7", case_action_token=token),
                "target": {"uuid": case.subject.player_uuid},
            }
        )
        for index in range(30):
            await db["moderation_audit"].insert_one(
                {
                    **audit(
                        case.number,
                        action="NOTE",
                        at=NOW + timedelta(seconds=index + 1),
                        kind="noise",
                        case_action_token="other",
                    ),
                    "_id": f"noise-{index}",
                }
            )
        raise RuntimeError("post-audit failure")

    final = await service.execute_action(case.number, "ban", member(), interrupted)
    assert final.resolution.code == "banned" and final.resolution.ban_id == 7
    assert final.resolution.audit_id == f"audit-{case.number}-BAN"


async def test_reopen_same_millisecond_discards_old_token_and_uses_latest_started_order():
    from test_cases_service import audit

    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    tokens = []

    async def no_completion(claimed):
        tokens.append(claimed.pending_action["token"])
        raise RuntimeError("no completion")

    with pytest.raises(RuntimeError):
        await service.execute_action(case.number, "ban", member(), no_completion)
    await service.reject(case.number, member(), reason_code="no_evidence")
    await service.reopen(case.number, member(200, (20,)))
    # A late completion of a prior attempt is newer by time but belongs before reopen by
    # the order of action_started events. It cannot close the reopened case.
    await db["moderation_audit"].insert_one(
        {
            **audit(
                case.number, at=NOW + timedelta(seconds=1), case_action_token=tokens[0]
            ),
            "_id": "old",
            "target": {"uuid": case.subject.player_uuid},
        }
    )
    await reconcile_cases(service)
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    for _ in range(2):
        with pytest.raises(RuntimeError):
            await service.execute_action(case.number, "ban", member(), no_completion)
    current = await service.get(case.number)
    starts = [e for e in current.events if e["type"] == "action_started"]
    assert all(e["at"] == NOW for e in starts)
    # Released guards retain their action_started token. Only the last attempt can recover,
    # including when all starts/reopen share a timestamp and the earlier audit arrives later.
    await db["moderation_audit"].insert_one(
        {
            **audit(
                case.number, at=NOW + timedelta(seconds=2), case_action_token=tokens[-2]
            ),
            "_id": "previous",
            "target": {"uuid": case.subject.player_uuid},
        }
    )
    await reconcile_cases(service)
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    await db["moderation_audit"].insert_one(
        {
            **audit(case.number, ban_id="99", case_action_token=tokens[-1]),
            "_id": "latest",
            "target": {"uuid": case.subject.player_uuid},
        }
    )
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert final.status == CaseStatus.RESOLVED and final.resolution.ban_id == 99


async def test_audits_for_applies_since_and_exact_token_in_database_before_limit():
    from test_cases_service import audit

    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    await db["moderation_audit"].insert_one(
        audit(case.number, case_action_token="wanted")
    )
    expected = audit(
        case.number, at=NOW + timedelta(seconds=1), case_action_token="wanted"
    )
    expected["_id"] = "new"
    await db["moderation_audit"].insert_one(expected)
    for index in range(30):
        await db["moderation_audit"].insert_one(
            {
                **audit(
                    case.number,
                    at=NOW + timedelta(seconds=2),
                    case_action_token="other",
                ),
                "_id": f"other-{index}",
            }
        )
    rows = await service.store.audits_for(
        case, action_token="wanted", since=NOW + timedelta(seconds=1)
    )
    assert [row["_id"] for row in rows] == ["new"]
