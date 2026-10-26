from __future__ import annotations

from datetime import timedelta
from unittest.mock import AsyncMock

import pytest
from cases_appeals_fakes import act, appeal_core, ban_row, opened, player_row
from cases_fakes import NOW, Interaction, member

from xcore_discord_bot.cases.flows import appeals
from xcore_discord_bot.cases.model import (
    AccessDenied,
    ActionFailed,
    CaseRace,
    CaseStatus,
)
from xcore_discord_bot.cases.reconcile import reconcile_case
from xcore_discord_bot.staff_actions import BanNotFound


async def test_unban_confirmation_real_helper_delete_audit_before_pardon_and_notice():
    service, db, bot, mutations, bus, _ = await appeal_core(discord_bans_channel_id=100)
    case = await opened(service, bot)
    result = await act(service, case, "unban")
    assert result is None and mutations["bans"].docs
    interaction = Interaction(bot)
    await service.dispatch_action(
        case.number, "unban", member(), "en", interaction=interaction
    )
    confirmation = interaction.events[-1][2]["view"]
    original = bot.bus.publish_pardon_player

    async def pardon(**kwargs):
        assert not mutations["bans"].docs
        assert mutations["moderation_audit"].docs[-1]["action"] == "UNBAN"
        await original(**kwargs)

    bot.bus.publish_pardon_player = pardon
    await confirmation.children[0].callback(Interaction(bot))
    case = await service.get(case.number)
    assert case.status == CaseStatus.RESOLVED and case.resolution.code == "unbanned"
    assert case.resolution.audit_id == mutations["moderation_audit"].docs[0]["audit_id"]
    assert case.resolution.ban_id == 101 and bus.pardons == ["private-1="]
    audit = mutations["moderation_audit"].docs[0]
    token = next(
        e["data"]["token"] for e in case.events if e["type"] == "action_started"
    )
    assert audit["details"]["extra"] == {
        "case": str(case.number),
        "ban_id": "101",
        "case_action_token": token,
    }
    assert case.progress["appeal_unban_log"]
    log = bot.channels[100].messages[0]
    assert f"B-101 lifted — case #{case.number} — Staff 101" in log.content
    assert db["case_notices"].docs[0]["kind"] == "appeal_accepted"
    with pytest.raises(CaseRace):
        await act(service, case, "unban", values={"confirmed": "yes"})
    assert len(mutations["moderation_audit"].docs) == 1


@pytest.mark.parametrize("verb", ["unban", "shorten"])
@pytest.mark.parametrize("change", ["gone", "expired", "replacement", "subject"])
async def test_missing_expired_replaced_subject_guard_stays_open_without_mutation(
    verb, change
):
    service, db, bot, mutations, bus, log = await appeal_core()
    case = await opened(service, bot)
    if change == "gone":
        mutations["bans"].docs.clear()
    if change == "expired":
        mutations["bans"].docs[0]["expire_date"] = NOW
    if change == "replacement":
        mutations["bans"].docs[0]["ban_id"] = 202
    if change == "subject":
        mutations["bans"].docs[0]["uuid"] = "someone-else"
    with pytest.raises(ActionFailed, match="original ban"):
        await act(service, case, verb, values={"confirmed": "yes", "duration": "1h"})
    case = await service.get(case.number)
    assert case.status == CaseStatus.OPEN and not case.pending_action
    assert not db["moderation_audit"].docs and not bus.pardons and not log.embeds
    if change != "gone":
        assert len(mutations["bans"].docs) == 1


async def test_unban_replacement_between_lookup_and_delete_real_mongo_guard(
    monkeypatch,
):
    service, db, bot, mutations, bus, _ = await appeal_core()
    case = await opened(service, bot)
    original = appeals.perform_unban

    async def reban(bot, ban, actor, **kwargs):
        mutations["bans"].docs[0]["ban_id"] = 202
        return await original(bot, ban, actor, **kwargs)

    monkeypatch.setattr(appeals, "perform_unban", reban)
    with pytest.raises((BanNotFound, ActionFailed)):
        await act(service, case, "unban", values={"confirmed": "yes"})
    assert mutations["bans"].docs[0]["ban_id"] == 202
    assert not db["moderation_audit"].docs and not bus.pardons
    assert (await service.get(case.number)).status == CaseStatus.OPEN


