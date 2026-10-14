from __future__ import annotations

from datetime import timedelta
from unittest.mock import AsyncMock

import discord
import pytest
from cases_appeals_fakes import (
    act,
    appeal_core,
    ban_row,
    form,
    opened,
    player_row,
    submit,
)
from cases_fakes import NOW, Interaction, member

from xcore_discord_bot.cases.flows import appeals
from xcore_discord_bot.cases.flows.appeals import APPEAL_FLOW, parse_reference
from xcore_discord_bot.cases.model import (
    AccessDenied,
    CaseKind,
    CaseStatus,
)
from xcore_discord_bot.cases.registry import flow_registry
from xcore_discord_bot.cases.views import (
    CaseActionButton,
    PanelButton,
    case_view,
    panel_view,
)
from xcore_discord_bot.i18n import t


@pytest.mark.parametrize(
    "value,expected",
    [
        ("1234", ("ban_id", 1234)),
        ("#1234", ("ban_id", 1234)),
        ("B-1234", ("ban_id", 1234)),
        ("b1234", ("ban_id", 1234)),
        ("pid -5", ("pid", -5)),
        ("PID:0", ("pid", 0)),
        ("player ID #+5", ("pid", 5)),
        ("-1", ("pid", -1)),
        ("0", ("pid", 0)),
        ("+1", ("pid", 1)),
    ],
)
def test_reference_parser(value, expected):
    assert parse_reference(value) == expected


@pytest.mark.parametrize(
    "value",
    ["", "B-0", "B--1", "pid -2147483648", "pid 2147483648", "1.0", "admin", "#-1"],
)
def test_invalid_reference(value):
    with pytest.raises(ValueError):
        parse_reference(value)


async def test_disabled_flow_import_startup_panel_and_no_io():
    assert flow_registry.copy()._flows[CaseKind.APPEAL] is APPEAL_FLOW
    service, _, bot, *_ = await appeal_core(enabled=False)
    service.players.find_players_by_discord_id = AsyncMock(
        side_effect=AssertionError("disabled I/O")
    )
    interaction = Interaction(bot)
    await PanelButton(CaseKind.APPEAL).callback(interaction)
    assert interaction.events == [
        ("message", t("en", "error-try-again-later"), {"ephemeral": True})
    ]
    assert not panel_view(service.registry).children
    service.players.find_players_by_discord_id.assert_not_awaited()


async def test_one_linked_modal_first_response_no_io_and_registered_threads():
    service, _db, bot, mutations, *_ = await appeal_core()
    interaction = Interaction(bot, user=member(42, ()))
    original = service.players.find_players_by_discord_id

    async def linked(discord_id):
        assert interaction.events[0][0] == "defer"
        return await original(discord_id)

    service.players.find_players_by_discord_id = linked
    await PanelButton(CaseKind.APPEAL).callback(interaction)
    view = interaction.events[-1][2]["view"]
    service.players.find_players_by_discord_id = AsyncMock(
        side_effect=AssertionError("modal I/O")
    )
    step = Interaction(bot, user=member(42, ()))
    await view.children[0].callback(step)
    modal = step.events[0][1]
    assert step.events[0][0] == "modal" and "B-101" in modal.title
    assert "reference" not in modal.inputs
    service.players.find_players_by_discord_id = original
    result = await submit(bot, modal)
    assert result.events[0][0] == "defer"
    case = await service.store.by_submission("discord:700")
    assert case.opener.player_uuid == case.subject.player_uuid == "private-1="
    assert case.payload["ban_id"] == 101 and case.dedup_key == "appeal:101"
    assert case.discord["user_thread_id"] and not case.flags
    staff = bot.channels[int(case.discord["thread_id"])]
    assert {tag.name for tag in staff.applied_tags} == {"Appeal", "Open"}
    assert {
        child.verb for child in case_view(service.registry, case, "en").children
    } == {"take", "unban", "shorten", "history", "reject"}
    assert "issued_at" not in case.payload["ban_snapshot"]
    assert len(mutations["bans"].docs) == 1


