from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime
from enum import StrEnum
from typing import Any

from ..staff_actions import Actor


class CaseKind(StrEnum):
    REPORT = "report"
    APPEAL = "appeal"
    BADGE = "badge"
    COMPLAINT = "complaint"
    MAP = "map"


class CaseStatus(StrEnum):
    CREATING = "creating"
    OPEN = "open"
    TAKEN = "taken"
    RESOLVED = "resolved"
    REJECTED = "rejected"
    CANCELLED = "cancelled"


ACTIVE_STATUSES = frozenset({CaseStatus.CREATING, CaseStatus.OPEN, CaseStatus.TAKEN})
ACTION_STATUSES = frozenset({CaseStatus.OPEN, CaseStatus.TAKEN})
TRANSITIONS = {
    CaseStatus.CREATING: frozenset({CaseStatus.OPEN, CaseStatus.CANCELLED}),
    CaseStatus.OPEN: frozenset(
        {
            CaseStatus.TAKEN,
            CaseStatus.RESOLVED,
            CaseStatus.REJECTED,
            CaseStatus.CANCELLED,
        }
    ),
    CaseStatus.TAKEN: frozenset(
        {CaseStatus.OPEN, CaseStatus.RESOLVED, CaseStatus.REJECTED}
    ),
    CaseStatus.RESOLVED: frozenset({CaseStatus.OPEN}),
    CaseStatus.REJECTED: frozenset({CaseStatus.OPEN}),
    CaseStatus.CANCELLED: frozenset(),
}
RESOLUTIONS = {
    CaseKind.REPORT: frozenset({"banned", "muted", "rejected"}),
    CaseKind.APPEAL: frozenset({"unbanned", "shortened", "rejected"}),
    CaseKind.BADGE: frozenset({"granted", "rejected"}),
    CaseKind.COMPLAINT: frozenset({"upheld", "rejected"}),
    CaseKind.MAP: frozenset({"accepted", "rejected"}),
}


class CaseError(ValueError):
    key = "error-generic"


class InvalidTransition(CaseError):
    key = "error-already-handled"


class CaseRace(CaseError):
    key = "error-already-handled"


class CaseNotFound(CaseError):
    key = "error-not-found"


class FlowDisabled(CaseError):
    key = "error-try-again-later"


class AccessDenied(CaseError):
    key = "error-not-allowed"


class OpeningBlocked(AccessDenied):
    key = "error-opening-blocked"


class OpenLimit(CaseError):
    key = "cases-open-limit"


class Cooldown(CaseError):
    key = "error-cooldown"

    def __init__(self, seconds: int) -> None:
        self.seconds = seconds
        super().__init__(str(seconds))


class InvalidForm(CaseError):
    key = "cases-form-invalid"


class ActionFailed(CaseError):
    """A flow refuses or fails an action with its own, already localised explanation."""

    key = "cases-action-failed"

    def __init__(self, text: str) -> None:
        self.text = text
        super().__init__(text)


def utc(value: datetime) -> datetime:
    return value.replace(tzinfo=UTC) if value.tzinfo is None else value.astimezone(UTC)


def check_transition(before: CaseStatus, after: CaseStatus) -> None:
    if after not in TRANSITIONS[before]:
        raise InvalidTransition(f"{before} → {after}")


def event(
    event_type: str, actor: Actor | None, at: datetime, **data: Any
) -> dict[str, Any]:
    return {
        "at": at,
        "type": event_type,
        "actor": {
            "type": "discord" if actor and actor.discord_id else "bot",
            "discord_id": actor.discord_id if actor else None,
            "name": actor.name if actor else "bot",
        },
        "data": data,
    }


@dataclass(frozen=True, slots=True)
class Party:
    player_uuid: str | None = None
    player_pid: int | None = None
    player_name: str | None = None
    discord_id: str | None = None
    discord_name: str | None = None


def party_of(row: dict[str, Any]) -> Party:
    """The ``Party`` inside a stored row that carries more than its fields (``co_openers``)."""
    return Party(**{name: row.get(name) for name in Party.__dataclass_fields__})


def same_person(left: Party, right: Party) -> bool:
    return bool(
        (left.player_uuid and left.player_uuid == right.player_uuid)
        or (left.discord_id and left.discord_id == right.discord_id)
    )


