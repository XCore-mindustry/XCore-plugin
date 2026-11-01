"""Bounded audit history shared by the cases flows."""

from datetime import UTC, datetime

import discord

from ..cards import safe_text
from ..model import utc

SANCTIONS = (("BAN", "bans"), ("MUTE", "mutes"), ("KICK", "kicks"), ("WARN", "warns"))


def audit_at(row, *, epoch_fallback=True):
    if isinstance(row.get("occurred_at"), datetime):
        return utc(row["occurred_at"]) if epoch_fallback else row["occurred_at"]
    if epoch_fallback and isinstance(row.get("created_at_epoch_ms"), int):
        return datetime.fromtimestamp(row["created_at_epoch_ms"] / 1000, UTC)
    return None


def audit_entry(row, secrets, *, epoch_fallback=True, action_secrets=False):
    return {
        "action": safe_text(
            row.get("action"), secrets=secrets if action_secrets else (), limit=32
        ),
        "at": audit_at(row, epoch_fallback=epoch_fallback),
        "reason": safe_text(row.get("reason"), secrets=secrets, limit=120),
        "actor_name": safe_text(
            row.get("actor", {}).get("name_snapshot"), secrets=secrets, limit=64
        ),
    }


async def history_rows(service, predicate, limit, *, sanctions_only=True):
    audit = service.store.database["moderation_audit"]
    counts = {
        key: await audit.count_documents({**predicate, "action": action})
        for action, key in SANCTIONS
    }
    query = (
        {**predicate, "action": {"$in": [a for a, _ in SANCTIONS]}}
        if sanctions_only
        else predicate
    )
    rows = [
        row
        async for row in audit.find(query).sort("created_at_epoch_ms", -1).limit(limit)
    ]
    return counts, rows


def audit_line(entry, *, date_style="d", actor=False, date_first=True):
    at = (
        discord.utils.format_dt(utc(entry["at"]), date_style) if entry.get("at") else ""
    )
    parts = [at, entry["action"]] if date_first else [entry["action"], at]
    parts.append(entry["reason"])
    if actor:
        parts.append(entry["actor_name"])
    return " · ".join(filter(None, parts))
