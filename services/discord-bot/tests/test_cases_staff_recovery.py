"""Completion recovery with real staff helpers and in-memory Mongo/Discord boundaries."""

from __future__ import annotations

from dataclasses import replace
from datetime import timedelta
from unittest.mock import AsyncMock

import discord
import pytest
from cases_action_fakes import mirror_audits
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import NOW, core, member
from test_ban_identity import _ban
from test_staff_actions import FILE, PLAYER, _action_bot

from xcore_discord_bot.cases.model import (
    CaseKind,
    CaseRace,
    CaseStatus,
    Origin,
    Party,
    Resolution,
)
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.staff_actions import (
    BadgeGrantUnconfirmed,
    MapUploadTimeout,
    perform_badge_grant,
    perform_ban,
    perform_map_upload,
    perform_mute,
    perform_shorten_ban,
    perform_unban,
)


async def setup_action(kind, *, payload=None):
    closed = []

    async def on_closed(service, case):
        closed.append(case.number)

    flow = replace(
        DUMMY_FLOW, kind=CaseKind(kind), panel=None, outcomes=None, on_closed=on_closed
    )
    service, db, discord_bot = await core(flow=flow)
    discord_fields = None
    if kind == "map":
        created = await discord_bot.channels[400].create_thread(
            name="Map", embed=discord.Embed(title="Author"), view=None
        )
        discord_fields = {"forum_id": "400", "thread_id": str(created.thread.id)}
    case = await service.open(
        kind=kind,
        origin=Origin("forum" if kind == "map" else "game", "submission"),
        opener=Party(discord_id="42"),
        subject=None
        if kind == "map"
        else Party(player_uuid=PLAYER.uuid, player_pid=PLAYER.pid),
        payload=payload or {},
        discord=discord_fields,
    )
    bot, mutation_db, bus, channel = _action_bot()
    bot.store.now_utc = lambda: NOW
    mirror_audits(bot, bot, db, mutation_db)
    return service, db, case, bot, mutation_db, bus, channel, closed


def context(claimed):
    return {
        "case_number": claimed.number,
        "case_action_token": claimed.pending_action["token"],
        "request_id": "request-1",
    }


@pytest.mark.parametrize(
    "action,kind,code",
    [
        ("ban", "report", "banned"),
        ("mute", "report", "muted"),
        ("unban", "appeal", "unbanned"),
        ("shorten", "appeal", "shortened"),
        ("grant", "badge", "granted"),
        ("accept", "map", "accepted"),
    ],
)
async def test_real_helpers_recover_durable_completion_without_replaying(
    action, kind, code
):
    payload = (
        {"ban_id": 71}
        if kind == "appeal"
        else {"badge": " MAP-MAKER "}
        if kind == "badge"
        else {"file_name": FILE.filename, "sha256": "reviewed"}
        if kind == "map"
        else {}
    )
    service, db, case, bot, mutation_db, bus, channel, closed = await setup_action(
        kind, payload=payload
    )
    mutation_db["bans"].docs = [_ban(ban_id=71, expire_date=NOW + timedelta(days=7))]
    actor = service.actor(member())
    calls = []
    if action in {"ban", "mute", "shorten"}:
        channel.send = AsyncMock(side_effect=RuntimeError("log unavailable"))
    if action == "unban":
        bot.container.moderation_bus.publish_pardon_player = AsyncMock(
            side_effect=RuntimeError("pardon unavailable")
        )
    if action == "grant":
        bot.container.accounts_bus.publish_player_badge_inventory_changed = AsyncMock(
            side_effect=RuntimeError("inventory unavailable")
        )

    async def callback(claimed):
        calls.append(claimed.pending_action["token"])
        kwargs = context(claimed)
        started = claimed.events[-1]
        assert (
            started["type"] == "action_started"
            and started["data"]["token"] == kwargs["case_action_token"]
        )
        if action == "ban":
            await perform_ban(
                bot,
                player=PLAYER,
                duration=timedelta(days=1),
                reason="grief",
                actor=actor,
                **kwargs,
            )
        elif action == "mute":
            await perform_mute(
                bot, PLAYER, timedelta(minutes=10), "spam", actor, **kwargs
            )
        elif action == "unban":
            await perform_unban(
                bot, await bot.store.find_ban_by_id(71), actor, **kwargs
            )
        elif action == "shorten":
            await perform_shorten_ban(bot, 71, NOW + timedelta(days=1), actor, **kwargs)
        elif action == "grant":
            await perform_badge_grant(bot, PLAYER, "map-maker", actor, **kwargs)
        else:
            await perform_map_upload(
                bot, "mini-pvp", [FILE], actor, reviewed_sha256="reviewed", **kwargs
            )
        raise RuntimeError("interrupted after completion")

    final = await service.execute_action(
        case.number, action, member(101, (10, 20, 30)), callback
    )
    assert final.status == CaseStatus.RESOLVED and final.pending_action is None
    assert final.resolution.code == code
    assert (
        final.resolution.by_discord_id == "101"
        and final.resolution.by_name == "Staff 101"
    )
    row = db["moderation_audit"].docs[0]
    assert final.resolution.audit_id == row["audit_id"]
    assert row["details"]["extra"]["case_action_token"] == calls[0]
    assert row["origin"]["request_id"] == "request-1"
    assert all(isinstance(v, str) for v in row["details"]["extra"].values())
    if kind == "appeal":
        assert final.resolution.ban_id == 71
    if kind == "map":
        assert (
            final.resolution.server == "mini-pvp"
            and final.resolution.file_name == FILE.filename
        )
    await reconcile_cases(service)
    await reconcile_cases(service)
    assert len(calls) == len(db["moderation_audit"].docs) == 1
    assert closed == [case.number] and final.progress["closed_done"]
    assert len(bus.upload_calls) == (1 if kind == "map" else 0)


