from __future__ import annotations

import ast
import re
from pathlib import Path
from unittest.mock import AsyncMock

import discord
import pytest
from cases_appeals_fakes import appeal_core
from cases_badges_fakes import (
    badge_core,
    badge_form,
    choose,
    last_view,
    opened_badge,
    player_row,
    submit_form,
)
from cases_fakes import Interaction, member

from xcore_discord_bot.badges import BADGES, BadgeDef
from xcore_discord_bot.cases.flows import badges
from xcore_discord_bot.cases.flows._choice import PanelChoiceView
from xcore_discord_bot.cases.flows.badges import (
    BADGE_FLOW,
    requestable_badges,
)
from xcore_discord_bot.cases.ids import CASE_PATTERN
from xcore_discord_bot.cases.model import ActionFailed, CaseKind, CaseStatus
from xcore_discord_bot.cases.registry import PanelContext, flow_registry
from xcore_discord_bot.cases.views import CaseActionButton, PanelButton, panel_view
from xcore_discord_bot.i18n import SUPPORTED, loader, t


async def test_autoimport_and_disabled_panel_do_no_player_io():
    assert flow_registry.copy()._flows[CaseKind.BADGE] is BADGE_FLOW
    service, _, bot, *_ = await badge_core(enabled=False)
    service.player_store.find_players_by_discord_id = AsyncMock(
        side_effect=AssertionError("disabled I/O")
    )
    assert not panel_view(service.registry).children
    interaction = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.BADGE).callback(interaction)
    assert interaction.events == [
        ("message", t("en", "error-try-again-later"), {"ephemeral": True})
    ]
    service.player_store.find_players_by_discord_id.assert_not_awaited()


@pytest.mark.parametrize(
    "locale,code",
    [
        (discord.Locale.american_english, "en"),
        (discord.Locale.russian, "ru"),
        (discord.Locale.ukrainian, "uk"),
    ],
)
async def test_unlinked_refused_with_localized_real_link_instructions(locale, code):
    _, db, bot, *_ = await badge_core(rows=[])
    press = Interaction(bot, user=member(42, ()), locale=locale)
    await PanelButton(CaseKind.BADGE).callback(press)
    assert press.events[0][0] == "defer"
    assert press.events[-1][1] == t(code, "badges-link-needed")
    assert (
        "/discord link" in press.events[-1][1] and "/link code" in press.events[-1][1]
    )
    assert db["cases"].docs == []


async def test_canonical_allowlist_owned_and_non_grantable_never_offered(monkeypatch):
    service, _, bot, *_ = await badge_core(
        rows=[player_row(owned=("translator", "veteran"))],
        cases_badges_requestable=" ADMIN, UNKNOWN, MAP-MAKER, map-maker, Translator, veteran, season-champion, locked",
    )
    original = badges.get_badge
    monkeypatch.setattr(
        badges,
        "get_badge",
        lambda raw: (
            BadgeDef("locked", "Locked", grantable=False)
            if raw == "locked"
            else original(raw)
        ),
    )
    assert [b.id for b in requestable_badges(service.settings)] == [
        "map-maker",
        "translator",
        "veteran",
        "season-champion",
    ]
    click = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.BADGE).callback(click)
    assert [o.value for o in last_view(click).select.options] == [
        "map-maker",
        "season-champion",
    ]


@pytest.mark.parametrize(
    "allowlist,owned", [("admin,missing", ()), ("map-maker", ("map-maker",))]
)
async def test_no_available_badges_explained(allowlist, owned):
    _, db, bot, *_ = await badge_core(
        rows=[player_row(owned=owned)], cases_badges_requestable=allowlist
    )
    click = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.BADGE).callback(click)
    assert click.events[-1][1] == t("en", "badges-no-options")
    assert "view" not in click.events[-1][2] and db["cases"].docs == []


