"""Public map review. Stored attachment identity and review hash bind every Accept."""

from __future__ import annotations

import asyncio
import hashlib
from dataclasses import replace
from datetime import timedelta
from uuid import uuid4

import discord
from xcore_protocol.generated.maps import MapsInspectResponseV1

from ...i18n import resolve_dm_locale, t
from ...redis_bus import RpcError
from ...registry import server_registry
from ...retry import TRANSIENT_EXCEPTIONS
from ...rpc.mindustry_rpc import NoLiveServerError
from ...staff_actions import (
    Actor,
    MapUploadFile,
    MapUploadTimeout,
    perform_map_upload,
    prepare_map_upload,
)
from ..cards import base_card, safe_text, sanitize_card
from ..model import (
    ActionFailed,
    Case,
    CaseKind,
    CaseRace,
    Origin,
    Party,
    Resolution,
    utc,
)
from ..msav import InvalidMsav, validate_msav
from ..notify import Outcome
from ..registry import Action, ActionContext, Flow, flow_registry
from .badges import _linked, grant_map_maker

BADGE_WINDOW = timedelta(days=30)
KNOWN_FINDINGS = frozenset(
    (
        "missing-name",
        "missing-author",
        "requires-mods",
        "unknown-content",
        "size-out-of-range",
        "no-core",
        "no-spawn",
        "pvp-single-team",
        "attack-no-enemy-core",
        "td-road-floor-mixed",
        "td-road-floor-unexpected",
        "td-road-disconnected",
        "td-spawn-not-at-road-start",
        "td-border-wall-missing",
        "td-drop-zone-radius",
        "td-waves-enabled",
        "invalid-map",
        "inspection-mismatch",
    )
)


def _belongs(service, thread) -> bool:
    return (
        service.registry.enabled(CaseKind.MAP)
        and getattr(thread, "parent_id", None) == service.settings.discord_maps_forum_id
        and getattr(getattr(thread, "guild", None), "id", None)
        == service.settings.discord_guild_id
    )


def _ignored(service, thread) -> bool:
    ignored = {v.strip() for v in service.settings.maps_ignored_tags.split(",")}
    return any(tag.name in ignored for tag in thread.applied_tags)


def _profile(service, thread) -> str | None:
    profiles = {}
    for pair in service.settings.maps_tag_profiles.split(","):
        name, sep, profile = pair.partition("=")
        if sep and name.strip() and profile.strip():
            profiles[name.strip()] = profile.strip()
    return next(
        (profiles[tag.name] for tag in thread.applied_tags if tag.name in profiles),
        None,
    )


def _file(message):
    return next(
        (
            a
            for a in reversed(message.attachments)
            if a.filename.lower().endswith(".msav")
        ),
        None,
    )


def _identity(message, attachment) -> dict:
    return {
        "message_id": str(message.id),
        "attachment_id": str(attachment.id),
        "file_name": attachment.filename,
        "size": attachment.size,
    }


def _unknown(case: Case) -> bool:
    return case.payload.get("upload_attempt", {}).get("state") == "unknown"


async def _starter(thread):
    for attempt in range(3):
        try:
            return await thread.fetch_message(thread.id)
        except discord.NotFound:
            if attempt == 2:
                raise
            await asyncio.sleep(0.25)


async def _latest(thread):
    # A long discussion must not make a newer author file disappear from selection.
    async for message in thread.history(limit=None):
        if (
            str(message.author.id) == str(thread.owner_id)
            and not message.author.bot
            and _file(message)
        ):
            return message
    message = await _starter(thread)
    return (
        message
        if str(message.author.id) == str(thread.owner_id) and _file(message)
        else None
    )


async def _request_file(service, thread, locale):
    # Before a case exists there is no projection to own this prompt. Adopt a sent prompt
    # after a crash, just as the core adopts a review card by its footer.
    content = t(locale, "maps-file-needed")
    if not await service.threads._has_content(thread, content):
        await thread.send(content, allowed_mentions=discord.AllowedMentions.none())


