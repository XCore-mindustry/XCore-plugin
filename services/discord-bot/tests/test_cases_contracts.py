from __future__ import annotations

from dataclasses import replace
from datetime import timedelta
from pathlib import Path
from types import SimpleNamespace

import discord
import pytest
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import Interaction, core, member
from test_cases_service import OPENER, SUBJECT, opened

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.cases.access import roles_for
from xcore_discord_bot.cases.cards import safe_text
from xcore_discord_bot.cases.model import (
    AccessDenied,
    CaseKind,
    CaseStatus,
    Origin,
    Party,
)
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.cases.registry import FlowRegistry
from xcore_discord_bot.cases.startup import initialize_flows
from xcore_discord_bot.cases.views import panel_view
from xcore_discord_bot.cogs.cases import CasesCog
from xcore_discord_bot.i18n import LocalePref, XCoreTranslator, t


async def test_transport_event_hooks_only_run_when_enabled_and_errors_propagate():
    seen = []

    async def report(service, payload):
        seen.append(payload)
        if payload == "bad":
            raise ValueError("Malformed report")

    service, _, _ = await core(
        flow=replace(DUMMY_FLOW, event_hooks={"cases.report.submitted": report})
    )
    await service.registry.dispatch_event("cases.report.submitted", service, "good")
    assert seen == ["good"]
    with pytest.raises(ValueError):
        await service.registry.dispatch_event("cases.report.submitted", service, "bad")
    service.registry.disable(CaseKind.REPORT)
    await service.registry.dispatch_event("cases.report.submitted", service, "off")
    assert seen == ["good", "bad"]


async def test_merged_hook_retries_and_co_reporter_cooldown_is_enforced():
    seen = []

    async def merged(service, case, joined):
        seen.append(joined["submission_id"])
        if len(seen) == 1:
            raise RuntimeError("Could not acknowledge merge")

    service, _, _ = await core(flow=replace(DUMMY_FLOW, on_merged=merged))
    case = await opened(service, dedup_key="report:subject-secret")
    second = await service.open(
        kind=CaseKind.REPORT,
        origin=Origin("discord", "merge", "en"),
        opener=Party(discord_id="43"),
        subject=SUBJECT,
        dedup_key="report:subject-secret",
        member=member(43, ()),
    )
    assert second.number == case.number
    final = await service.resume_creation(case.number)
    assert final.progress["merged_submissions"] == ["merge"] and seen == [
        "merge",
        "merge",
    ]
    from xcore_discord_bot.cases.model import Cooldown

    with pytest.raises(Cooldown):
        await service.open(
            kind=CaseKind.REPORT,
            origin=Origin("discord", "merge-too-soon"),
            opener=Party(discord_id="43"),
            subject=SUBJECT,
            dedup_key="report:subject-secret",
            member=member(43, ()),
        )


async def test_issuer_linked_names_are_loaded_by_service_without_senior_override():
    flow = replace(DUMMY_FLOW, kind=CaseKind.APPEAL)
    service, _, _ = await core(flow=flow)
    case = await service.open(
        kind=CaseKind.APPEAL,
        origin=Origin("game", "appeal"),
        opener=OPENER,
        subject=SUBJECT,
        payload={"ban_snapshot": {"admin_name": "Issuer"}},
    )

    async def players(discord_id):
        assert discord_id == "101"
        return [SimpleNamespace(nickname="Issuer", username="admin")]

    service.players = SimpleNamespace(find_players_by_discord_id=players)
    with pytest.raises(AccessDenied):
        await service.take(case.number, member(101, (10, 20)))


