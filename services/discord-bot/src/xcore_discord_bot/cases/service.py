from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from dataclasses import asdict, replace
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import uuid4

from pymongo.errors import DuplicateKeyError

from ..badges import get_badge
from ..staff_actions import Actor
from ._privacy import log_failure
from .access import check_access, check_opening
from .model import (
    RESOLUTIONS,
    AccessDenied,
    Case,
    CaseError,
    CaseKind,
    CaseNotFound,
    CaseRace,
    CaseStatus,
    Cooldown,
    InvalidTransition,
    OpenLimit,
    Origin,
    Party,
    Resolution,
    event,
    party_of,
    utc,
)
from .registry import REJECT_OTHER, ActionContext, FlowRegistry, FormField, clean_values
from .store import CaseStore
from .transport import CaseTransport, NoCaseTransport

logger = logging.getLogger(__name__)
# Lifetime of the Redis claim and of an action guard nobody finished (process death mid-action).
ACTION_GUARD_SECONDS = 600
# Which audit record closes which kind: (kind, audit action, details.extra.kind) -> resolution.
AUDIT_RESOLUTIONS = {
    ("report", "BAN", ""): "banned",
    ("report", "MUTE", ""): "muted",
    ("appeal", "UNBAN", ""): "unbanned",
    ("appeal", "NOTE", "ban_shortened"): "shortened",
    ("badge", "NOTE", "badge_granted"): "granted",
    ("map", "NOTE", "map_uploaded"): "accepted",
}


def _without_thread_receipts(case: Case, owners: set[str]) -> list[dict[str, Any]]:
    identities = owners | {
        p.player_uuid
        for p in (case.opener, *(party_of(row) for row in case.co_openers))
        if p.discord_id in owners and p.player_uuid
    }
    receipts = []
    for receipt in case.progress.get("notification_receipts", []):
        receipt = dict(receipt)
        if receipt["key"].split(":", 1)[1] in identities:
            receipt.pop("thread", None)
        receipts.append(receipt)
    return receipts


