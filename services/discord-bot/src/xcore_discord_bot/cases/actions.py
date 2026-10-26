"""Running a staff action on a case, and finishing one that a crash interrupted.

A running action holds a guard on the case (``pending_action``, with a token that the
action's audit records carry). The audit trail is the proof that a ban, mute, unban,
shortening, badge grant or map upload happened: recovery closes the case from a matching
audit record and never runs the action again. Without one the outcome is unknown, and the
guard is only released for a person to decide.
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from dataclasses import asdict, replace
from datetime import datetime, timedelta
from typing import Any, Protocol
from uuid import uuid4

from ..badges import get_badge
from ..dto import Actor
from ._privacy import log_failure
from .model import (
    RESOLUTIONS,
    Case,
    CaseError,
    CaseKind,
    CaseRace,
    CaseStatus,
    Resolution,
    event,
    utc,
)
from .registry import FlowRegistry
from .store import CaseStore

logger = logging.getLogger(__name__)
# Lifetime of the Redis claim and of an action guard nobody finished (process death mid-action).
ACTION_GUARD_SECONDS = 600
# Which audit record closes which kind: (kind, audit action, details.extra.kind) -> resolution.
AUDIT_RESOLUTIONS: dict[tuple[str, str | None, str], str] = {
    ("report", "BAN", ""): "banned",
    ("report", "MUTE", ""): "muted",
    ("appeal", "UNBAN", ""): "unbanned",
    ("appeal", "NOTE", "ban_shortened"): "shortened",
    ("badge", "NOTE", "badge_granted"): "granted",
    ("map", "NOTE", "map_uploaded"): "accepted",
}


class CaseCore(Protocol):
    """What the actions use of ``CaseService``: its store, flows, claims and clock."""

    store: CaseStore
    registry: FlowRegistry
    claims: Any
    now: Callable[[], datetime]

    async def get(self, number: int) -> Case: ...

    async def checkpoint(
        self,
        number: int,
        *,
        set_fields: dict[str, Any] | None = None,
        unset_fields: tuple[str, ...] = (),
        event_type: str | None = None,
        actor: Actor | None = None,
        data: dict[str, Any] | None = None,
        action_token: str | None = None,
    ) -> Case: ...


class CaseActions:
    """The action guard, the close it ends in, and recovery from the audit trail.

    ``CaseService`` authorizes the member and runs the closing steps around these calls.
    """

    def __init__(self, cases: CaseCore) -> None:
        # The service, not copies of its parts: its clock and claims can be replaced later.
        self._cases = cases

    async def run(
        self,
        case: Case,
        verb: str,
        actor: Actor,
        action: Callable[[Case], Awaitable[Resolution]],
    ) -> None:
        """Claim the case, run ``action`` and close the case with its resolution.

        ``action`` performs the side effect (``staff_actions.*`` with ``case_number`` and the pending token) and
        returns the resolution. If it raises without completion evidence, the case stays open
        with an ``error`` event and the error is raised again; an external mutation may still
        have happened. A matching durable completion audit closes the case instead, without
        replaying the action.
        """
        number = case.number
        if not case.actionable or case.pending_action:
            raise CaseRace()
        # A claim cannot be released, so the revision is part of the key: a repeated press on
        # the same state is dropped, while the press after a failed attempt is a new claim.
        if not await self._cases.claims.claim_idempotency(
            f"case-act:{number}:{verb}:{case.revision}",
            ttl_seconds=ACTION_GUARD_SECONDS,
        ):
            raise CaseRace()
        token = uuid4().hex
        # The claim is per verb. This guard also keeps two different actions (Ban and Mute by
        # two people) from both running, and marks an interrupted action for the reconcile task.
        claimed = await self._cases.store.cas(
            case,
            set_fields={
                "pending_action": {
                    "token": token,
                    "verb": verb,
                    "at": self._cases.now(),
                    "actor": asdict(actor),
                }
            },
            event=event(
                "action_started", actor, self._cases.now(), verb=verb, token=token
            ),
        )
        try:
            resolution = await action(claimed)
            self.check_resolution(case.kind, resolution)
        except Exception as error:  # recovered from the audit, or the guard is released and it re-raises
            if not await self.recover(number):
                await self._release_guard(number, token, actor, verb, type(error))
                raise
            logger.warning(
                "Case #%s: %s failed after completion was recorded; closed from the audit",
                number,
                verb,
                exc_info=case.kind != CaseKind.COMPLAINT,
            )
        else:
            await self._finish(
                number,
                token,
                replace(
                    resolution,
                    at=self._cases.now(),
                    by_discord_id=actor.discord_id,
                    by_name=actor.name,
                ),
                actor,
            )

    @staticmethod
    def check_resolution(kind: CaseKind, resolution: Resolution) -> None:
        if (
            not isinstance(resolution, Resolution)
            or resolution.code not in RESOLUTIONS[kind]
        ):
            raise ValueError("Resolution is not valid for this kind")

    async def _finish(
        self, number: int, token: str, resolution: Resolution, actor: Actor
    ) -> Case:
        for _ in range(5):
            case = await self._cases.get(number)
            if (case.pending_action or {}).get("token") != token:
                break
            try:
                return await self.close(case, resolution, actor, action_token=token)
            except CaseRace:
                continue
        raise CaseRace()

    async def _release_guard(
        self, number: int, token: str, actor: Actor | None, verb: str, reason: type
    ) -> None:
        try:
            await self._cases.checkpoint(
                number,
                unset_fields=("pending_action",),
                event_type="error",
                actor=actor,
                data={"verb": verb, "error_type": reason.__name__},
                action_token=token,
            )
        except CaseError:
            known = self._cases.store.peek(number)
            log_failure(
                logger,
                known.kind if known else None,
                "Case #%s: the action guard could not be released",
                number,
            )

    async def close(
        self,
        case: Case,
        resolution: Resolution,
        actor: Actor | None,
        *,
        action_token: str | None = None,
    ) -> Case:
        fields: dict[str, Any] = {
            "status": CaseStatus.REJECTED
            if resolution.code == "rejected"
            else CaseStatus.RESOLVED,
            "resolution": asdict(resolution),
            "closed_at": resolution.at or self._cases.now(),
        }
        if case.assignee is None and actor is not None and actor.discord_id:
            # 4.2: acting on an open case makes the actor its assignee.
            fields["assignee"] = {
                "discord_id": actor.discord_id,
                "name": actor.name,
                "taken_at": self._cases.now(),
            }
        return await self._cases.store.cas(
            case,
            set_fields=fields,
            unset_fields=("pending_action",),
            event=event(
                "rejected" if resolution.code == "rejected" else "action",
                actor,
                self._cases.now(),
                code=resolution.code,
                audit_id=resolution.audit_id,
            ),
            action_token=action_token,
        )

    async def close_observed(self, case: Case, resolution: Resolution) -> None:
        """Close with a sanction observed outside the case, at the revision ``case`` has."""
        self._cases.registry.require(case.kind)
        if not case.actionable or case.pending_action:
            raise CaseRace()
        self.check_resolution(case.kind, resolution)
        await self.close(
            case, replace(resolution, at=resolution.at or self._cases.now()), None
        )

    async def recover(self, number: int) -> bool:  # noqa: C901
        """Close the case from the audit record of its interrupted action, if there is one."""
        case = await self._cases.get(number)
        if not case.actionable:
            return False
        self._cases.registry.require(case.kind)
        # Event order is authoritative: Mongo timestamps can share a millisecond across
        # a close/reopen. Never revive an earlier attempt, even if its audit arrived late.
        recent_events: list[dict[str, Any]] = []
        threshold = case.created_at
        for entry in case.events:
            if entry["type"] == "reopened":
                recent_events.clear()
                threshold = utc(entry["at"])
            else:
                recent_events.append(entry)
        started = next(
            (
                entry
                for entry in reversed(recent_events)
                if entry["type"] == "action_started"
                and entry.get("data", {}).get("token")
            ),
            None,
        )
        legacy_started = next(
            (
                entry
                for entry in reversed(recent_events)
                if entry["type"] == "action_started"
            ),
            None,
        )
        attempt = started or legacy_started or {}
        pending = case.pending_action or {}
        token = pending.get("token") or attempt.get("data", {}).get("token")
        verb = pending.get("verb") or attempt.get("data", {}).get("verb")
        if pending.get("at"):
            threshold = max(threshold, utc(pending["at"]))
        elif attempt:
            threshold = max(threshold, utc(attempt["at"]))
        # Guards predating token-bearing events can still have untokened completion rows.
        # New attempts must supply their token; never fall back to an older legacy row.
        legacy_guard = bool(pending) and started is None
        audits = await self._cases.store.audits_for(
            case, action_token=token, since=threshold
        )
        if legacy_guard:
            audits += [
                audit
                for audit in await self._cases.store.audits_for(case, since=threshold)
                if not audit.get("details", {})
                .get("extra", {})
                .get("case_action_token")
            ]
        for audit in audits:
            extra = audit.get("details", {}).get("extra", {})
            if not token and extra.get("case_action_token"):
                continue
            code = self.completion_code(case, audit, verb=verb, strict=bool(token))
            if code is None:
                continue
            try:
                ban_id = int(extra["ban_id"]) if extra.get("ban_id") else None
            except (TypeError, ValueError):
                continue
            actor_data = audit.get("actor", {})
            actor = Actor(
                actor_data.get("discord_id"), actor_data.get("name_snapshot") or ""
            )
            resolution = Resolution(
                code,
                by_discord_id=actor.discord_id,
                by_name=actor.name or None,
                at=utc(audit["occurred_at"]),
                audit_id=audit.get("audit_id"),
                ban_id=ban_id,
                server=extra.get("server") if code == "accepted" else None,
                file_name=extra.get("file_name") if code == "accepted" else None,
                duration_ms=audit.get("details", {}).get("duration_ms"),
            )
            try:
                await self.close(
                    case,
                    resolution,
                    actor,
                    action_token=(case.pending_action or {}).get("token"),
                )
            except CaseRace:
                # Someone else closed it in between; the next pass looks again.
                return not (await self._cases.get(number)).actionable
            return True
        return False

    @staticmethod
    def completion_code(  # noqa: C901
        case: Case, audit: dict[str, Any], *, verb: str | None, strict: bool
    ) -> str | None:
        """The resolution ``audit`` proves for ``case``, or None when it is not its action."""
        extra = audit.get("details", {}).get("extra", {})
        code = AUDIT_RESOLUTIONS.get(
            (case.kind.value, audit.get("action"), extra.get("kind", ""))
        )
        if code is None:
            return None
        expected_verb = {
            "banned": "ban",
            "muted": "mute",
            "unbanned": "unban",
            "shortened": "shorten",
            "granted": "grant",
            "accepted": "accept",
        }[code]
        if (strict and verb is None) or (verb is not None and verb != expected_verb):
            return None
        if case.kind != CaseKind.MAP and case.subject and case.subject.player_uuid:
            target = audit.get("target", {}).get("uuid")
            if target != case.subject.player_uuid and (strict or target is not None):
                return None
        if (
            case.kind == CaseKind.APPEAL
            and case.payload.get("ban_id") is not None
            and extra.get("ban_id") != str(case.payload["ban_id"])
        ):
            return None
        if case.kind == CaseKind.BADGE and case.payload.get("badge"):
            badge = get_badge(case.payload["badge"])
            if badge is None or extra.get("badge_id") != badge.id:
                return None
        if code == "accepted":
            if (
                extra.get("status") != "installed"
                or not isinstance(extra.get("server"), str)
                or not extra["server"].strip()
                or not isinstance(extra.get("file_name"), str)
                or not extra["file_name"].strip()
            ):
                return None
            for payload_key, audit_key in (
                ("server", "server"),
                ("file_name", "file_name"),
                ("sha256", "reviewed_sha256"),
            ):
                expected = case.payload.get(payload_key)
                if expected is not None and extra.get(audit_key) != expected:
                    return None
        return code

    async def release_abandoned(self, number: int) -> bool:
        """Free a case whose action neither finished nor left an audit record.

        The outcome may be unknown. Nothing is executed again: a person decides.
        """
        case = await self._cases.get(number)
        pending = case.pending_action
        if not pending or self._cases.now() - utc(pending["at"]) < timedelta(
            seconds=ACTION_GUARD_SECONDS
        ):
            return False
        await self._release_guard(
            number, pending["token"], None, pending["verb"], TimeoutError
        )
        return True