async def test_cog_commands_defer_check_roles_and_keep_complaints_private():
    from xcore_discord_bot.cases.panel import CasePanel

    service, _, bot = await core(flow=DUMMY_FLOW)
    bot.container.case_panel = CasePanel(service, bot)
    case = await opened(service)
    cog = CasesCog(bot)
    denied = Interaction(bot, user=member(7, ()))
    await CasesCog.find.callback(cog, denied, str(case.number))
    assert denied.events[0][0] == "defer" and denied.events[-1][1] == t(
        "en", "error-not-allowed"
    )
    reviewer = Interaction(bot, user=member(7, (30,)))
    await CasesCog.find.callback(cog, reviewer, str(case.number))
    assert str(case.number) in reviewer.events[-1][1]
    senior = Interaction(bot, user=member(7, (20,)))
    await CasesCog.panel_publish.callback(cog, senior)
    assert "https://discord.com/" in senior.events[-1][1]
    await service.store.set_locale_pref(7, "uk", source="manual")
    language = Interaction(bot, user=member(7, ()))
    await CasesCog.language.callback(cog, language)
    assert language.events[0][0] == "message" and language.events[0][1] == t(
        "uk", "language-select-prompt"
    )


async def test_startup_maps_only_checks_management_and_does_not_create_forum_tags():
    service, _, bot = await core(
        flow=replace(DUMMY_FLOW, kind=CaseKind.MAP, panel=None), cases_maps_enabled=True
    )
    forum = bot.channels[400]
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.MAP) and forum.available_tags == []
    permissions = discord.Permissions.all()
    permissions.manage_threads = False
    forum.role_permissions[999] = permissions
    await initialize_flows(service)
    assert not service.registry.enabled(CaseKind.MAP)


async def test_translator_localizes_new_command_names_descriptions_and_parameters():
    from discord import app_commands

    translator = XCoreTranslator()
    command = CasesCog.find
    for locale in (discord.Locale.russian, discord.Locale.ukrainian):
        name = await translator.translate(
            app_commands.locale_str("find"),
            locale,
            SimpleNamespace(
                location=app_commands.TranslationContextLocation.command_name,
                data=command,
            ),
        )
        assert name == t(locale.value, "cmd-cases-find-name")
        description = await translator.translate(
            app_commands.locale_str("Find a case by its number"),
            locale,
            SimpleNamespace(
                location=app_commands.TranslationContextLocation.command_description,
                data=command,
            ),
        )
        assert description == t(locale.value, "cmd-cases-find-description")


def test_card_privacy_includes_ipv6_and_all_known_uuid_forms():
    assert "2001:db8::1" not in safe_text("Address 2001:db8::1")
    assert "uuid-base64=" not in safe_text("uuid-base64=", secrets=["uuid-base64="])
    assert "::1" not in safe_text("IP ::1")


def test_release_allows_senior_role_without_plain_admin_role():
    from cases_fakes import settings

    assert roles_for(settings(), CaseKind.REPORT, "release") == (10, 20)


def as_real_bot(bot, service):
    """Give the fake what the real listeners of ``XCoreDiscordBot`` use from ``self``."""
    bot.container = SimpleNamespace(cases=service)
    bot.settings = service.settings
    bot._dispatch_case_hook = lambda hook, value: XCoreDiscordBot._dispatch_case_hook(
        bot, hook, value
    )


async def test_all_flags_off_the_bot_listeners_do_nothing():
    service, db, bot = await core(flow=DUMMY_FLOW)
    seen = []

    async def hook(service, payload):
        seen.append(payload)

    service.registry = FlowRegistry()
    service.registry.register(
        replace(
            DUMMY_FLOW, on_thread_create=hook, on_message=hook, on_thread_delete=hook
        )
    )
    as_real_bot(bot, service)
    assert not service.settings.cases_enabled

    def forbidden(*args, **kwargs):
        pytest.fail("With every flag off nothing reads or writes case storage")

    for name in ("remember_interaction", "by_thread", "get", "list_cases"):
        setattr(service.store, name, forbidden)
    await XCoreDiscordBot.on_interaction(
        bot, Interaction(bot, locale=discord.Locale.ukrainian)
    )
    await XCoreDiscordBot.on_thread_create(bot, "thread")
    await XCoreDiscordBot.on_raw_thread_delete(
        bot, SimpleNamespace(thread_id=123, parent_id=200)
    )
    await XCoreDiscordBot._dispatch_case_hook(bot, "on_message", "message")
    await reconcile_cases(service)
    assert seen == [] and db["discord_user_prefs"].docs == []
    assert panel_view(service.registry).children == []