async def intake(service, thread, *, manual: bool = False, message=None) -> Case | None:
    if not _belongs(service, thread) or _ignored(service, thread):
        return None
    async with service._lock(f"map-intake:{thread.id}"):
        existing = await service.store.by_submission(f"map:{thread.id}")
        if existing:
            return await service.resume_creation(existing.number)
        markers = service.store.database["case_map_intake"]
        marker = await markers.find_one({"_id": str(thread.id)})
        # Gateway events also announce old posts when the bot gains access to them.
        if (
            not manual
            and marker is None
            and (
                thread.created_at is None
                or utc(thread.created_at)
                < utc(getattr(service, "maps_enabled_at", service.started_at))
            )
        ):
            return None
        if marker is None:
            await markers.update_one(
                {"_id": str(thread.id)},
                {
                    "$setOnInsert": {
                        "author_id": str(thread.owner_id),
                        "at": service.now(),
                    }
                },
                upsert=True,
            )
        if message is None:
            message = await _latest(thread) if manual else await _starter(thread)
        if message is None:
            author = service.bot.get_user(
                thread.owner_id
            ) or await service.bot.fetch_user(thread.owner_id)
        else:
            author = message.author
        locale = await resolve_dm_locale(thread.owner_id, service.store)
        attachment = (
            _file(message)
            if message and str(message.author.id) == str(thread.owner_id)
            else None
        )
        if attachment is None:
            await _request_file(service, thread, locale)
            return None
        return await service.open(
            kind=CaseKind.MAP,
            origin=Origin("forum", f"map:{thread.id}", locale),
            opener=Party(
                discord_id=str(thread.owner_id), discord_name=author.display_name
            ),
            payload={
                **_identity(message, attachment),
                "profile": _profile(service, thread),
                "creator_handle": getattr(author, "name", None),
                "review_stamp": uuid4().hex,
            },
            dedup_key=f"map:{thread.id}",
            flags=("deep_checks_pending",),
            discord={"forum_id": str(thread.parent_id), "thread_id": str(thread.id)},
            member=author if isinstance(author, discord.Member) else None,
        )


async def on_thread_create(service, thread):
    await intake(service, thread)


async def on_message(service, message):
    thread = message.channel
    if (
        not _belongs(service, thread)
        or message.author.bot
        or str(message.author.id) != str(thread.owner_id)
        or not _file(message)
        or _ignored(service, thread)
    ):
        return
    case = await service.store.by_submission(f"map:{thread.id}")
    if case is None:
        await intake(service, thread, message=message)
        case = await service.store.by_submission(f"map:{thread.id}")
        if case is None:
            return
    async with service._lock(f"map-file:{case.number}"):
        case = await service.get(case.number)
        if not case.actionable or case.pending_action or _unknown(case):
            return
        attachment = _file(message)
        current_order = (
            int(case.payload["message_id"]),
            int(case.payload["attachment_id"]),
        )
        if (message.id, attachment.id) <= current_order:
            return
        await _refresh(service, case, message=message)
    await service.project(case.number)


async def _attachment(service, case, *, locale=None):
    thread = await service.threads.channel(case.discord["thread_id"])
    message = await thread.fetch_message(int(case.payload["message_id"]))
    attachment = next(
        (a for a in message.attachments if str(a.id) == case.payload["attachment_id"]),
        None,
    )
    if (
        str(message.author.id) != case.opener.discord_id
        or str(thread.owner_id) != case.opener.discord_id
        or attachment is None
        or _identity(message, attachment)
        != {
            key: case.payload[key]
            for key in ("message_id", "attachment_id", "file_name", "size")
        }
    ):
        raise ActionFailed(t(locale or case.origin.locale, "maps-file-changed"))
    return attachment