async def test_paginated_all_linked_bans_dedup_and_private_selection():
    service, _, bot, *_ = await appeal_core(
        players=[player_row(i) for i in range(1, 29)],
        bans=[ban_row(i) for i in range(1, 29)],
    )
    # duplicate linked record does not produce a duplicate sanction option.
    original = service.players.find_players_by_discord_id

    async def duplicated(discord_id):
        rows = await original(discord_id)
        return [*rows, rows[0]]

    service.players.find_players_by_discord_id = duplicated
    interaction = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.APPEAL).callback(interaction)
    assert (
        interaction.events[0][0] == "defer" and interaction.events[-1][2]["ephemeral"]
    )
    view = interaction.events[-1][2]["view"]
    assert len(view.select.options) == 25
    assert all(
        "private-" not in str(o.to_dict()) and "203.0.113" not in str(o.to_dict())
        for o in view.select.options
    )
    await view._next(Interaction(bot, user=member(42, ())))
    assert len(view.select.options) == 3
    view.select._values = ["25"]
    selected = Interaction(bot, user=member(42, ()))
    await view._chosen(selected)
    assert selected.events[0][0] == "modal" and "B-126" in selected.events[0][1].title


@pytest.mark.parametrize(
    "pid,reference",
    [
        (1, "B-101"),
        (1, "#101"),
        (1, "b101"),
        (1, "101"),
        (0, "pid 0"),
        (-1, "pid -1"),
        (-5, "-5"),
        (0, "0"),
        (17, "17"),
    ],
)
async def test_manual_lookup_signed_zero_and_positive_pid_fallback(pid, reference):
    service, _, bot, *_ = await appeal_core(
        players=[player_row(pid=pid, discord_id=None)], bans=[ban_row(pid=pid)]
    )
    modal = await form(bot)
    assert "reference" in modal.inputs and "Banned" not in modal.title
    result = await submit(bot, modal, reference=reference)
    case = await service.store.by_submission("discord:700")
    assert case is not None, result.events
    assert case.subject.player_pid == pid and "unverified_identity" in case.flags
    assert case.opener.player_uuid is None


@pytest.mark.parametrize(
    "expiry", [None, NOW + timedelta(seconds=1), NOW + timedelta(days=1)]
)
async def test_active_expiry_including_permanent(expiry):
    service, _, bot, *_ = await appeal_core(bans=[ban_row(expire_date=expiry)])
    case = await opened(service, bot)
    assert case.payload["ban_snapshot"]["expire_date"] == expiry


@pytest.mark.parametrize(
    "change",
    [
        {"expire_date": NOW},
        {"expire_date": NOW - timedelta(seconds=1)},
        {"expire_date": "tomorrow"},
        {"ban_id": -3},
        {"ban_id": True},
        {"pid": True},
        {"admin_name": 123},
    ],
)
async def test_bad_or_expired_records_do_not_create_or_leak(change):
    service, db, bot, *_ = await appeal_core(players=[], bans=[ban_row(**change)])
    result = await submit(bot, await form(bot))
    assert db["cases"].docs == []
    assert "private ban reason" not in str(result.events)
    assert service.store.peek(1) is None


async def test_lazy_id_assignment_is_idempotent():
    service, _, bot, mutations, *_ = await appeal_core(bans=[ban_row(ban_id=None)])
    first = await form(bot)
    second = await form(bot)
    assert first.title == second.title and mutations["bans"].docs[0]["ban_id"] == 1
    case = await opened(service, bot)
    assert case.payload["ban_id"] == 1 and mutations["counters"].docs == [
        {"_id": "ban_id", "seq": 1}
    ]


