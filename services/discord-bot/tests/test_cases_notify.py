from __future__ import annotations

import asyncio
from dataclasses import replace
from datetime import timedelta
from types import SimpleNamespace

import discord
import pytest
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import Interaction, core, member
from test_cases_service import opened

from xcore_discord_bot.cases.model import Party
from xcore_discord_bot.cases.notify import Outcome
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.cases.transport import NoCaseTransport
from xcore_discord_bot.i18n import LocalePref, t


async def test_auto_preference_follows_latest_interaction_and_never_overwrites_manual():
    service, db, bot = await core(flow=DUMMY_FLOW)
    interaction = Interaction(bot, locale=discord.Locale.russian)
    assert await service.store.remember_interaction(interaction) == "ru"
    assert await service.store.get_locale_pref(101) == LocalePref("ru", "auto")
    assert db["discord_user_prefs"].docs[0]["updated_at"]
    interaction.locale = discord.Locale.ukrainian
    assert await service.store.remember_interaction(interaction) == "uk"
    await asyncio.gather(
        service.store.set_locale_pref(101, "ru", source="auto"),
        service.store.set_locale_pref(101, "uk", source="manual"),
    )
    await service.store.remember_interaction(Interaction(bot))
    assert await service.store.get_locale_pref(101) == LocalePref("uk", "manual")
    await service.store.load_locale_prefs()
    assert service.store.interaction_locale_now(interaction) == "uk"


@pytest.mark.parametrize(
    ("pref", "current_language", "player_language", "expected", "player_present"),
    [
        (None, None, "ru", "ru", False),
        (None, None, "uk_UA", "uk", False),
        (None, None, None, "ru", False),
        (LocalePref("uk", "auto"), None, "ru", "uk", False),
        (LocalePref("ru", "manual"), None, "uk", "ru", False),
        (None, "uk_UA", None, "uk", True),
        (None, "uk_UA", "ru", "uk", True),
        (None, None, "ru", "ru", True),
        (LocalePref("uk", "auto"), "ru", None, "uk", True),
        (LocalePref("ru", "manual"), "uk", "uk", "ru", True),
    ],
)
async def test_notice_is_durable_idempotent_and_dm_locale_is_resolved(
    pref, current_language, player_language, expected, player_present
):
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    if pref:
        await service.store.set_locale_pref(42, pref.locale, source=pref.source)
    if player_present:
        service.player_store.by_uuid["opener-secret"] = SimpleNamespace(
            language=current_language, online_server=None
        )
    calls = []

    async def wakeup(**kwargs):
        calls.append(kwargs)

    service.transport.publish_case_notice = wakeup
    outcome = Outcome(
        case.opener, "report_resolved", "cases-outcome", player_language=player_language
    )
    await service.notifier.send(service, case, outcome)
    await service.notifier.send(service, case, outcome)
    assert len(db["case_notices"].docs) == 1 and len(bot.users[42].messages) == 1
    row = db["case_notices"].docs[0]
    assert row["_id"] == f"{case.number}:opener-secret:report_resolved"
    assert row["expires_at"] - row["created_at"] == timedelta(days=30)
    assert row["params"] == {"number": str(case.number)} and row["delivered_at"] is None
    assert bot.users[42].messages == [t(expected, "cases-outcome", number=case.number)]
    # One wake-up for the one row, carrying the id the game claims the notice by.
    assert calls == [
        {
            "notice_id": row["_id"],
            "player_uuid": "opener-secret",
            "server": "server",
        }
    ]


async def test_wakeup_failure_keeps_notice_and_dm_forbidden_is_ignored():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    async def wakeup(**kwargs):
        raise RuntimeError("No server")

    async def forbidden(*args, **kwargs):
        raise discord.Forbidden(
            SimpleNamespace(status=403, reason="Forbidden"), "DMs closed"
        )

    service.transport.publish_case_notice = wakeup
    user = await bot.fetch_user(42)
    user.send = forbidden
    await service.notifier.send(
        service, case, Outcome(case.opener, "report_rejected", "cases-outcome")
    )
    assert len(db["case_notices"].docs) == 1
    receipts = (await service.get(case.number)).progress["notification_receipts"]
    assert receipts[0]["dm_unavailable"] and not receipts[0].get("dm")


async def test_dm_transient_failure_is_retried_without_duplicate_user_thread_reply():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service, source="discord")
    bot.failures.arm("dm")
    await service.reject(case.number, member(), reason_code="no_evidence")
    assert not (await service.get(case.number)).progress.get("outcomes_done")
    await service.finish_close(case.number)
    final = await service.get(case.number)
    assert final.progress["outcomes_done"] and final.progress["closed_done"]
    assert len(db["case_notices"].docs) == 1 and len(bot.users[42].messages) == 1
    assert len(bot.channels[int(final.discord["user_thread_id"])].messages) == 2