async def test_enabled_flow_gets_events_and_the_auto_language_row():
    seen = []

    async def hook(service, payload):
        seen.append(payload)

    flow = replace(DUMMY_FLOW, on_thread_create=hook, on_thread_delete=hook)
    service, db, bot = await core(flow=flow, cases_reports_enabled=True)
    as_real_bot(bot, service)
    interaction = Interaction(bot, locale=discord.Locale.ukrainian)
    await XCoreDiscordBot.on_interaction(bot, interaction)
    assert await service.store.get_locale_pref(101) == LocalePref("uk", "auto")
    written = dict(db["discord_user_prefs"].docs[0])
    # The same language again is not written again.
    await XCoreDiscordBot.on_interaction(bot, interaction)
    assert db["discord_user_prefs"].docs == [written]
    interaction.type = discord.InteractionType.autocomplete
    interaction.locale = discord.Locale.russian
    await XCoreDiscordBot.on_interaction(bot, interaction)
    assert await service.store.get_locale_pref(101) == LocalePref("uk", "auto")
    await XCoreDiscordBot.on_thread_create(bot, "thread")
    payload = SimpleNamespace(thread_id=123, parent_id=200)
    await XCoreDiscordBot.on_raw_thread_delete(bot, payload)
    assert seen == ["thread", payload]


async def test_one_failing_flow_hook_does_not_stop_the_others(caplog):
    seen = []

    async def broken(service, payload):
        raise RuntimeError("flow bug")

    async def hook(service, payload):
        seen.append(payload)

    service, _, _ = await core(flow=replace(DUMMY_FLOW, on_thread_create=broken))
    service.registry.register(
        replace(DUMMY_FLOW, kind=CaseKind.APPEAL, on_thread_create=hook)
    )
    service.registry.enable(CaseKind.APPEAL)
    await service.registry.dispatch("on_thread_create", service, "thread")
    assert seen == ["thread"] and "flow bug" in caplog.text
    with pytest.raises(ValueError):
        await service.registry.dispatch("on_reaction", service, "x")


async def test_opening_from_discord_needs_the_member_and_respects_the_blacklist():
    service, _, bot = await core(
        flow=replace(DUMMY_FLOW, kind=CaseKind.MAP, panel=None, outcomes=None),
        cases_blacklist_role_id=40,
    )
    created = await bot.channels[400].create_thread(
        name="Map", embed=discord.Embed(title="author"), view=None
    )
    args = {
        "kind": CaseKind.MAP,
        "origin": Origin("forum", f"map:{created.thread.id}"),
        "opener": Party(discord_id="42"),
        "discord": {"forum_id": "400", "thread_id": str(created.thread.id)},
    }
    with pytest.raises(AccessDenied):
        await service.open(**args, member=member(42, (40,)))
    with pytest.raises(AccessDenied):
        await service.open(**args, member=member(43, ()))
    with pytest.raises(AccessDenied):
        await service.open(**{**args, "origin": Origin("discord", "panel")})
    assert await service.store.by_submission(args["origin"].submission_id) is None
    # A post whose author is no longer resolvable (an old post started by a reviewer).
    case = await service.open(**args)
    assert case.status == CaseStatus.OPEN and case.events[0]["actor"]["type"] == "bot"
    # No cooldown or cap for forum posts: they are limited by the forum itself.
    for n in range(4):
        other = await bot.channels[400].create_thread(
            name=f"Map {n}", embed=discord.Embed(title="author"), view=None
        )
        await service.open(
            **{
                **args,
                "origin": Origin("forum", f"map:{other.thread.id}"),
                "discord": {"forum_id": "400", "thread_id": str(other.thread.id)},
            },
            member=member(42, ()),
        )


