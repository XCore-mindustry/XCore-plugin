"""Ban appeals. Cached choices describe forms; durable DB state decides every action."""

from __future__ import annotations

import math
import re
from dataclasses import replace
from datetime import datetime, timedelta

import discord

from ...dto import BanRecord, PlayerRecord
from ...i18n import t
from ...player_pids import NO_PID, or_none
from ...staff_actions import perform_shorten_ban, perform_unban
from ...utils.duration import parse_duration
from ..access import check_opening
from ..cards import add_sanctions, base_card, safe_text
from ..modals import OpenCaseModal
from ..model import (
    AccessDenied,
    ActionFailed,
    Case,
    CaseKind,
    CaseRace,
    Party,
    Resolution,
    utc,
)
from ..notify import Outcome
from ..registry import Action, Flow, FormField, PanelContext, PanelEntry, flow_registry
from ._choice import PanelChoiceView
from ._history import audit_entry, audit_line, history_rows

HISTORY_LIMIT = 6
_BAN = re.compile(r"(?:#|b-?)?([1-9][0-9]*)", re.IGNORECASE)
_PID = re.compile(r"(?:pid|player(?:\s+id)?|id)\s*[:#]?\s*([+-]?[0-9]+)", re.IGNORECASE)
_NUMBER = re.compile(r"[+-]?[0-9]+")


def parse_reference(value: str) -> tuple[str, int]:
    """Bare positive numbers try ban IDs first; explicit/signed/zero PIDs stay PIDs."""
    value = value.strip()
    match = _PID.fullmatch(value)
    if match:
        kind, number = "pid", int(match[1])
    elif match := _BAN.fullmatch(value):
        return "ban_id", int(match[1])
    elif _NUMBER.fullmatch(value):
        kind, number = "pid", int(value)
    else:
        raise ValueError("Invalid ban number or PID")
    if not -(2**31) < number < 2**31 or number == NO_PID:
        raise ValueError("Invalid PID")
    return kind, number


def _player_valid(player, *, uuid=None, pid=None, discord_id=None) -> bool:
    return (
        isinstance(player, PlayerRecord)
        and isinstance(player.uuid, str)
        and bool(player.uuid.strip())
        and not player.uuid.startswith("merged:")
        and type(player.pid) is int
        and -(2**31) <= player.pid < 2**31
        and (uuid is None or player.uuid == uuid)
        and (pid is None or player.pid == pid)
        and (discord_id is None or player.discord_id == discord_id)
    )


async def _linked(service, discord_id: str) -> list[PlayerRecord]:
    found = {}
    for player in await service.players.find_players_by_discord_id(discord_id):
        if _player_valid(player, discord_id=discord_id):
            found[player.uuid] = player
    return list(found.values())


def _valid_ban(ban) -> bool:
    return (
        isinstance(ban, BanRecord)
        and (ban.ban_id is None or type(ban.ban_id) is int and ban.ban_id > 0)
        and (ban.pid is None or type(ban.pid) is int and -(2**31) < ban.pid < 2**31)
        and (ban.expire_date is None or isinstance(ban.expire_date, datetime))
        and all(isinstance(v, str) for v in (ban.name, ban.admin_name, ban.reason))
        and all(
            v is None or isinstance(v, str)
            for v in (ban.uuid, ban.ip, ban.admin_discord_id)
        )
        and bool(ban.uuid or ban.ip)
        and (
            not ban.admin_discord_id
            or ban.admin_discord_id.isdigit()
            and int(ban.admin_discord_id) > 0
        )
    )


def _active(ban, now) -> bool:
    return _valid_ban(ban) and (
        ban.expire_date is None or utc(ban.expire_date) > utc(now)
    )


def _same_subject(first: BanRecord, second: BanRecord) -> bool:
    # An IP match alone must never select a replacement for a known UUID ban.
    return (
        (first.uuid == second.uuid)
        if first.uuid
        else (not second.uuid and first.ip == second.ip)
    )