async def test_explicit_multiple_accounts_signed_zero_pid_and_bounded_pages():
    rows = [player_row(i + 1, pid=i - 1) for i in range(28)]
    service, _, bot, mutations, *_ = await badge_core(rows=rows)
    click = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.BADGE).callback(click)
    view = last_view(click)
    assert len(view.select.options) == 25
    assert (
        "ID -1" in view.select.options[0].label
        and "ID 0" in view.select.options[1].label
    )
    assert all(
        p["uuid"] not in str([(o.label, o.value) for o in view.options]) for p in rows
    )
    # A value from another page is rejected, including valid-but-not-offered accounts.
    forged = await choose(view, bot, "27")
    assert forged.events[0][1] == t("en", "cases-form-invalid") and not view.used
    await view.next.callback(Interaction(bot, user=member(42, ())))
    assert len(view.select.options) == 3 and view.next.disabled
    selected = await choose(view, bot, "27")
    form_click = await choose(last_view(selected), bot, "map-maker")
    await submit_form(bot, form_click.events[0][1])
    case = await service.store.by_submission("discord:700")
    assert case.subject.player_uuid == mutations["players"].docs[27]["uuid"]
    assert case.subject.player_pid == 26 and case.opener.discord_id == "42"


@pytest.mark.parametrize("kind", [CaseKind.BADGE, CaseKind.APPEAL])
async def test_panel_choices_navigation_access_page_values_used_and_timeout(kind):
    factory = badge_core if kind == CaseKind.BADGE else appeal_core
    service, _, bot, *_ = await factory(cases_blacklist_role_id=90)
    context = PanelContext(service, Interaction(bot, user=member(42, ())), kind, "en")
    chosen = AsyncMock()
    view = PanelChoiceView(
        context,
        [discord.SelectOption(label=str(i), value=str(i)) for i in range(51)],
        chosen,
        previous_label=t("en", kind.value + "s-previous"),
        next_label=t("en", kind.value + "s-next"),
    )
    assert (
        view.timeout == 60 and len(view.select.options) == 25 and view.previous.disabled
    )
    await view._next(Interaction(bot, user=member(43, ())))
    assert view.page == 0
    for user, key in [
        (member(43, ()), "error-not-allowed"),
        (member(42, (90,)), "error-opening-blocked"),
    ]:
        assert (await choose(view, bot, "0", user=user)).events[0][1] == t("en", key)
    service.registry.disable(kind)
    assert (await choose(view, bot, "0")).events[0][1] == t(
        "en", "error-try-again-later"
    )
    service.registry.enable(kind)
    for invalid in ("50", "admin"):
        assert (await choose(view, bot, invalid)).events[0][1] == t(
            "en", "cases-form-invalid"
        ) and not view.used
    for expected in (25, 1):
        await view._next(Interaction(bot, user=member(42, ())))
        assert len(view.select.options) == expected
    await view._next(Interaction(bot, user=member(42, ())))
    assert view.page == 2 and view.next.disabled
    await view._previous(Interaction(bot, user=member(42, ())))
    assert view.page == 1
    await view._next(Interaction(bot, user=member(42, ())))
    await choose(view, bot, "50")
    assert chosen.await_args.args[1] == "50"
    assert (await choose(view, bot, "50")).events[0][1] == t("en", "error-not-allowed")
    chosen.assert_awaited_once()
    await view.on_timeout()
    assert view.used
    timed = PanelChoiceView(
        context,
        [discord.SelectOption(label="0", value="0")],
        chosen,
        previous_label="Previous",
        next_label="Next",
    )
    await timed.on_timeout()
    assert (await choose(timed, bot, "0")).events[0][1] == t("en", "error-not-allowed")


