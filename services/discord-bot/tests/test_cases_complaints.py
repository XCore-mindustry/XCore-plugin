from __future__ import annotations

import json
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import AsyncMock

import discord
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
from cases_fakes import Interaction, member

from xcore_discord_bot.cases.flows import complaints
from xcore_discord_bot.cases.model import CaseKind, CaseStatus, InvalidForm
from xcore_discord_bot.cases.registry import PanelContext
from xcore_discord_bot.cases.views import CaseActionButton, case_view, panel_view
from xcore_discord_bot.i18n import t


@pytest.mark.parametrize(
    "locale",
    [discord.Locale.american_english, discord.Locale.russian, discord.Locale.ukrainian],
)
async def test_panel_modal_senior_post_private_parent_and_tags(locale):
    service, db, bot = await complaint_core()
    modal = await form(bot, locale=locale)
    assert set(modal.inputs) == {"raw_subject", "statement", "when"}
    assert all(item.required for item in modal.inputs.values())
    assert isinstance(modal.files, discord.ui.FileUpload)
    assert (
        not modal.files.required
        and modal.files.min_values == 0
        and modal.files.max_values == 5
    )
    assert len(modal.children) == 4
    response = await submit(bot, modal, locale=locale)
    case = await service.store.by_submission("discord:700")
    post, thread = (
        bot.channels[int(case.discord["thread_id"])],
        bot.channels[int(case.discord["user_thread_id"])],
    )
    assert (
        case.kind == CaseKind.COMPLAINT
        and case.subject is None
        and case.dedup_key is None
    )
    assert case.status == CaseStatus.OPEN and "subject_unresolved" in case.flags
    assert post.parent_id == 300 and [tag.name for tag in post.applied_tags] == [
        "Complaint",
        "Open",
    ]
    assert thread.parent_id == 500 and thread.members == {42}
    assert thread.creation_kwargs["type"] == discord.ChannelType.private_thread
    assert thread.creation_kwargs["invitable"] is False
    assert not bot.channels[100].all_threads and not bot.channels[200].all_threads
    assert all(
        event[2].get("ephemeral") for event in response.events if event[0] == "followup"
    )
    assert {
        button.verb for button in case_view(service.registry, case, "en").children
    } == {"take", "subject", "resolve", "reject"}
    assert db["case_notices"].docs == [] and not bot.users


async def test_complainant_history_multiple_accounts_and_safe_card():
    p1, p2 = player(1), player(2)
    rows = [
        audit(action="BAN"),
        audit(index=2, action="MUTE", target=p2.uuid),
        audit(index=3, action="KICK", actor="88"),
        audit(action="WARN", target="unrelated"),
    ]
    service, _, bot = await complaint_core(players=[p1, p2], audits=rows)
    case = await opened(
        service,
        bot,
        statement=f"Text {p1.uuid} {p2.uuid} 203.0.113.8 2001:db8::1 91da46c5-89a4-45d8-828b-e258d211dc2f",
    )
    assert case.opener.player_uuid is None
    assert case.evidence["sanctions"]["bans"] == 1
    assert case.evidence["sanctions"]["mutes"] == 1
    assert case.evidence["sanctions"]["kicks"] == 1
    assert case.evidence["sanctions"]["warns"] == 0
    text = json.dumps(
        bot.channels[int(case.discord["thread_id"])].messages[0].embeds[0].to_dict()
    )
    assert (
        "Player 1" in text and "Player 2" in text and "ID 1" in text and "ID 2" in text
    )
    assert "Staff member" in text and "Sanction history" in text
    for secret in (
        p1.uuid,
        p2.uuid,
        "203.0.113.8",
        "2001:db8::1",
        "91da46c5-89a4-45d8-828b-e258d211dc2f",
    ):
        assert secret not in text


async def test_unlinked_complainant_can_submit_without_identity_or_dedup():
    service, _, bot = await complaint_core(players=[])
    first = await opened(service, bot)
    second = await opened(service, bot, id=701)
    assert first.number != second.number and first.opener.player_uuid is None
    assert first.evidence["sanctions"]["last"] == []
    assert first.payload["account_uuids"] == []