class CaseService:
    def __init__(
        self,
        *,
        store: CaseStore,
        settings,
        registry: FlowRegistry,
        threads,
        notifier,
        claims,
        players=None,
        now: Callable[[], datetime] | None = None,
    ) -> None:
        self.store = store
        self.settings = settings
        self.registry = registry
        self.threads = threads
        self.notifier = notifier
        self.claims = claims
        self.players = players
        self.now = now or (lambda: datetime.now(UTC))
        self.started_at = self.now()
        self._locks: dict[str, tuple[asyncio.Lock, int]] = {}
        self.transport: CaseTransport = NoCaseTransport()
        self.storage_available = True

    @property
    def bot(self):
        """The bot, as ``staff_actions.perform_*`` take it for their first argument."""
        return self.threads.bot

    def set_transport(self, transport: CaseTransport) -> None:
        self.transport = transport

    @asynccontextmanager
    async def _lock(self, key: str) -> AsyncIterator[None]:
        # Counted so a key is dropped once nobody holds or awaits it; keys are per case and player.
        lock, users = self._locks.get(key, (asyncio.Lock(), 0))
        self._locks[key] = (lock, users + 1)
        try:
            async with lock:
                yield
        finally:
            users = self._locks[key][1] - 1
            if users:
                self._locks[key] = (lock, users)
            else:
                del self._locks[key]

    async def get(self, number: int) -> Case:
        case = await self.store.get(number)
        if case is None:
            raise CaseNotFound()
        return case

    async def authorize(self, case: Case, member: Any, verb: str) -> None:
        self.registry.require(case.kind)
        linked_names: tuple[str, ...] = ()
        linked_uuids: tuple[str, ...] = ()
        if case.kind == CaseKind.REPORT and self.players is not None:
            players = await self.players.find_players_by_discord_id(str(member.id))
            linked_uuids = tuple(p.uuid for p in players if p.uuid)
        if (
            case.kind == CaseKind.APPEAL
            and not case.payload.get("ban_snapshot", {}).get("admin_discord_id")
            and self.players is not None
        ):
            players = await self.players.find_players_by_discord_id(str(member.id))
            linked_names = tuple(p.nickname or p.username or "" for p in players)
        check_access(
            self.settings,
            member,
            case,
            verb,
            linked_names=linked_names,
            linked_uuids=linked_uuids,
        )

    def precheck(
        self, case: Case, member: Any, verb: str, locale: str
    ) -> tuple[FormField, ...]:
        """Everything a press can be refused for without I/O; returns the form to open.

        ``case`` is the cached copy, so this only decides the first response. The same checks,
        plus the ones that need MongoDB, run again before anything is changed.
        """
        self.registry.require(case.kind)
        check_access(self.settings, member, case, verb)
        if not case.actionable or case.pending_action:
            raise CaseRace()
        return self.registry.fields_for(case, verb, locale)

    @staticmethod
    def actor(member) -> Actor:
        return Actor(str(member.id), member.display_name)

    async def open(
        self,
        *,
        kind: CaseKind,
        origin: Origin,
        opener: Party,
        subject: Party | None = None,
        payload: dict[str, Any] | None = None,
        evidence: dict[str, Any] | None = None,
        dedup_key: str | None = None,
        flags: tuple[str, ...] = (),
        member=None,
        discord: dict[str, Any] | None = None,
    ) -> Case:
        """Create a case, or return the one this submission already created (8.8).

        A second report on the same subject merges into the open case. For any other kind an
        open case with the same ``dedup_key`` raises ``CaseRace``.
        """
        kind = CaseKind(kind)
        self.registry.require(kind)
        if origin.source == "discord" and member is None:
            raise AccessDenied()
        if member is not None:
            # 8.5: nobody holding the blacklist role opens a case from Discord. A flow that
            # opens for a member it could not resolve (an old forum post) passes none.
            if opener.discord_id != str(member.id):
                raise AccessDenied()
            check_opening(self.settings, member)
        # One bot instance: serialize the cap and cooldown for an opener and kind.
        key = f"open:{kind}:{opener.discord_id or opener.player_uuid or origin.submission_id}"
        async with self._lock(key):
            existing = await self.store.by_submission(origin.submission_id)
            if existing:
                return await self.resume_creation(existing.number)
            if origin.source == "discord":
                await self._check_limits(opener.discord_id, kind)
            now = self.now()
            case = Case(
                number=await self.store.next_case_number(),
                kind=kind,
                status=CaseStatus.CREATING,
                revision=1,
                origin=origin,
                opener=opener,
                subject=subject,
                created_at=now,
                updated_at=now,
                dedup_key=dedup_key,
                payload=dict(payload or {}),
                evidence=dict(evidence or {}),
                discord=dict(discord or {}),
                flags=tuple(flags),
                events=(event("created", self.actor(member) if member else None, now),),
            )
            try:
                await self.store.insert(case)
            except DuplicateKeyError:
                existing = await self.store.by_submission(origin.submission_id)
                if existing:
                    return await self.resume_creation(existing.number)
                existing = (
                    await self.store.by_dedup(kind, dedup_key) if dedup_key else None
                )
                if existing is None:
                    raise
                if kind != CaseKind.REPORT:
                    raise CaseRace(f"Existing case #{existing.number}")
                existing = await self.merge(
                    existing.number, opener=opener, origin=origin, payload=payload
                )
                return await self.resume_creation(existing.number)
        return await self.resume_creation(case.number)

    async def _check_limits(self, discord_id: str, kind: CaseKind) -> None:
        if (
            await self.store.count_open(discord_id, kind)
            >= self.settings.cases_max_open_per_user
        ):
            raise OpenLimit()
        latest = await self.store.latest_submission_at(discord_id, kind)
        if latest:
            wait = (
                self.settings.cases_open_cooldown_seconds
                - (self.now() - latest).total_seconds()
            )
            if wait > 0:
                raise Cooldown(max(1, int(wait + 0.999)))

    async def merge(
        self,
        number: int,
        *,
        opener: Party,
        origin: Origin,
        payload: dict[str, Any] | None = None,
    ) -> Case:
        for _ in range(5):
            case = await self.get(number)
            self.registry.require(case.kind)
            if case.kind != CaseKind.REPORT or not case.active:
                raise InvalidTransition("Only active reports can be merged")
            if case.origin.submission_id == origin.submission_id or any(
                p["submission_id"] == origin.submission_id for p in case.co_openers
            ):
                return case
            joined = {
                **asdict(opener),
                "joined_at": self.now(),
                "submission_id": origin.submission_id,
                "source": origin.source,
                "locale": origin.locale,
                "server": origin.server,
                "payload": dict(payload or {}),
            }
            try:
                # Joining is not a decision, so it does not wait for an action in progress.
                return await self.store.cas(
                    case,
                    push_fields={"co_openers": joined},
                    event=event(
                        "merged_reporter",
                        None,
                        self.now(),
                        submission_id=origin.submission_id,
                    ),
                    action_token=(case.pending_action or {}).get("token"),
                )
            except CaseRace:
                continue
        raise CaseRace()

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
    ) -> Case:
        """A write that is not a decision (stored ids, progress markers, events): it is
        repeated on a lost compare-and-set instead of being reported as a race."""
        for _ in range(5):
            case = await self.get(number)
            try:
                return await self.store.cas(
                    case,
                    set_fields=set_fields,
                    unset_fields=unset_fields,
                    event=event(event_type, actor, self.now(), **(data or {}))
                    if event_type
                    else None,
                    action_token=action_token,
                )
            except CaseRace:
                if case.pending_action and action_token is None:
                    raise
        raise CaseRace()

    @staticmethod
    def owes_thread(case: Case, joined: dict[str, Any]) -> bool:
        """Whether a merged reporter still waits for a private thread of their own.

        Only someone who reported from Discord gets one; a game reporter hears in game and
        by DM (8.4). The opener reporting again keeps the thread they have.
        """
        discord_id = joined.get("discord_id")
        if joined.get("source") != "discord" or not discord_id:
            return False
        if discord_id == case.opener.discord_id and case.discord.get("user_thread_id"):
            return False
        return not any(
            row["discord_id"] == discord_id and row.get("thread_id") and row.get("done")
            for row in case.discord.get("reporter_threads", [])
        )

    @classmethod
    def creation_complete(cls, case: Case) -> bool:
        merged = case.progress.get("merged_submissions", [])
        return (
            case.status != CaseStatus.CREATING
            and bool(case.progress.get("creation_done"))
            and (
                case.origin.source != "discord"
                or bool(
                    case.discord.get("user_thread_id")
                    and case.progress.get("user_thread_done")
                )
            )
            and all(p["submission_id"] in merged for p in case.co_openers)
            and not any(cls.owes_thread(case, p) for p in case.co_openers)
        )

    async def resume_creation(self, number: int) -> Case:
        """Run the creation steps that are not recorded as done; each one is idempotent."""
        async with self._lock(f"projection:{number}"):
            case = await self.get(number)
            flow = self.registry.require(case.kind)
            if not case.active or case.pending_action:
                return case
            try:
                case = await self.threads.validate_private_threads(self, case)
                if self.creation_complete(case):
                    await self.threads.reconcile_memberships(self, case)
                    return await self.get(number)
                await self._create(case, flow)
            except Exception as error:
                log_failure(
                    logger, case.kind, "Creation of case #%s is incomplete", number
                )
                await self._note_error(number, "creation", error)
            return await self.get(number)

    async def _create(self, case: Case, flow) -> None:
        number = case.number
        if not case.discord.get("thread_id") or not case.discord.get("card_message_id"):
            # Only a retry looks for a post to adopt: the search walks the forum.
            adopt = bool(case.progress.get("post_attempted"))
            if not adopt:
                case = await self.checkpoint(
                    number, set_fields={"progress.post_attempted": True}
                )
            ids = await self.threads.ensure_post(self, case, adopt=adopt)
            fields = {f"discord.{key}": value for key, value in ids.items()}
            if case.status == CaseStatus.CREATING:
                fields["status"] = CaseStatus.OPEN
            case = await self.checkpoint(number, set_fields=fields)
        elif case.status == CaseStatus.CREATING:
            case = await self.checkpoint(number, set_fields={"status": CaseStatus.OPEN})
        if not case.progress.get("evidence_done"):
            await self.threads.post_evidence(self, case)
            case = await self.checkpoint(
                number, set_fields={"progress.evidence_done": True}
            )
        case = await self._ensure_private_threads(case)
        case = await self._creation_hooks(case, flow)
        await self.threads.render(self, case)
        await self.checkpoint(number, set_fields={"progress.creation_done": True})

    async def _creation_hooks(self, case: Case, flow) -> Case:
        number = case.number
        if not case.progress.get("created_hook_done") and (
            case.origin.source != "discord" or case.progress.get("user_thread_done")
        ):
            if flow.on_created:
                # The hook must be idempotent: it can run again after a failed write.
                await flow.on_created(self, case)
            case = await self.checkpoint(
                number, set_fields={"progress.created_hook_done": True}
            )
        completed = list(case.progress.get("merged_submissions", []))
        for joined in case.co_openers:
            if self.owes_thread(case, joined):
                continue
            if joined["submission_id"] not in completed:
                if flow.on_merged:
                    await flow.on_merged(self, case, joined)
                completed.append(joined["submission_id"])
                case = await self.checkpoint(
                    number,
                    set_fields={"progress.merged_submissions": completed},
                )
        return case

    async def _ensure_private_threads(self, case: Case) -> Case:
        number = case.number
        errors = []
        owed = {p["discord_id"] for p in case.co_openers if self.owes_thread(case, p)}
        if case.origin.source == "discord" and (
            not case.discord.get("user_thread_id")
            or not case.progress.get("user_thread_done")
        ):
            owed.add(case.opener.discord_id)
        if owed and not case.active:
            missing = owed - case.user_threads.keys()
            case = await self.checkpoint(
                number,
                set_fields={
                    "progress.notification_receipts": _without_thread_receipts(
                        case, missing
                    )
                },
                unset_fields=("progress.outcomes_done", "progress.closed_done"),
            )
        try:
            if case.origin.source == "discord" and (
                not case.discord.get("user_thread_id")
                or not case.progress.get("user_thread_done")
            ):
                adopt = bool(case.progress.get("user_thread_attempted"))
                if not adopt:
                    case = await self.checkpoint(
                        number, set_fields={"progress.user_thread_attempted": True}
                    )
                ids = await self.threads.ensure_user_thread(self, case, adopt=adopt)
                fields = {f"discord.{key}": value for key, value in ids.items()}
                fields["progress.user_thread_done"] = True
                case = await self.checkpoint(number, set_fields=fields)
        except Exception as error:
            errors.append(error)
        case = await self.get(number)
        for joined in case.co_openers:
            if self.owes_thread(case, joined):
                try:
                    case = await self.threads.ensure_reporter_thread(self, case, joined)
                except Exception as error:
                    errors.append(error)
        if errors:
            raise ExceptionGroup("Owned private threads are incomplete", errors)
        return await self.get(number)

    async def _note_error(self, number: int, step: str, error: Exception) -> None:
        data = {"step": step, "error_type": type(error).__name__}
        try:
            case = await self.get(number)
            last = case.events[-1] if case.events else {}
            # A step retried every few minutes must not fill the history with one line.
            if last.get("type") == "error" and last.get("data") == data:
                return
            await self.checkpoint(number, event_type="error", data=data)
        except CaseError:
            logger.debug("Case #%s changed while recording an error", number)

    async def _staff_case(self, number: int, member, verb: str) -> Case:
        case = await self.get(number)
        await self.authorize(case, member, verb)
        return case

    async def _update(
        self,
        number: int,
        member,
        verb: str,
        change: Callable[[Case], tuple[dict[str, Any], dict[str, Any]]],
    ) -> Case:
        """A staff decision as a compare-and-set.

        Role, conflict and state are read again on every attempt, so a real conflict (someone
        else took or closed the case) is refused by ``change`` while an unrelated write, such
        as a merged reporter, only costs a retry.
        """
        for _ in range(5):
            case = await self._staff_case(number, member, verb)
            if case.pending_action:
                raise CaseRace()
            fields, entry = change(case)
            try:
                return await self.store.cas(case, set_fields=fields, event=entry)
            except CaseRace:
                continue
        raise CaseRace()

    async def take(self, number: int, member) -> Case:
        def change(case: Case):
            if case.status != CaseStatus.OPEN:
                raise InvalidTransition()
            now = self.now()
            return {
                "status": CaseStatus.TAKEN,
                "assignee": {
                    "discord_id": str(member.id),
                    "name": member.display_name,
                    "taken_at": now,
                },
            }, event("taken", self.actor(member), now)

        await self._update(number, member, "take", change)
        await self.project(number)
        return await self.get(number)

    async def release(self, number: int, member) -> Case:
        def change(case: Case):
            if case.status != CaseStatus.TAKEN:
                raise InvalidTransition()
            return {"status": CaseStatus.OPEN, "assignee": None}, event(
                "released", self.actor(member), self.now()
            )

        await self._update(number, member, "release", change)
        await self.project(number)
        return await self.get(number)

    async def set_subject(self, number: int, member, subject: Party) -> Case:
        def change(case: Case):
            if not case.actionable:
                raise InvalidTransition()
            fields = {
                "subject": asdict(subject),
                "flags": [flag for flag in case.flags if flag != "subject_unresolved"],
            }
            if case.kind == CaseKind.REPORT and subject.player_uuid:
                fields["dedup_key"] = f"report:{subject.player_uuid}"
            if case.kind == CaseKind.COMPLAINT:
                fields["progress.complaint_subject_revision"] = case.revision + 1
                fields["progress.complaint_history_pending"] = True
                fields["progress.user_thread_staff"] = [
                    staff
                    for staff in case.progress.get("user_thread_staff", [])
                    if staff != subject.discord_id
                ]
                if (case.assignee or {}).get("discord_id") == subject.discord_id:
                    fields.update(status=CaseStatus.OPEN, assignee=None)
            return fields, event("subject_set", self.actor(member), self.now())

        async with self._lock(f"projection:{number}"):
            try:
                case = await self._update(number, member, "subject", change)
            except DuplicateKeyError as error:
                raise CaseRace() from error
            if case.kind == CaseKind.COMPLAINT:
                try:
                    # Serialize cleanup with subject decisions and invitation projection.
                    # Evidence I/O runs afterwards, outside this lock.
                    await self.threads.exclude_subject(case)
                except Exception:
                    log_failure(
                        logger,
                        case.kind,
                        "Case #%s needs subject membership repair",
                        number,
                    )
        flow = self.registry.require(case.kind)
        if flow.on_subject_set:
            try:
                await flow.on_subject_set(self, case)
            except Exception:
                log_failure(
                    logger, case.kind, "Case #%s needs subject evidence repair", number
                )
        await self.project(number)
        return await self.get(number)

    async def exclude_subject(self, number: int) -> None:
        """Repair uses the CURRENT subject under the same lock as decisions/projection."""
        async with self._lock(f"projection:{number}"):
            case = await self.get(number)
            await self.threads.exclude_subject(case)

    async def resolve(
        self, number: int, member, resolution: Resolution, *, verb: str = "resolve"
    ) -> Case:
        """Close with a resolution that needs no staff action (a complaint upheld)."""
        return await self.execute_action(
            number, verb, member, lambda case: _resolved(resolution)
        )

    async def reject(
        self,
        number: int,
        member,
        *,
        reason_code: str,
        reason_text: str | None = None,
    ) -> Case:
        if not reason_code.strip():
            raise ValueError("A rejection reason is required")
        resolution = Resolution(
            "rejected",
            reason_code=reason_code.strip()[:100],
            reason_text=(reason_text or "").strip()[:1000] or None,
        )
        return await self.execute_action(
            number, "reject", member, lambda case: _resolved(resolution)
        )

    async def execute_action(
        self,
        number: int,
        verb: str,
        member,
        action: Callable[[Case], Awaitable[Resolution]],
    ) -> Case:
        """The action pipeline of 8.3: claim, staff action, close, notify, re-render.

        ``action`` performs the side effect (``staff_actions.*`` with ``case_number`` and the pending token) and
        returns the resolution. If it raises without completion evidence, the case stays open
        with an ``error`` event; an external mutation may still have happened. A matching
        durable completion audit lets the core finish closure without replaying the action.
        """
        case = await self._staff_case(number, member, verb)
        if not case.actionable or case.pending_action:
            raise CaseRace()
        # A claim cannot be released, so the revision is part of the key: a repeated press on
        # the same state is dropped, while the press after a failed attempt is a new claim.
        if not await self.claims.claim_idempotency(
            f"case-act:{number}:{verb}:{case.revision}",
            ttl_seconds=ACTION_GUARD_SECONDS,
        ):
            raise CaseRace()
        token = uuid4().hex
        actor = self.actor(member)
        # The claim is per verb. This guard also keeps two different actions (Ban and Mute by
        # two people) from both running, and marks an interrupted action for the reconcile task.
        claimed = await self.store.cas(
            case,
            set_fields={
                "pending_action": {
                    "token": token,
                    "verb": verb,
                    "at": self.now(),
                    "actor": asdict(actor),
                }
            },
            event=event("action_started", actor, self.now(), verb=verb, token=token),
        )
        try:
            resolution = await action(claimed)
            self._check_resolution(case.kind, resolution)
        except Exception as error:
            if not await self._recover(number):
                await self._release_guard(number, token, actor, verb, type(error))
                raise
            logger.warning(
                "Case #%s: %s failed after completion was recorded; closed from the audit",
                number,
                verb,
                exc_info=case.kind != CaseKind.COMPLAINT,
            )
        else:
            await self._finish_action(
                number,
                token,
                replace(
                    resolution,
                    at=self.now(),
                    by_discord_id=actor.discord_id,
                    by_name=actor.name,
                ),
                actor,
            )
        await self.finish_close(number)
        return await self.get(number)

    @staticmethod
    def _check_resolution(kind: CaseKind, resolution: Resolution) -> None:
        if (
            not isinstance(resolution, Resolution)
            or resolution.code not in RESOLUTIONS[kind]
        ):
            raise ValueError("Resolution is not valid for this kind")

    async def _finish_action(
        self, number: int, token: str, resolution: Resolution, actor: Actor
    ) -> Case:
        for _ in range(5):
            case = await self.get(number)
            if (case.pending_action or {}).get("token") != token:
                break
            try:
                return await self._close(case, resolution, actor, action_token=token)
            except CaseRace:
                continue
        raise CaseRace()

    async def _release_guard(
        self, number: int, token: str, actor: Actor | None, verb: str, reason: type
    ) -> None:
        try:
            await self.checkpoint(
                number,
                unset_fields=("pending_action",),
                event_type="error",
                actor=actor,
                data={"verb": verb, "error_type": reason.__name__},
                action_token=token,
            )
        except CaseError:
            log_failure(
                logger,
                (self.store.peek(number).kind if self.store.peek(number) else None),
                "Case #%s: the action guard could not be released",
                number,
            )

    async def _close(
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
            "closed_at": resolution.at or self.now(),
        }
        if case.assignee is None and actor is not None and actor.discord_id:
            # 4.2: acting on an open case makes the actor its assignee.
            fields["assignee"] = {
                "discord_id": actor.discord_id,
                "name": actor.name,
                "taken_at": self.now(),
            }
        return await self.store.cas(
            case,
            set_fields=fields,
            unset_fields=("pending_action",),
            event=event(
                "rejected" if resolution.code == "rejected" else "action",
                actor,
                self.now(),
                code=resolution.code,
                audit_id=resolution.audit_id,
            ),
            action_token=action_token,
        )

    async def close_validated(self, case: Case, resolution: Resolution) -> Case:
        """Close an observed sanction only at the revision its caller validated.

        A lost CAS is left to the next event/repair pass, which must validate subject and
        filing/reopen cutoff again. Never reload and silently close a newer revision.
        """
        self.registry.require(case.kind)
        if not case.actionable or case.pending_action:
            raise CaseRace()
        self._check_resolution(case.kind, resolution)
        await self._close(
            case, replace(resolution, at=resolution.at or self.now()), None
        )
        await self.finish_close(case.number)
        return await self.get(case.number)

    async def recover_action(self, number: int) -> bool:
        """Reconcile duty 3: finish a close whose sanction is already in the audit trail."""
        if not await self._recover(number):
            return False
        await self.finish_close(number)
        return True

    async def _recover(self, number: int) -> bool:
        case = await self.get(number)
        if not case.actionable:
            return False
        self.registry.require(case.kind)
        # Event order is authoritative: Mongo timestamps can share a millisecond across
        # a close/reopen. Never revive an earlier attempt, even if its audit arrived late.
        recent_events = []
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
        audits = await self.store.audits_for(case, action_token=token, since=threshold)
        if legacy_guard:
            audits += [
                audit
                for audit in await self.store.audits_for(case, since=threshold)
                if not audit.get("details", {})
                .get("extra", {})
                .get("case_action_token")
            ]
        for audit in audits:
            extra = audit.get("details", {}).get("extra", {})
            if not token and extra.get("case_action_token"):
                continue
            code = self._completion_code(case, audit, verb=verb, strict=bool(token))
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
                await self._close(
                    case,
                    resolution,
                    actor,
                    action_token=(case.pending_action or {}).get("token"),
                )
            except CaseRace:
                # Someone else closed it in between; the next pass looks again.
                return not (await self.get(number)).actionable
            return True
        return False

    @staticmethod
    def _completion_code(
        case: Case, audit: dict[str, Any], *, verb: str | None, strict: bool
    ) -> str | None:
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

    async def release_abandoned_action(self, number: int) -> bool:
        """Free a case whose action neither finished nor left an audit record.

        The outcome may be unknown. Nothing is executed again: a person decides.
        """
        case = await self.get(number)
        pending = case.pending_action
        if not pending or self.now() - utc(pending["at"]) < timedelta(
            seconds=ACTION_GUARD_SECONDS
        ):
            return False
        await self._release_guard(
            number, pending["token"], None, pending["verb"], TimeoutError
        )
        await self.project(number)
        return True

    async def reopen(self, number: int, member) -> Case:
        async with self._lock(f"projection:{number}"):
            case = await self._staff_case(number, member, "reopen")
            if case.status not in {CaseStatus.RESOLVED, CaseStatus.REJECTED}:
                raise InvalidTransition()
            try:
                await self.store.cas(
                    case,
                    set_fields={
                        "status": CaseStatus.OPEN,
                        "resolution": None,
                        "assignee": None,
                    },
                    unset_fields=(
                        "closed_at",
                        "progress.closed_done",
                        "progress.closed_hook_done",
                        "progress.outcomes_done",
                        "progress.creation_done",
                        "progress.notification_receipts",
                    ),
                    event=event("reopened", self.actor(member), self.now()),
                )
            except DuplicateKeyError as error:
                # Another active case owns this dedup key; reopening must not break the index.
                raise CaseRace() from error
            # Outcome notice ids are per (case, player, kind): free them for the next decision
            # and drop a stale undelivered one. The registration notice stays deduplicated.
            await self.store.database["case_notices"].delete_many(
                {"case_number": number, "kind": {"$ne": "report_received"}}
            )
        # Re-renders the card, which unlocks and unarchives the post and the user thread.
        return await self.resume_creation(number)

    async def cancel(self, number: int, *, reason: str) -> Case:
        case = await self.get(number)
        self.registry.require(case.kind)
        updated = await self.store.cas(
            case,
            set_fields={"status": CaseStatus.CANCELLED, "closed_at": self.now()},
            event=event("cancelled", None, self.now(), reason=reason),
        )
        await self.project(number)
        return updated

    async def thread_deleted(
        self, thread_id: int, parent_id: int | None = None
    ) -> None:
        if parent_id is not None and not self.threads.owns_parent(parent_id):
            return
        case = await self.store.by_thread(thread_id)
        if case is None or not self.registry.enabled(case.kind):
            return
        is_post = case.discord.get("thread_id") == str(thread_id)
        set_fields = None
        if is_post:
            fields = (
                "discord.thread_id",
                "discord.card_message_id",
                "progress.creation_done",
                "progress.evidence_done",
            )
        elif case.discord.get("user_thread_id") == str(thread_id):
            fields = (
                "discord.user_thread_id",
                "progress.creation_done",
                "progress.user_thread_done",
            )
        else:
            # A merged reporter's thread: forget it, creation gives them a new one.
            fields = ("progress.creation_done",)
            set_fields = {
                "discord.reporter_threads": [
                    row
                    for row in case.discord.get("reporter_threads", [])
                    if row.get("thread_id") != str(thread_id)
                ]
            }
        owner = next(
            (
                owner
                for owner, tid in case.user_threads.items()
                if tid == str(thread_id)
            ),
            None,
        )
        if owner:
            set_fields = {
                **(set_fields or {}),
                "progress.notification_receipts": _without_thread_receipts(
                    case, {owner}
                ),
                "discord.managed_memberships": [
                    row
                    for row in case.discord.get("managed_memberships", [])
                    if row["thread_id"] != str(thread_id)
                ],
            }
            fields += ("progress.outcomes_done", "progress.closed_done")
        await self.checkpoint(
            case.number,
            set_fields=set_fields,
            unset_fields=fields,
            event_type="thread_deleted",
            data={"thread_id": str(thread_id)},
            action_token=(case.pending_action or {}).get("token"),
        )
        if (
            is_post
            and case.kind == CaseKind.MAP
            and case.status in {CaseStatus.CREATING, CaseStatus.OPEN}
            and not case.pending_action
        ):
            await self.cancel(case.number, reason="map_post_deleted")

    async def project(self, number: int) -> None:
        """Re-render the card, tags and thread state from the stored case."""
        async with self._lock(f"projection:{number}"):
            case = await self.get(number)
            self.registry.require(case.kind)
            try:
                await self.threads.render(self, case)
            except Exception:
                log_failure(
                    logger, case.kind, "Case #%s needs a projection repair", number
                )

    async def finish_close(self, number: int) -> None:
        """Everything after the close is stored: flow hook, outcomes, card, tags, archive.

        Every step records its completion, so the reconcile task can repeat this safely.
        """
        async with self._lock(f"projection:{number}"):
            case = await self.get(number)
            flow = self.registry.require(case.kind)
            if case.status not in {CaseStatus.RESOLVED, CaseStatus.REJECTED}:
                return
            errors = []
            try:
                case = await self.threads.validate_private_threads(self, case)
            except Exception as error:
                errors.append(error)
            try:
                case = await self._ensure_private_threads(await self.get(number))
            except Exception as error:
                errors.append(error)
            case = await self.get(number)
            if case.progress.get("closed_done") and not errors:
                try:
                    await self.threads.reconcile_memberships(self, case)
                except Exception as error:
                    await self._note_error(number, "closing", error)
                return
            try:
                case = await self._creation_hooks(case, flow)
            except Exception as error:
                errors.append(error)
            try:
                if not case.progress.get("closed_hook_done"):
                    if flow.on_closed:
                        await flow.on_closed(self, case)
                    case = await self.checkpoint(
                        number, set_fields={"progress.closed_hook_done": True}
                    )
            except Exception as error:
                errors.append(error)
            if not case.progress.get("outcomes_done"):
                for outcome in flow.outcomes(case) if flow.outcomes else ():
                    try:
                        await self.notifier.send(self, case, outcome)
                    except Exception as error:
                        log_failure(
                            logger, case.kind, "Case #%s needs closing repair", number
                        )
                        errors.append(error)
                if not errors:
                    case = await self.checkpoint(
                        number,
                        set_fields={"progress.outcomes_done": True},
                        event_type="notified",
                    )
            try:
                await self.threads.render(self, await self.get(number))
            except Exception as error:
                log_failure(logger, case.kind, "Case #%s needs closing repair", number)
                errors.append(error)
            if errors:
                await self._note_error(
                    number, "closing", ExceptionGroup("Closing is incomplete", errors)
                )
            else:
                await self.checkpoint(number, set_fields={"progress.closed_done": True})

    async def dispatch_action(
        self,
        number: int,
        verb: str,
        member,
        locale: str,
        values: dict[str, Any] | None = None,
        *,
        interaction=None,
    ) -> Case | str | None:
        """One press of a card button, after the interaction is acknowledged.

        Flag, role, conflict, state and the submitted form are all checked here against the
        stored case, whatever the first response was based on.
        """
        case = await self._staff_case(number, member, verb)
        if not case.actionable or case.pending_action:
            raise CaseRace()
        cleaned = clean_values(self.registry.fields_for(case, verb, locale), values)
        if verb == "take":
            return await self.take(number, member)
        if verb == "release":
            return await self.release(number, member)
        if verb == "reject":
            return await self.reject(
                number,
                member,
                reason_code=cleaned.get("reason_code") or REJECT_OTHER,
                reason_text=cleaned.get("reason_text"),
            )
        action = self.registry.action(case.kind, verb)
        context = ActionContext(
            self, case, member, locale, verb, cleaned, interaction=interaction
        )
        result = await action.handler(context)
        current = await self.get(number)
        if current.active and current.revision != case.revision:
            await self.project(number)
        if result is None and not context.replied:
            return current
        return result


async def _resolved(resolution: Resolution) -> Resolution:
    return resolution