async def test_cached_choice_does_not_appeal_replacement_or_changed_subject():
    _service, db, bot, mutations, *_ = await appeal_core()
    modal = await form(bot)
    mutations["bans"].docs[0]["ban_id"] = 999
    result = await submit(bot, modal)
    assert not db["cases"].docs and result.events[-1][1] == t("en", "appeals-no-active")
    mutations["bans"].docs[0]["ban_id"] = 101
    mutations["bans"].docs[0]["uuid"] = "replacement"
    await submit(bot, modal, id=701)
    assert not db["cases"].docs


async def test_unlink_during_form_changes_to_generic_unverified_submission():
    service, _, bot, mutations, *_ = await appeal_core()
    modal = await form(bot)
    mutations["players"].docs[0]["discord_id"] = "43"
    await submit(bot, modal)
    case = await service.store.by_submission("discord:700")
    assert case.opener.player_uuid is None and "unverified_identity" in case.flags


async def test_dedup_existing_number_and_same_submission_before_cooldown():
    service, _, bot, mutations, *_ = await appeal_core()
    modal = await form(bot)
    await submit(bot, modal)
    result = await submit(bot, modal, id=701)
    assert result.events[-1][1] == t(
        "en",
        "appeals-existing",
        number=(await service.store.by_submission("discord:700")).number,
    )
    service.settings = service.settings.model_copy(
        update={"cases_open_cooldown_seconds": 600}
    )
    mutations["bans"].docs.clear()
    retry = await submit(bot, modal)
    assert (
        f"#{(await service.store.by_submission('discord:700')).number}"
        in retry.events[-1][1]
    )
    assert len(service.store.database["cases"].docs) == 1
    service.settings = service.settings.model_copy(
        update={"cases_blacklist_role_id": 70}
    )
    blocked = await submit(bot, modal, user=member(42, (70,)))
    assert blocked.events[-1][1] == t("en", "error-opening-blocked")


@pytest.mark.parametrize(
    "age,ban_id,blocked",
    [
        (1, 101, True),
        (13, 101, True),
        (14, 101, False),
        (30, 101, False),
        (1, 999, False),
    ],
)
async def test_rejection_cooldown_only_same_ban_recent_closed_date(
    age, ban_id, blocked
):
    service, db, bot, *_ = await appeal_core()
    case = await opened(service, bot)
    await service.reject(
        case.number, member(), reason_code="other", reason_text="Not enough explanation"
    )
    row = db["cases"].docs[0]
    row["closed_at"] = NOW - timedelta(days=age)
    row["payload"]["ban_id"] = ban_id
    result = await submit(bot, await form(bot), id=701)
    assert (await service.store.by_submission("discord:701") is None) == blocked
    if blocked:
        assert (
            f"#{case.number}" in result.events[-1][1]
            and "private ban reason" not in result.events[-1][1]
        )


@pytest.mark.parametrize(
    "locale,code",
    [
        (discord.Locale.american_english, "en"),
        (discord.Locale.russian, "ru"),
        (discord.Locale.ukrainian, "uk"),
    ],
)
@pytest.mark.parametrize("resolution", ["unbanned", "shortened", "rejected"])
async def test_unverified_privacy_modal_result_threads_dm_outcomes(
    locale, code, resolution
):
    service, db, bot, _mutations, _bus, _ = await appeal_core(
        players=[player_row(discord_id=None)]
    )
    modal = await form(bot, locale=locale)
    interaction = await submit(bot, modal, locale=locale)
    case = await service.store.by_submission("discord:700")
    assert "unverified_identity" in case.flags
    if resolution == "rejected":
        case = await service.reject(
            case.number,
            member(),
            reason_code="other",
            reason_text="Issuer private ban reason",
        )
    else:
        case = await act(
            service,
            case,
            "unban" if resolution == "unbanned" else "shorten",
            values={"confirmed": "yes"}
            if resolution == "unbanned"
            else {"duration": "1h"},
        )
    thread = bot.channels[int(case.discord["user_thread_id"])]
    dm = bot.users[42]
    user_text = "\n".join(
        [
            modal.title,
            str(interaction.followup.texts()),
            *(m.content for m in thread.messages),
            *dm.messages,
        ]
    )
    for secret in (
        "Issuer",
        "Banned 1",
        "private ban reason",
        "private-1=",
        "203.0.113",
        "B-101",
    ):
        assert secret not in user_text
    assert t(code, "appeals-outcome-" + resolution, number=case.number) in user_text
    notices = db["case_notices"].docs
    assert [n["kind"] for n in notices] == (
        []
        if resolution == "rejected"
        else ["appeal_accepted" if resolution == "unbanned" else "appeal_shortened"]
    )