async def test_take_adds_only_senior_then_required_resolve_note_closes_neutrally():
    service, db, bot = await complaint_core()
    case = await opened(service, bot)
    case = await act(service, case, "take")
    thread = bot.channels[int(case.discord["user_thread_id"])]
    assert thread.members == {42, 101}
    with pytest.raises(InvalidForm):
        await act(service, case, "resolve", values={"note": " "})
    click = Interaction(bot, user=member(101, (20,)))
    await CaseActionButton(case.number, "resolve").callback(click)
    modal = click.events[0][1]
    assert modal.inputs["note"].required
    modal.inputs["note"]._value = "Private senior note"
    await modal.on_submit(Interaction(bot, user=member(101, (20,))))
    case = await service.get(case.number)
    assert case.status == CaseStatus.RESOLVED and case.resolution.code == "upheld"
    assert case.resolution.reason_text == "Private senior note"
    assert thread.archived and thread.locked
    post = bot.channels[int(case.discord["thread_id"])]
    assert (
        post.archived
        and post.locked
        and [tag.name for tag in post.applied_tags] == ["Complaint", "Resolved"]
    )
    assert any(
        field.name == "Note" and field.value == "Private senior note"
        for field in post.messages[0].embeds[0].fields
    )
    assert not post.messages[0].view.children
    assert bot.users[42].messages == [t("en", "complaints-outcome", number=case.number)]
    assert thread.messages[-1].content == t(
        "en", "complaints-outcome", number=case.number
    )
    assert db["case_notices"].docs[0]["kind"] == "complaint_closed"
    assert db["case_notices"].docs[0]["params"] == {"number": str(case.number)}
    bot.container.audit.append_moderation_audit.assert_not_awaited()
    assert set(bot.users) == {42}


async def test_reject_requires_reason_neutral_thread_and_dm_once():
    service, _, bot = await complaint_core()
    case = await opened(service, bot)
    with pytest.raises(InvalidForm):
        await act(service, case, "reject", values={"reason_text": ""})
    case = await act(
        service, case, "reject", values={"reason_text": "Private rejection reason"}
    )
    assert case.resolution.code == "rejected"
    assert case.resolution.reason_text == "Private rejection reason"
    await service.finish_close(case.number)
    assert len(bot.users[42].messages) == 1
    thread = bot.channels[int(case.discord["user_thread_id"])]
    assert thread.archived and thread.locked
    text = "\n".join(bot.users[42].messages + [m.content for m in thread.messages])
    assert (
        "Private rejection reason" not in text and "Private complaint text" not in text
    )


async def test_files_are_copied_only_to_senior_post():
    service, _, bot = await complaint_core()
    modal = await form(bot)
    attachment = SimpleNamespace(
        id=123,
        filename="proof.png",
        size=5,
        to_file=AsyncMock(
            return_value=discord.File(BytesIO(b"proof"), filename="proof.png")
        ),
    )
    modal.files._values = [attachment]
    await submit(bot, modal)
    case = await service.store.by_submission("discord:700")
    attachment.to_file.assert_awaited_once()
    assert case.evidence["attachments"][0]["source_id"] == "123"
    post = bot.channels[int(case.discord["thread_id"])]
    assert case.evidence["attachments"][0]["message_id"] == str(post.messages[-1].id)
    assert not bot.channels[100].messages and not bot.channels[200].all_threads


async def test_long_statement_complete_only_in_senior_post_and_retry_idempotent():
    service, _, bot = await complaint_core()
    statement = "Sensitive details " * 220
    case = await opened(service, bot, statement=statement)
    post = bot.channels[int(case.discord["thread_id"])]
    texts = [m.content for m in post.messages[1:]]
    assert len(texts) == 3 and all(len(text) < 2000 for text in texts)
    full = "".join(text.split("\n", 1)[1] for text in texts)
    assert full == statement.strip()
    await complaints.created(service, case)
    assert [m.content for m in post.messages[1:]] == texts
    assert all(
        statement[:100] not in m.content
        for m in bot.channels[int(case.discord["user_thread_id"])].messages
    )


async def test_disabled_flow_panel_and_stale_modal_have_no_effect():
    service, db, bot = await complaint_core(enabled=False)
    assert not panel_view(service.registry).children
    response = Interaction(bot, user=member(42, ()))
    await complaints.open_form(
        PanelContext(service, response, CaseKind.COMPLAINT, "en")
    ).on_submit(response)
    assert not db["cases"].docs and not bot.channels[300].all_threads
    assert response.followup.texts() == [t("en", "error-try-again-later")]