@pytest.mark.parametrize("mode", ["map", "badge_owned", "badge_lookup"])
async def test_real_completion_survives_failed_case_cas_or_post_grant_lookup(mode):
    kind = "map" if mode == "map" else "badge"
    payload = (
        {"file_name": FILE.filename, "sha256": "reviewed"}
        if kind == "map"
        else {"badge": "map-maker"}
    )
    service, db, case, bot, mutation_db, bus, _, closed = await setup_action(
        kind, payload=payload
    )
    if mode == "badge_owned":
        mutation_db["players"].docs[0]["unlocked_badges"] = ["map-maker"]
    if mode == "badge_lookup":
        bot.store.find_player_by_uuid = AsyncMock(
            side_effect=RuntimeError("fresh lookup failed")
        )
    finish = service._finish_action
    if mode != "badge_lookup":
        service._finish_action = AsyncMock(side_effect=CaseRace())

    async def callback(claimed):
        if kind == "map":
            result = await perform_map_upload(
                bot,
                "mini-pvp",
                [FILE],
                service.actor(member()),
                reviewed_sha256="reviewed",
                **context(claimed),
            )
            return Resolution(
                "accepted",
                audit_id=result.audit_id,
                server="mini-pvp",
                file_name=result.results[0].fileName,
            )
        result = await perform_badge_grant(
            bot, PLAYER, "map-maker", service.actor(member()), **context(claimed)
        )
        return Resolution("granted", audit_id=result.audit_id)

    if mode == "badge_lookup":
        await service.execute_action(case.number, "grant", member(101, (20,)), callback)
    else:
        with pytest.raises(CaseRace):
            await service.execute_action(
                case.number,
                "accept" if kind == "map" else "grant",
                member(101, (20, 30)),
                callback,
            )
        assert (await service.get(case.number)).pending_action
    service._finish_action = finish
    await reconcile_cases(service)
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert final.status == CaseStatus.RESOLVED and final.pending_action is None
    assert final.resolution.audit_id == db["moderation_audit"].docs[0]["audit_id"]
    assert closed == [case.number]
    if mode == "badge_owned":
        assert db["moderation_audit"].docs[0]["details"]["extra"]["changed"] == "false"
        assert not bus.inventory_events
    if kind == "map":
        assert (
            final.resolution.server == "mini-pvp"
            and final.resolution.file_name == FILE.filename
        )
        assert len(bus.upload_calls) == 1