async def test_private_choice_and_modal_reject_forgery_stale_disabled_blacklisted(
    monkeypatch,
):
    service, db, bot, *_ = await badge_core(cases_blacklist_role_id=90)
    click = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.BADGE).callback(click)
    view = last_view(click)

    async def forbidden(*args):
        pytest.fail("No player or store I/O may precede the modal first response")

    with monkeypatch.context() as patch:
        patch.setattr(service.player_store, "find_players_by_discord_id", forbidden)
        patch.setattr(service.player_store, "find_player_by_uuid", forbidden)
        patch.setattr(service.store, "remember_interaction", forbidden)
        form_click = await choose(view, bot, "map-maker")
    assert form_click.events[0][0] == "modal" and len(form_click.events) == 1
    form = form_click.events[0][1]
    assert form.files.min_values == 0 and form.files.max_values == 5
    assert (await submit_form(bot, form, user=member(43, ()))).events[-1][1] == t(
        "en", "error-not-allowed"
    )
    assert (await submit_form(bot, form, user=member(42, (90,)))).events[-1][1] == t(
        "en", "error-opening-blocked"
    )
    assert db["cases"].docs == []


@pytest.mark.parametrize(
    "change,key",
    [
        ("unlink", "badges-link-needed"),
        ("disappear", "badges-link-needed"),
        ("owned", "badges-already-owned"),
        ("allowlist", "badges-unavailable"),
        ("blacklist", "error-opening-blocked"),
        ("disabled", "error-try-again-later"),
    ],
)
async def test_submit_revalidates_current_state(change, key):
    service, db, bot, mutations, *_ = await badge_core(cases_blacklist_role_id=90)
    form = await badge_form(bot)
    user = member(42, ())
    row = mutations["players"].docs[0]
    if change == "unlink":
        row["discord_id"] = "43"
    elif change == "disappear":
        mutations["players"].docs.clear()
    elif change == "owned":
        row["unlocked_badges"] = ["map-maker"]
    elif change == "allowlist":
        service.settings = service.settings.model_copy(
            update={"cases_badges_requestable": "translator"}
        )
    elif change == "blacklist":
        user = member(42, (90,))
    else:
        service.registry.disable(CaseKind.BADGE)
    submit = await submit_form(bot, form, user=user)
    assert submit.events[0][0] == "defer"
    assert submit.events[-1][1] == t("en", key) and db["cases"].docs == []


async def test_submit_refuses_stale_link_list_and_other_account_uuid():
    service, db, bot, mutations, *_ = await badge_core(
        rows=[player_row(), player_row(2, discord_id="43")]
    )
    form = await badge_form(bot)
    snapshot = await service.player_store.find_player_by_uuid(
        mutations["players"].docs[0]["uuid"]
    )
    service.player_store.find_players_by_discord_id = AsyncMock(return_value=[snapshot])
    mutations["players"].docs[0]["discord_id"] = "43"
    refused = await submit_form(bot, form)
    assert (
        refused.events[-1][1] == t("en", "badges-link-needed")
        and db["cases"].docs == []
    )


async def test_duplicate_open_cooldown_cap_and_same_interaction_redelivery():
    service, db, bot, mutations, *_ = await badge_core(cases_open_cooldown_seconds=0)
    form = await badge_form(bot)
    await submit_form(bot, form)
    duplicate = await submit_form(bot, form, id=701)
    assert duplicate.events[-1][1] == t("en", "error-already-handled")
    assert len(db["cases"].docs) == 1
    service.settings = service.settings.model_copy(
        update={"cases_open_cooldown_seconds": 60, "cases_max_open_per_user": 1}
    )
    mutations["players"].docs[0]["unlocked_badges"] = ["map-maker"]
    retry = await submit_form(bot, form)
    assert retry.events[-1][1].startswith(
        t("en", "cases-registered", number=db["cases"].docs[0]["_id"])
    )
    assert len(db["cases"].docs) == 1
    translator_form = await badge_form(bot, badge="translator")
    assert (await submit_form(bot, translator_form, id=702)).events[-1][1] == t(
        "en", "cases-open-limit"
    )
    service.settings = service.settings.model_copy(
        update={"cases_max_open_per_user": 3}
    )
    assert (await submit_form(bot, translator_form, id=703)).events[-1][1] == t(
        "en", "error-cooldown", seconds=60
    )