def _row_ban(raw) -> BanRecord | None:
    if raw is None or "expire_date" not in raw:
        return None
    if raw.get("ban_id") is not None and (
        type(raw["ban_id"]) is not int or raw["ban_id"] <= 0
    ):
        return None
    if raw.get("pid") is not None and (
        type(raw["pid"]) is not int or not -(2**31) <= raw["pid"] < 2**31
    ):
        return None
    if any(
        raw.get(key) is not None and not isinstance(raw[key], str)
        for key in ("uuid", "ip", "name", "reason", "admin_name", "admin_discord_id")
    ):
        return None
    current = BanRecord(
        ban_id=raw.get("ban_id"),
        uuid=raw.get("uuid"),
        ip=raw.get("ip"),
        pid=None if raw.get("pid") == NO_PID else raw.get("pid"),
        name=raw.get("name") or "",
        reason=raw.get("reason") or "",
        admin_name=raw.get("admin_name") or "",
        admin_discord_id=raw.get("admin_discord_id"),
        expire_date=raw["expire_date"],
    )
    return current if _valid_ban(current) else None


async def _checked(service, ban) -> BanRecord | None:
    """Validate the raw row too: DTO normalization must not turn corrupt data into a ban."""
    if not _valid_ban(ban):
        return None
    query = (
        {"ban_id": ban.ban_id}
        if ban.ban_id
        else ({"uuid": ban.uuid} if ban.uuid else {"ip": ban.ip})
    )
    raw = await service.store.database["bans"].find_one(query)
    current = _row_ban(raw)
    if not _active(current, service.now()) or not _same_subject(ban, current):
        return None
    if current.ban_id is None:
        assigned = await service.players.ensure_ban_id(
            uuid=current.uuid or "", ip=current.ip
        )
        if (
            not _valid_ban(assigned)
            or assigned.ban_id is None
            or not _same_subject(current, assigned)
        ):
            return None
        # A legacy row replaced during lazy assignment is not the selected sanction.
        if (
            assigned.name,
            assigned.reason,
            assigned.admin_name,
            assigned.expire_date,
        ) != (current.name, current.reason, current.admin_name, current.expire_date):
            return None
        return await _checked(service, assigned)
    return current


async def _lookup(service, reference: str) -> tuple[BanRecord | None, str]:
    kind, number = parse_reference(reference)
    if kind == "ban_id":
        ban = await service.players.find_ban_by_id(number)
        # An existing expired/malformed ban number must not silently become a PID.
        if ban is not None:
            return await _checked(service, ban), kind
        if not reference.strip().isdigit() or number >= 2**31:
            return None, kind
    player = await service.players.find_player_by_pid(number)
    if not _player_valid(player, pid=number):
        return None, "pid"
    ban = await service.players.find_ban(
        uuid=player.uuid, ip=player.ip or player.last_ip
    )
    return await _checked(service, ban), "pid"


def _secrets(ban, linked=()):
    return tuple(
        v
        for v in (
            ban.uuid,
            ban.ip,
            *(v for p in linked for v in (p.uuid, p.ip, p.last_ip)),
        )
        if v
    )


async def _snapshot(service, ban, locale) -> dict:
    raw = await service.store.database["bans"].find_one({"ban_id": ban.ban_id})
    current = _row_ban(raw)
    if not _active(current, service.now()) or not _same_subject(ban, current):
        raise ActionFailed(t(locale, "appeals-stale"))
    # This read can observe another issuer update after lookup. Authorize the actual
    # snapshot, rather than silently retaining the issuer from an earlier DTO read.
    ban = current
    snapshot = {
        "ban_id": ban.ban_id,
        "reason": ban.reason,
        "admin_name": ban.admin_name,
        "admin_discord_id": ban.admin_discord_id,
        "expire_date": ban.expire_date,
    }
    # The public DTO has no issued date. Read only actual dates; never use submission time.
    if raw:
        issued = raw.get("issued_at", raw.get("created_at"))
        if isinstance(issued, datetime):
            snapshot["issued_at"] = utc(issued)
    return snapshot


async def _history_data(service, uuid, *, exclude=None):
    if not uuid:
        return {"sanctions": {}, "earlier_appeals": []}
    counts, rows = await history_rows(
        service, {"target.uuid": uuid}, HISTORY_LIMIT, sanctions_only=False
    )
    # Appeal history deliberately has no epoch fallback for a missing occurred_at.
    counts["last"] = [audit_entry(row, (uuid,), epoch_fallback=False) for row in rows]
    query = {"kind": "appeal", "subject.player_uuid": uuid}
    if exclude is not None:
        query["_id"] = {"$ne": exclude}
    previous = [
        row
        async for row in service.store.database["cases"]
        .find(query)
        .sort("_id", -1)
        .limit(HISTORY_LIMIT)
    ]
    return {
        "sanctions": counts,
        "earlier_appeals": [
            {"number": r["_id"], "status": r["status"]} for r in previous
        ],
    }


