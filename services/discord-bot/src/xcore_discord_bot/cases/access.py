from __future__ import annotations

from collections.abc import Iterable

from ..permissions import (
    admin_role_ids,
    general_admin_role_ids,
    has_any_role,
    head_admin_role_ids,
    map_reviewer_role_ids,
    member_role_ids,
)
from ..settings import Settings
from .model import AccessDenied, Case, CaseKind, OpeningBlocked

SENIOR_VERBS = frozenset({"reopen", "subject", "publish"})


def roles_for(settings: Settings, kind: CaseKind, verb: str) -> tuple[int, ...]:
    if kind == CaseKind.COMPLAINT or verb in SENIOR_VERBS:
        return head_admin_role_ids(settings)
    if verb == "find":
        return staff_roles(settings)
    if verb == "release":
        return tuple(
            dict.fromkeys(
                roles_for(settings, kind, "take") + head_admin_role_ids(settings)
            )
        )
    if kind == CaseKind.BADGE:
        return general_admin_role_ids(settings)
    if kind == CaseKind.MAP:
        return map_reviewer_role_ids(settings)
    return admin_role_ids(settings)


def staff_roles(settings: Settings) -> tuple[int, ...]:
    return tuple(
        set(
            admin_role_ids(settings)
            + general_admin_role_ids(settings)
            + head_admin_role_ids(settings)
            + map_reviewer_role_ids(settings)
        )
    )


def check_opening(settings: Settings, member: object) -> None:
    if (
        settings.cases_blacklist_role_id
        and settings.cases_blacklist_role_id in member_role_ids(member)
    ):
        raise OpeningBlocked()


def conflicted(
    case: Case,
    discord_id: str,
    linked_names: Iterable[str] = (),
    linked_uuids: Iterable[str] = (),
) -> bool:
    def matches(party) -> bool:
        return party is not None and party.discord_id == discord_id

    if case.kind == CaseKind.REPORT:
        involved = {
            p.player_uuid for p in (case.opener, case.subject) if p and p.player_uuid
        }
        involved.update(
            p["player_uuid"] for p in case.co_openers if p.get("player_uuid")
        )
        return (
            matches(case.opener)
            or matches(case.subject)
            or any(p.get("discord_id") == discord_id for p in case.co_openers)
            or bool(involved.intersection(linked_uuids))
        )
    if case.kind == CaseKind.COMPLAINT:
        return matches(case.subject)
    if case.kind == CaseKind.MAP:
        return matches(case.opener)
    if case.kind == CaseKind.APPEAL:
        ban = case.payload.get("ban_snapshot", {})
        issuer = ban.get("admin_discord_id")
        if issuer:
            return str(issuer) == discord_id
        name = str(ban.get("admin_name") or "").strip().casefold()
        return bool(name) and name in {n.strip().casefold() for n in linked_names}
    return False


def check_access(
    settings: Settings,
    member: object,
    case: Case,
    verb: str,
    *,
    linked_names: Iterable[str] = (),
    linked_uuids: Iterable[str] = (),
) -> None:
    discord_id = str(getattr(member, "id", ""))
    if not discord_id or not has_any_role(member, roles_for(settings, case.kind, verb)):
        raise AccessDenied()
    # There is no senior override for a conflict, including read buttons and commands.
    if conflicted(case, discord_id, linked_names, linked_uuids):
        raise AccessDenied()
    if (
        verb == "release"
        and case.assignee
        and case.assignee["discord_id"] != discord_id
        and not has_any_role(member, head_admin_role_ids(settings))
    ):
        raise AccessDenied()