async def test_case_snapshot_card_sanitized_and_core_owns_tags_and_user_thread():
    service, _, bot, mutations, *_ = await badge_core(
        rows=[player_row(owned=("veteran",))]
    )
    form = await badge_form(bot)
    uuid = mutations["players"].docs[0]["uuid"]
    mutations["players"].docs[0]["nickname"] = "New name"
    text = f"{uuid} 203.0.113.9 2001:db8::1 @everyone [red]maps"
    await submit_form(bot, form, statement=text)
    case = await service.store.by_submission("discord:700")
    assert case.opener == case.subject
    assert case.subject.player_name == "New name" and case.subject.player_pid == 1
    assert case.payload["badge"] == "map-maker" and case.payload["owned_badges"] == [
        "veteran"
    ]
    thread = bot.channels[int(case.discord["thread_id"])]
    assert [tag.name for tag in thread.applied_tags] == ["Badge", "Open"]
    card = str(thread.messages[0].embeds[0].to_dict())
    for secret in (uuid, "203.0.113.9", "2001:db8::1", "@everyone"):
        assert secret not in card and secret not in case.payload["statement"]
    assert (
        "New name" in card
        and "ID 1" in card
        and "Map Maker" in card
        and "Veteran" in card
    )
    assert [b.verb for b in thread.messages[0].view.children] == [
        "take",
        "grant",
        "reject",
    ]
    user_thread = bot.channels[int(case.discord["user_thread_id"])]
    assert user_thread.private and user_thread.members == {42}


@pytest.mark.parametrize(
    "already_owned,inventory_failure", [(False, False), (True, False), (False, True)]
)
async def test_persistent_grant_real_helper_confirmed_audit_and_inventory_recovery(
    already_owned, inventory_failure
):
    service, db, bot, mutations, bus, *_ = await badge_core()
    case = await opened_badge(service, bot)
    row = mutations["players"].docs[0]
    if already_owned:
        row["unlocked_badges"] = ["map-maker"]
    if inventory_failure:
        bot.bus.publish_player_badge_inventory_changed = AsyncMock(
            side_effect=RuntimeError("inventory delivery failed")
        )
    await service.take(case.number, member(101, (20,)))
    service.store.peek = lambda number: None
    custom_id = f"xc:1:case:grant:{case.number}"
    click = Interaction(bot, user=member(101, (20,)))
    button = await CaseActionButton.from_custom_id(
        click, None, re.fullmatch(CASE_PATTERN, custom_id)
    )
    await button.callback(click)
    final = await service.get(case.number)
    assert final.status == CaseStatus.RESOLVED and final.resolution.code == "granted"
    audit = db["moderation_audit"].docs[0]
    assert audit["details"]["extra"]["case"] == str(case.number)
    assert audit["details"]["extra"]["badge_id"] == "map-maker"
    assert audit["details"]["extra"]["changed"] == str(not already_owned).lower()
    token = next(
        e["data"]["token"] for e in final.events if e["type"] == "action_started"
    )
    assert audit["details"]["extra"]["case_action_token"] == token
    assert final.resolution.audit_id == audit["audit_id"]
    assert row["unlocked_badges"] == ["map-maker"]
    assert len(bus.inventory_events) == (0 if already_owned or inventory_failure else 1)
    assert bot.channels[int(case.discord["thread_id"])].archived
    assert bot.channels[int(case.discord["user_thread_id"])].locked
    stale = Interaction(bot, user=member(102, (20,)))
    await button.callback(stale)
    assert stale.events[-1][1] == t("en", "error-already-handled-by", name="Staff 101")
    assert len(db["moderation_audit"].docs) == 1


