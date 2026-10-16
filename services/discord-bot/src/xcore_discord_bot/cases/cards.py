from __future__ import annotations

import re
from collections.abc import Iterable, Sequence
from ipaddress import ip_address
from typing import TYPE_CHECKING

import discord

from ..i18n import t
from ..utils.mindustry_colors import strip_mindustry_colors
from .ids import format_case_number
from .model import Case, CaseKind, Party, Resolution, utc

if TYPE_CHECKING:
    from .registry import Choice

_IP = re.compile(r"(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?(?![\w.])")
_UUID = re.compile(r"\b[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\b")
_IPV6 = re.compile(r"(?<![\w:])(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}(?![\w:])")


def safe_text(value: object, *, secrets: Iterable[str] = (), limit: int = 1024) -> str:
    text = str(value or "").replace("\x00", "")
    for secret in secrets:
        if secret:
            text = text.replace(secret, "…")
    text = strip_mindustry_colors(text)
    text = _UUID.sub("…", _IP.sub("…", text))

    def hide_ipv6(match: re.Match[str]) -> str:
        try:
            ip_address(match[0])
        except ValueError:
            return match[0]
        return "…"

    text = _IPV6.sub(hide_ipv6, text)
    return discord.utils.escape_mentions(text)[:limit]


def case_secrets(case: Case) -> tuple[str, ...]:
    """Known internal identities for presentation only; never log this collection."""
    secrets = [p.player_uuid for p in (case.opener, case.subject) if p]
    secrets.extend(p.get("player_uuid") for p in case.co_openers)
    if case.kind == CaseKind.COMPLAINT:
        secrets.extend(case.payload.get("account_uuids", []))
        for key in ("sanctions", "subject_actions"):
            secrets.extend(case.evidence.get(key, {}).get("actor_uuids", []))
    return tuple(dict.fromkeys(s for s in secrets if isinstance(s, str) and s))


def party_text(party: Party | None, locale: str, *, secrets: Iterable[str] = ()) -> str:
    if party is None:
        return t(locale, "cases-unknown")
    name = safe_text(
        party.player_name or party.discord_name or t(locale, "cases-unknown"),
        secrets=(*secrets, party.player_uuid or ""),
    )
    bits = [name]
    if party.player_pid is not None:
        bits.append(t(locale, "cases-pid", pid=party.player_pid))
    if party.discord_id and party.discord_id.isdigit():
        bits.append(f"<@{party.discord_id}>")
    return " · ".join(bits)


def card_locale(case: Case, settings) -> str:
    return (
        case.origin.locale if case.kind == CaseKind.MAP else settings.cases_staff_locale
    )


def reason_text(
    resolution: Resolution, locale: str, reasons: Sequence[Choice] = ()
) -> str:
    """The rejection reason as staff read it: the label of the code, then the free text."""
    label = next(
        (
            t(locale, choice.label_key)
            for choice in reasons
            if choice.value == resolution.reason_code
        ),
        "",
    )
    return " — ".join(
        part for part in (label, safe_text(resolution.reason_text or "")) if part
    )


def base_card(
    case: Case, locale: str, *, reasons: Sequence[Choice] = ()
) -> discord.Embed:
    """Header, parties, status line, assignee, outcome and footer of every card.

    A flow adds its own fields; pass its ``reject_reasons`` to show the reason by name.
    """
    embed = discord.Embed(
        title=f"{format_case_number(case.number)} · {t(locale, 'cases-kind-' + case.kind.value)}",
        colour=discord.Colour.blurple(),
    )
    embed.add_field(
        name=t(locale, "cases-opener"),
        value=party_text(case.opener, locale),
        inline=False,
    )
    if case.subject:
        embed.add_field(
            name=t(locale, "cases-subject"),
            value=party_text(case.subject, locale),
            inline=False,
        )
    embed.add_field(
        name=t(locale, "cases-status"),
        value=t(
            locale,
            "cases-status-pending"
            if case.pending_action
            else "cases-status-" + case.status.value,
        ),
    )
    if case.assignee:
        embed.add_field(
            name=t(locale, "cases-assignee"),
            value=f"<@{case.assignee['discord_id']}>"
            if case.assignee.get("discord_id")
            else safe_text(case.assignee["name"]),
        )
    if case.resolution:
        embed.add_field(
            name=t(locale, "cases-resolution"),
            value=t(locale, "cases-resolution-" + case.resolution.code),
            inline=False,
        )
        reason = reason_text(case.resolution, locale, reasons)
        if reason:
            embed.add_field(name=t(locale, "cases-reason"), value=reason, inline=False)
    embed.set_footer(text=format_case_number(case.number))
    return embed


