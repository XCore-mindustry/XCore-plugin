from __future__ import annotations

import re
from dataclasses import replace
from datetime import timedelta
from types import SimpleNamespace

import discord
import pytest
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import NOW, Interaction, core, member
from test_cases_service import opened

from xcore_discord_bot.cases.cards import (
    base_card,
    evidence_messages,
    sanitize_card,
    split_evidence,
    thread_title,
)
from xcore_discord_bot.cases.ids import CASE_PATTERN, PANEL_PATTERN
from xcore_discord_bot.cases.modals import CaseActionModal, OpenCaseModal
from xcore_discord_bot.cases.model import (
    ActionFailed,
    CaseKind,
    CaseStatus,
    FlowDisabled,
    InvalidForm,
    Origin,
    Party,
    Resolution,
)
from xcore_discord_bot.cases.panel import CasePanel, LanguageView
from xcore_discord_bot.cases.registry import (
    Action,
    Choice,
    FlowRegistry,
    FormField,
    PanelEntry,
    clean_values,
)
from xcore_discord_bot.cases.views import (
    CaseActionButton,
    ChoiceView,
    ConfirmView,
    PanelButton,
    UserChoiceView,
    case_view,
    panel_view,
)
from xcore_discord_bot.i18n import SUPPORTED, LocalePref, t
from xcore_discord_bot.staff_actions import InvalidDuration

REASONS = (
    Choice("no_evidence", "cases-status-open"),
    Choice("no_violation", "cases-status-taken"),
)
REASON_FIELD = FormField("why", "cases-reason")


def pressed(interaction):
    """What the person saw: the kind of the first response and the last text."""
    return interaction.events[0][0], interaction.events[-1][1]


async def press(bot, number, verb, **kwargs):
    interaction = Interaction(bot, **kwargs)
    await CaseActionButton(number, verb).callback(interaction)
    return interaction


def with_action(verb, handler, **kwargs):
    action = Action(verb, "cases-button-" + verb, handler, **kwargs)
    return replace(DUMMY_FLOW, actions=(*DUMMY_FLOW.actions, action))


async def test_panel_publish_then_edit_in_place_and_adopt_failed_send():
    service, db, bot = await core(flow=DUMMY_FLOW)
    panel = CasePanel(service, bot)
    bot.failures.arm("panel_send", after=True)
    with pytest.raises(RuntimeError):
        await panel.publish()
    first = await panel.publish()
    message = bot.channels[100].messages[0]
    assert len(bot.channels[100].messages) == 1
    assert [item.item.custom_id for item in message.view.children] == [
        "xc:1:panel:open:report",
        "xc:1:panel:lang",
    ]
    # One message for everyone: the text in every language, English words on the buttons.
    assert all(t(locale, "cases-panel-text") in message.content for locale in SUPPORTED)
    assert [item.item.label for item in message.view.children] == ["Report", "Language"]
    assert str(message.view.children[0].item.emoji) == "📣"
    bot.calls.clear()
    assert (await panel.publish()) == first and await panel.refresh() is False
    assert bot.calls == []
    assert db["case_panels"].docs[0]["message_id"] == str(first)
    service.registry.disable(DUMMY_FLOW.kind)
    assert await panel.refresh() is True
    assert message.view.children == [] and len(bot.channels[100].messages) == 1
    assert len(db["case_panels"].docs) == 1


async def test_panel_moves_with_the_setting_and_survives_a_deleted_message():
    service, db, bot = await core(flow=DUMMY_FLOW)
    panel = CasePanel(service, bot)
    first = await panel.publish()
    old = bot.channels[100].messages[0]
    service.settings = service.settings.model_copy(
        update={"cases_panel_channel_id": 500}
    )
    service.threads.settings = service.settings
    second = await panel.publish()
    assert second != first and len(bot.channels[500].messages) == 1
    # The old message stays, without buttons that would lead nowhere.
    assert bot.channels[100].messages == [old] and old.view is None
    bot.channels[500].messages.clear()
    third = await panel.publish()
    assert third != second and len(bot.channels[500].messages) == 1
    assert db["case_panels"].docs[0]["message_id"] == str(third)
    service.settings = service.settings.model_copy(update={"cases_panel_channel_id": 0})
    with pytest.raises(ValueError, match="CASES_PANEL_CHANNEL_ID"):
        await panel.publish()
    assert await panel.refresh() is False


