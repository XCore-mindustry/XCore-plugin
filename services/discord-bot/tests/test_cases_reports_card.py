"""B5: what staff see on a report card and in its post (9.1 steps 9–10, 4.5)."""

from __future__ import annotations

import json
from datetime import timedelta

import discord
from cases_fakes import NOW
from cases_reports_fakes import (
    REPORTER_IP,
    REPORTER_UUID,
    TARGET_IP,
    TARGET_UUID,
    from_second,
    open_report,
    reports_core,
)

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.i18n import t

SECRETS = (REPORTER_UUID, TARGET_UUID, REPORTER_IP, TARGET_IP)


def everything_posted(bot) -> str:
    """Every text and embed the bot put into the staff forum, as one string."""
    parts = []
    for thread in bot.channels[200].all_threads:
        parts.append(thread.name)
        for message in thread.messages:
            parts.append(message.content)
            parts += [json.dumps(embed.to_dict()) for embed in message.embeds]
    return "\n".join(parts)


def card_fields(bot, case) -> dict[str, str]:
    thread = bot.channels[int(case.discord["thread_id"])]
    card = thread.messages[0].embeds[0]
    return {field.name: field.value for field in card.fields}


async def seed_history(db):
    rows = [
        ("BAN", "old grief", 3),
        ("MUTE", "spam", 2),
        ("KICK", "afk", 1),
        ("WARN", "rude", 0),
    ]
    for action, reason, days in rows:
        at = NOW - timedelta(days=days + 1)
        await db["moderation_audit"].insert_one(
            {
                "_id": f"audit-{action}",
                "audit_id": f"audit-{action}",
                "action": action,
                "target": {"uuid": TARGET_UUID, "ip_snapshot": TARGET_IP},
                "actor": {"name_snapshot": "Old Admin"},
                "reason": f"{reason} from {TARGET_IP}",
                "occurred_at": at,
                "created_at_epoch_ms": int(at.timestamp() * 1000),
                "details": {"extra": {}},
            }
        )


async def test_the_card_shows_parties_reason_context_and_sanctions_without_identifiers():
    service, db, bot = await reports_core()
    await seed_history(db)
    case = await open_report(service)
    fields = card_fields(bot, case)
    en = "en"
    assert fields[t(en, "cases-subject")] == "Target · {} · <@202>".format(
        t(en, "cases-pid", pid=-5)
    )
    assert fields[t(en, "cases-opener")] == "Reporter · {} · <@201>".format(
        t(en, "cases-pid", pid=0)
    )
    assert fields[t(en, "reports-card-reason")] == t(en, "reports-reason-griefing")
    assert fields[t(en, "reports-card-comment")] == "Destroyed our core"
    context = fields[t(en, "reports-card-context")].splitlines()
    assert context == [
        "origin · Arena · survival",
        t(en, "reports-online"),
        t(en, "reports-same-network"),
    ]
    sanctions = fields[t(en, "cases-sanctions")].splitlines()
    assert sanctions[0] == t(
        en, "cases-sanctions-counts", bans=1, mutes=1, kicks=1, warns=1
    )
    # The three latest, newest first.
    assert len(sanctions) == 4 and "WARN" in sanctions[1] and "BAN" not in sanctions[3]
    posted = everything_posted(bot)
    for secret in SECRETS:
        assert secret not in posted


async def test_the_evidence_posts_chat_and_a_block_summary_without_a_configured_count():
    service, _, bot = await reports_core()
    case = await open_report(service)
    thread = bot.channels[int(case.discord["thread_id"])]
    evidence = "\n".join(message.content for message in thread.messages[1:])
    assert "Target · " + t("en", "cases-pid", pid=-5) + ": my ip is …" in evidence
    summary = t("en", "cases-block-summary", minutes=10, built=12, destroyed=40)
    assert summary in evidence
    # "Built" says what it holds, and there is no third count the game cannot measure.
    assert "rotations" in summary and "configur" not in evidence.lower()
    assert TARGET_IP not in evidence


async def test_current_ban_and_mute_are_shown_as_in_force():
    service, _, bot = await reports_core()
    await service.players.bot.upsert_mute(
        uuid=TARGET_UUID,
        pid=-5,
        name="Target",
        admin_name="Admin",
        admin_discord_id=None,
        reason="spam",
        expire_date=NOW + timedelta(hours=2),
    )
    case = await open_report(service)
    in_force = card_fields(bot, case)[t("en", "reports-card-in-force")]
    until = discord.utils.format_dt(NOW + timedelta(hours=2), "f")
    assert in_force == t("en", "reports-current-mute", until=until) + " · spam"
    assert case.evidence["sanctions"]["current_mute"]["reason"] == "spam"
    assert "current_ban" not in case.evidence["sanctions"]


async def test_merged_reporters_are_listed_on_the_card():
    service, _, bot = await reports_core()
    case = await open_report(service)
    await reports.submitted(service, from_second())
    case = await service.get(case.number)
    fields = card_fields(bot, case)
    reporters = fields[t("en", "reports-card-reporters", count=2)]
    assert reporters.splitlines() == [
        "Reporter · {} · <@201>".format(t("en", "cases-pid", pid=0)),
        "Second · {}".format(t("en", "cases-pid", pid=7)),
    ]
    assert SECRETS[0] not in everything_posted(bot)


async def test_the_staff_card_uses_the_staff_language():
    service, _, bot = await reports_core(cases_staff_locale="uk")
    case = await open_report(service)
    fields = card_fields(bot, case)
    assert fields[t("uk", "reports-card-reason")] == t("uk", "reports-reason-griefing")
    thread = bot.channels[int(case.discord["thread_id"])]
    assert t("uk", "cases-kind-report") in thread.name