def add_sanctions(embed: discord.Embed, case: Case, locale: str) -> None:
    """The sanctions block: counts, then the last three entries of ``evidence.sanctions``."""
    sanctions = case.evidence.get("sanctions", {})
    if not sanctions:
        return
    lines = [
        t(
            locale,
            "cases-sanctions-counts",
            bans=sanctions.get("bans", 0),
            mutes=sanctions.get("mutes", 0),
            kicks=sanctions.get("kicks", 0),
            warns=sanctions.get("warns", 0),
        )
    ]
    for entry in sanctions.get("last", [])[:3]:
        bits = [str(entry.get("action") or "")]
        if entry.get("at"):
            bits.insert(0, discord.utils.format_dt(utc(entry["at"]), "d"))
        if entry.get("reason"):
            bits.append(safe_text(entry["reason"], limit=120))
        if entry.get("actor_name"):
            bits.append(safe_text(entry["actor_name"], limit=64))
        lines.append(" · ".join(bit for bit in bits if bit))
    embed.add_field(
        name=t(locale, "cases-sanctions"), value="\n".join(lines)[:1024], inline=False
    )


def sanitize_card(embed: discord.Embed, case: Case) -> discord.Embed:
    """Apply the common privacy boundary to flow-built embeds and cap Discord's budget."""
    secrets = case_secrets(case)
    doc = embed.to_dict()
    # URLs are omitted: flow-specific evidence links belong in messages/attachments.
    for field in ("url", "image", "thumbnail", "author"):
        doc.pop(field, None)
    doc["title"] = safe_text(doc.get("title"), secrets=secrets, limit=256)
    doc["description"] = safe_text(doc.get("description"), secrets=secrets, limit=3000)
    budget = 5500 - len(doc["title"]) - len(doc["description"])
    fields = []
    for value in doc.get("fields", [])[:25]:
        name = safe_text(value["name"], secrets=secrets, limit=256) or "…"
        text = safe_text(
            value["value"], secrets=secrets, limit=min(1024, max(0, budget - len(name)))
        )
        if not text:
            break
        fields.append(
            {"name": name, "value": text, "inline": value.get("inline", False)}
        )
        budget -= len(name) + len(text)
    doc["fields"] = fields
    doc["footer"] = {"text": format_case_number(case.number)}
    return discord.Embed.from_dict(doc)


def thread_title(case: Case, locale: str) -> str:
    name = (
        case.subject.player_name or case.subject.discord_name
        if case.subject
        else case.opener.player_name or case.opener.discord_name
    )
    return safe_text(
        f"#{case.number} · {t(locale, 'cases-kind-' + case.kind.value)} · {name or t(locale, 'cases-unknown')}",
        secrets=case_secrets(case),
        limit=100,
    )


def split_evidence(text: str, *, limit: int = 1800) -> list[str]:
    text = text.replace("```", "''' ")
    chunks: list[str] = []
    current = ""
    for line in text.splitlines():
        while len(line) > limit:
            if current:
                chunks.append(current)
                current = ""
            chunks.append(line[:limit])
            line = line[limit:]
        if len(current) + len(line) + 1 > limit:
            chunks.append(current)
            current = ""
        current += ("\n" if current else "") + line
    if current:
        chunks.append(current)
    return [f"```\n{chunk}\n```" for chunk in chunks]


def evidence_messages(case: Case, locale: str) -> list[str]:
    lines = []
    for line in case.evidence.get("chat", [])[:30]:
        author = str(line.get("name", ""))
        if line.get("pid") is not None:
            author += " · " + t(locale, "cases-pid", pid=line["pid"])
        lines.append(f"{author}: {line.get('text', '')}")
    summary = case.evidence.get("block_summary")
    if summary:
        # The game counts two things: removals, and every other action on a block. So
        # "built" also holds rotations and setting changes, and there is no third count.
        lines.append(
            t(
                locale,
                "cases-block-summary",
                minutes=max(1, int(summary.get("window_seconds") or 600) // 60),
                built=summary.get("built", 0),
                destroyed=summary.get("destroyed", 0),
            )
        )
        for entry in summary.get("top_blocks", [])[:8]:
            lines.append(
                f"{entry.get('block', '')}: +{entry.get('built', 0)}"
                f" / -{entry.get('destroyed', 0)}"
            )
    secrets = case_secrets(case)
    return split_evidence(
        safe_text("\n".join(lines), secrets=secrets, limit=60000)
    ) + complaint_statement_messages(case, locale)


def complaint_statement_messages(case: Case, locale: str) -> list[str]:
    """Full statement is evidence belonging to each senior post, including replacements."""
    if (
        case.kind != CaseKind.COMPLAINT
        or len(case.payload.get("statement", "")) <= 1024
    ):
        return []
    text = safe_text(case.payload["statement"], secrets=case_secrets(case), limit=4000)
    return [
        t(
            locale,
            "complaints-statement-part",
            part=start // 1800 + 1,
            text=text[start : start + 1800],
        )
        for start in range(0, len(text), 1800)
    ]