async def test_flags_off_mean_no_buttons_no_hooks_no_database_reads():
    calls = []

    async def hook(service, value):
        calls.append(value)

    flow = replace(
        DUMMY_FLOW, on_message=hook, on_thread_create=hook, on_thread_delete=hook
    )
    service, _, _ = await core(flow=flow)
    case = await opened(service)
    service.registry.disable(flow.kind)
    assert panel_view(service.registry).children == []
    assert case_view(service.registry, case, "en").children == []
    service.store.by_thread = lambda _: pytest.fail("No disabled hook may read storage")
    for name in ("on_message", "on_thread_create", "on_thread_delete"):
        await service.registry.dispatch(name, service, object())
    assert calls == []
    with pytest.raises(FlowDisabled):
        await service.take(case.number, member())


async def test_panel_modal_opens_case_and_blacklist_is_rechecked():
    service, _, bot = await core(flow=DUMMY_FLOW, cases_blacklist_role_id=40)
    click = Interaction(bot, user=member(42, ()), locale=discord.Locale.ukrainian)
    await PanelButton(DUMMY_FLOW.kind).callback(click)
    assert click.events[0][0] == "modal"
    modal = click.events[0][1]
    modal.inputs["note"]._value = "Statement"
    submit = Interaction(
        bot, user=member(42, ()), locale=discord.Locale.ukrainian, id=556
    )
    await modal.on_submit(submit)
    case = await service.store.by_submission("discord:556")
    assert case.origin.locale == "uk" and case.payload["statement"] == "Statement"
    blocked = Interaction(bot, user=member(42, (40,)), id=557)
    await modal.on_submit(blocked)
    assert blocked.events[-1][1] == t("en", "error-opening-blocked")
    assert await service.store.by_submission("discord:557") is None


async def test_language_select_is_private_and_manual_wins_concurrent_auto():
    service, _, bot = await core(flow=DUMMY_FLOW)
    view = LanguageView(service.store, 42, "en")
    select = view.children[0]
    select._values = ["uk"]
    intruder = Interaction(bot, user=member(43))
    await select.callback(intruder)
    assert await service.store.get_locale_pref(42) is None
    interaction = Interaction(bot, user=member(42))
    await select.callback(interaction)
    assert await service.store.get_locale_pref(42) == LocalePref("uk", "manual")
    await service.store.remember_interaction(interaction)
    assert await service.store.get_locale_pref(42) == LocalePref("uk", "manual")


async def test_cards_privacy_budget_titles_and_evidence_chunks():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(
        service,
        evidence={
            "chat": [{"name": "Name", "pid": 2, "text": "text" * 1000}] * 30,
            "block_summary": {"built": 1, "destroyed": 2, "configured": 3},
        },
    )
    original = base_card(case, "en")
    original.description = "opener-secret subject-secret 192.0.2.1 " + "a" * 5000
    original.add_field(name="data", value="12345678-1234-1234-1234-123456789abc " * 100)
    clean = sanitize_card(original, case)
    rendered = str(clean.to_dict())
    assert (
        "opener-secret" not in rendered
        and "subject-secret" not in rendered
        and "192.0.2.1" not in rendered
    )
    assert "12345678-1234" not in rendered and len(clean) <= 6000
    messages = evidence_messages(case, "en")
    assert len(messages) > 1 and all(len(m) < 1900 for m in messages)
    assert all(m.startswith("```") and m.endswith("```") for m in messages)
    title = thread_title(
        replace(case, subject=Party(player_name="[red]Name" * 40)), "en"
    )
    assert (
        title.startswith(f"#{case.number} · Report · ")
        and len(title) <= 100
        and "[red]" not in title
    )
    assert all(len(chunk) < 1900 for chunk in split_evidence("x" * 5000))