async def _submit(
    context: PanelContext, values: dict[str, str], choice: BanRecord | None = None
) -> Case:
    service, user, locale = context.service, context.interaction.user, context.locale
    service.registry.require(CaseKind.APPEAL)
    check_opening(service.settings, user)
    existing = await service.store.by_submission(f"discord:{context.interaction.id}")
    if existing:
        if existing.kind != CaseKind.APPEAL or existing.opener.discord_id != str(
            user.id
        ):
            raise AccessDenied()
        return await context.open(
            opener=existing.opener,
            subject=existing.subject,
            payload=existing.payload,
            dedup_key=existing.dedup_key,
            flags=existing.flags,
        )
    linked = await _linked(service, str(user.id))
    if choice:
        ban = await _checked(
            service, await service.players.find_ban_by_id(choice.ban_id)
        )
        lookup = "link"
        if ban is None or ban.ban_id != choice.ban_id or not _same_subject(choice, ban):
            raise ActionFailed(t(locale, "appeals-no-active"))
    else:
        try:
            ban, lookup = await _lookup(service, values.get("reference", ""))
        except (ValueError, OverflowError) as error:
            raise ActionFailed(t(locale, "appeals-reference-invalid")) from error
    if ban is None:
        raise ActionFailed(t(locale, "appeals-no-active"))
    verified = next((p for p in linked if p.uuid == ban.uuid), None)
    dedup = f"appeal:{ban.ban_id}"
    duplicate = await service.store.by_dedup(CaseKind.APPEAL, dedup)
    if duplicate:
        raise ActionFailed(t(locale, "appeals-existing", number=duplicate.number))
    # Sort/filter before limit: an older appeal or a different ban never blocks this one.
    cutoff = utc(service.now()) - timedelta(
        days=service.settings.cases_appeal_cooldown_days
    )
    rows = (
        service.store.database["cases"]
        .find(
            {
                "kind": "appeal",
                "payload.ban_id": ban.ban_id,
                "status": "rejected",
                "closed_at": {"$gt": cutoff},
            }
        )
        .sort("closed_at", -1)
        .limit(1)
    )
    async for row in rows:
        if isinstance(row.get("closed_at"), datetime):
            days = max(
                1, math.ceil((utc(row["closed_at"]) - cutoff).total_seconds() / 86400)
            )
            raise ActionFailed(t(locale, "appeals-wait", number=row["_id"], days=days))
    secrets = _secrets(ban, linked)
    statement = safe_text(
        values.get("statement", ""), secrets=secrets, limit=1000
    ).strip()
    if not statement:
        raise ActionFailed(t(locale, "cases-form-invalid"))
    opener = Party(
        discord_id=str(user.id),
        discord_name=safe_text(user.display_name, secrets=secrets),
        player_uuid=verified.uuid if verified else None,
        player_pid=or_none(verified.pid) if verified else None,
        player_name=safe_text(ban.name, secrets=secrets) if verified else None,
    )
    subject_player = verified
    if subject_player is None and ban.uuid:
        candidate = await service.players.find_player_by_uuid(ban.uuid)
        if _player_valid(candidate, uuid=ban.uuid):
            subject_player = candidate
    subject = Party(
        player_uuid=ban.uuid,
        player_pid=ban.pid
        if ban.pid is not None
        else (or_none(subject_player.pid) if subject_player else None),
        player_name=safe_text(ban.name, secrets=secrets),
    )
    flags = [] if verified else ["unverified_identity"]
    if not ban.admin_discord_id:
        flags.append("issuer_unknown")
    payload = {
        "ban_id": ban.ban_id,
        "ban_snapshot": await _snapshot(service, ban, locale),
        "statement": statement,
        "lookup": lookup,
        "ban_reference": {"uuid": ban.uuid, "ip": ban.ip},
    }
    try:
        return await context.open(
            opener=opener,
            subject=subject,
            payload=payload,
            evidence=await _history_data(service, ban.uuid),
            dedup_key=dedup,
            flags=tuple(flags),
        )
    except CaseRace:
        duplicate = await service.store.by_dedup(CaseKind.APPEAL, dedup)
        if duplicate:
            raise ActionFailed(
                t(locale, "appeals-existing", number=duplicate.number)
            ) from None
        raise