async def _refresh(service, case, *, message=None):
    # All writes use the revision actually inspected. A concurrent Accept or replacement
    # wins rather than receiving stale results from a slow inspection.
    payload = dict(case.payload)
    thread = await service.threads.channel(case.discord["thread_id"])
    if message is not None:
        identity = _identity(message, _file(message))
        if any(payload.get(key) != value for key, value in identity.items()):
            for key in (
                "server",
                "upload_attempt",
                "last_upload",
                "badge_state",
                "pending_badge",
            ):
                payload.pop(key, None)
        payload.update(identity)
    payload.update(
        profile=_profile(service, thread),
        findings=[],
        info={},
        validation_error=None,
        inspection_mismatch=False,
        checked_at=service.now(),
        review_stamp=uuid4().hex,
    )
    payload.pop("sha256", None)
    selected = Case.from_doc({**case.to_doc(), "payload": payload})
    pending = False
    try:
        attachment = await _attachment(service, selected)
        if attachment.size > service.settings.maps_max_file_bytes:
            raise InvalidMsav("size")
        data = await attachment.read()
        if len(data) != attachment.size:
            raise InvalidMsav("size")
        local = await asyncio.to_thread(
            validate_msav,
            data,
            attachment.filename,
            max_file_bytes=service.settings.maps_max_file_bytes,
        )
        payload.update(
            sha256=local.sha256,
            info={
                "name": local.meta.get("name")
                or local.meta.get("mapname")
                or attachment.filename,
                "saveVersion": local.version,
            },
        )
        try:
            raw = await service.transport.rpc_inspect_map(
                url=attachment.url,
                file_name=attachment.filename,
                profile=payload["profile"],
            )
            response = (
                MapsInspectResponseV1.from_payload(raw) if raw is not None else None
            )
        except (
            NoLiveServerError,
            RpcError,
            TimeoutError,
            *TRANSIENT_EXCEPTIONS,
            ValueError,
            TypeError,
        ):
            response = None
        if response is None or response.status.value == "download_failed":
            pending = True
        elif response.status.value == "invalid_map" and response.info is None:
            # A final answer: it carries no info, so the profile check below would retry forever.
            payload["findings"] = [
                {
                    "code": "invalid-map",
                    "severity": "error",
                    "detail": response.detail,
                }
            ]
        elif payload["profile"] is not None and (
            response.info is None or response.info.profile != payload["profile"]
        ):
            pending = True
        else:
            info = response.info
            if info is not None and (
                info.sha256.lower() != local.sha256 or info.sizeBytes != local.size
            ):
                pending = True
                payload["inspection_mismatch"] = True
                payload["findings"] = [
                    {"code": "inspection-mismatch", "severity": "error"}
                ]
            elif response.status.value == "invalid_map":
                payload["findings"] = [{"code": "invalid-map", "severity": "error"}]
            elif info is None:
                pending = True
            else:
                payload["info"] = info.to_payload()
                payload["findings"] = [f.to_payload() for f in response.findings]
    except InvalidMsav as error:
        payload["validation_error"] = error.code
    except (discord.HTTPException, ActionFailed):
        # CDN/message problems remain retryable; no old hash/findings survive.
        pending = True
        payload["validation_error"] = "unavailable"
    flags = [f for f in case.flags if f != "deep_checks_pending"]
    if pending:
        flags.append("deep_checks_pending")
    from ..model import event

    return await service.store.cas(
        case,
        set_fields={"payload": payload, "flags": flags},
        event=event("evidence_refreshed", None, service.now()),
    )


async def on_created(service, case):
    async with service._lock(f"map-file:{case.number}"):
        await _refresh(service, await service.get(case.number))


async def recheck(context: ActionContext):
    async with context.service._lock(f"map-file:{context.case.number}"):
        case = await context.service.get(context.case.number)
        await context.service.authorize(case, context.member, "recheck")
        if not case.actionable or case.pending_action:
            raise CaseRace()
        if _unknown(case):
            raise ActionFailed(t(context.locale, "maps-upload-unknown"))
        thread = await context.service.threads.channel(case.discord["thread_id"])
        message = await _latest(thread)
        await _refresh(context.service, case, message=message)
    await context.service.project(case.number)
    return t(context.locale, "maps-rechecked")


def _ready(case, locale):
    if _unknown(case):
        raise ActionFailed(t(locale, "maps-upload-unknown"))
    if (
        case.payload.get("validation_error")
        or not case.payload.get("sha256")
        or case.payload.get("inspection_mismatch")
    ):
        raise ActionFailed(t(locale, "maps-not-valid"))