async def test_user_thread_announces_only_opener_messages():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service, source="discord")
    thread = bot.channels[int(case.discord["thread_id"])]
    user_thread = bot.channels[int(case.discord["user_thread_id"])]
    message = SimpleNamespace(
        author=member(42),
        channel=user_thread,
        jump_url="https://discord.com/channels/123/456/789",
    )
    before = len(thread.messages)
    await service.registry.dispatch("on_message", service, message)
    assert (
        len(thread.messages) == before + 1
        and message.jump_url in thread.messages[-1].content
    )
    message.author = member(101)
    await service.registry.dispatch("on_message", service, message)
    assert len(thread.messages) == before + 1


async def test_buttons_work_from_the_custom_id_alone_after_a_restart():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    card = bot.channels[int(case.discord["thread_id"])].messages[0]
    # A restart: no view object, no cached case, only the id on the message.
    service.store._active.clear()
    interaction = Interaction(bot)
    recovered = await CaseActionButton.from_custom_id(
        interaction,
        discord.ui.Button(),
        re.fullmatch(CASE_PATTERN, f"xc:1:case:take:{case.number}"),
    )
    assert (recovered.number, recovered.verb) == (case.number, "take")
    await recovered.callback(interaction)
    assert pressed(interaction) == (
        "defer",
        t("en", "cases-updated", number=case.number),
    )
    assert (await service.get(case.number)).status == CaseStatus.TAKEN
    assert [item.item.custom_id for item in card.view.children] == [
        f"xc:1:case:{verb}:{case.number}" for verb in ("release", "resolve", "reject")
    ]
    # The second person pressed the Take they still saw: told who has it, nothing changes.
    stale = await press(bot, case.number, "take", user=member(102))
    assert pressed(stale) == (
        "defer",
        t("en", "error-already-handled-by", name="Staff 101"),
    )
    assert (await service.get(case.number)).assignee["discord_id"] == "101"
    panel = await PanelButton.from_custom_id(
        interaction,
        discord.ui.Button(),
        re.fullmatch(PANEL_PATTERN, "xc:1:panel:open:report"),
    )
    assert panel.kind == DUMMY_FLOW.kind
    language = await PanelButton.from_custom_id(
        interaction, discord.ui.Button(), re.fullmatch(PANEL_PATTERN, "xc:1:panel:lang")
    )
    assert language.kind is None


async def test_form_button_sends_the_modal_first_and_rechecks_on_submit():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    await service.store.set_locale_pref(101, "uk", source="manual")
    interaction = Interaction(bot, locale=discord.Locale.russian)

    def after_modal(name):
        original = getattr(service.store, name)

        async def checked(*args, **kwargs):
            assert interaction.events and interaction.events[0][0] == "modal", name
            return await original(*args, **kwargs)

        setattr(service.store, name, checked)
        return original

    originals = {
        name: after_modal(name)
        for name in ("get", "remember_interaction", "audits_for", "cas")
    }
    await CaseActionButton(case.number, "reject").callback(interaction)
    assert [event[0] for event in interaction.events] == ["modal"]
    modal = interaction.events[0][1]
    assert isinstance(modal, CaseActionModal)
    # The stored manual choice wins over the Russian client, with no read on the way.
    assert modal.title == t("uk", "cases-button-reject")
    assert list(modal.inputs) == ["reason_text"] and modal.timeout >= 600
    for name, original in originals.items():
        setattr(service.store, name, original)
    # The roles were removed while the form was open: the submit cannot act.
    submit = Interaction(bot, user=member(101, ()), locale=discord.Locale.russian)
    modal.inputs["reason_text"]._value = "No proof"
    await modal.on_submit(submit)
    assert pressed(submit) == ("defer", t("uk", "error-not-allowed"))
    # Somebody else submitting this modal object is refused as well.
    other = Interaction(bot, user=member(102))
    await modal.on_submit(other)
    assert pressed(other) == ("defer", t("en", "error-not-allowed"))
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    await modal.on_submit(Interaction(bot))
    final = await service.get(case.number)
    assert final.status == CaseStatus.REJECTED
    assert (final.resolution.reason_code, final.resolution.reason_text) == (
        "other",
        "No proof",
    )