class AppealChoiceView(PanelChoiceView):
    """Ban labels and modal construction stay private to the appeal flow."""

    def __init__(self, context: PanelContext, bans: list[BanRecord], verified_ids=()):
        self.bans = tuple(bans)
        self.verified_ids = frozenset(verified_ids)
        self.locale = context.locale
        options = (
            [
                discord.SelectOption(
                    label=(
                        t(
                            self.locale,
                            "appeals-choice",
                            ban=ban.ban_id,
                            player=safe_text(ban.name, secrets=_secrets(ban), limit=50),
                        )
                        if ban.ban_id in self.verified_ids
                        else t(
                            self.locale, "appeals-choice-unverified", option=index + 1
                        )
                    )[:100],
                    value=str(index),
                )
                for index, ban in enumerate(self.bans)
            ]
            if len(bans) > 1
            else []
        )
        super().__init__(
            context,
            options,
            self._choice,
            previous_label=t(context.locale, "appeals-previous"),
            next_label=t(context.locale, "appeals-next"),
        )
        if len(bans) <= 1:
            button = discord.ui.Button(label=t(self.locale, "appeals-open-form"))
            button.callback = self._open
            self.add_item(button)

    async def _choice(self, interaction, value):
        await self._modal(interaction, self.bans[int(value)])

    async def _open(self, interaction):
        if await self.interaction_check(interaction):
            await self._modal(interaction, self.bans[0] if self.bans else None)

    async def _modal(self, interaction, ban):
        self.finish()
        current = PanelContext(
            self.context.service, interaction, CaseKind.APPEAL, self.locale
        )

        async def submit(context, values):
            return await _submit(context, values, ban)

        fields = (FormField("statement", "appeals-statement", paragraph=True),)
        if ban is None:
            fields = (
                FormField("reference", "appeals-reference", max_length=64),
                *fields,
            )
        modal = OpenCaseModal(
            current, title_key="appeals-title", fields=fields, submit=submit, files=True
        )
        if ban and ban.ban_id in self.verified_ids:
            modal.title = t(self.locale, "appeals-title-ban", ban=ban.ban_id)[:45]
        await interaction.response.send_modal(modal)


async def panel_handler(context: PanelContext):
    bans, verified_ids = {}, set()
    for player in await _linked(context.service, str(context.interaction.user.id)):
        ban = await _checked(
            context.service,
            await context.service.players.find_ban(
                uuid=player.uuid, ip=player.ip or player.last_ip
            ),
        )
        if ban:
            bans[ban.ban_id] = ban
            # An IP lookup can find another account's sanction. Keep the choice generic
            # until the banned UUID belongs to one of this member's linked accounts.
            if ban.uuid == player.uuid:
                verified_ids.add(ban.ban_id)
    await context.interaction.followup.send(
        t(context.locale, "appeals-select" if len(bans) > 1 else "appeals-continue"),
        view=AppealChoiceView(context, list(bans.values()), verified_ids),
        ephemeral=True,
        allowed_mentions=discord.AllowedMentions.none(),
    )


def _date(value, locale):
    return (
        discord.utils.format_dt(utc(value), "f")
        if isinstance(value, datetime)
        else t(locale, "appeals-permanent")
    )


def card_builder(case, locale):
    embed = base_card(case, locale)
    secrets = tuple(v for v in case.payload.get("ban_reference", {}).values() if v)
    snapshot = case.payload.get("ban_snapshot", {})
    for label, value in (
        ("appeals-ban", f"B-{case.payload.get('ban_id')}"),
        ("cases-reason", safe_text(snapshot.get("reason"), secrets=secrets)),
        ("appeals-issuer", safe_text(snapshot.get("admin_name"), secrets=secrets)),
        (
            "appeals-issued",
            _date(snapshot["issued_at"], locale)
            if snapshot.get("issued_at")
            else t(locale, "cases-unknown"),
        ),
        ("appeals-expiry", _date(snapshot.get("expire_date"), locale)),
        (
            "appeals-statement",
            safe_text(case.payload.get("statement"), secrets=secrets),
        ),
    ):
        embed.add_field(
            name=t(locale, label),
            value=value or t(locale, "cases-unknown"),
            inline=False,
        )
    add_sanctions(embed, case, locale)
    embed.add_field(
        name=t(locale, "appeals-earlier"),
        value=_previous_text(case.evidence.get("earlier_appeals", []), locale),
        inline=False,
    )
    if snapshot.get("admin_discord_id") or snapshot.get("admin_name"):
        embed.add_field(
            name=t(locale, "appeals-conflict"),
            value=safe_text(snapshot.get("admin_name"), secrets=secrets)
            or t(locale, "cases-unknown"),
            inline=False,
        )
    for flag in ("unverified_identity", "issuer_unknown"):
        if flag in case.flags:
            embed.add_field(
                name=t(locale, "appeals-flags"),
                value=t(locale, "appeals-flag-" + flag),
                inline=False,
            )
    return embed