@pytest.mark.parametrize("verb", ["unban", "shorten"])
@pytest.mark.parametrize("mode", ["id", "name"])
async def test_fresh_database_issuer_change_reauthorizes_claimed_case(verb, mode):
    service, db, bot, mutations, *_ = await appeal_core(
        players=[
            player_row(),
            {**player_row(2, discord_id="101"), "nickname": "Staff 101"},
        ]
    )
    case = await opened(service, bot)
    mutations["bans"].docs[0].update(
        admin_discord_id="101" if mode == "id" else None, admin_name="Staff 101"
    )
    with pytest.raises(AccessDenied):
        await act(
            service,
            case,
            verb,
            values={"confirmed": "yes", "duration": "1h"},
            user=member(101, (10, 20)),
        )
    assert not db["moderation_audit"].docs
    assert (await service.get(case.number)).status == CaseStatus.OPEN


@pytest.mark.parametrize(
    "initial_expiry,duration,milliseconds",
    [(NOW + timedelta(days=7), "2h30m", 9_000_000), (None, "1h", 3_600_000)],
)
async def test_shorten_real_helper_same_id_duration_audit_and_single_helper_log(
    initial_expiry, duration, milliseconds
):
    service, db, bot, mutations, bus, log = await appeal_core(
        bans=[ban_row(expire_date=initial_expiry)], discord_bans_channel_id=100
    )
    case = await opened(service, bot)
    case = await act(service, case, "shorten", values={"duration": duration})
    assert case.resolution.code == "shortened" and case.resolution.ban_id == 101
    assert case.resolution.duration_ms == milliseconds
    assert mutations["bans"].docs[0]["ban_id"] == 101 and mutations["bans"].docs[0][
        "expire_date"
    ] == NOW + timedelta(milliseconds=milliseconds)
    audit = mutations["moderation_audit"].docs[0]
    assert (
        audit["action"] == "NOTE"
        and audit["details"]["extra"]["kind"] == "ban_shortened"
    )
    assert audit["details"]["extra"]["ban_id"] == "101" and audit["details"]["extra"][
        "case"
    ] == str(case.number)
    assert audit["details"]["expires_at"] == NOW + timedelta(milliseconds=milliseconds)
    token = next(
        e["data"]["token"] for e in case.events if e["type"] == "action_started"
    )
    assert audit["details"]["extra"]["case_action_token"] == token
    assert audit["details"]["duration_ms"] == milliseconds
    assert (await service.get(case.number)).status == CaseStatus.RESOLVED
    assert db["moderation_audit"].docs[0] == audit
    assert case.resolution.audit_id == audit["audit_id"] and not bus.pardons
    assert len(log.embeds) == 1 and log.embeds[0].title == "Ban Shortened"
    assert not bot.channels[100].messages
    assert db["case_notices"].docs[0]["kind"] == "appeal_shortened"


@pytest.mark.parametrize("permanent", [False, True])
async def test_shorten_case_log_redacts_identifiers_without_changing_audit(permanent):
    raw_name = "Player private-1= at 203.0.113.1"
    raw_reason = "Investigated private-1= at 203.0.113.1; other 2001:db8::1"
    expiry = None if permanent else NOW + timedelta(days=7)
    service, db, bot, mutations, _, log = await appeal_core(
        bans=[ban_row(name=raw_name, reason=raw_reason, expire_date=expiry)],
        discord_bans_channel_id=100,
    )
    case = await opened(service, bot)
    actor = member()
    actor.display_name = "Staff private-1= 203.0.113.1"
    await service.dispatch_action(
        case.number, "shorten", actor, "en", {"duration": "1h"}
    )
    assert len(log.embeds) == 1
    embed = log.embeds[0].to_dict()
    displayed = str(embed)
    for identifier in ("private-1=", "203.0.113.1", "2001:db8::1"):
        assert identifier not in displayed
    assert "B-101" in displayed and f"#{case.number}" in displayed
    audit = db["moderation_audit"].docs[0]
    assert audit["target"]["uuid"] == "private-1="
    assert audit["target"]["ip_snapshot"] == "203.0.113.1"
    assert audit["target"]["name_snapshot"] == raw_name
    assert audit["reason"] == raw_reason
    assert audit["actor"]["name_snapshot"] == actor.display_name
    assert mutations["bans"].docs[0]["name"] == raw_name
    assert mutations["bans"].docs[0]["reason"] == raw_reason
    assert (await service.get(case.number)).resolution.code == "shortened"