async def test_case_closed_while_the_form_was_open_names_who_closed_it():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    click = await press(bot, case.number, "reject")
    modal = click.events[0][1]
    await service.reject(case.number, member(102), reason_code="no_evidence")
    modal.inputs["reason_text"]._value = "Too late"
    submit = Interaction(bot)
    await modal.on_submit(submit)
    assert pressed(submit) == (
        "defer",
        t("en", "error-already-handled-by", name="Staff 102"),
    )
    final = await service.get(case.number)
    assert final.resolution.by_discord_id == "102"
    assert [e["type"] for e in final.events].count("rejected") == 1
    # The same press on the closed card: deferred, explained, no form.
    again = await press(bot, case.number, "reject")
    assert pressed(again) == (
        "defer",
        t("en", "error-already-handled-by", name="Staff 102"),
    )


async def test_press_without_a_cached_case_is_answered_from_the_database():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    service.store._active.clear()
    first = await press(bot, case.number, "reject")
    assert pressed(first) == ("defer", t("en", "cases-press-again"))
    second = await press(bot, case.number, "reject")
    assert second.events[0][0] == "modal"
    missing = await press(bot, 999_999, "reject")
    assert pressed(missing) == ("defer", t("en", "error-not-found"))


async def test_press_is_refused_before_any_form_for_role_conflict_and_flag():
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    for user in (member(7, ()), member(7, (30,)), member(42, (10, 20))):
        for verb in ("take", "reject", "resolve"):
            click = await press(bot, case.number, verb, user=user)
            assert pressed(click) == ("defer", t("en", "error-not-allowed"))
    assert (await service.get(case.number)).revision == case.revision
    service.registry.disable(DUMMY_FLOW.kind)
    off = await press(bot, case.number, "take")
    assert pressed(off) == ("defer", t("en", "error-try-again-later"))
    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_conflict_that_appears_while_the_form_is_open_stops_the_submit():
    flow = replace(DUMMY_FLOW, kind=CaseKind.COMPLAINT, outcomes=None)
    service, _, bot = await core(flow=flow)
    case = await service.open(
        kind=CaseKind.COMPLAINT,
        origin=Origin("discord", "c"),
        opener=Party(discord_id="42", discord_name="Member"),
        member=member(42, ()),
    )
    senior = member(201, (20,))
    click = await press(bot, case.number, "reject", user=senior)
    modal = click.events[0][1]
    await service.set_subject(case.number, member(200, (20,)), Party(discord_id="201"))
    modal.inputs["reason_text"]._value = "Unfounded"
    submit = Interaction(bot, user=senior)
    await modal.on_submit(submit)
    assert pressed(submit) == ("defer", t("en", "error-not-allowed"))
    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_reject_form_offers_the_reasons_of_the_flow_and_validates_them():
    flow = replace(
        DUMMY_FLOW,
        reject_reasons=REASONS,
        card_builder=lambda case, locale: base_card(case, locale, reasons=REASONS),
    )
    service, _, bot = await core(flow=flow)
    case = await opened(service)
    click = await press(bot, case.number, "reject")
    modal = click.events[0][1]
    select, details = modal.inputs["reason_code"], modal.inputs["reason_text"]
    assert [option.value for option in select.options] == [c.value for c in REASONS]
    assert select.required and not details.required
    # Every component sits under a label, as modals require for selects.
    assert all(isinstance(item, discord.ui.Label) for item in modal.children)
    assert [item.text for item in modal.children] == [
        t("en", "cases-reason"),
        t("en", "cases-reject-details"),
    ]
    for values in ([], ["made_up"]):
        select._values = values
        refused = Interaction(bot)
        await modal.on_submit(refused)
        assert pressed(refused) == ("defer", t("en", "cases-form-invalid"))
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    select._values = ["no_violation"]
    details._value = "  Checked the logs  "
    await modal.on_submit(Interaction(bot))
    final = await service.get(case.number)
    assert (final.resolution.reason_code, final.resolution.reason_text) == (
        "no_violation",
        "Checked the logs",
    )
    card = bot.channels[int(case.discord["thread_id"])].messages[0].embeds[0]
    assert any(
        t("en", "cases-status-taken") in field.value
        and "Checked the logs" in field.value
        for field in card.fields
    )


