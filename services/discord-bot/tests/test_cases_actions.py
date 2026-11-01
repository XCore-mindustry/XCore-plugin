"""CaseActions on its own: the guard, the close and recovery from the audit trail.

The pipeline through ``CaseService`` is covered with each flow; these pin the branches the
flows rarely reach.
"""

from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from cases_dummy_flow import DUMMY_FLOW
from cases_fakes import NOW, core
from test_cases_service import audit, opened, racing_cas

from xcore_discord_bot.cases.actions import CaseActions
from xcore_discord_bot.cases.model import (
    CaseKind,
    CaseRace,
    CaseStatus,
    Party,
    Resolution,
)
from xcore_discord_bot.dto import Actor

STAFF = Actor("101", "Staff")


async def guarded(service, case, token="guard"):
    return await service.store.cas(
        case,
        set_fields={
            "pending_action": {
                "token": token,
                "verb": "ban",
                "at": NOW,
                "actor": {"discord_id": "101", "name": "Staff"},
            }
        },
    )


def sanction(case, **extra):
    return {**audit(case.number, **extra), "target": {"uuid": case.subject.player_uuid}}


async def test_a_lost_claim_runs_nothing_and_sets_no_guard():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    service.claims.claim_idempotency = AsyncMock(return_value=False)
    action = AsyncMock()

    with pytest.raises(CaseRace):
        await service.actions.run(case, "ban", STAFF, action)

    action.assert_not_awaited()
    assert (await service.get(case.number)).pending_action is None


async def test_a_case_with_a_running_action_takes_no_second_one():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await guarded(service, await opened(service))
    action = AsyncMock()

    with pytest.raises(CaseRace):
        await service.actions.run(case, "mute", STAFF, action)

    action.assert_not_awaited()


async def test_a_resolution_of_another_kind_releases_the_guard():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)

    with pytest.raises(ValueError, match="not valid for this kind"):
        await service.actions.run(
            case, "ban", STAFF, AsyncMock(return_value=Resolution("granted"))
        )

    current = await service.get(case.number)
    assert current.status == CaseStatus.OPEN and current.pending_action is None
    assert current.events[-1]["data"] == {"verb": "ban", "error_type": "ValueError"}


async def test_finish_retries_after_an_unrelated_write(monkeypatch):
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await guarded(service, await opened(service))

    async def note():
        await service.checkpoint(case.number, event_type="note", action_token="guard")

    await racing_cas(service, monkeypatch, note)
    closed = await service.actions._finish(
        case.number, "guard", Resolution("banned"), STAFF
    )

    assert closed.status == CaseStatus.RESOLVED and closed.pending_action is None


async def test_finish_does_not_close_over_another_attempts_guard():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await guarded(service, await opened(service), token="newer")

    with pytest.raises(CaseRace):
        await service.actions._finish(case.number, "older", Resolution("banned"), STAFF)

    assert (await service.get(case.number)).status == CaseStatus.OPEN


async def test_a_guard_that_cannot_be_released_is_logged_and_the_error_kept(caplog):
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    service.checkpoint = AsyncMock(side_effect=CaseRace())

    with pytest.raises(RuntimeError, match="server down"):
        await service.actions.run(
            case, "ban", STAFF, AsyncMock(side_effect=RuntimeError("server down"))
        )

    assert "the action guard could not be released" in caplog.text
    assert (await service.get(case.number)).pending_action is not None


async def test_recovery_does_not_touch_a_closed_case():
    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    await service.actions.close(case, Resolution("muted", at=NOW), STAFF)
    await db["moderation_audit"].insert_one(sanction(case))

    assert not await service.actions.recover(case.number)
    assert (await service.get(case.number)).resolution.code == "muted"


async def test_recovery_skips_an_audit_with_a_malformed_ban_id():
    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await guarded(service, await opened(service))
    await db["moderation_audit"].insert_one(
        sanction(case, ban_id="not-a-number", case_action_token="guard")
    )

    assert not await service.actions.recover(case.number)
    assert (await service.get(case.number)).pending_action is not None


@pytest.mark.parametrize("closed_by_other", [True, False])
async def test_recovery_that_loses_its_close(monkeypatch, closed_by_other):
    service, db, _ = await core(flow=DUMMY_FLOW)
    case = await guarded(service, await opened(service))
    await db["moderation_audit"].insert_one(
        sanction(case, ban_id="7", case_action_token="guard")
    )

    async def other_writer():
        current = await service.get(case.number)
        if closed_by_other:
            await service.actions.close(
                current, Resolution("muted", at=NOW), STAFF, action_token="guard"
            )
        else:
            await service.store.cas(current, event={"type": "note", "at": NOW})

    await racing_cas(service, monkeypatch, other_writer)

    # A case someone else closed counts as recovered; otherwise the next pass looks again.
    assert await service.actions.recover(case.number) is closed_by_other
    current = await service.get(case.number)
    assert current.actionable is not closed_by_other