@pytest.mark.parametrize(
    "duration",
    [
        "0",
        "0s",
        "-1d",
        "7d",
        "8d",
        "forever",
        "1h!",
        "1.5h",
        "9999999999999999999999y",
        "",
    ],
)
async def test_invalid_shorten_duration_never_updates(duration):
    service, db, bot, mutations, _, log = await appeal_core()
    case = await opened(service, bot)
    with pytest.raises(ValueError):
        await act(service, case, "shorten", values={"duration": duration})
    assert mutations["bans"].docs[0]["expire_date"] == NOW + timedelta(days=7)
    assert not db["moderation_audit"].docs and not log.embeds
    assert (await service.get(case.number)).status == CaseStatus.OPEN


@pytest.mark.parametrize("verb", ["unban", "shorten"])
async def test_followup_failure_after_audit_recovery_does_not_replay_helper(
    verb, monkeypatch
):
    service, db, bot, mutations, bus, _ = await appeal_core()
    case = await opened(service, bot)
    if verb == "unban":
        bot.bus.publish_pardon_player = AsyncMock(
            side_effect=RuntimeError("pardon failure")
        )
    else:
        bot.resolve_channel = AsyncMock(side_effect=RuntimeError("shorten log failure"))
    helper = getattr(
        appeals, "perform_" + ("unban" if verb == "unban" else "shorten_ban")
    )
    wrapped = AsyncMock(wraps=helper)
    monkeypatch.setattr(
        appeals, "perform_" + ("unban" if verb == "unban" else "shorten_ban"), wrapped
    )
    case = await act(service, case, verb, values={"confirmed": "yes", "duration": "1h"})
    assert case.status == CaseStatus.RESOLVED and case.resolution.audit_id
    assert len(mutations["moderation_audit"].docs) == 1 and wrapped.await_count == 1
    await service.finish_close(case.number)
    await reconcile_case(service, await service.get(case.number), audited=True)
    assert wrapped.await_count == 1 and len(db["case_notices"].docs) == 1
    # Recovery follows durable audit, not successful network delivery.
    if verb == "unban":
        assert bot.bus.publish_pardon_player.await_count == 1 and not bus.pardons


async def test_no_completion_audit_failure_keeps_case_open_no_invented_resolution():
    service, db, bot, mutations, bus, _ = await appeal_core()
    case = await opened(service, bot)
    bot.store.append_moderation_audit = AsyncMock(
        side_effect=RuntimeError("audit unavailable")
    )
    with pytest.raises(RuntimeError, match="audit unavailable"):
        await act(service, case, "unban", values={"confirmed": "yes"})
    case = await service.get(case.number)
    assert (
        case.status == CaseStatus.OPEN
        and case.resolution is None
        and not case.pending_action
    )
    assert (
        not mutations["bans"].docs
        and not db["moderation_audit"].docs
        and not bus.pardons
    )
    with pytest.raises(ActionFailed):
        await act(service, case, "unban", values={"confirmed": "yes"})
    assert case.resolution is None


@pytest.mark.parametrize("failure", ["missing", "send", "checkpoint"])
async def test_unban_log_retry_after_durable_completion_never_reexecutes_helper(
    failure, monkeypatch
):
    service, db, bot, mutations, bus, _ = await appeal_core(discord_bans_channel_id=100)
    case = await opened(service, bot)
    channel = bot.channels[100]
    original_send = channel.send
    if failure == "missing":
        del bot.channels[100]
    elif failure == "send":
        channel.send = AsyncMock(side_effect=RuntimeError("log send failed"))
    else:
        original_checkpoint = service.checkpoint
        failed = False

        async def checkpoint(number, **kwargs):
            nonlocal failed
            if (
                "progress.appeal_unban_log" in (kwargs.get("set_fields") or {})
                and not failed
            ):
                failed = True
                raise RuntimeError("log checkpoint failed")
            return await original_checkpoint(number, **kwargs)

        monkeypatch.setattr(service, "checkpoint", checkpoint)
    helper = AsyncMock(wraps=appeals.perform_unban)
    monkeypatch.setattr(appeals, "perform_unban", helper)
    case = await act(service, case, "unban", values={"confirmed": "yes"})
    assert case.status == CaseStatus.RESOLVED and not case.progress.get(
        "closed_hook_done"
    )
    assert not case.progress.get("appeal_unban_log")
    assert len(mutations["moderation_audit"].docs) == 1 and bus.pardons == [
        "private-1="
    ]
    bot.channels[100] = channel
    channel.send = original_send
    await service.finish_close(case.number)
    case = await service.get(case.number)
    assert (
        case.progress["appeal_unban_log"]
        and case.progress["closed_done"]
        and helper.await_count == 1
    )
    assert len(channel.messages) == (2 if failure == "checkpoint" else 1)
    assert len(db["case_notices"].docs) == 1
    await service.finish_close(case.number)
    assert len(channel.messages) == (2 if failure == "checkpoint" else 1)