@pytest.mark.parametrize(
    "failure", ["missing", "changed_subject", "unconfirmed", "audit", "allowlist"]
)
async def test_grant_failure_never_false_closes(failure):
    service, db, bot, mutations, *_ = await badge_core()
    case = await opened_badge(service, bot)
    if failure == "missing":
        mutations["players"].docs.clear()
    elif failure == "changed_subject":
        case = await service.store.cas(
            case, set_fields={"subject.player_uuid": "other-player"}
        )
    elif failure == "unconfirmed":
        bot.store.grant_badge = AsyncMock(return_value=False)
    elif failure == "audit":
        bot.store.append_moderation_audit = AsyncMock(
            side_effect=RuntimeError("audit failed")
        )
    else:
        service.settings = service.settings.model_copy(
            update={"cases_badges_requestable": "translator"}
        )
    with pytest.raises((ActionFailed, RuntimeError)):
        await service.dispatch_action(case.number, "grant", member(101, (20,)), "ru")
    current = await service.get(case.number)
    assert current.status == CaseStatus.OPEN and current.pending_action is None
    assert not db["moderation_audit"].docs and not db["case_notices"].docs
    assert any(e["type"] == "error" for e in current.events)


async def test_general_admin_role_checked_for_take_grant_and_reject():
    service, db, bot, *_ = await badge_core()
    case = await opened_badge(service, bot)
    for verb in ("take", "grant", "reject"):
        click = Interaction(bot, user=member(101, (30,)))
        await CaseActionButton(case.number, verb).callback(click)
        assert click.events[-1][1] == t("en", "error-not-allowed")
    assert not db["moderation_audit"].docs


@pytest.mark.parametrize(
    "code,locale,manual",
    [("granted", "en", "ru"), ("rejected", "ru", "uk"), ("rejected", "uk", "en")],
)
async def test_outcomes_user_thread_dm_preference_and_canonical_plugin_notice(
    code, locale, manual
):
    service, db, bot, *_ = await badge_core()
    form = await badge_form(
        bot, locale=discord.Locale(locale if locale != "en" else "en-US")
    )
    await submit_form(
        bot, form, locale=discord.Locale(locale if locale != "en" else "en-US")
    )
    case = await service.store.by_submission("discord:700")
    await service.store.set_locale_pref(42, manual, source="manual")
    if code == "granted":
        await service.dispatch_action(case.number, "grant", member(101, (20,)), "en")
    else:
        await service.reject(
            case.number,
            member(101, (20,)),
            reason_code="other",
            reason_text="Not enough work",
        )
    expected = t(
        manual, "badges-outcome-" + code, number=case.number, badge="map-maker"
    )
    assert bot.users[42].messages == [expected]
    user_thread = bot.channels[int(case.discord["user_thread_id"])]
    assert user_thread.messages[-1].content == expected
    assert user_thread.archived and user_thread.locked
    notice = db["case_notices"].docs[0]
    assert notice["kind"] == "badge_" + code
    assert notice["params"] == {"number": str(case.number), "badge": "map-maker"}
    assert notice["player_uuid"] == case.subject.player_uuid
    await service.finish_close(case.number)
    assert len(db["case_notices"].docs) == len(bot.users[42].messages) == 1


def test_all_dynamic_badge_and_contract_keys_exist_and_format():
    tree = ast.parse(Path(badges.__file__).read_text())
    keys = {
        node.value
        for node in ast.walk(tree)
        if isinstance(node, ast.Constant)
        and isinstance(node.value, str)
        and node.value.startswith("badges-")
        and not node.value.endswith("-")
    }
    keys |= {"badges-name-" + badge.id for badge in BADGES}
    for locale in SUPPORTED:
        for key in keys:
            assert loader.get_bundle(locale).has_message(key), (locale, key)
            assert (
                t(locale, key, account="Name", pid=0, number=12, badge="map-maker")
                != key
            )