async def accept(context: ActionContext):
    case = context.case
    _ready(case, context.locale)
    stamp = context.values.setdefault("review_stamp", case.payload["review_stamp"])
    if stamp != case.payload["review_stamp"]:
        raise ActionFailed(t(context.locale, "maps-review-changed"))
    profile = case.payload.get("profile") or case.payload.get("info", {}).get("profile")
    servers = sorted(
        server_registry.get_all_servers(),
        key=lambda s: (s.mode != profile, s.name),
    )
    if not servers:
        raise ActionFailed(t(context.locale, "maps-no-server"))
    if "server" not in context.values:
        await context.choose(
            t(context.locale, "maps-select-server"),
            "server",
            [
                discord.SelectOption(label=safe_text(s.name, limit=100), value=s.name)
                for s in servers[:25]
            ],
        )
        return None
    server = context.values["server"]
    if server not in {s.name for s in servers}:
        raise ActionFailed(t(context.locale, "maps-no-server"))
    has_errors = any(f["severity"] == "error" for f in case.payload.get("findings", []))
    if has_errors and context.values.get("accept_anyway") != "yes":
        await context.confirm(
            t(context.locale, "maps-accept-anyway"), name="accept_anyway"
        )
        return None
    if (
        "deep_checks_pending" in case.flags
        and context.values.get("accept_pending") != "yes"
    ):
        await context.confirm(
            t(context.locale, "maps-accept-pending"), name="accept_pending"
        )
        return None

    async def upload(claimed):
        _ready(claimed, context.locale)
        if claimed.payload["review_stamp"] != stamp:
            raise ActionFailed(t(context.locale, "maps-review-changed"))
        thread = await context.service.threads.channel(claimed.discord["thread_id"])
        attachment = await _attachment(context.service, claimed, locale=context.locale)
        if (
            hashlib.sha256(await attachment.read()).hexdigest()
            != claimed.payload["sha256"]
        ):
            raise ActionFailed(t(context.locale, "maps-file-changed"))
        creator = Actor(
            claimed.opener.discord_id,
            claimed.opener.discord_name or "",
            claimed.payload.get("creator_handle"),
        )
        files = [MapUploadFile(attachment.url, attachment.filename)]
        uploader = Actor(
            str(context.member.id),
            context.member.display_name,
            getattr(context.member, "name", None),
        )
        # Validate the common slash payload before recording an uncertain external action.
        prepare_map_upload(server, files, uploader, creator)
        token = claimed.pending_action["token"]
        await context.service.checkpoint(
            claimed.number,
            action_token=token,
            set_fields={
                "payload.server": server,
                "payload.upload_attempt": {
                    "state": "unknown",
                    "token": token,
                    "server": server,
                },
            },
            unset_fields=("payload.badge_state",),
        )
        latest = await _latest(thread)
        if latest is None or _identity(latest, _file(latest)) != {
            key: claimed.payload[key]
            for key in ("message_id", "attachment_id", "file_name", "size")
        }:
            # Nothing was sent: release only this pre-dispatch marker so the newer file
            # can be reviewed after the action guard is released.
            await context.service.checkpoint(
                claimed.number,
                action_token=token,
                unset_fields=("payload.upload_attempt", "payload.server"),
            )
            raise ActionFailed(t(context.locale, "maps-review-changed"))
        try:
            result = await perform_map_upload(
                context.service.bot,
                server,
                files,
                uploader,
                creator,
                case_number=claimed.number,
                case_action_token=token,
                request_id=f"map:{claimed.number}:{token}",
                reviewed_sha256=claimed.payload["sha256"],
            )
        except MapUploadTimeout as error:
            raise ActionFailed(t(context.locale, "maps-upload-unknown")) from error
        status = result.results[0]
        if status.status.value != "installed":
            await context.service.checkpoint(
                claimed.number,
                action_token=token,
                set_fields={
                    "payload.upload_attempt.state": "result",
                    "payload.last_upload": {
                        "server": server,
                        "file_name": status.fileName,
                        "status": status.status.value,
                        "detail": status.detail or "",
                    },
                },
            )
            raise ActionFailed(
                t(
                    context.locale,
                    "maps-upload-result",
                    file=safe_text(status.fileName),
                    status=t(
                        context.locale, "maps-upload-status-" + status.status.value
                    ),
                    detail=safe_text(status.detail),
                )
            )
        await context.service.checkpoint(
            claimed.number,
            action_token=token,
            set_fields={
                "payload.upload_attempt.state": "installed",
                "payload.pending_badge": True,
            },
            unset_fields=("payload.last_upload",),
        )
        return Resolution(
            "accepted",
            audit_id=result.audit_id,
            server=server,
            file_name=status.fileName,
        )

    try:
        return await context.run(upload)
    finally:
        current = await context.service.get(case.number)
        if current.actionable and not current.pending_action and not _unknown(current):
            thread = await context.service.threads.channel(current.discord["thread_id"])
            latest = await _latest(thread)
            if latest is not None:
                await on_message(context.service, latest)
        await context.service.project(case.number)