@pytest.mark.parametrize("issuer_mode", ["id", "name"])
@pytest.mark.parametrize(
    "verb", ["take", "release", "unban", "shorten", "history", "reject"]
)
async def test_issuer_blocked_every_action_head_admin_no_override(issuer_mode, verb):
    players = [player_row(), player_row(2, discord_id="99")]
    players[1]["nickname"] = "Issuer"
    service, db, bot, mutations, *_ = await appeal_core(
        players=players,
        bans=[ban_row(admin_discord_id="99" if issuer_mode == "id" else None)],
    )
    case = await opened(service, bot)
    with pytest.raises(AccessDenied):
        await act(
            service,
            case,
            verb,
            user=member(99, (10, 20)),
            values={"confirmed": "yes", "duration": "1h", "reason_text": "No"},
        )
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    assert not db["moderation_audit"].docs and len(mutations["bans"].docs) == 1


@pytest.mark.parametrize("verb", ["shorten", "reject"])
@pytest.mark.parametrize("issuer_mode", ["id", "name"])
async def test_issuer_blocked_modal_submission_after_role_recheck(verb, issuer_mode):
    service, db, bot, mutations, *_ = await appeal_core(
        players=[player_row(), {**player_row(2, discord_id="99"), "nickname": "Issuer"}]
    )
    case = await opened(service, bot)
    interaction = Interaction(bot, user=member(99, (10, 20)))
    # Open form before a change of issuer; submission must use the current case snapshot.
    mutations["bans"].docs[0]["admin_discord_id"] = "88"
    await service.checkpoint(
        case.number, set_fields={"payload.ban_snapshot.admin_discord_id": "88"}
    )
    await CaseActionButton(case.number, verb).callback(interaction)
    assert interaction.events[0][0] == "modal"
    await service.checkpoint(
        case.number,
        set_fields={
            "payload.ban_snapshot.admin_discord_id": "99"
            if issuer_mode == "id"
            else None
        },
    )
    modal = interaction.events[0][1]
    for item in modal.inputs.values():
        item._value = "1h" if verb == "shorten" else "Reject reason"
    submitted = Interaction(bot, user=member(99, (10, 20)), id=901)
    await modal.on_submit(submitted)
    assert submitted.events[0][0] == "defer" and submitted.events[-1][1] == t(
        "en", "error-not-allowed"
    )
    assert not db["moderation_audit"].docs


async def test_unknown_issuer_keeps_name_conflict_and_staff_card_actual_dates_sanitized():
    service, _, bot, _mutations, *_ = await appeal_core(
        bans=[ban_row(admin_discord_id=None, issued_at=NOW - timedelta(days=1))]
    )
    case = await opened(
        service, bot, statement="[red]Please @everyone private-1= 203.0.113.1"
    )
    assert "issuer_unknown" in case.flags
    card = appeals.card_builder(case, "en").to_dict()
    text = str(card)
    assert (
        "B-101" in text
        and "Issuer" in text
        and "private-1=" not in text
        and "203.0.113.1" not in text
        and "[red]" not in text
    )
    assert (
        "@everyone" not in text
        and str(int((NOW - timedelta(days=1)).timestamp())) in text
    )
    assert case.payload["ban_snapshot"]["admin_name"] == "Issuer"
    await act(service, case, "history")