def test_submitted_values_are_checked_against_the_form():
    fields = (
        FormField("duration", "cases-note", max_length=5),
        FormField("why", "cases-reason", required=False),
        FormField("kind", "cases-reason", choices=REASONS, required=False),
    )
    assert clean_values(fields, {"duration": " 7d ", "extra": 3}) == {
        "duration": "7d",
        "why": "",
        "kind": "",
        "extra": "3",
    }
    assert clean_values(fields, {"duration": "1234567"})["duration"] == "12345"
    for values in (None, {}, {"duration": "   "}, {"duration": "1d", "kind": "x"}):
        with pytest.raises(InvalidForm):
            clean_values(fields, values)
    with pytest.raises(ValueError):
        FormField("", "cases-note")
    with pytest.raises(ValueError):
        FormField("a", "cases-note", choices=REASONS * 13)


async def test_form_can_depend_on_the_case_and_be_prefilled():
    def fields(case, locale):
        return (
            FormField("reason", "cases-reason", default=case.payload["statement"]),
            FormField("duration", "cases-note", default="7d", max_length=10),
        )

    async def ban(context):
        return await context.run(
            lambda case: _done(
                Resolution("banned", reason_text=context.values["reason"])
            )
        )

    service, _, bot = await core(flow=with_action("ban", ban, fields=fields))
    case = await opened(service, payload={"statement": "Griefing the core"})
    click = await press(bot, case.number, "ban")
    modal = click.events[0][1]
    assert modal.title == t("en", "cases-button-ban")
    assert modal.inputs["reason"].default == "Griefing the core"
    assert modal.inputs["duration"].default == "7d"
    modal.inputs["reason"]._value = "Griefing"
    modal.inputs["duration"]._value = "3d"
    await modal.on_submit(Interaction(bot))
    final = await service.get(case.number)
    assert (
        final.resolution.code == "banned" and final.resolution.reason_text == "Griefing"
    )


async def _done(resolution):
    return resolution


async def test_confirmation_step_runs_the_action_only_on_confirm_by_the_same_person():
    seen = []

    async def ban(context):
        seen.append(dict(context.values))
        if context.values.get("confirmed") != "yes":
            await context.confirm("Ban this player?")
            return None
        return await context.run(lambda case: _done(Resolution("banned")))

    service, _, bot = await core(flow=with_action("ban", ban))
    case = await opened(service)
    click = await press(bot, case.number, "ban")
    assert [event[0] for event in click.events] == ["defer", "followup"]
    assert click.events[-1][1] == "Ban this player?"
    view = click.events[-1][2]["view"]
    assert isinstance(view, ConfirmView) and click.events[-1][2]["ephemeral"]
    assert [item.label for item in view.children] == [
        t("en", "button-confirm"),
        t("en", "button-cancel"),
    ]
    intruder = Interaction(bot, user=member(102))
    assert await view.interaction_check(intruder) is False
    assert pressed(intruder) == ("message", t("en", "error-not-allowed"))
    cancel = Interaction(bot)
    assert await view.interaction_check(cancel)
    await view.children[1].callback(cancel)
    assert cancel.events == [
        ("edit_message", {"content": t("en", "cases-cancelled"), "view": None})
    ]
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    # A used step cannot be pressed again.
    assert await view.interaction_check(Interaction(bot)) is False
    again = await press(bot, case.number, "ban")
    confirm = Interaction(bot)
    await again.events[-1][2]["view"].children[0].callback(confirm)
    assert pressed(confirm) == ("defer", t("en", "cases-updated", number=case.number))
    assert seen == [{}, {}, {"confirmed": "yes"}]
    final = await service.get(case.number)
    assert final.status == CaseStatus.RESOLVED and final.resolution.code == "banned"


