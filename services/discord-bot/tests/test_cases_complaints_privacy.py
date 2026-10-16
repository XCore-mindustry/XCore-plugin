from __future__ import annotations

import logging
from dataclasses import replace
from types import SimpleNamespace
from unittest.mock import AsyncMock

import discord
import pytest
from cases_complaints_fakes import act, complaint_core, form, opened, submit
from cases_fakes import Interaction, Role, member

from xcore_discord_bot.cases.flows import complaints
from xcore_discord_bot.cases.model import CaseKind, CaseStatus, Party
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.cases.startup import initialize_flows, validate_flow
from xcore_discord_bot.cases.views import run_case_action


@pytest.mark.parametrize(
    "problem",
    [
        "senior_forum_zero",
        "panel_zero",
        "parent_missing",
        "public_forum",
        "staff_forum_view",
        "other_role_forum_view",
        "staff_parent_view",
        "reviewer_parent_view",
        "everyone_manage",
        "staff_manage",
        "other_manage",
        "other_bot_manage",
        "other_bot_forum",
        "missing_senior",
        "senior_parent_hidden",
        "senior_admin_fallback",
        "senior_equals_admin",
    ],
)
async def test_startup_refuses_unsafe_layout_and_logs_configuration(problem, caplog):
    settings = {
        "senior_forum_zero": {"cases_senior_forum_id": 0},
        "panel_zero": {"cases_panel_channel_id": 0},
        "parent_missing": {"cases_complaint_thread_channel_id": 888},
        "senior_admin_fallback": {"discord_general_admin_role_id": None},
        "senior_equals_admin": {"discord_general_admin_role_id": 10},
    }.get(problem, {})
    service, _, bot = await complaint_core(**settings)
    forum, parent = bot.channels[300], bot.channels[500]
    if problem in {"public_forum", "staff_forum_view", "other_role_forum_view"}:
        role = {"public_forum": 1, "staff_forum_view": 10, "other_role_forum_view": 40}[
            problem
        ]
        forum.role_permissions[role] = discord.Permissions(view_channel=True)
    elif problem in {"staff_parent_view", "reviewer_parent_view"}:
        parent.role_permissions[10 if problem == "staff_parent_view" else 30] = (
            discord.Permissions(view_channel=True)
        )
    elif problem in {"everyone_manage", "staff_manage", "other_manage"}:
        role = {"everyone_manage": 1, "staff_manage": 10, "other_manage": 40}[problem]
        parent.role_permissions[role] = discord.Permissions(
            view_channel=True, manage_threads=True
        )
    elif problem in {"other_bot_manage", "other_bot_forum"}:
        parent.guild.roles.append(Role(60, bot=True))
        if problem == "other_bot_manage":
            parent.role_permissions[60] = discord.Permissions(manage_threads=True)
        else:
            forum.role_permissions[60] = discord.Permissions(view_channel=True)
    elif problem == "missing_senior":
        parent.guild.roles = [r for r in parent.guild.roles if r.id != 20]
    elif problem == "senior_parent_hidden":
        parent.role_permissions[20] = discord.Permissions.none()
    caplog.set_level(logging.WARNING)
    await initialize_flows(service)
    assert not service.registry.enabled(CaseKind.COMPLAINT)
    assert service.registry.disabled_reasons[CaseKind.COMPLAINT]
    assert "disabled" in caplog.text
    assert not bot.channels[300].all_threads


async def test_startup_allows_safe_layout_bot_own_role_and_creates_named_tags():
    service, _, bot = await complaint_core()
    guild = bot.channels[300].guild
    own = Role(60, bot=True)
    guild.roles.append(own)
    guild.me.roles = [guild.default_role, own]
    bot.channels[300].role_permissions[60] = discord.Permissions(view_channel=True)
    bot.channels[500].role_permissions[60] = discord.Permissions(manage_threads=True)
    bot.channels[300].available_tags = []
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.COMPLAINT)
    assert {tag.name for tag in bot.channels[300].available_tags} >= {
        "Complaint",
        "Open",
        "Taken",
        "Resolved",
        "Rejected",
    }
    # @everyone is in the bot's roles too; it is never exempt from privacy checks.
    bot.channels[500].role_permissions[1] = discord.Permissions(
        view_channel=True, send_messages_in_threads=True, manage_threads=True
    )
    assert any(
        "Manage Threads" in reason
        for reason in await validate_flow(service, CaseKind.COMPLAINT)
    )


async def test_startup_tolerates_unhideable_administrator_roles(caplog):
    service, _, bot = await complaint_core()
    bot.channels[300].guild.roles.append(Role(70, admin=True))
    bot.channels[300].role_permissions[70] = discord.Permissions.all()
    bot.channels[500].role_permissions[70] = discord.Permissions.all()
    caplog.set_level(logging.WARNING)
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.COMPLAINT)
    assert "Roles with Administrator can see complaints: 70" in caplog.text