@pytest.mark.parametrize(
    "status",
    [
        "download_failed",
        "invalid_map",
        "superseded",
        "metadata_failed",
        "rejected",
        "timeout",
        "audit_failed",
    ],
)
async def test_real_map_unconfirmed_results_stay_open_and_never_retry(status):
    service, db, case, bot, _, bus, _, closed = await setup_action(
        "map", payload={"file_name": FILE.filename, "sha256": "reviewed"}
    )
    if status == "timeout":
        bus.upload_error = TimeoutError("installed remotely but no receipt")
    elif status == "audit_failed":
        bot.store.append_moderation_audit = AsyncMock(
            side_effect=ValueError("audit unavailable")
        )
    else:
        bus.statuses = [status]

    async def callback(claimed):
        result = await perform_map_upload(
            bot,
            "mini-pvp",
            [FILE],
            service.actor(member()),
            reviewed_sha256="reviewed",
            **context(claimed),
        )
        assert result.audit_id is None
        raise RuntimeError("not installed")

    expected = (
        MapUploadTimeout
        if status == "timeout"
        else ValueError
        if status == "audit_failed"
        else RuntimeError
    )
    with pytest.raises(expected):
        await service.execute_action(
            case.number, "accept", member(101, (30,)), callback
        )
    await reconcile_cases(service)
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert (
        final.status == CaseStatus.OPEN
        and final.resolution is None
        and final.pending_action is None
    )
    assert not closed and not final.progress.get("closed_done")
    assert len(bus.upload_calls) == 1 and not db["case_notices"].docs
    assert not bus.inventory_events
    if status not in {"timeout", "audit_failed"}:
        assert db["moderation_audit"].docs[0]["details"]["extra"]["status"] == status
        assert (
            db["moderation_audit"].docs[0]["details"]["extra"]["kind"]
            == "map_upload_result"
        )
    else:
        assert not db["moderation_audit"].docs


@pytest.mark.parametrize("kind", ["appeal", "badge"])
async def test_real_mutation_without_completion_audit_remains_uncertain(kind):
    service, db, case, bot, mutation_db, bus, _, closed = await setup_action(
        kind, payload={"ban_id": 71} if kind == "appeal" else {"badge": "map-maker"}
    )
    mutation_db["bans"].docs = [_ban(ban_id=71, expire_date=NOW + timedelta(days=7))]
    bot.store.append_moderation_audit = AsyncMock(
        side_effect=RuntimeError("audit unavailable")
    )
    calls = []

    async def callback(claimed):
        calls.append(claimed.number)
        if kind == "appeal":
            await perform_unban(
                bot,
                await bot.store.find_ban_by_id(71),
                service.actor(member()),
                **context(claimed),
            )
        else:
            await perform_badge_grant(
                bot, PLAYER, "map-maker", service.actor(member()), **context(claimed)
            )
        return Resolution("unbanned" if kind == "appeal" else "granted")

    with pytest.raises(RuntimeError, match="audit unavailable"):
        await service.execute_action(
            case.number,
            "unban" if kind == "appeal" else "grant",
            member(101, (10, 20)),
            callback,
        )
    await reconcile_cases(service)
    await reconcile_cases(service)
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    assert calls == [case.number] and not db["moderation_audit"].docs and not closed
    assert not bus.pardons and not bus.inventory_events
    if kind == "appeal":
        assert not mutation_db["bans"].docs
    else:
        assert mutation_db["players"].docs[0]["unlocked_badges"] == ["map-maker"]


async def test_real_false_badge_grant_missing_player_does_not_close():
    service, db, case, bot, mutation_db, _, _, closed = await setup_action(
        "badge", payload={"badge": "map-maker"}
    )
    mutation_db["players"].docs.clear()

    async def callback(claimed):
        await perform_badge_grant(
            bot,
            replace(PLAYER, unlocked_badges=("map-maker",)),
            "map-maker",
            service.actor(member()),
            **context(claimed),
        )
        return Resolution("granted")

    with pytest.raises(BadgeGrantUnconfirmed):
        await service.execute_action(case.number, "grant", member(101, (20,)), callback)
    await reconcile_cases(service)
    assert (await service.get(case.number)).status == CaseStatus.OPEN
    assert not db["moderation_audit"].docs and not closed


MAP_COMPLETION = {
    "kind": "map_uploaded",
    "status": "installed",
    "server": "mini-pvp",
    "file_name": "arena.msav",
}


def _recovery_case(
    id_,
    *,
    kind="report",
    verb="ban",
    action="BAN",
    extra=None,
    payload=None,
    change="none",
):
    return pytest.param(kind, verb, action, extra or {}, payload or {}, change, id=id_)


def _map_recovery(id_, *, extra, payload=None):
    return _recovery_case(
        id_, kind="map", verb="accept", action="NOTE", extra=extra, payload=payload
    )