def _previous_text(rows, locale):
    return "\n".join(
        f"#{r['number']} · {t(locale, 'cases-status-' + r['status'])}"
        for r in rows[:HISTORY_LIMIT]
    ) or t(locale, "appeals-none")


async def _current(context, claimed):
    service = context.service
    ban_id = claimed.payload.get("ban_id")
    if (
        type(ban_id) is not int
        or ban_id <= 0
        or claimed.dedup_key != f"appeal:{ban_id}"
    ):
        raise ActionFailed(t(context.locale, "appeals-stale"))
    ban = await _checked(service, await service.players.find_ban_by_id(ban_id))
    reference = claimed.payload.get("ban_reference", {})
    if (
        ban is None
        or ban.ban_id != ban_id
        or not claimed.subject
        or (
            ban.uuid != claimed.subject.player_uuid
            or (not ban.uuid and ban.ip != reference.get("ip"))
        )
    ):
        raise ActionFailed(t(context.locale, "appeals-stale"))
    # Keep the appeal's expected ID/subject intact; refresh issuer before reauthorizing.
    fresh = replace(
        claimed,
        payload={
            **claimed.payload,
            "ban_snapshot": await _snapshot(service, ban, context.locale),
        },
    )
    await service.authorize(fresh, context.member, context.verb)
    fields = {"payload.ban_snapshot": fresh.payload["ban_snapshot"]}
    await service.checkpoint(
        claimed.number,
        set_fields=fields,
        action_token=claimed.pending_action["token"]
        if claimed.pending_action
        else None,
    )
    return ban


class _ActionBot:
    """The real helper's DB calls retain the case's authorization/subject boundary.

    Helpers may read the ban again. Validate that fresh read too, and check immediately
    before mutation. The final DB write still uses the existing ban-ID guard; this is
    not an atomic issuer/read/write transaction with concurrent external editors.
    """

    def __init__(self, context, claimed):
        self.context, self.claimed = context, claimed

    def __getattr__(self, name):
        return getattr(self.context.service.bot, name)

    async def find_ban_by_id(self, ban_id):
        if ban_id != self.claimed.payload["ban_id"]:
            raise ActionFailed(t(self.context.locale, "appeals-stale"))
        return await _current(self.context, self.claimed)

    async def delete_ban(self, *, uuid, ip, ban_id):
        ban = await _current(self.context, self.claimed)
        if (uuid, ip, ban_id) != (ban.uuid or "", ban.ip, ban.ban_id):
            raise ActionFailed(t(self.context.locale, "appeals-stale"))
        return await self.context.service.bot.delete_ban(
            uuid=uuid, ip=ip, ban_id=ban_id
        )

    async def update_ban_expiry(self, *, ban_id, new_expiry):
        ban = await self.find_ban_by_id(ban_id)
        if utc(new_expiry) <= utc(self.context.service.now()) or (
            ban.expire_date is not None and utc(new_expiry) >= utc(ban.expire_date)
        ):
            raise ActionFailed(t(self.context.locale, "appeals-duration-invalid"))
        return await self.context.service.bot.update_ban_expiry(
            ban_id=ban_id, new_expiry=new_expiry
        )


async def unban(context):
    if context.values.get("confirmed") != "yes":
        await _current(context, context.case)
        await context.confirm(
            t(
                context.locale,
                "appeals-confirm-unban",
                ban=context.case.payload["ban_id"],
            )
        )
        return None

    async def action(claimed):
        ban = await _current(context, claimed)
        result = await perform_unban(
            _ActionBot(context, claimed),
            ban,
            context.actor,
            case_number=claimed.number,
            case_action_token=claimed.pending_action["token"],
        )
        return Resolution("unbanned", ban_id=ban.ban_id, audit_id=result.audit_id)

    return await context.run(action)