async def _badge(service, case):
    if (
        not case.resolution
        or case.resolution.code != "accepted"
        or not case.closed_at
        or not timedelta(0) <= service.now() - case.closed_at <= BADGE_WINDOW
    ):
        return
    await service.checkpoint(case.number, set_fields={"payload.pending_badge": True})
    try:
        result = await grant_map_maker(
            service,
            case.opener.discord_id,
            actor=Actor(case.resolution.by_discord_id, case.resolution.by_name or ""),
            request_id=f"map-maker:{case.number}",
        )
    except Exception as error:
        await service._note_error(case.number, "map_maker", error)
        return
    await service.checkpoint(
        case.number,
        set_fields={
            "payload.badge_state": result,
        },
        unset_fields=("payload.pending_badge",) if result == "complete" else (),
    )
    if result == "link_needed":
        await service.threads.post_note(
            case, t(case.origin.locale, "badges-link-needed")
        )


def outcomes(case):
    recipient = case.progress.get("map_outcome", {})
    if recipient.get("closed_at") != case.closed_at:
        return ()
    party = replace(case.opener, player_uuid=recipient.get("player_uuid"))
    secrets = (party.player_uuid or "",)
    accepted = case.resolution.code == "accepted"
    params = (
        {
            "file": safe_text(
                case.resolution.file_name or case.payload.get("file_name"),
                secrets=secrets,
            ),
            "server": safe_text(case.resolution.server, secrets=secrets),
        }
        if accepted
        else {
            "reason": safe_text(
                case.resolution.reason_text or case.resolution.reason_code,
                secrets=secrets,
            )
        }
    )
    params["map"] = safe_text(
        case.payload.get("info", {}).get("name") or case.payload.get("file_name"),
        secrets=secrets,
    )
    return (
        Outcome(
            party,
            "map_accepted" if accepted else "map_rejected",
            "maps-outcome-accepted" if accepted else "maps-outcome-rejected",
            params,
        ),
    )


async def on_closed(service, case):
    if case.progress.get("map_outcome", {}).get("closed_at") != case.closed_at:
        players = await _linked(service, case.opener.discord_id)
        case = await service.checkpoint(
            case.number,
            set_fields={
                "progress.map_outcome": {
                    "closed_at": case.closed_at,
                    "player_uuid": players[0].uuid if len(players) == 1 else None,
                }
            },
        )
    if case.resolution.code == "accepted" and _unknown(case):
        case = await service.checkpoint(
            case.number, set_fields={"payload.upload_attempt.state": "installed"}
        )
    if (
        case.resolution.code == "accepted"
        and case.payload.get("badge_state") != "complete"
    ):
        await _badge(service, case)
    outcome = outcomes(case)[0]
    await service.threads.post_note(
        case,
        t(
            case.origin.locale,
            outcome.message_key,
            number=case.number,
            **outcome.params,
        ),
    )


async def reconcile(service, case):
    if (
        case.actionable
        and not case.pending_action
        and not _unknown(case)
        and "deep_checks_pending" in case.flags
    ):
        async with service._lock(f"map-file:{case.number}"):
            current = await service.get(case.number)
            if (
                current.actionable
                and not current.pending_action
                and not _unknown(current)
            ):
                await _refresh(service, current)
        await service.project(case.number)
    elif case.payload.get("pending_badge"):
        if (
            case.resolution
            and case.resolution.code == "accepted"
            and case.closed_at
            and service.now() - case.closed_at <= BADGE_WINDOW
            and not case.progress.get("outcomes_done")
        ):
            await service.finish_close(case.number)
            return
        await _badge(service, case)
        await service.project(case.number)