@pytest.mark.parametrize("enabled,forum", [(True, 200), (False, 200), (True, 0)])
async def test_startup_uses_flag_and_safe_forum_checks(enabled, forum):
    from xcore_discord_bot.cases.startup import initialize_flows

    service, _, _bot, *_ = await appeal_core(
        enabled=enabled, cases_staff_forum_id=forum
    )
    await initialize_flows(service)
    assert service.registry.enabled(CaseKind.APPEAL) == (enabled and bool(forum))
    assert len(panel_view(service.registry).children) == (2 if enabled and forum else 0)


@pytest.mark.parametrize("kind", ["ip_only", "shared_ip"])
async def test_ip_lookup_active_ban_generic_choices_unverified_subject(kind):
    ban = ban_row(uuid=None if kind == "ip_only" else "another-uuid")
    service, _, bot, *_ = await appeal_core(bans=[ban])
    modal = await form(bot)
    assert modal.title == t("en", "appeals-title")
    assert "reference" not in modal.inputs
    result = await submit(bot, modal)
    case = await service.store.by_submission("discord:700")
    assert case is not None, result.events
    assert "unverified_identity" in case.flags and case.opener.player_uuid is None
    assert case.subject.player_uuid == ban["uuid"]
    assert "B-101" not in result.events[-1][1]


async def test_unknown_ban_pid_sentinel_not_confused_with_signed_pids():
    service, _, bot, *_ = await appeal_core(bans=[ban_row(pid=-(2**31))])
    case = await opened(service, bot)
    assert (
        case.subject.player_pid == 1
    )  # Actual DB player PID fills the unknown ban PID.
    # The reserved sentinel is never a lookup for a real player.
    assert parse_reference("pid -1") == ("pid", -1)
    with pytest.raises(ValueError):
        parse_reference("pid -2147483648")


async def test_expired_number_never_falls_back_to_different_active_pid():
    service, db, bot, *_ = await appeal_core(
        players=[player_row(2, pid=101, discord_id=None)],
        bans=[ban_row(expire_date=NOW), ban_row(2)],
    )
    result = await submit(bot, await form(bot), reference="101")
    assert not db["cases"].docs and result.events[-1][1] == t("en", "appeals-no-active")
    await submit(bot, await form(bot), reference="pid 101", id=701)
    case = await service.store.by_submission("discord:701")
    assert case.payload["ban_id"] == 102


async def test_blacklist_newly_added_and_requester_disabled_between_panel_and_form():
    service, db, bot, *_ = await appeal_core(cases_blacklist_role_id=70)
    interaction = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.APPEAL).callback(interaction)
    view = interaction.events[-1][2]["view"]
    blocked = Interaction(bot, user=member(42, (70,)))
    await view.children[0].callback(blocked)
    assert blocked.events[0][0] == "message" and blocked.events[0][1] == t(
        "en", "error-opening-blocked"
    )
    service.registry.disable(CaseKind.APPEAL)
    disabled = Interaction(bot, user=member(42, ()))
    await view.children[0].callback(disabled)
    assert disabled.events[0][0] == "message" and not db["cases"].docs


@pytest.mark.parametrize("pid", [0, -5, -(2**31)])
async def test_legacy_ban_unknown_pid_fills_verified_player_pid_without_sentinel_display(
    pid,
):
    service, _, bot, *_ = await appeal_core(
        players=[player_row(pid=pid)], bans=[ban_row(pid=None)]
    )
    modal = await form(bot)
    assert "B-101" in modal.title and modal.files is not None
    await submit(bot, modal)
    case = await service.store.by_submission("discord:700")
    assert case.opener.player_uuid == case.subject.player_uuid == "private-1="
    expected = None if pid == -(2**31) else pid
    assert case.opener.player_pid == case.subject.player_pid == expected
    assert "-2147483648" not in str(appeals.card_builder(case, "en").to_dict())