async def shorten(context):
    async def action(claimed):
        ban = await _current(context, claimed)
        try:
            duration = parse_duration(context.values.get("duration", ""))
            expiry = utc(context.service.now()) + duration
        except (ValueError, OverflowError) as error:
            raise ActionFailed(t(context.locale, "appeals-duration-invalid")) from error
        if duration <= timedelta(0) or (
            ban.expire_date is not None and expiry >= utc(ban.expire_date)
        ):
            raise ActionFailed(t(context.locale, "appeals-duration-invalid"))
        result = await perform_shorten_ban(
            _ActionBot(context, claimed),
            ban.ban_id,
            expiry,
            context.actor,
            case_number=claimed.number,
            case_action_token=claimed.pending_action["token"],
        )
        return Resolution(
            "shortened",
            ban_id=ban.ban_id,
            audit_id=result.audit_id,
            duration_ms=int(duration.total_seconds() * 1000),
        )

    return await context.run(action)


async def history(context):
    # Stored issuer conflict already checked by core; current issuer is checked when present.
    current = await context.service.players.find_ban_by_id(
        context.case.payload["ban_id"]
    )
    if current is not None:
        await _current(context, context.case)
    data = await _history_data(
        context.service,
        context.case.subject.player_uuid if context.case.subject else None,
        exclude=context.case.number,
    )
    secrets = tuple(
        v for v in context.case.payload.get("ban_reference", {}).values() if v
    )
    lines = [
        t(context.locale, "appeals-history-title"),
        _previous_text(data["earlier_appeals"], context.locale),
    ]
    for row in data["sanctions"].get("last", []):
        lines.append(audit_line(row, date_style="f", actor=True, date_first=False))
    return safe_text("\n".join(lines), secrets=secrets, limit=1900)


async def on_closed(service, case):
    """Unban log after durable closure; send/checkpoint crash may duplicate the log only.

    Shorten is logged by the helper. A failed send remains retryable through finish_close;
    never infer a successful unban from a missing row or replay a helper here.
    """
    if (
        not case.resolution
        or case.resolution.code != "unbanned"
        or case.progress.get("appeal_unban_log")
    ):
        return
    channel_id = service.settings.discord_bans_channel_id
    if not channel_id:
        return
    channel = await service.threads.channel(channel_id)
    result = await channel.send(
        t(
            service.settings.cases_staff_locale,
            "appeals-log-unbanned",
            ban=case.resolution.ban_id,
            number=case.number,
            actor=safe_text(
                case.resolution.by_name,
                secrets=tuple(case.payload.get("ban_reference", {}).values()),
            ),
        ),
        allowed_mentions=discord.AllowedMentions.none(),
    )
    if result is None:
        raise RuntimeError("Unban log send was not acknowledged")
    await service.checkpoint(
        case.number, set_fields={"progress.appeal_unban_log": str(result.id)}
    )


def outcomes(case):
    if not case.resolution or case.resolution.code not in {
        "unbanned",
        "shortened",
        "rejected",
    }:
        return ()
    code = case.resolution.code
    # The rejection label is only a Discord receipt namespace, never a game notice kind.
    kind = {
        "unbanned": "appeal_accepted",
        "shortened": "appeal_shortened",
        "rejected": "discord",
    }[code]
    recipient = (
        case.opener if code != "rejected" else replace(case.opener, player_uuid=None)
    )
    result = [
        Outcome(
            recipient,
            kind,
            "appeals-outcome-" + code,
            user_thread=True,
            player_language=case.origin.locale,
        )
    ]
    if (
        code != "rejected"
        and not recipient.player_uuid
        and case.subject
        and case.subject.player_uuid
    ):
        result.append(
            Outcome(
                replace(case.subject, discord_id=None), kind, "appeals-outcome-" + code
            )
        )
    return tuple(result)


APPEAL_FLOW = flow_registry.register(
    Flow(
        kind=CaseKind.APPEAL,
        card_builder=card_builder,
        panel=PanelEntry("appeals-panel", emoji="🔓", handler=panel_handler),
        actions=(
            Action(
                "unban", "cases-button-unban", unban, style=discord.ButtonStyle.success
            ),
            Action(
                "shorten",
                "cases-button-shorten",
                shorten,
                fields=(FormField("duration", "appeals-duration", max_length=64),),
            ),
            Action("history", "cases-button-history", history),
        ),
        on_closed=on_closed,
        outcomes=outcomes,
    )
)
