"""Reports on players: from the game and from the panel (design spec 9.1, 9.2).

A report from the game arrives as ``cases.report.submitted``; one from Discord through the panel
form. Both open a case on the reported player, and a second report on a player who already has
an open one joins it. Staff ban or mute from the card, or reject with a reason code. A sanction
issued anywhere else while the report is open closes it as handled.

What this module keeps outside the case, in ``case_report_limits``: one row per reporter for the
cross-server cooldown (``CASES_REPORTER_COOLDOWN_SECONDS``) (it names the submission that holds it, so a redelivery is never refused by
its own first attempt) and one row for the burst alert. Submission decisions have a bounded TTL. A cooldown
row retains its last decision until the submission record is acknowledged, so a crash or
lost write acknowledgement cannot turn a refusal into admission on redelivery.
"""

from __future__ import annotations

import logging
import re
from dataclasses import asdict, replace
from datetime import UTC, datetime, timedelta
from typing import Any

import discord
from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError
from xcore_protocol.generated.cases import CasesReportSubmittedV1
from xcore_protocol.generated.moderation import (
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
)
from xcore_protocol.generated.shared import PlayerRefV1

from ...dto import PlayerRecord
from ...i18n import normalize_locale, t
from ...player_pids import is_assigned, or_none
from ...staff_actions import InvalidDuration, perform_ban, perform_mute
from ...utils.duration import parse_duration
from ...utils.mindustry_colors import strip_mindustry_colors
from ..cards import add_sanctions, base_card, case_secrets, party_text, safe_text
from ..modals import OpenCaseModal
from ..model import (
    ActionFailed,
    Case,
    CaseKind,
    CaseRace,
    Origin,
    Party,
    Resolution,
    party_of,
    same_person,
    utc,
)
from ..notify import Outcome
from ..registry import (
    Action,
    ActionContext,
    Choice,
    Flow,
    FormField,
    PanelContext,
    PanelEntry,
    flow_registry,
)
from ._history import audit_at as _audit_at
from ._history import audit_entry, audit_line, history_rows

logger = logging.getLogger(__name__)
REPORT_EVENT = "cases.report.submitted"
BAN_EVENT = "moderation.ban.created"
MUTE_EVENT = "moderation.mute.created"
REASON_CODES = ("no_violation", "no_evidence", "already_handled", "duplicate", "abuse")
REJECT_REASONS = tuple(
    Choice(code, "reports-reject-" + code.replace("_", "-")) for code in REASON_CODES
)
GAME_REASONS = frozenset(
    {"griefing", "toxicity", "cheating", "nsfw", "evasion", "other"}
)
UNRESOLVED = "subject_unresolved"
# ``resolution.reason_code`` of a report closed by a sanction issued outside its card.
EXTERNAL = "external"
LIMITS = "case_report_limits"
HISTORY_LIMIT = 10
REPORTERS_SHOWN = 8
_PID = re.compile(r"#?(-?[0-9]{1,10})")


def _plain(value: object, limit: int) -> str:
    return strip_mindustry_colors(str(value or "")).strip()[:limit]


def _date(value: object) -> datetime | None:
    """A protocol date-time. One without a zone is not trusted to be UTC."""
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.strip())
    except ValueError:
        return None
    return utc(parsed) if parsed.tzinfo is not None else None


def _player_party(player: PlayerRecord, ref: PlayerRefV1 | None = None) -> Party:
    """A player as the case stores them: uuid for lookups, name and ID for people."""
    pid = or_none(ref.playerPid) if ref is not None else None
    name = (
        ref.playerName if ref is not None else player.custom_nickname or player.nickname
    )
    discord_id = str(player.discord_id or "").strip()
    return Party(
        player_uuid=ref.playerUuid if ref is not None else player.uuid,
        player_pid=pid if pid is not None else or_none(player.pid),
        player_name=_plain(name, 64) or None,
        discord_id=discord_id or None,
        discord_name=_plain(player.discord_username, 64) or None,
    )


def _ref_party(ref: PlayerRefV1, player: PlayerRecord | None) -> Party:
    if player is not None:
        return _player_party(player, ref)
    return Party(
        player_uuid=ref.playerUuid,
        player_pid=or_none(ref.playerPid),
        player_name=_plain(ref.playerName, 64) or None,
    )