@pytest.mark.parametrize(
    "kind,verb,action,extra,payload,change",
    [
        _recovery_case("wrong-token", change="token"),
        _recovery_case("untokened", change="untokened"),
        _recovery_case("wrong-target", change="target"),
        _recovery_case("missing-target", change="missing_target"),
        _recovery_case("wrong-action", action="MUTE"),
        _recovery_case("wrong-verb", verb="reject"),
        _recovery_case("wrong-kind", change="kind"),
        _recovery_case("pre-attempt", change="before_attempt"),
        _recovery_case(
            "wrong-ban",
            kind="appeal",
            verb="unban",
            action="UNBAN",
            payload={"ban_id": 71},
            extra={"ban_id": "72"},
        ),
        _recovery_case(
            "unban-shortening",
            kind="appeal",
            verb="unban",
            action="NOTE",
            payload={"ban_id": 71},
            extra={"kind": "ban_shortened", "ban_id": "71"},
        ),
        _recovery_case(
            "invalid-ban",
            kind="appeal",
            verb="shorten",
            action="NOTE",
            payload={"ban_id": 71},
            extra={"kind": "ban_shortened", "ban_id": "invalid"},
        ),
        _recovery_case(
            "wrong-badge",
            kind="badge",
            verb="grant",
            action="NOTE",
            payload={"badge": "map-maker"},
            extra={"kind": "badge_granted", "badge_id": "translator"},
        ),
        _recovery_case(
            "missing-badge",
            kind="badge",
            verb="grant",
            action="NOTE",
            payload={"badge": "map-maker"},
            extra={"kind": "badge_granted"},
        ),
        _map_recovery(
            "map-badge-kind", extra={"kind": "badge_granted", "badge_id": "map-maker"}
        ),
        _map_recovery(
            "map-result-kind", extra={**MAP_COMPLETION, "kind": "map_upload_result"}
        ),
        _map_recovery(
            "map-nonterminal", extra={**MAP_COMPLETION, "status": "metadata_failed"}
        ),
        _map_recovery(
            "missing-server",
            extra={
                key: value for key, value in MAP_COMPLETION.items() if key != "server"
            },
        ),
        _map_recovery(
            "missing-filename",
            extra={
                key: value
                for key, value in MAP_COMPLETION.items()
                if key != "file_name"
            },
        ),
        _map_recovery("blank-server", extra={**MAP_COMPLETION, "server": " "}),
        _map_recovery("blank-filename", extra={**MAP_COMPLETION, "file_name": " "}),
        _map_recovery(
            "wrong-filename",
            payload={"file_name": "arena.msav"},
            extra={**MAP_COMPLETION, "file_name": "wrong.msav"},
        ),
        _map_recovery(
            "wrong-hash",
            payload={"file_name": "arena.msav", "sha256": "reviewed"},
            extra={**MAP_COMPLETION, "reviewed_sha256": "old"},
        ),
        _map_recovery(
            "missing-hash", payload={"sha256": "reviewed"}, extra={**MAP_COMPLETION}
        ),
        _map_recovery(
            "wrong-server",
            payload={"server": "mini-pvp"},
            extra={**MAP_COMPLETION, "server": "other"},
        ),
    ],
)
async def test_recovery_refuses_other_attempts_verbs_targets_and_nonterminal_maps(
    kind, verb, action, extra, payload, change
):
    service, db, case, _, _, _, _, closed = await setup_action(kind, payload=payload)
    row = {
        "_id": "completion",
        "audit_id": "completion",
        "action": action,
        "occurred_at": NOW,
        "target": {"uuid": PLAYER.uuid},
        "actor": {"discord_id": "101", "name_snapshot": "Staff"},
        "details": {"extra": {"case": str(case.number), **extra}},
    }

    async def interrupted(claimed):
        row["details"]["extra"]["case_action_token"] = claimed.pending_action["token"]
        if change == "token":
            row["details"]["extra"]["case_action_token"] = "other-attempt"
        elif change == "untokened":
            del row["details"]["extra"]["case_action_token"]
        elif change == "target":
            row["target"]["uuid"] = "other-player"
        elif change == "missing_target":
            del row["target"]
        elif change == "kind":
            row["details"]["extra"]["kind"] = "intention"
        elif change == "before_attempt":
            row["occurred_at"] = NOW - timedelta(milliseconds=1)
        await db["moderation_audit"].insert_one(row)
        raise RuntimeError("interrupted")

    with pytest.raises(RuntimeError, match="interrupted"):
        await service.execute_action(
            case.number, verb, member(101, (10, 20, 30)), interrupted
        )
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert final.status == CaseStatus.OPEN and final.resolution is None
    assert not closed and not db["case_notices"].docs