async def test_choice_steps_carry_their_answers_into_the_next_run():
    async def accept(context):
        if "server" not in context.values:
            await context.choose(
                "Which server?",
                "server",
                [discord.SelectOption(label="Mini PvP", value="mini-pvp")],
            )
            return None
        if "reviewer" not in context.values:
            await context.choose_user("Who checked it?", "reviewer")
            return None
        values = context.values
        return f"{values['server']} · {values['reviewer']} · {values['reviewer_name']}"

    service, _, bot = await core(flow=with_action("accept", accept))
    case = await opened(service)
    click = await press(bot, case.number, "accept")
    first = click.events[-1][2]["view"]
    assert isinstance(first, ChoiceView) and click.events[-1][1] == "Which server?"
    assert first.select.placeholder == t("en", "cases-choice-placeholder")
    first.select._values = ["mini-pvp"]
    step = Interaction(bot)
    await first.select.callback(step)
    second = step.events[-1][2]["view"]
    assert isinstance(second, UserChoiceView) and step.events[0][0] == "defer"
    second.select._values = [member(555)]
    last = Interaction(bot)
    await second.select.callback(last)
    assert pressed(last) == ("defer", "mini-pvp · 555 · Staff 555")
    # The handler closed nothing, so the case is untouched.
    assert (await service.get(case.number)).revision == case.revision


async def test_failed_action_is_explained_and_leaves_the_buttons_usable():
    async def mute(context):
        async def staff_action(case):
            raise InvalidDuration("bad")

        return await context.run(staff_action)

    async def grant(context):
        raise ActionFailed("The badge is already granted.")

    flow = with_action("mute", mute)
    flow = replace(
        flow, actions=(*flow.actions, Action("grant", "cases-button-grant", grant))
    )
    service, _, bot = await core(flow=flow)
    case = await opened(service)
    card = bot.channels[int(case.discord["thread_id"])].messages[0]
    click = await press(bot, case.number, "mute", locale=discord.Locale.russian)
    assert pressed(click) == ("defer", t("ru", "cases-error-duration"))
    current = await service.get(case.number)
    assert current.status == CaseStatus.OPEN and current.pending_action is None
    assert len(card.view.children) == 5
    own = await press(bot, case.number, "grant")
    assert pressed(own) == ("defer", "The badge is already granted.")
    # Pressing again at once is a new attempt, not a "duplicate press".
    retry = await press(bot, case.number, "mute")
    assert pressed(retry) == ("defer", t("en", "cases-error-duration"))


async def test_hidden_actions_and_untakeable_flows_are_refused_as_well_as_hidden():
    async def recheck(context):
        return "rechecked"

    flow = with_action(
        "recheck", recheck, visible=lambda case: "deep_checks_pending" in case.flags
    )
    service, _, bot = await core(flow=replace(flow, takeable=False))
    case = await opened(service)
    verbs = [item.verb for item in case_view(service.registry, case, "en").children]
    assert verbs == ["resolve", "reject"]
    for verb in ("recheck", "take", "future_verb"):
        click = await press(bot, case.number, verb)
        assert pressed(click) == ("defer", t("en", "error-not-allowed"))
    flagged = await service.checkpoint(
        case.number, set_fields={"flags": ["deep_checks_pending"]}
    )
    verbs = [item.verb for item in case_view(service.registry, flagged, "en").children]
    assert verbs == ["resolve", "recheck", "reject"]
    click = await press(bot, case.number, "recheck")
    assert pressed(click) == ("defer", "rechecked")