async def test_no_card_or_evidence_message_carries_a_uuid_or_an_address():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(
        service,
        source="discord",
        payload={"statement": "from 203.0.113.9 uuid subject-secret"},
        evidence={
            "chat": [{"name": "Subject", "pid": 2, "text": "my ip 198.51.100.7"}],
            "context": {"map_name": "opener-secret"},
        },
    )
    await service.take(case.number, member())
    await service.reject(case.number, member(), reason_code="x", reason_text="10.0.0.1")
    everything = []
    for channel in bot.channels.values():
        for message in getattr(channel, "messages", []):
            everything.append(message.content)
            everything.extend(str(embed.to_dict()) for embed in message.embeds)
        everything.append(getattr(channel, "name", ""))
    everything.extend(bot.users[42].messages)
    text = "\n".join(everything)
    assert str(case.number) in text
    for secret in ("opener-secret", "subject-secret", "203.0.113.9", "198.51.100.7"):
        assert secret not in text
    assert "10.0.0.1" not in text


async def test_the_example_flow_of_the_contract_document_works(monkeypatch, tmp_path):
    from importlib.util import module_from_spec, spec_from_file_location

    from xcore_discord_bot import staff_actions
    from xcore_discord_bot.cases import registry as registry_module
    from xcore_discord_bot.cases.views import CaseActionButton, PanelButton

    readme = Path(registry_module.__file__).with_name("README.md").read_text("utf-8")
    source = tmp_path / "example.py"
    source.write_text(readme.rsplit("```python\n", 1)[1].split("```", 1)[0], "utf-8")
    registered = FlowRegistry()
    monkeypatch.setattr(registry_module, "flow_registry", registered)
    muted = []

    async def perform_mute(
        bot,
        player,
        duration,
        reason,
        actor,
        case_number=None,
        *,
        case_action_token=None,
    ):
        assert case_action_token
        muted.append((player.pid, duration, reason, actor.discord_id, case_number))
        return SimpleNamespace(audit_id="audit-1")

    monkeypatch.setattr(staff_actions, "perform_mute", perform_mute)
    # Loaded under the name a real flow module has, so its relative imports resolve.
    spec = spec_from_file_location("xcore_discord_bot.cases.flows.example", source)
    spec.loader.exec_module(module_from_spec(spec))
    registered.enable(CaseKind.REPORT)
    service, _, bot = await core(flow=registered.require(CaseKind.REPORT))
    player = SimpleNamespace(uuid="subject-secret", pid=7, nickname="Griefer")
    service.players.by_pid[7] = service.players.by_uuid["subject-secret"] = player

    click = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.REPORT).callback(click)
    form = click.events[0][1]
    form.inputs["player"]._value = "8"
    form.inputs["statement"]._value = "Destroyed the core"
    unknown = Interaction(bot, user=member(42, ()), id=700)
    await form.on_submit(unknown)
    assert unknown.events[-1][1] == "reports-player-unknown"
    form.inputs["player"]._value = "7"
    submit = Interaction(bot, user=member(42, ()), id=701)
    await form.on_submit(submit)
    case = await service.store.by_submission("discord:701")
    assert case.subject.player_pid == 7 and case.dedup_key == "report:subject-secret"

    press = Interaction(bot)
    await CaseActionButton(case.number, "mute").callback(press)
    modal = press.events[0][1]
    assert modal.inputs["duration"].default == "1d"
    assert modal.inputs["reason"].default == "Destroyed the core"
    modal.inputs["duration"]._value = "soon"
    modal.inputs["reason"]._value = "Griefing"
    invalid = Interaction(bot)
    await modal.on_submit(invalid)
    assert invalid.events[-1][1] == t("en", "cases-error-duration")
    modal.inputs["duration"]._value = "2d"
    asked = Interaction(bot)
    await modal.on_submit(asked)
    assert muted == [] and asked.events[-1][1] == "reports-mute-confirm"
    confirmed = Interaction(bot)
    await asked.events[-1][2]["view"].children[0].callback(confirmed)
    assert muted == [(7, timedelta(days=2), "Griefing", "101", case.number)]
    final = await service.get(case.number)
    assert final.status == CaseStatus.RESOLVED and final.resolution.code == "muted"
    assert final.resolution.audit_id == "audit-1"
    assert final.resolution.duration_ms == 172_800_000
    assert bot.users[42].messages == ["reports-outcome-resolved"]