async def test_zero_log_channel_skips_no_receipt_and_closes():
    service, _, bot, *_ = await appeal_core()
    case = await act(
        service, await opened(service, bot), "unban", values={"confirmed": "yes"}
    )
    assert case.progress["closed_done"] and "appeal_unban_log" not in case.progress


async def test_history_bounded_latest_subject_sanctions_appeals_and_restrictions():
    service, db, bot, _mutations, *_ = await appeal_core()
    case = await opened(service, bot)
    for index in range(20):
        await db["moderation_audit"].insert_one(
            {
                "_id": index,
                "action": "BAN",
                "target": {"uuid": "private-1="},
                "created_at_epoch_ms": index,
                "occurred_at": NOW,
                "reason": f"reason {index} private-1= 203.0.113.1 [red]@everyone",
                "actor": {"name_snapshot": "Issuer"},
            }
        )
        row = case.to_doc()
        row.update(
            _id=case.number + index + 1,
            origin={**row["origin"], "submission_id": f"old:{index}"},
        )
        row.pop("active", None)
        row.pop("discord", None)
        row["status"] = "rejected"
        await db["cases"].insert_one(row)
    text = await act(service, case, "history")
    assert "reason 19" in text and "reason 13" not in text
    assert (
        "private-1=" not in text
        and "203.0.113.1" not in text
        and "@everyone" not in text
    )
    assert len(text) <= 1900
    with pytest.raises(AccessDenied):
        await act(service, case, "history", user=member(123, ()))
    service.registry.disable(case.kind)
    with pytest.raises(ValueError):
        await act(service, case, "history")


async def test_shorten_rechecks_the_expiry_right_before_the_write(monkeypatch):
    service, db, bot, mutations, _, log = await appeal_core()
    case = await opened(service, bot)
    helper = appeals.perform_shorten_ban
    shortened = NOW + timedelta(minutes=30)

    async def shortened_meanwhile(*args, **kwargs):
        # Another moderator shortens the ban below the requested hour in the meantime.
        mutations["bans"].docs[0]["expire_date"] = shortened
        return await helper(*args, **kwargs)

    monkeypatch.setattr(appeals, "perform_shorten_ban", shortened_meanwhile)
    with pytest.raises(ActionFailed, match="before the current expiry"):
        await act(service, case, "shorten", values={"duration": "1h"})
    assert mutations["bans"].docs[0]["expire_date"] == shortened
    assert not db["moderation_audit"].docs and not log.embeds
    assert (await service.get(case.number)).status == CaseStatus.OPEN


@pytest.mark.parametrize("verb", ["unban", "shorten"])
@pytest.mark.parametrize("change", ["issuer", "subject", "replacement"])
async def test_helper_fresh_read_reauthorizes_after_flow_lookup(
    verb, change, monkeypatch
):
    service, db, bot, mutations, bus, log = await appeal_core()
    case = await opened(service, bot)
    name = "perform_unban" if verb == "unban" else "perform_shorten_ban"
    helper = getattr(appeals, name)

    async def changed(*args, **kwargs):
        mutations["bans"].docs[0].update(
            {"admin_discord_id": "101"}
            if change == "issuer"
            else {"uuid": "wrong-target"}
            if change == "subject"
            else {"ban_id": 202}
        )
        return await helper(*args, **kwargs)

    monkeypatch.setattr(appeals, name, changed)
    with pytest.raises(AccessDenied if change == "issuer" else ActionFailed):
        await act(service, case, verb, values={"confirmed": "yes", "duration": "1h"})
    assert not db["moderation_audit"].docs and not bus.pardons and not log.embeds
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    assert len(mutations["bans"].docs) == 1