def _evidence(raw: dict[str, Any]) -> dict[str, Any]:
    """What the game sent with a report or answered to an evidence request, as stored (5.1)."""
    chat = []
    for line in raw.get("chat", [])[:30]:
        try:
            sent_at = datetime.fromtimestamp(int(line["sentAt"]) / 1000, UTC)
        except (KeyError, TypeError, ValueError, OverflowError, OSError):
            sent_at = None
        chat.append(
            {
                "sent_at": sent_at,
                "name": _plain(line.get("playerName"), 64),
                "pid": or_none(line.get("playerPid")),
                "role": str(line.get("role") or "other"),
                "text": _plain(line.get("text"), 300),
                "team": bool(line.get("team")),
            }
        )
    context = {
        "server": raw.get("server"),
        "map_name": raw.get("mapName"),
        "map_file": raw.get("mapFileName"),
        "game_mode": raw.get("gameMode"),
        "subject_online": raw.get("targetOnline"),
        "same_network": raw.get("sameNetwork"),
    }
    evidence: dict[str, Any] = {
        "chat": chat,
        "context": {
            key: _plain(value, 128) if isinstance(value, str) else value
            for key, value in context.items()
            if value is not None
        },
        "captured_at": _date(raw.get("capturedAt") or raw.get("occurredAt")),
    }
    summary = raw.get("blockSummary")
    if summary:
        evidence["block_summary"] = {
            "window_seconds": summary.get("windowSeconds"),
            "built": summary.get("built", 0),
            "destroyed": summary.get("destroyed", 0),
            # The game cannot tell a configuration from a placement: always 0, never shown.
            "configured": summary.get("configured", 0),
            "truncated": bool(summary.get("truncated")),
            "top_blocks": [
                {
                    "block": _plain(entry.get("block"), 64),
                    "built": entry.get("built", 0),
                    "destroyed": entry.get("destroyed", 0),
                }
                for entry in summary.get("topBlocks", [])[:8]
            ],
            "area": summary.get("area"),
            "last_action_seconds_ago": summary.get("lastActionSecondsAgo"),
        }
    return evidence


async def _sanctions(service, uuid: str) -> dict[str, Any]:
    """Counts, the last three sanctions and what is in force now (9.1 step 9)."""
    result, rows = await history_rows(service, {"target.uuid": uuid}, 3)
    result["last"] = [audit_entry(row, (uuid,)) for row in rows]
    now = service.now()
    for key, current in (
        ("current_ban", await service.players.find_ban(uuid=uuid, ip=None)),
        ("current_mute", await service.players.find_mute(uuid=uuid)),
    ):
        if current is None:
            continue
        until = (
            utc(current.expire_date)
            if isinstance(current.expire_date, datetime)
            else None
        )
        # Only a ban can be without a date: it is permanent.
        if until is None and key == "current_ban" or until is not None and until > now:
            result[key] = {
                "until": until,
                "reason": safe_text(current.reason, secrets=(uuid,), limit=150),
            }
    return result


async def _live(service, player: PlayerRecord) -> dict[str, Any] | None:
    """Chat and block history from the server the player is on, or ``None`` (9.2 step 4)."""
    if not player.online_server:
        return None
    try:
        raw = await service.transport.rpc_case_evidence(
            server=player.online_server, player_uuid=player.uuid
        )
    except Exception:
        # Evidence is an extra: a report is registered with or without it.
        logger.warning(
            "Live evidence from %s is unavailable", player.online_server, exc_info=True
        )
        return None
    return _evidence(raw) if raw else None


# --- reports from the game ----------------------------------------------------------------------


async def _blacklisted(service, reporter: PlayerRecord | None) -> bool:
    role_id = service.settings.cases_blacklist_role_id
    discord_id = str(getattr(reporter, "discord_id", None) or "")
    if not role_id or not discord_id.isdigit():
        return False
    guild = service.bot.get_guild(service.settings.discord_guild_id)
    if guild is None:
        # Not knowing is not a reason to let the report through: the event is retried.
        raise RuntimeError("The guild is not available for the blacklist check")
    member = guild.get_member(int(discord_id))
    if member is None:
        try:
            member = await guild.fetch_member(int(discord_id))
        except discord.NotFound:
            return False
    return any(role.id == role_id for role in member.roles)


async def _burst(service) -> bool:
    """Whether too many report cases were opened from the game lately (8.8)."""
    window = timedelta(seconds=service.settings.cases_report_burst_window_seconds)
    recent = await service.store.database["cases"].count_documents(
        {
            "kind": CaseKind.REPORT.value,
            "origin.source": "game",
            "created_at": {"$gte": service.now() - window},
        }
    )
    return recent >= service.settings.cases_report_burst_limit