async def test_card_buttons_follow_the_stored_case():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    def verbs(current):
        return [
            item.verb for item in case_view(service.registry, current, "en").children
        ]

    assert verbs(case) == ["take", "resolve", "reject"]
    assert verbs(await service.take(case.number, member())) == [
        "release",
        "resolve",
        "reject",
    ]
    assert verbs(replace(case, pending_action={"token": "x"})) == []
    closed = await service.reject(case.number, member(), reason_code="no_evidence")
    assert verbs(closed) == []
    styles = [
        item.item.style for item in case_view(service.registry, case, "en").children
    ]
    assert styles[-1] == discord.ButtonStyle.danger


async def test_panel_entry_with_a_lookup_defers_first_and_reports_errors():
    seen = []

    async def handler(context):
        seen.append((context.kind, context.locale, context.interaction.user.id))
        if len(seen) == 2:
            raise ActionFailed("You have no active ban.")
        await context.interaction.followup.send("Pick a ban", ephemeral=True)

    flow = replace(DUMMY_FLOW, panel=PanelEntry("cases-kind-appeal", handler=handler))
    service, db, bot = await core(flow=flow, cases_blacklist_role_id=40)
    click = Interaction(bot, user=member(42, ()), locale=discord.Locale.ukrainian)
    await PanelButton(DUMMY_FLOW.kind).callback(click)
    assert pressed(click) == ("defer", "Pick a ban")
    assert seen == [(DUMMY_FLOW.kind, "uk", 42)]
    assert db["discord_user_prefs"].docs[0]["locale"] == "uk"
    failed = Interaction(bot, user=member(42, ()))
    await PanelButton(DUMMY_FLOW.kind).callback(failed)
    assert pressed(failed) == ("defer", "You have no active ban.")
    blocked = Interaction(bot, user=member(43, (40,)))
    await PanelButton(DUMMY_FLOW.kind).callback(blocked)
    assert pressed(blocked) == ("message", t("en", "error-opening-blocked"))
    service.registry.disable(DUMMY_FLOW.kind)
    off = Interaction(bot, user=member(44, ()))
    await PanelButton(DUMMY_FLOW.kind).callback(off)
    assert pressed(off) == ("message", t("en", "error-try-again-later"))
    assert len(seen) == 2


async def test_language_button_answers_privately_in_the_current_language():
    service, _, bot = await core(flow=DUMMY_FLOW)
    click = Interaction(bot, user=member(42, ()), locale=discord.Locale.russian)
    await PanelButton().callback(click)
    kind, text, kwargs = click.events[0]
    assert (kind, text) == ("message", t("ru", "language-select-prompt"))
    assert kwargs["ephemeral"] and isinstance(kwargs["view"], LanguageView)
    assert await service.store.get_locale_pref(42) == LocalePref("ru", "auto")


async def test_open_form_reports_the_number_cooldown_and_file_problems():
    class Attachment:
        id, filename, size = 9, "proof.png", 10

        async def to_file(self):
            return "file"

    def modal(context):
        return OpenCaseModal(
            context,
            title_key="cases-kind-report",
            fields=(FormField("note", "cases-note", paragraph=True),),
            submit=DUMMY_FLOW.panel.modal_factory(context).submit,
            files=True,
        )

    flow = replace(
        DUMMY_FLOW, panel=PanelEntry("cases-kind-report", modal_factory=modal)
    )
    service, _, bot = await core(flow=flow)
    click = Interaction(bot, user=member(42, ()), locale=discord.Locale.russian)
    await PanelButton(DUMMY_FLOW.kind).callback(click)
    form = click.events[0][1]
    assert click.events[0][0] == "modal" and form.title == t("ru", "cases-kind-report")
    upload = form.files
    # Optional really means optional: the payload says "at least zero files".
    assert upload.to_component_dict()["min_values"] == 0 and not upload.required
    form.inputs["note"]._value = "He broke the core"
    upload._values = [Attachment()]
    submit = Interaction(
        bot, user=member(42, ()), locale=discord.Locale.russian, id=600
    )
    await form.on_submit(submit)
    case = await service.store.by_submission("discord:600")
    assert pressed(submit) == (
        "defer",
        t("ru", "cases-registered", number=case.number)
        + f"\n<#{case.discord['user_thread_id']}>",
    )
    assert case.evidence["attachments"][0]["file_name"] == "proof.png"
    assert case.origin.locale == "ru" and case.events[0]["actor"]["discord_id"] == "42"
    soon = Interaction(bot, user=member(42, ()), locale=discord.Locale.russian, id=601)
    await form.on_submit(soon)
    assert pressed(soon) == ("defer", t("ru", "error-cooldown", seconds=60))
    assert await service.store.by_submission("discord:601") is None
    stranger = Interaction(bot, user=member(43, ()), id=602)
    await form.on_submit(stranger)
    assert pressed(stranger) == ("defer", t("en", "error-not-allowed"))

    async def broken(*args):
        raise RuntimeError("Discord refused the file")

    service.threads.upload_attachments = broken
    service.now = lambda: NOW + timedelta(minutes=5)
    late = Interaction(bot, user=member(42, ()), id=603)
    await form.on_submit(late)
    second = await service.store.by_submission("discord:603")
    # The case exists, so the person gets its number whatever else failed. This client is
    # English, and the ``auto`` language follows the latest interaction.
    assert second is not None and late.events[-1][1].startswith(
        t("en", "cases-registered", number=second.number)
        + "\n"
        + t("en", "cases-files-retry")
    )


