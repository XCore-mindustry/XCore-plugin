"""The periodic repair task (design spec 8.8).

It reads only the cases that can need something: active ones, closed ones whose closing is not
recorded as finished, closed ones still holding chat evidence past retention, and closed map
cases waiting for a badge. A healthy case costs one cached channel lookup and no write; a flow
with ``reconcile_open`` adds what its own check of an open case costs.
"""

from __future__ import annotations

import logging
from datetime import timedelta

import discord

from ._privacy import log_failure
from .model import Case, CaseKind, CaseRace, CaseStatus

logger = logging.getLogger(__name__)
CREATION_GRACE = timedelta(minutes=1)
GIVE_UP_AFTER = timedelta(hours=24)
PENDING_BADGE_WINDOW = timedelta(days=30)
PAGE = 200
CLOSED = (CaseStatus.RESOLVED.value, CaseStatus.REJECTED.value)


async def reconcile_cases(service) -> None:
    kinds = [flow.kind.value for flow in service.registry.active_flows()]
    if not kinds:
        return
    now = service.now()
    retention = timedelta(days=service.settings.cases_evidence_retention_days)
    query = {
        "kind": {"$in": kinds},
        "$or": [
            {"active": True},
            {
                "kind": CaseKind.COMPLAINT.value,
                "progress.complaint_history_pending": True,
            },
            {"status": {"$in": list(CLOSED)}, "progress.closed_done": {"$ne": True}},
            {
                "status": {"$in": list(CLOSED)},
                "$or": [
                    {"origin.source": "discord"},
                    {"co_openers.source": "discord"},
                    {"discord.user_thread_id": {"$exists": True}},
                    {"discord.reporter_threads.0": {"$exists": True}},
                ],
            },
            {
                "closed_at": {"$lte": now - retention},
                "evidence.chat.0": {"$exists": True},
            },
            {
                "payload.pending_badge": {"$exists": True},
                "closed_at": {"$gte": now - PENDING_BADGE_WINDOW},
            },
        ],
    }
    last = 0
    while True:
        cases = await service.store.list_cases(
            {**query, "_id": {"$gt": last}}, limit=PAGE
        )
        if not cases:
            return
        audited = await service.store.audited_numbers(
            [case.number for case in cases if case.actionable]
        )
        for case in cases:
            last = case.number
            try:
                await reconcile_case(service, case, audited=case.number in audited)
            except CaseRace:
                logger.debug("Case #%s changed during reconciliation", case.number)
            except Exception:  # noqa: BLE001 -- one broken case must not stop the pass
                log_failure(logger, case.kind, "Cannot reconcile case #%s", case.number)


async def reconcile_case(service, case: Case, *, audited: bool) -> None:
    now = service.now()
    number = case.number
    flow = service.registry.require(case.kind)
    if case.status == CaseStatus.CREATING:
        # Duty 1: adopt or create the post; give up after a day.
        age = now - case.created_at
        if age >= GIVE_UP_AFTER:
            await service.cancel(number, reason="creation_abandoned")
        elif age >= CREATION_GRACE:
            await service.resume_creation(number)
    elif case.actionable:
        # Duty 3: the sanction is in the audit trail but the case is still open.
        if audited and await service.recover_action(number):
            return
        if case.pending_action:
            await service.release_abandoned_action(number)
            return
        await _repair_post(service, case)
    elif case.status.value in CLOSED:
        # Failed deliveries and owned threads remain repairable, even after a day.
        await service.finish_close(number)
    case = await service.get(number)
    # Duty 4: the flow retries what it left pending (deep map checks, the Map Maker badge)
    # or looks at an open case again (a report whose player was sanctioned elsewhere).
    if flow.reconcile and (
        case.kind == CaseKind.COMPLAINT
        and case.progress.get("complaint_history_pending")
        and not case.pending_action
        or case.active
        and "deep_checks_pending" in case.flags
        or flow.reconcile_open
        and case.actionable
        and not case.pending_action
        or case.payload.get("pending_badge")
        and case.closed_at
        and now - case.closed_at <= PENDING_BADGE_WINDOW
    ):
        await flow.reconcile(service, case)
    # Duty 5: chat evidence is kept for a limited time after the close.
    if (
        case.closed_at
        and case.evidence.get("chat")
        and now - case.closed_at
        >= timedelta(days=service.settings.cases_evidence_retention_days)
    ):
        await service.checkpoint(
            number, set_fields={"evidence.chat": []}, event_type="evidence_cleared"
        )


async def _repair_post(service, case: Case) -> None:
    case = await service.resume_creation(case.number)
    thread_id = case.discord.get("thread_id")
    if not thread_id:
        return
    try:
        thread = await service.threads.channel(thread_id)
    except discord.NotFound:
        # Deleted while the bot was away; invalidate and recreate in this cycle.
        await service.thread_deleted(int(thread_id))
        await service.resume_creation(case.number)
        return
    if thread.archived or thread.locked and case.kind != CaseKind.MAP:
        # Duty 2: the buttons of an open case must stay reachable.
        await service.project(case.number)