def _finding(finding, locale):
    code = finding["code"]
    text = (
        t(locale, "maps-finding-" + code)
        if code in KNOWN_FINDINGS
        else safe_text(code, limit=120)
    )
    if finding.get("x") is not None and finding.get("y") is not None:
        text += f" ({finding['x']}, {finding['y']})"
    if finding.get("count") is not None:
        text += " · " + t(locale, "maps-finding-count", count=finding["count"])
    if finding.get("detail"):
        text += " — " + safe_text(finding["detail"], limit=180)
    return text[:350]


def card_builder(case, locale):
    embed = base_card(case, locale)
    # Bound the free-text fields before allocating space to grouped findings.
    for index, field in enumerate(embed.fields):
        embed.set_field_at(
            index, name=field.name, value=field.value[:300], inline=field.inline
        )
    payload, info = case.payload, case.payload.get("info", {})
    embed.add_field(
        name=t(locale, "maps-map"),
        value=safe_text(info.get("name") or payload.get("file_name"), limit=200) or "…",
        inline=False,
    )
    embed.add_field(
        name=t(locale, "maps-file"),
        value=t(
            locale,
            "maps-file-info",
            file=safe_text(payload.get("file_name"), limit=150),
            size=payload.get("size", 0),
        ),
        inline=False,
    )
    mode = (
        info.get("profile")
        or payload.get("profile")
        or info.get("gameMode")
        or t(locale, "cases-unknown")
    )
    dimensions = (
        t(locale, "maps-dimensions", width=info["width"], height=info["height"])
        if "width" in info and "height" in info
        else ""
    )
    embed.add_field(
        name=t(locale, "maps-mode"),
        value=safe_text(mode, limit=100) + (" · " + dimensions if dimensions else ""),
        inline=False,
    )
    if payload.get("validation_error"):
        embed.add_field(
            name=t(locale, "maps-validation"),
            value=t(locale, "maps-invalid-" + payload["validation_error"]),
            inline=False,
        )
    if "deep_checks_pending" in case.flags:
        embed.add_field(
            name=t(locale, "maps-checks"),
            value=t(locale, "maps-checks-pending"),
            inline=False,
        )
    if _unknown(case) and case.active:
        embed.add_field(
            name=t(locale, "maps-upload"),
            value=t(locale, "maps-upload-unknown"),
            inline=False,
        )
    elif payload.get("last_upload") and case.active:
        result = payload["last_upload"]
        embed.add_field(
            name=t(locale, "maps-upload"),
            value=t(
                locale,
                "maps-upload-result",
                file=safe_text(result["file_name"], limit=150),
                status=t(locale, "maps-upload-status-" + result["status"]),
                detail=safe_text(result["detail"], limit=200),
            ),
            inline=False,
        )
    if case.resolution and case.resolution.code == "accepted":
        embed.add_field(
            name=t(locale, "maps-installed"),
            value=t(
                locale,
                "maps-installed-info",
                server=safe_text(case.resolution.server, limit=100),
                file=safe_text(case.resolution.file_name, limit=150),
            ),
            inline=False,
        )
        if payload.get("pending_badge"):
            embed.add_field(
                name=t(locale, "maps-badge"),
                value=t(locale, "maps-badge-pending"),
                inline=False,
            )
    for severity in ("error", "warning", "info"):
        findings = [f for f in payload.get("findings", []) if f["severity"] == severity]
        if not findings:
            continue
        lines = []
        for finding in findings:
            line = _finding(finding, locale)
            if sum(len(v) + 1 for v in lines) + len(line) > 850:
                break
            lines.append(line)
        if len(lines) < len(findings):
            lines.append(t(locale, "maps-more", count=len(findings) - len(lines)))
        embed.add_field(
            name=t(locale, "maps-severity-" + severity),
            value="\n".join(lines),
            inline=False,
        )
    return sanitize_card(embed, case)


MAP_FLOW = flow_registry.register(
    Flow(
        kind=CaseKind.MAP,
        card_builder=card_builder,
        takeable=False,
        actions=(
            Action("accept", "maps-accept", accept, style=discord.ButtonStyle.success),
            Action("recheck", "maps-recheck", recheck),
        ),
        on_thread_create=on_thread_create,
        on_message=on_message,
        on_created=on_created,
        on_closed=on_closed,
        outcomes=outcomes,
        reconcile=reconcile,
    )
)