@dataclass(frozen=True, slots=True)
class Origin:
    source: str
    submission_id: str
    locale: str = "en"
    server: str | None = None

    def __post_init__(self) -> None:
        if self.source not in {"game", "discord", "forum"}:
            raise ValueError("Invalid case origin")
        if not self.submission_id or len(self.submission_id) > 64:
            raise ValueError("Submission id must contain 1–64 characters")
        if self.locale not in {"en", "ru", "uk"}:
            raise ValueError("Unsupported case locale")


@dataclass(frozen=True, slots=True)
class Resolution:
    code: str
    reason_code: str | None = None
    reason_text: str | None = None
    by_discord_id: str | None = None
    by_name: str | None = None
    at: datetime | None = None
    ban_id: int | None = None
    audit_id: str | None = None
    duration_ms: int | None = None
    server: str | None = None
    file_name: str | None = None

    def __post_init__(self) -> None:
        if self.code == "rejected" and not (self.reason_code or "").strip():
            raise ValueError("A rejection reason code is required")


@dataclass(frozen=True, slots=True)
class Case:
    number: int
    kind: CaseKind
    status: CaseStatus
    revision: int
    origin: Origin
    opener: Party
    subject: Party | None
    created_at: datetime
    updated_at: datetime
    dedup_key: str | None = None
    co_openers: tuple[dict[str, Any], ...] = ()
    payload: dict[str, Any] = field(default_factory=dict)
    evidence: dict[str, Any] = field(default_factory=dict)
    discord: dict[str, Any] = field(default_factory=dict)
    assignee: dict[str, Any] | None = None
    resolution: Resolution | None = None
    flags: tuple[str, ...] = ()
    events: tuple[dict[str, Any], ...] = ()
    closed_at: datetime | None = None
    progress: dict[str, Any] = field(default_factory=dict)
    pending_action: dict[str, Any] | None = None

    @property
    def active(self) -> bool:
        return self.status in ACTIVE_STATUSES

    @property
    def actionable(self) -> bool:
        return self.status in ACTION_STATUSES

    @property
    def user_threads(self) -> dict[str, str]:
        """Private thread id by the Discord id of the person it belongs to.

        The opener's thread is ``discord.user_thread_id``. A report can also hold one thread
        for each Discord reporter who joined it later (``discord.reporter_threads``).
        """
        threads: dict[str, str] = {}
        if self.opener.discord_id and self.discord.get("user_thread_id"):
            threads[self.opener.discord_id] = self.discord["user_thread_id"]
        for row in self.discord.get("reporter_threads", []):
            if row.get("thread_id"):
                threads.setdefault(row["discord_id"], row["thread_id"])
        return threads

    @property
    def reporters(self) -> tuple[Party, ...]:
        """The opener and everyone merged into the case, each person once."""
        parties: list[Party] = []
        for party in (self.opener, *(party_of(row) for row in self.co_openers)):
            if not any(same_person(party, known) for known in parties):
                parties.append(party)
        return tuple(parties)

    def to_doc(self) -> dict[str, Any]:
        doc = asdict(self)
        doc["_id"] = doc.pop("number")
        doc["kind"], doc["status"] = self.kind.value, self.status.value
        for key in ("co_openers", "flags", "events"):
            doc[key] = list(doc[key])
        if self.active:
            doc["active"] = True
        if self.dedup_key is None:
            doc.pop("dedup_key")
        if self.pending_action is None:
            doc.pop("pending_action")
        return doc

    @classmethod
    def from_doc(cls, doc: dict[str, Any]) -> Case:
        values = {name: doc[name] for name in cls.__dataclass_fields__ if name in doc}
        values.update(
            number=int(doc["_id"]),
            kind=CaseKind(doc["kind"]),
            status=CaseStatus(doc["status"]),
            origin=Origin(**doc["origin"]),
            opener=Party(**doc["opener"]),
            subject=Party(**doc["subject"]) if doc.get("subject") else None,
        )
        values["resolution"] = (
            Resolution(**doc["resolution"]) if doc.get("resolution") else None
        )
        for key in ("co_openers", "flags", "events"):
            values[key] = tuple(doc.get(key, ()))
        for key in ("created_at", "updated_at", "closed_at"):
            if values.get(key) is not None:
                values[key] = utc(values[key])
        return cls(**values)