def test_registry_refuses_flows_the_core_cannot_show():
    registry = FlowRegistry()
    registry.register(DUMMY_FLOW)
    handler = DUMMY_FLOW.actions[0].handler
    with pytest.raises(ValueError, match="already registered"):
        registry.register(DUMMY_FLOW)
    for verb in ("take", "release", "reject", "reopen"):
        with pytest.raises(ValueError, match="Reserved"):
            FlowRegistry().register(
                replace(DUMMY_FLOW, actions=(Action(verb, "cases-note", handler),))
            )
    bad = (
        replace(DUMMY_FLOW, actions=DUMMY_FLOW.actions * 2),
        replace(DUMMY_FLOW, actions=(Action("Bad Verb", "cases-note", handler),)),
        replace(
            DUMMY_FLOW,
            actions=tuple(Action(f"verb{n}", "cases-note", handler) for n in range(24)),
        ),
        replace(
            DUMMY_FLOW,
            actions=(
                Action(
                    "ban",
                    "cases-note",
                    handler,
                    fields=tuple(FormField(f"f{n}", "cases-note") for n in range(6)),
                ),
            ),
        ),
        replace(DUMMY_FLOW, kind=CaseKind.MAP),
    )
    for flow in bad:
        with pytest.raises(ValueError):
            FlowRegistry().register(flow)
    # Two kinds may use one verb with different forms: the form belongs to the case.
    registry.register(
        replace(
            DUMMY_FLOW,
            kind=CaseKind.APPEAL,
            actions=(Action("resolve", "cases-note", handler, fields=(REASON_FIELD,)),),
        )
    )
    with pytest.raises(FlowDisabled):
        registry.action(CaseKind.APPEAL, "resolve")
    with pytest.raises(ValueError, match="No registered flow"):
        registry.enable(CaseKind.BADGE)
    registry.enable(CaseKind.APPEAL)
    assert registry.action(CaseKind.APPEAL, "resolve").fields == (REASON_FIELD,)
    assert registry.action(CaseKind.APPEAL, "ban") is None
    assert registry.label_key(CaseKind.APPEAL, "resolve") == "cases-note"
    assert registry.label_key(CaseKind.APPEAL, "take") == "cases-button-take"


@pytest.mark.parametrize("kind", list(CaseKind))
@pytest.mark.parametrize("discord_id", ["101", None])
async def test_assignee_card_uses_mention_or_sanitized_name(kind, discord_id):
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = replace(
        await opened(service),
        kind=kind,
        assignee={"discord_id": discord_id, "name": "Admin 203.0.113.25"},
    )
    card = sanitize_card(base_card(case, "en"), case)
    value = next(f.value for f in card.fields if f.name == t("en", "cases-assignee"))
    assert value == ("<@101>" if discord_id else "Admin …")
