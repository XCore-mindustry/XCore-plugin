"""Mute appeals: the same panel button and case kind as ban appeals, with their own actions."""

from __future__ import annotations

from datetime import timedelta

import pytest
from cases_appeals_fakes import act, appeal_core, ban_row, form, player_row, submit
from cases_fakes import NOW, Interaction, member

from xcore_discord_bot.cases.model import ActionFailed, CaseKind, CaseStatus
from xcore_discord_bot.cases.views import PanelButton, case_view
from xcore_discord_bot.i18n import t

EXPIRY = NOW + timedelta(days=7)


def mute_row(index=1, *, expire_date=EXPIRY):
    return {
        "uuid": f"private-{index}=",
        "pid": index,
        "name": f"Muted {index}",
        "admin_name": "Issuer",
        "admin_discord_id": "99",
        "reason": "private mute reason",
        "expire_date": expire_date,
    }


async def muted(*, bans=(), mutes=None):
    service, db, bot, bus, log = await appeal_core(bans=list(bans))
    db["mutes"].docs = [mute_row()] if mutes is None else mutes
    return service, db, bot, bus, log


async def mute_case(service, bot):
    modal = await form(bot)
    await submit(bot, modal, statement="I was joking")
    return await service.store.by_submission("discord:700")


async def test_a_linked_mute_opens_its_own_appeal():
    service, _, bot, *_ = await muted()
    modal = await form(bot)
    assert modal.title == t("en", "appeals-title-mute")
    assert "reference" not in modal.inputs
    await submit(bot, modal, statement="I was joking")
    case = await service.store.by_submission("discord:700")
    assert case.kind == CaseKind.APPEAL and case.dedup_key == "appeal:mute:private-1="
    assert case.subject.player_uuid == "private-1=" and not case.flags
    assert case.payload["mute"]["expire_date"] == EXPIRY
    assert "ban_id" not in case.payload
    assert {
        child.verb for child in case_view(service.registry, case, "en").children
    } == {"take", "unmute", "shorten_mute", "history", "reject"}
    staff = bot.channels[int(case.discord["thread_id"])]
    fields = {f.name: f.value for f in staff.messages[0].embeds[0].fields}
    assert fields[t("en", "appeals-mute")] and "private-1=" not in str(fields)
    history = await act(service, case, "history")
    assert history.startswith(t("en", "appeals-history-title"))


async def test_a_ban_and_a_mute_are_both_offered():
    service, _, bot, *_ = await muted(bans=[ban_row()])
    interaction = Interaction(bot, user=member(42, ()))
    await PanelButton(CaseKind.APPEAL).callback(interaction)
    view = interaction.events[-1][2]["view"]
    labels = [option.label for option in view.select.options]
    assert labels[0].startswith("B-101") and labels[1].startswith("Mute ·")
    modal = await form(bot, choice="1")
    await submit(bot, modal)
    case = await service.store.by_submission("discord:700")
    assert "mute" in case.payload


async def test_a_pid_without_a_ban_finds_the_mute_unverified():
    service, db, bot, *_ = await muted()
    db["players"].docs = [player_row(discord_id=None)]
    modal = await form(bot)
    await submit(bot, modal, reference="pid 1")
    case = await service.store.by_submission("discord:700")
    assert case.payload["lookup"] == "pid" and "unverified_identity" in case.flags


async def test_unmute_lifts_exactly_that_mute_and_tells_the_player():
    service, db, bot, *_ = await muted()
    case = await mute_case(service, bot)
    assert await act(service, case, "unmute") is None
    assert db["mutes"].docs
    await act(service, case, "unmute", values={"confirmed": "yes"})
    case = await service.get(case.number)
    assert case.status == CaseStatus.RESOLVED and case.resolution.code == "unmuted"
    assert not db["mutes"].docs
    [audit] = db["moderation_audit"].docs
    assert audit["action"] == "UNMUTE" and audit["target"]["uuid"] == "private-1="
    assert audit["details"]["extra"]["case"] == str(case.number)
    assert case.resolution.audit_id == audit["audit_id"]
    assert db["case_notices"].docs[0]["kind"] == "appeal_unmuted"


async def test_shorten_mute_moves_the_expiry_earlier():
    service, db, bot, _, log = await muted()
    case = await mute_case(service, bot)
    with pytest.raises(ActionFailed):
        await act(service, case, "shorten_mute", values={"duration": "30d"})
    await act(service, case, "shorten_mute", values={"duration": "1h"})
    case = await service.get(case.number)
    assert case.resolution.code == "mute_shortened"
    assert db["mutes"].docs[0]["expire_date"] == NOW + timedelta(hours=1)
    [audit] = db["moderation_audit"].docs
    assert audit["details"]["extra"]["kind"] == "mute_shortened"
    assert log.embeds[-1].title == "Mute Shortened"
    assert db["case_notices"].docs[0]["kind"] == "appeal_mute_shortened"


@pytest.mark.parametrize("change", ["gone", "expired", "replaced"])
@pytest.mark.parametrize("verb", ["unmute", "shorten_mute"])
async def test_a_changed_mute_leaves_the_appeal_open(change, verb):
    service, db, bot, *_ = await muted()
    case = await mute_case(service, bot)
    if change == "gone":
        db["mutes"].docs.clear()
    if change == "expired":
        db["mutes"].docs[0]["expire_date"] = NOW
    if change == "replaced":
        db["mutes"].docs[0]["expire_date"] = EXPIRY + timedelta(days=1)
    with pytest.raises(ActionFailed, match="original mute"):
        await act(service, case, verb, values={"confirmed": "yes", "duration": "1h"})
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    assert not db["moderation_audit"].docs


async def test_a_rejected_mute_blocks_only_that_mute():
    service, db, bot, *_ = await muted()
    case = await mute_case(service, bot)
    await service.reject(case.number, member(), reason_code="other", reason_text="No")
    again = await submit(bot, await form(bot), id=701)
    assert f"#{case.number}" in again.events[-1][1]
    db["mutes"].docs[0]["expire_date"] = EXPIRY + timedelta(days=1)
    await submit(bot, await form(bot), id=702)
    assert await service.store.by_submission("discord:702") is not None