async def test_transport_noops_and_event_registry_contract():
    transport = NoCaseTransport()
    assert (
        await transport.publish_case_notice(
            notice_id="1:u:report_resolved", player_uuid="u", server="mini-pvp"
        )
        is None
    )
    assert await transport.rpc_case_evidence(server="mini-pvp", player_uuid="u") is None
    assert (
        await transport.rpc_inspect_map(
            url="https://example.invalid/map", file_name="a.msav", profile="pvp"
        )
        is None
    )
    service, _, _ = await core(flow=DUMMY_FLOW)
    assert isinstance(service.transport, NoCaseTransport)
    case = await opened(service)

    class Transport(NoCaseTransport):
        def __init__(self):
            self.published = []

        async def publish_case_notice(self, **kwargs):
            self.published.append(kwargs)

    real = Transport()
    service.set_transport(real)
    assert service.transport is real
    await service.reject(case.number, member(), reason_code="no_evidence")
    assert real.published == [
        {
            "notice_id": f"{case.number}:opener-secret:report_rejected",
            "player_uuid": "opener-secret",
            "server": "server",
        }
    ]


@pytest.mark.parametrize(
    ("outcome_server", "online_server", "origin", "expected"),
    [
        # Where the player is now wins over where the report was submitted (9.1 step 13).
        ("survival", "mini-pvp", "game", "mini-pvp"),
        ("survival", None, "game", "survival"),
        (None, "mini-pvp", "game", "mini-pvp"),
        (None, None, "game", "server"),
        (None, None, "discord", None),
    ],
)
async def test_wakeup_goes_where_the_player_is_and_nowhere_when_unknown(
    outcome_server, online_server, origin, expected
):
    service, db, _ = await core(flow=replace(DUMMY_FLOW, outcomes=None))
    case = await opened(service, source=origin)
    service.player_store.by_uuid["opener-secret"] = SimpleNamespace(
        language="ru", online_server=online_server
    )
    calls = []

    async def wakeup(**kwargs):
        calls.append(kwargs["server"])

    service.transport.publish_case_notice = wakeup
    await service.notifier.send(
        service,
        case,
        Outcome(case.opener, "report_resolved", "cases-outcome", server=outcome_server),
    )
    assert calls == ([expected] if expected else [])
    # The row is what delivers on the next join, with or without a wake-up.
    assert len(db["case_notices"].docs) == 1


async def test_outcome_reaches_each_person_once_and_skips_missing_channels():
    service, db, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service, source="discord")
    in_game_only = Party(player_uuid="co-secret", player_name="Second")
    discord_only = Party(discord_id="43", discord_name="Third")
    for party in (case.opener, in_game_only, discord_only, case.opener):
        await service.notifier.send(
            service,
            case,
            Outcome(party, "report_resolved", "cases-outcome", user_thread=True),
        )
    assert sorted(row["player_uuid"] for row in db["case_notices"].docs) == [
        "co-secret",
        "opener-secret",
    ]
    assert len(bot.users[42].messages) == 1 and len(bot.users[43].messages) == 1
    user_thread = bot.channels[int(case.discord["user_thread_id"])]
    # The opener's thread gets the text once, in the opener's language.
    assert [m.content for m in user_thread.messages].count(
        t("ru", "cases-outcome", number=case.number)
    ) == 1
    receipts = (await service.get(case.number)).progress["notification_receipts"]
    assert sorted(row["key"] for row in receipts) == [
        "report_resolved:43",
        "report_resolved:co-secret",
        "report_resolved:opener-secret",
    ]
    # No uuid or Discord id ever becomes a MongoDB field name.
    assert all(set(row) <= {"key", "notice", "thread", "dm"} for row in receipts)


@pytest.mark.parametrize("status", [404, 500])
async def test_dm_refusal_is_final_but_a_discord_outage_is_retried(status):
    service, _, bot = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    user = await bot.fetch_user(42)
    send, failures = user.send, []

    async def failing(*args, **kwargs):
        failures.append(status)
        user.send = send
        raise discord.HTTPException(SimpleNamespace(status=status, reason="x"), "x")

    user.send = failing
    await service.reject(case.number, member(), reason_code="no_evidence")
    closed = await service.get(case.number)
    assert bool(closed.progress.get("outcomes_done")) is (status == 404)
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert final.progress["outcomes_done"] and final.progress["closed_done"]
    assert len(user.messages) == (0 if status == 404 else 1) and failures == [status]


@pytest.mark.parametrize("staff_locale", ["en", "ru", "uk"])
async def test_staff_card_is_in_the_staff_language_and_the_user_text_in_theirs(
    staff_locale,
):
    service, _, bot = await core(flow=DUMMY_FLOW, cases_staff_locale=staff_locale)
    await service.store.set_locale_pref(42, "uk", source="manual")
    case = await opened(service, source="discord")
    thread = bot.channels[int(case.discord["thread_id"])]
    card = thread.messages[0]
    assert t(staff_locale, "cases-kind-report") in thread.name
    assert [field.name for field in card.embeds[0].fields][:2] == [
        t(staff_locale, "cases-opener"),
        t(staff_locale, "cases-subject"),
    ]
    assert [item.item.label for item in card.view.children] == [
        t(staff_locale, key)
        for key in ("cases-button-take", "cases-button-resolve", "cases-button-reject")
    ]
    await service.reject(case.number, member(), reason_code="no_evidence")
    assert bot.users[42].messages == [t("uk", "cases-outcome", number=case.number)]
    user_thread = bot.channels[int(case.discord["user_thread_id"])]
    # The thread was opened from a Russian client; the manual choice wins for the outcome.
    assert user_thread.messages[0].content == t(
        "ru", "cases-user-thread-note", number=case.number
    )
    assert user_thread.messages[-1].content == bot.users[42].messages[0]