async def _burst_alert(service) -> None:
    """Count the refused report and tell staff once per window.

    Called after the refusal is stored, and best-effort: a Discord failure leaves
    ``alerted_at`` unset, so the next refusal in the window tries again.
    """
    settings, now = service.settings, service.now()
    limits = service.store.database[LIMITS]
    row = await limits.find_one_and_update(
        {"_id": "burst"},
        {"$inc": {"refused": 1}, "$set": {"refused_at": now}},
        upsert=True,
        return_document=ReturnDocument.AFTER,
    )
    alerted_at = row.get("alerted_at")
    window = timedelta(seconds=settings.cases_report_burst_window_seconds)
    if alerted_at is not None and now - utc(alerted_at) < window:
        return
    locale = settings.cases_staff_locale
    forum = await service.threads.channel(settings.cases_staff_forum_id)
    await forum.create_thread(
        name=t(locale, "reports-burst-title")[:100],
        embed=discord.Embed(
            title=t(locale, "reports-burst-title"),
            description=t(
                locale,
                "reports-burst-alert",
                limit=settings.cases_report_burst_limit,
                minutes=max(1, settings.cases_report_burst_window_seconds // 60),
            ),
            colour=discord.Colour.orange(),
        ),
        allowed_mentions=discord.AllowedMentions.none(),
    )
    # Recorded after the post: a failure in between repeats the alert, it never loses it.
    await limits.update_one({"_id": "burst"}, {"$set": {"alerted_at": now}})


async def _admission_decision(service, event, reporter, *, merging: bool) -> str | None:
    """Linearize the decision with cooldown ownership in one reporter-row CAS.

    Before replacing a witness, persist it in the TTL submission record. If either write
    fails (including a lost acknowledgement), propagate the uncertainty. The witness may
    already exist; a retry or the next submission promotes it before changing the row.
    """
    limits = service.store.database[LIMITS]
    decision_id = f"decision:{event.submissionId}"

    async def promote(witness):
        await limits.update_one(
            {"_id": f"decision:{witness['submission_id']}"},
            {"$setOnInsert": witness},
            upsert=True,
        )

    async with service._lock(f"report-intake:{event.reporter.playerUuid}"):
        for _ in range(5):
            stored = await limits.find_one({"_id": decision_id})
            if stored is not None:
                return stored.get("reason")
            key = f"cooldown:{event.reporter.playerUuid}"
            row = await limits.find_one({"_id": key})
            if row and row.get("decision"):
                await promote(row["decision"])
                if row["decision"]["submission_id"] == event.submissionId:
                    return row["decision"].get("reason")
            now = service.now()
            blocked = await _blacklisted(service, reporter)
            held = bool(
                row
                and row.get("until")
                and utc(row["until"]) > now
                and row.get("submission_id") != event.submissionId
            )
            reason = "blocked" if blocked else "cooldown" if held else None
            if reason is None and not merging and await _burst(service):
                reason = "burst"
            witness = {
                "submission_id": event.submissionId,
                "player_uuid": event.reporter.playerUuid,
                "reason": reason,
                "decided_at": now,
                "expires_at": now
                + timedelta(days=service.settings.cases_notice_ttl_days),
            }
            fields = {"decision": witness}
            if not blocked and not held:
                fields.update(
                    submission_id=event.submissionId,
                    until=now
                    + timedelta(
                        seconds=service.settings.cases_reporter_cooldown_seconds
                    ),
                )
            query = {
                "_id": key,
                "revision": row["revision"]
                if row and "revision" in row
                else {"$exists": False},
            }
            try:
                owned = await limits.find_one_and_update(
                    query,
                    {"$set": fields, "$inc": {"revision": 1}},
                    upsert=True,
                    return_document=ReturnDocument.AFTER,
                )
            except DuplicateKeyError:
                continue
            await promote(owned["decision"])
            return owned["decision"].get("reason")
    raise CaseRace()


def _refusal_id(submission_id: str) -> str:
    return f"refused:{submission_id}"


async def _refuse(
    service,
    event: CasesReportSubmittedV1,
    reporter: PlayerRecord | None,
    reason: str,
) -> None:
    """Tell the reporter in game that nothing was registered; no case is created.

    The admission decision is already durable; the notice is its retryable delivery effect.
    It has no case number, and the game's text for it takes no arguments.
    """
    now = service.now()
    notice_id = _refusal_id(event.submissionId)
    await service.store.insert_notice(
        {
            "_id": notice_id,
            "player_uuid": event.reporter.playerUuid,
            "kind": "report_not_accepted",
            "params": {"reason": reason},
            "created_at": now,
            "expires_at": now + timedelta(days=service.settings.cases_notice_ttl_days),
            "delivered_at": None,
        }
    )
    try:
        await service.transport.publish_case_notice(
            notice_id=notice_id,
            player_uuid=event.reporter.playerUuid,
            server=getattr(reporter, "online_server", None) or event.server,
        )
    except Exception:
        logger.exception("Notice wake-up failed for a refused report")


async def submitted(service, event: CasesReportSubmittedV1) -> None:
    """``cases.report.submitted``. Raising leaves the event to the stream's retry.

    Nothing is acknowledged before it is stored: until the case (or the refusal notice) is
    written an error propagates. Once the case exists its creation steps are the core's, and
    the repair task finishes what Discord refused.
    """
    service.registry.require(CaseKind.REPORT)
    if not service.storage_available:
        raise RuntimeError("Case storage is unavailable; report remains pending")
    existing = await service.store.by_submission(event.submissionId)
    if existing is not None:
        await service.resume_creation(existing.number)
        return
    refused = await service.store.database["case_notices"].find_one(
        {"_id": _refusal_id(event.submissionId)}
    )
    if refused is not None:
        return
    players = service.players
    reporter = await players.find_player_by_uuid(event.reporter.playerUuid)
    dedup_key = f"report:{event.target.playerUuid}"
    merging = await service.store.by_dedup(CaseKind.REPORT, dedup_key) is not None
    reason = await _admission_decision(service, event, reporter, merging=merging)
    if reason is not None:
        await _refuse(service, event, reporter, reason)
        if reason == "burst":
            try:
                await _burst_alert(service)
            except Exception:
                logger.exception("The report burst alert could not be posted")
        return
    target = await players.find_player_by_uuid(event.target.playerUuid)
    payload = {"reason": event.reason}
    if event.comment and event.comment.strip():
        payload["comment"] = _plain(event.comment, 300)
    evidence = _evidence(event.to_payload())
    evidence["sanctions"] = await _sanctions(service, event.target.playerUuid)
    await service.open(
        kind=CaseKind.REPORT,
        origin=Origin(
            "game",
            event.submissionId,
            normalize_locale(event.reporterLocale) or "en",
            event.server,
        ),
        opener=_ref_party(event.reporter, reporter),
        subject=_ref_party(event.target, target),
        payload=payload,
        evidence=evidence,
        dedup_key=dedup_key,
    )


# --- reports from the panel ---------------------------------------------------------------------


async def _find_player(service, who: str) -> PlayerRecord | None:
    """Digits or ``#n`` are a player ID, anything else an exact name; ``None`` unless exactly
    one player matches (9.2 step 2)."""
    typed = _PID.fullmatch(who)
    if typed:
        player = await service.players.find_player_by_pid(int(typed[1]))
        return player if player is not None and player.uuid else None
    matches = await service.players.find_players_by_exact_name(who)
    return matches[0] if len(matches) == 1 and matches[0].uuid else None


async def _member_party(service, user) -> Party:
    """The member, with their game account when exactly one is linked (for the in-game
    notice of 9.2 step 8)."""
    linked = [
        player
        for player in await service.players.find_players_by_discord_id(str(user.id))
        if player.uuid
    ]
    name = _plain(user.display_name, 64)
    if len(linked) != 1:
        return Party(discord_id=str(user.id), discord_name=name)
    party = _player_party(linked[0])
    return replace(party, discord_id=str(user.id), discord_name=name)


async def submit(context: PanelContext, values: dict[str, str]) -> Case:
    service = context.service
    payload = {
        key: values[field]
        for key, field in (("comment", "what"), ("when", "when"), ("where", "where"))
        if values.get(field)
    }
    opener = await _member_party(service, context.interaction.user)
    player = await _find_player(service, values["who"])
    if player is None:
        return await context.open(
            opener=opener,
            payload={**payload, "raw_subject": values["who"]},
            flags=(UNRESOLVED,),
        )
    evidence = await _live(service, player) or {}
    evidence["sanctions"] = await _sanctions(service, player.uuid)
    return await context.open(
        opener=opener,
        subject=_player_party(player),
        payload=payload,
        evidence=evidence,
        dedup_key=f"report:{player.uuid}",
    )


def open_form(context: PanelContext) -> discord.ui.Modal:
    return OpenCaseModal(
        context,
        title_key="reports-panel",
        fields=(
            FormField("who", "reports-field-who", max_length=64),
            FormField("what", "reports-field-what", paragraph=True),
            FormField("when", "reports-field-when", required=False, max_length=100),
            FormField("where", "reports-field-where", required=False, max_length=100),
        ),
        submit=submit,
        files=True,
    )


# --- the card -----------------------------------------------------------------------------------


def _reason_label(payload: dict[str, Any], locale: str) -> str:
    code = payload.get("reason")
    if not code:
        return ""
    return t(locale, f"reports-reason-{code}") if code in GAME_REASONS else str(code)


def _reporters_text(case: Case, locale: str) -> str:
    threads = case.user_threads
    reporters = case.reporters
    lines = []
    for party in reporters[:REPORTERS_SHOWN]:
        line = party_text(party, locale)
        if party.discord_id in threads:
            line += f" · <#{threads[party.discord_id]}>"
        lines.append(line)
    if len(reporters) > REPORTERS_SHOWN:
        lines.append(
            t(locale, "reports-card-more", count=len(reporters) - REPORTERS_SHOWN)
        )
    return "\n".join(lines)


def _context_text(case: Case, locale: str) -> str:
    context = case.evidence.get("context", {})
    lines = [
        " · ".join(
            str(context[key])
            for key in ("server", "map_name", "game_mode")
            if context.get(key)
        )
    ]
    if "subject_online" in context:
        lines.append(
            t(
                locale,
                "reports-online" if context["subject_online"] else "reports-offline",
            )
        )
    elif case.origin.source == "discord" and UNRESOLVED not in case.flags:
        lines.append(t(locale, "reports-no-live"))
    if context.get("same_network"):
        lines.append(t(locale, "reports-same-network"))
    return "\n".join(line for line in lines if line)


def _in_force_text(case: Case, locale: str) -> str:
    lines = []
    for key in ("current_ban", "current_mute"):
        current = case.evidence.get("sanctions", {}).get(key)
        if not current:
            continue
        label = (
            t(
                locale,
                "reports-" + key.replace("_", "-"),
                until=discord.utils.format_dt(utc(current["until"]), "f"),
            )
            if current.get("until")
            else t(locale, "reports-current-ban-permanent")
        )
        lines.append(
            " · ".join(part for part in (label, current.get("reason")) if part)
        )
    return "\n".join(lines)


def card(case: Case, locale: str) -> discord.Embed:
    embed = base_card(case, locale, reasons=REJECT_REASONS)
    payload = case.payload
    fields = [
        (
            "reports-card-reported-as",
            "\n".join(
                (
                    safe_text(payload.get("raw_subject"), limit=200),
                    t(locale, "reports-card-unresolved"),
                )
            )
            if UNRESOLVED in case.flags
            else "",
        ),
        ("reports-card-reason", _reason_label(payload, locale)),
        ("reports-card-comment", payload.get("comment")),
        ("reports-card-when", payload.get("when")),
        ("reports-card-where", payload.get("where")),
        ("reports-card-context", _context_text(case, locale)),
        ("reports-card-in-force", _in_force_text(case, locale)),
    ]
    for key, value in fields:
        if value:
            embed.add_field(name=t(locale, key), value=safe_text(value), inline=False)
    if case.co_openers or case.user_threads:
        # Not through safe_text: it would break the mentions and the thread links.
        embed.add_field(
            name=t(locale, "reports-card-reporters", count=len(case.reporters)),
            value=_reporters_text(case, locale)[:1024],
            inline=False,
        )
    add_sanctions(embed, case, locale)
    return embed


# --- hooks and outcomes -------------------------------------------------------------------------


async def _received(service, case: Case, uuid: str | None, server: str | None) -> None:
    """The in-game "registered as #n" of 9.1 step 11. No DM: that is for outcomes."""
    if uuid:
        await service.notifier.send(
            service,
            case,
            Outcome(
                Party(player_uuid=uuid),
                "report_received",
                "cases-registered",
                server=server,
            ),
        )


async def on_created(service, case: Case) -> None:
    # A member who reported from Discord has the reply to the form and their thread.
    if case.origin.source == "game":
        await _received(service, case, case.opener.player_uuid, case.origin.server)


async def on_merged(service, case: Case, joined: dict[str, Any]) -> None:
    locale = service.settings.cases_staff_locale
    position = next(
        index
        for index, row in enumerate(case.co_openers)
        if row["submission_id"] == joined["submission_id"]
    )
    payload = joined.get("payload") or {}
    reason = " — ".join(
        part
        for part in (
            _reason_label(payload, locale),
            safe_text(payload.get("comment"), secrets=case_secrets(case), limit=300),
        )
        if part
    )
    # The running total makes every line different, so a repeated hook posts nothing new.
    await service.threads.post_note(
        case,
        safe_text(
            t(
                locale,
                "reports-merged",
                total=position + 2,
                reporter=party_text(party_of(joined), locale),
                reason=reason or t(locale, "cases-unknown"),
            ),
            secrets=case_secrets(case),
            limit=1900,
        ),
    )
    if joined.get("source") == "game":
        await _received(service, case, joined.get("player_uuid"), joined.get("server"))


async def on_closed(service, case: Case) -> None:
    resolution = case.resolution
    if resolution is None or resolution.reason_code != EXTERNAL:
        return
    if not case.discord.get("thread_id"):
        return
    locale = service.settings.cases_staff_locale
    await service.threads.post_note(
        case,
        safe_text(
            t(
                locale,
                f"reports-external-{resolution.code}",
                name=safe_text(resolution.by_name, limit=64)
                or t(locale, "cases-unknown"),
            ),
            secrets=case_secrets(case),
            limit=1900,
        ),
    )


def _recipients(case: Case) -> list[tuple[Party, str | None, str | None]]:
    """Every reporter once, with the language and server of their first report.

    Someone who reported from the game and from Discord is one person holding both
    identities, so the outcome reaches them in game, in their thread and by DM.
    """
    people: list[tuple[Party, str | None, str | None]] = []
    rows = [(case.opener, case.origin.locale, case.origin.server)]
    rows += [
        (party_of(row), row.get("locale"), row.get("server")) for row in case.co_openers
    ]
    for party, locale, server in rows:
        for index, (known, known_locale, known_server) in enumerate(people):
            if same_person(party, known):
                merged = {
                    name: getattr(known, name)
                    if getattr(known, name) is not None
                    else getattr(party, name)
                    for name in Party.__dataclass_fields__
                }
                people[index] = (Party(**merged), known_locale, known_server or server)
                break
        else:
            people.append((party, locale, server))
    return people


def outcomes(case: Case) -> list[Outcome]:
    """What every reporter is told: that action was taken, or why not — never by whom and
    never what the sanction was (9.1 step 14)."""
    resolution = case.resolution
    if resolution.code == "rejected":
        kind = "report_rejected"
        code = resolution.reason_code
        key = (
            "reports-outcome-" + code.replace("_", "-")
            if code in REASON_CODES
            else "reports-outcome-closed"
        )
        # The game picks its text by this code.
        params = {"reason": code}
    else:
        kind, key, params = "report_resolved", "reports-outcome-action", {}
    return [
        Outcome(
            party,
            kind,
            key,
            params,
            player_language=locale,
            server=server,
            user_thread=True,
        )
        for party, locale, server in _recipients(case)
    ]


# --- card buttons -------------------------------------------------------------------------------


def sanction_form(case: Case, locale: str) -> tuple[FormField, ...]:
    pid = case.subject.player_pid if case.subject else None
    reason = " · ".join(
        part
        for part in (
            _reason_label(case.payload, locale),
            t(locale, "reports-case-reference", number=case.number),
        )
        if part
    )
    return (
        FormField(
            "pid",
            "reports-form-pid",
            max_length=12,
            default=str(pid) if pid is not None else None,
        ),
        FormField("duration", "reports-form-length", max_length=20, default="1d"),
        FormField("reason", "cases-reason", max_length=200, default=reason[:200]),
    )


async def _target(context: ActionContext) -> PlayerRecord:
    """The player the form names. For a known subject the ID can only confirm it."""
    typed = _PID.fullmatch(context.values["pid"])
    player = None
    if typed and is_assigned(int(typed[1])):
        player = await context.service.players.find_player_by_pid(int(typed[1]))
    if player is None or not player.uuid:
        raise ActionFailed(t(context.locale, "cases-error-player"))
    subject = context.case.subject
    if subject and subject.player_uuid:
        if subject.player_uuid != player.uuid:
            raise ActionFailed(t(context.locale, "reports-error-other-player"))
        return player
    # One open report per player: naming this one here would collide with the other report.
    other = await context.service.store.by_dedup(
        CaseKind.REPORT, f"report:{player.uuid}"
    )
    if other is not None:
        raise ActionFailed(
            t(context.locale, "reports-error-open-report", number=other.number)
        )
    return player


async def sanction(context: ActionContext) -> Case | None:
    """Ban and Mute: the same form and steps, a different helper."""
    try:
        duration = parse_duration(context.values["duration"])
    except ValueError as error:
        raise InvalidDuration(str(error)) from error
    player = await _target(context)
    if context.values.get("confirmed") != "yes":
        await context.confirm(
            safe_text(
                t(
                    context.locale,
                    f"reports-confirm-{context.verb}",
                    player=party_text(_player_party(player), context.locale),
                    duration=context.values["duration"],
                ),
                secrets=(*case_secrets(context.case), player.uuid),
                limit=1900,
            )
        )
        return None

    async def staff_action(case: Case) -> Resolution:
        service = context.service
        token = case.pending_action["token"]
        fresh = await _target(context)
        if fresh.uuid != player.uuid:
            raise CaseRace()
        if not (case.subject and case.subject.player_uuid):
            # Under this attempt's guard: the case now names the player, and the conflict
            # rule is asked again about the staff member and that player.
            try:
                case = await service.checkpoint(
                    case.number,
                    set_fields={
                        "subject": asdict(_player_party(fresh)),
                        "dedup_key": f"report:{fresh.uuid}",
                        "flags": [flag for flag in case.flags if flag != UNRESOLVED],
                    },
                    event_type="subject_set",
                    actor=context.actor,
                    action_token=token,
                )
            except DuplicateKeyError as error:
                raise CaseRace() from error
        elif case.subject.player_uuid != player.uuid:
            raise ActionFailed(t(context.locale, "reports-error-other-player"))
        await service.authorize(case, context.member, context.verb)
        duration_ms = int(duration.total_seconds() * 1000)
        if context.verb == "ban":
            banned = await perform_ban(
                service.bot,
                player=fresh,
                duration=duration,
                reason=context.values["reason"],
                actor=context.actor,
                case_number=case.number,
                case_action_token=token,
            )
            return Resolution(
                "banned",
                audit_id=banned.audit_id,
                ban_id=banned.ban.ban_id,
                duration_ms=duration_ms,
            )
        muted = await perform_mute(
            service.bot,
            fresh,
            duration,
            context.values["reason"],
            context.actor,
            case.number,
            case_action_token=token,
        )
        return Resolution("muted", audit_id=muted.audit_id, duration_ms=duration_ms)

    return await context.run(staff_action)


def _known_subject(case: Case) -> bool:
    return bool(case.subject and case.subject.player_uuid)


async def history(context: ActionContext) -> str:
    uuid = context.case.subject.player_uuid
    cursor = (
        context.service.store.database["moderation_audit"]
        .find({"target.uuid": uuid})
        .sort("created_at_epoch_ms", -1)
        .limit(HISTORY_LIMIT)
    )
    lines = []
    async for row in cursor:
        entry = audit_entry(row, (uuid, *case_secrets(context.case)))
        lines.append(audit_line(entry, actor=True))
    if not lines:
        return t(context.locale, "reports-history-empty")
    title = t(
        context.locale,
        "reports-history-title",
        player=safe_text(context.case.subject.player_name, limit=64),
    )
    return safe_text(
        "\n".join([title, *lines]), secrets=case_secrets(context.case), limit=1900
    )


async def refresh(context: ActionContext) -> str:
    """Read the sanctions again and, when the player is online, the live evidence (9.2 step 7)."""
    service, number = context.service, context.case.number
    uuid = context.case.subject.player_uuid
    player = await service.players.find_player_by_uuid(uuid)
    live = await _live(service, player) if player is not None else None
    fields = {"evidence.sanctions": await _sanctions(service, uuid)}
    unset: tuple[str, ...] = ()
    if live:
        # What the game sent with the report (same network, map file) stays; what the
        # server reports now replaces the rest.
        current = live.pop("context")
        # When the game captured the report is when it was filed (see _since).
        live.pop("captured_at", None)
        fields.update({f"evidence.context.{k}": v for k, v in current.items()})
        fields.update({f"evidence.{key}": value for key, value in live.items()})
        # The core posts evidence that is not in the post yet and renders the card again.
        unset = ("progress.evidence_done", "progress.creation_done")
        if "block_summary" not in live:
            unset += ("evidence.block_summary",)
    await service.checkpoint(
        number,
        set_fields=fields,
        unset_fields=unset,
        event_type="evidence_refreshed",
        actor=context.actor,
    )
    if live:
        await service.resume_creation(number)
    return t(
        context.locale, "reports-refreshed" if live else "reports-refreshed-offline"
    )


# --- a sanction issued outside the card ---------------------------------------------------------


def _since(case: Case) -> datetime:
    """From when a sanction counts as an answer to this report: when it was filed (a game
    report can wait in the stream while the bot is down) or its last reopen."""
    since = case.created_at
    filed = case.evidence.get("captured_at")
    if case.origin.source == "game" and isinstance(filed, datetime):
        since = min(since, utc(filed))
    for entry in case.events:
        if entry["type"] == "reopened":
            since = utc(entry["at"])
    return since


async def _handled(service, case: Case, resolution: Resolution) -> None:
    try:
        await service.close_validated(case, resolution)
    except CaseRace:
        # Closed meanwhile is the wanted state. An action in progress is not: the caller
        # (the stream's retry or the next repair pass) comes back.
        if (await service.get(case.number)).actionable:
            raise


async def sanctioned(
    service, event: ModerationBanCreatedV1 | ModerationMuteCreatedV1
) -> None:
    """A ban or mute issued in game for a player with an open report (9.1 step 12)."""
    case = await service.store.by_dedup(
        CaseKind.REPORT, f"report:{event.target.playerUuid}"
    )
    if case is None or not case.actionable:
        return
    occurred_at = _date(event.occurredAt)
    if occurred_at is None:
        # Legacy or malformed timestamps are unknown, never the processing time.
        await reconcile(
            service,
            case,
            action="BAN" if isinstance(event, ModerationBanCreatedV1) else "MUTE",
            ban_id=event.banId if isinstance(event, ModerationBanCreatedV1) else None,
        )
        return
    if occurred_at < _since(case):
        return
    banned = isinstance(event, ModerationBanCreatedV1)
    await _handled(
        service,
        case,
        Resolution(
            "banned" if banned else "muted",
            reason_code=EXTERNAL,
            reason_text=_plain(event.reason, 300) or None,
            by_discord_id=event.actor.actorDiscordId,
            by_name=_plain(event.actor.actorName, 64) or None,
            ban_id=event.banId if banned else None,
            at=occurred_at,
        ),
    )


async def reconcile(
    service, case: Case, *, action: str | None = None, ban_id: int | None = None
) -> None:
    """The same from the audit trail, which also holds sanctions issued by slash command.

    The newest ban or mute of the reported player since the report was filed closes it. A
    record of this case's own card action is the core's: its recovery closes the case from
    it, or the case stays open for staff to inspect.
    """
    if not _known_subject(case):
        return
    since = int(_since(case).timestamp() * 1000)
    query = {
        "target.uuid": case.subject.player_uuid,
        "action": action or {"$in": ["BAN", "MUTE"]},
        "created_at_epoch_ms": {"$gte": since},
        "details.extra.case": {"$ne": str(case.number)},
    }
    if ban_id is not None:
        query["details.extra.ban_id"] = str(ban_id)
    cursor = (
        service.store.database["moderation_audit"]
        .find(query)
        .sort("created_at_epoch_ms", -1)
        .limit(1)
    )
    async for row in cursor:
        at = _audit_at(row)
        if at is None or at < _since(case):
            return
        actor = row.get("actor", {})
        extra = row.get("details", {}).get("extra", {})
        banned = row["action"] == "BAN"
        ban_id = extra.get("ban_id")
        await _handled(
            service,
            case,
            Resolution(
                "banned" if banned else "muted",
                reason_code=EXTERNAL,
                reason_text=_plain(row.get("reason"), 300) or None,
                by_discord_id=actor.get("discord_id"),
                by_name=_plain(actor.get("name_snapshot"), 64) or None,
                at=at,
                audit_id=row.get("audit_id"),
                ban_id=int(ban_id) if banned and str(ban_id or "").isdigit() else None,
            ),
        )


REPORT_FLOW = flow_registry.register(
    Flow(
        kind=CaseKind.REPORT,
        card_builder=card,
        panel=PanelEntry("reports-panel", emoji="🚩", modal_factory=open_form),
        actions=(
            Action(
                "ban",
                "cases-button-ban",
                sanction,
                style=discord.ButtonStyle.danger,
                fields=sanction_form,
            ),
            Action(
                "mute",
                "cases-button-mute",
                sanction,
                style=discord.ButtonStyle.primary,
                fields=sanction_form,
            ),
            Action("history", "cases-button-history", history, visible=_known_subject),
            Action("refresh", "cases-button-refresh", refresh, visible=_known_subject),
        ),
        reject_reasons=REJECT_REASONS,
        on_created=on_created,
        on_merged=on_merged,
        on_closed=on_closed,
        outcomes=outcomes,
        reconcile=reconcile,
        reconcile_open=True,
        event_hooks={
            REPORT_EVENT: submitted,
            BAN_EVENT: sanctioned,
            MUTE_EVENT: sanctioned,
        },
    )
)