async def test_parent_fallback_requires_same_safe_layout():
    service, _, bot = await complaint_core(cases_complaint_thread_channel_id=0)
    for role in (10, 30):
        bot.channels[100].role_permissions[role] = discord.Permissions.none()
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.COMPLAINT)
    bot.channels[100].role_permissions[10] = discord.Permissions(view_channel=True)
    await initialize_flows(service)
    assert not service.registry.enabled(CaseKind.COMPLAINT)


async def test_disabled_startup_performs_no_discord_validation():
    service, _, _ = await complaint_core(enabled=False)
    service.threads.channel = AsyncMock(
        side_effect=AssertionError("must not look up channels")
    )
    await initialize_flows(service)
    service.threads.channel.assert_not_awaited()


RAW = "RAW SENSITIVE COMPLAINT 203.0.113.9 private-1="


@pytest.mark.parametrize(
    "stage",
    [
        "submit",
        "creation",
        "attachment",
        "subject_history",
        "projection",
        "action",
        "closing",
        "reconcile",
        "gateway",
        "startup",
        "wakeup",
    ],
)
async def test_raw_complaint_exception_text_never_enters_logs_or_public_channels(
    stage, caplog
):
    service, _, bot = await complaint_core()
    caplog.set_level(logging.ERROR)
    fail = AsyncMock(side_effect=RuntimeError(RAW))
    if stage == "submit":
        service.players.find_players_by_discord_id = fail
        await submit(bot, await form(bot), statement=RAW)
    elif stage == "creation":
        service.threads.ensure_post = fail
        await opened(service, bot, statement=RAW)
    elif stage == "startup":
        service.threads.channel = fail
        await initialize_flows(service)
    else:
        case = await opened(service, bot, statement=RAW)
        if stage == "attachment":
            service.threads.upload_attachments = fail
            modal = await form(bot)
            modal.files._values = [SimpleNamespace(id=99)]
            await submit(bot, modal, id=701, statement=RAW)
        elif stage == "subject_history":
            service.store.database["moderation_audit"].count_documents = fail
            await service.set_subject(
                case.number, member(101, (20,)), Party(discord_id="99")
            )
        elif stage == "projection":
            service.threads.render = fail
            await service.project(case.number)
        elif stage == "action":
            service.dispatch_action = fail
            await run_case_action(
                service,
                Interaction(bot, user=member(101, (20,))),
                case.number,
                "resolve",
                "en",
                {"note": RAW},
            )
        elif stage == "closing":
            service.notifier.send = fail
            await act(service, case, "resolve", values={"note": RAW})
        elif stage == "reconcile":
            flow = replace(complaints.COMPLAINT_FLOW, reconcile=fail)
            service.registry._flows[CaseKind.COMPLAINT] = flow
            await reconcile_cases(service)
        elif stage == "gateway":
            service.threads.announce_user_message = fail
            await service.registry.dispatch(
                "on_message", service, SimpleNamespace(content=RAW)
            )
        elif stage == "wakeup":
            service.transport.publish_case_notice = fail
            service.players.by_uuid["private-1="].online_server = "test"
            await act(service, case, "resolve", values={"note": RAW})
    assert caplog.records, stage
    assert RAW not in caplog.text and "private-1=" not in caplog.text
    assert all(record.exc_info is None for record in caplog.records)
    assert all(
        RAW not in message.content
        for channel_id in (100, 200, 500)
        for message in bot.channels[channel_id].messages
    )
    assert not bot.channels[200].all_threads


async def test_subject_select_step_rechecks_new_conflict_and_demoted_actor():
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    for conflict in (True, False):
        senior = member(99, (20,))
        click = Interaction(bot, user=senior)
        await service.dispatch_action(
            case.number, "subject", senior, "en", interaction=click
        )
        view = click.events[-1][2]["view"]
        if conflict:
            await service.set_subject(
                case.number, member(101, (20,)), Party(discord_id="99")
            )
        step = Interaction(bot, user=senior if conflict else member(99, (10,)))
        view.select._values = [member(88)]
        await view.select.callback(step)
        assert (await service.get(case.number)).subject.discord_id == (
            "99" if conflict else "77"
        )
        await service.set_subject(
            case.number, member(101, (20,)), Party(discord_id="77")
        )


async def test_normal_outcome_never_notifies_identified_staff_subject():
    service, db, bot = await complaint_core()
    case = await opened(service, bot)
    await service.set_subject(case.number, member(101, (20,)), Party(discord_id="99"))
    case = await act(service, case, "resolve", values={"note": RAW})
    assert case.status == CaseStatus.RESOLVED and set(bot.users) == {42}
    assert "RAW SENSITIVE" not in bot.users[42].messages[0]
    assert db["case_notices"].docs[0]["params"] == {"number": str(case.number)}
    assert not any(
        "RAW SENSITIVE" in m.content
        for m in bot.channels[int(case.discord["user_thread_id"])].messages
    )