async def test_an_observed_sanction_closes_only_an_actionable_case():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await opened(service)
    await service.actions.close_observed(case, Resolution("banned", ban_id=3))
    closed = await service.get(case.number)
    assert closed.resolution.code == "banned" and closed.resolution.at == NOW
    # A sanction seen on an earlier revision does not close the case again.
    with pytest.raises(CaseRace):
        await service.actions.close_observed(closed, Resolution("muted"))


async def test_a_fresh_guard_is_not_abandoned():
    service, _, _ = await core(flow=DUMMY_FLOW)
    case = await guarded(service, await opened(service))
    assert not await service.actions.release_abandoned(case.number)
    assert (await service.get(case.number)).pending_action is not None


def _case(kind: CaseKind, subject_uuid: str | None = "subject", **payload):
    subject = Party(player_uuid=subject_uuid) if subject_uuid else None
    return SimpleNamespace(kind=kind, subject=subject, payload=payload)


def _record(action: str, target: str | None = "subject", **extra):
    row = {"action": action, "details": {"extra": extra}}
    if target is not None:
        row["target"] = {"uuid": target}
    return row


@pytest.mark.parametrize(
    ("case", "record", "verb", "strict", "code"),
    [
        # The verb of the attempt must match the record; a tokened attempt needs one.
        (_case(CaseKind.REPORT), _record("BAN"), "ban", True, "banned"),
        (_case(CaseKind.REPORT), _record("BAN"), "mute", True, None),
        (_case(CaseKind.REPORT), _record("BAN"), None, True, None),
        (_case(CaseKind.REPORT), _record("BAN"), None, False, "banned"),
        # The target must be the subject; an untokened record without one still counts.
        (_case(CaseKind.REPORT), _record("MUTE", "other"), "mute", False, None),
        (_case(CaseKind.REPORT), _record("MUTE", None), "mute", False, "muted"),
        (_case(CaseKind.REPORT), _record("MUTE", None), "mute", True, None),
        # An appeal is about one ban.
        (
            _case(CaseKind.APPEAL, ban_id=5),
            _record("UNBAN", ban_id="6"),
            "unban",
            True,
            None,
        ),
        (
            _case(CaseKind.APPEAL, ban_id=5),
            _record("UNBAN", ban_id="5"),
            "unban",
            True,
            "unbanned",
        ),
        # A badge case is about one badge.
        (
            _case(CaseKind.BADGE, badge="veteran"),
            _record("NOTE", kind="badge_granted", badge_id="developer"),
            "grant",
            True,
            None,
        ),
        (
            _case(CaseKind.BADGE, badge="veteran"),
            _record("NOTE", kind="badge_granted", badge_id="veteran"),
            "grant",
            True,
            "granted",
        ),
        # Only an installed map on the reviewed server and file is accepted.
        (
            _case(CaseKind.MAP, None, server="pvp", file_name="a.msav"),
            _record(
                "NOTE",
                None,
                kind="map_uploaded",
                status="failed",
                server="pvp",
                file_name="a.msav",
            ),
            "accept",
            True,
            None,
        ),
        (
            _case(CaseKind.MAP, None, server="pvp", file_name="a.msav"),
            _record(
                "NOTE",
                None,
                kind="map_uploaded",
                status="installed",
                server="hub",
                file_name="a.msav",
            ),
            "accept",
            True,
            None,
        ),
        (
            _case(CaseKind.MAP, None, server="pvp", file_name="a.msav", sha256="x"),
            _record(
                "NOTE",
                None,
                kind="map_uploaded",
                status="installed",
                server="pvp",
                file_name="a.msav",
                reviewed_sha256="y",
            ),
            "accept",
            True,
            None,
        ),
        (
            _case(CaseKind.MAP, None, server="pvp", file_name="a.msav", sha256="x"),
            _record(
                "NOTE",
                None,
                kind="map_uploaded",
                status="installed",
                server="pvp",
                file_name="a.msav",
                reviewed_sha256="x",
            ),
            "accept",
            True,
            "accepted",
        ),
        # A note of another kind is not a completion.
        (
            _case(CaseKind.APPEAL),
            _record("NOTE", kind="comment"),
            "shorten",
            True,
            None,
        ),
    ],
)
def test_completion_code(case, record, verb, strict, code):
    assert CaseActions.completion_code(case, record, verb=verb, strict=strict) == code
