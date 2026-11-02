"""Senior-only staff complaints. Staff consequences remain a human decision."""

from __future__ import annotations

import discord

from ...i18n import t
from ..cards import (
    add_sanctions,
    base_card,
    case_secrets,
    complaint_statement_messages,
    party_text,
    safe_text,
)
from ..modals import NOTE_FIELDS, OpenCaseModal
from ..model import Case, CaseKind, CaseRace, InvalidForm, Party, Resolution
from ..notify import Outcome
from ..registry import (
    Action,
    Flow,
    FormField,
    PanelContext,
    PanelEntry,
    clean_values,
    flow_registry,
)
from ..views import ActionContext
from ._history import audit_entry, audit_line, history_rows

OPEN_FIELDS = (
    FormField("raw_subject", "complaints-form-subject", max_length=200),
    FormField(
        "statement", "complaints-form-statement", paragraph=True, max_length=4000
    ),
    FormField("when", "complaints-form-when", max_length=200),
)
ACTION_LIMIT = 10


def _actor_uuids(rows):
    return list(
        dict.fromkeys(
            row.get("actor", {}).get("player_uuid")
            for row in rows
            if row.get("actor", {}).get("player_uuid")
        )
    )


async def _sanctions(service, uuids):
    counts, rows = await history_rows(service, {"target.uuid": {"$in": uuids}}, 3)
    counts["actor_uuids"] = _actor_uuids(rows)
    counts["last"] = [
        audit_entry(row, [*uuids, *counts["actor_uuids"]], action_secrets=True)
        for row in rows
    ]
    return counts


async def submit(context: PanelContext, values: dict[str, str]) -> Case:
    values = clean_values(OPEN_FIELDS, values)
    user = context.interaction.user
    players = await context.service.player_store.find_players_by_discord_id(
        str(user.id)
    )
    # Aggregate all linked accounts; a single account also receives the neutral game notice.
    uuids = list(dict.fromkeys(p.uuid for p in players if p.uuid))
    player = players[0] if len(players) == 1 else None
    return await context.open(
        opener=Party(
            discord_id=str(user.id),
            discord_name=user.display_name,
            player_uuid=player.uuid if player else None,
            player_pid=player.pid if player else None,
            player_name=player.nickname if player else None,
        ),
        payload={
            **values,
            "account_uuids": uuids,
            "accounts": [
                {"name": safe_text(p.nickname, secrets=uuids), "pid": p.pid}
                for p in players
            ],
        },
        evidence={"sanctions": await _sanctions(context.service, uuids)},
        flags=("subject_unresolved",),
    )


def open_form(context: PanelContext) -> discord.ui.Modal:
    return OpenCaseModal(
        context,
        title_key="cases-kind-complaint",
        fields=OPEN_FIELDS,
        submit=submit,
        files=True,
    )


def card(case: Case, locale: str) -> discord.Embed:
    embed = base_card(case, locale)
    secrets = case_secrets(case)
    embed.set_field_at(
        0,
        name=t(locale, "complaints-complainant"),
        value=party_text(case.opener, locale),
        inline=False,
    )
    for name, key in (
        ("raw_subject", "complaints-subject-text"),
        ("statement", "complaints-statement"),
        ("when", "complaints-when"),
    ):
        # Keep the entire statement in private follow-up evidence; Discord embeds are capped.
        embed.add_field(
            name=t(locale, key),
            value=safe_text(case.payload.get(name), secrets=secrets)
            or t(locale, "cases-unknown"),
            inline=False,
        )
    if len(case.payload.get("accounts", [])) > 1:
        embed.add_field(
            name=t(locale, "complaints-accounts"),
            value="\n".join(
                party_text(Party(player_name=p["name"], player_pid=p["pid"]), locale)
                for p in case.payload["accounts"]
            )[:1024],
            inline=False,
        )
    add_sanctions(embed, case, locale)
    if case.subject:
        embed.add_field(
            name=t(locale, "complaints-exclusion"),
            value=t(locale, "complaints-exclusion-text"),
            inline=False,
        )
        history = case.evidence.get("subject_actions", {})
        lines = (
            [t(locale, "complaints-actions-count", count=history["total"])]
            if history.get("subject_id") == case.subject.discord_id
            else [t(locale, "complaints-actions-pending")]
        )
        for entry in (
            history.get("last", [])
            if history.get("subject_id") == case.subject.discord_id
            else []
        ):
            lines.append(audit_line(entry))
        embed.add_field(
            name=t(locale, "complaints-subject-actions"),
            value="\n".join(lines)[:1024],
            inline=False,
        )
    if case.resolution and case.resolution.code == "upheld":
        for index, field in enumerate(embed.fields):
            if field.name == t(locale, "cases-reason"):
                embed.set_field_at(
                    index, name=t(locale, "cases-note"), value=field.value, inline=False
                )
    # Multi-account UUIDs are not core parties; scrub every field, including staff notes.
    for index, field in enumerate(embed.fields):
        embed.set_field_at(
            index,
            name=field.name,
            value=safe_text(field.value, secrets=secrets),
            inline=field.inline,
        )
    return embed


async def created(service, case: Case) -> None:
    # A 4000-character modal statement cannot fit one embed field. Store its full sanitized
    # text only in the senior post, in deterministic chunks so creation retries are safe.
    for text in complaint_statement_messages(case, service.settings.cases_staff_locale):
        await service.threads.post_note(case, text)


async def subject_set(service, case: Case) -> None:
    if not case.subject or not case.subject.discord_id:
        return
    audit = service.store.database["moderation_audit"]
    uuids = case.payload.get("account_uuids", [])
    # Stable Discord OR game actor identity; one Mongo predicate counts each row once.
    players = await service.player_store.find_players_by_discord_id(
        case.subject.discord_id
    )
    actor_uuids = list(dict.fromkeys(p.uuid for p in players if p.uuid))
    query = {
        "target.uuid": {"$in": uuids},
        "$or": [
            {"actor.discord_id": case.subject.discord_id},
            {"actor.player_uuid": {"$in": actor_uuids}},
        ],
    }
    total = await audit.count_documents(query)
    rows = [
        row
        async for row in audit.find(query)
        .sort("created_at_epoch_ms", -1)
        .limit(ACTION_LIMIT)
    ]
    secrets = (*case_secrets(case), *actor_uuids, *_actor_uuids(rows))
    history = {
        "subject_id": case.subject.discord_id,
        "subject_revision": case.progress.get("complaint_subject_revision"),
        "actor_uuids": list(dict.fromkeys([*actor_uuids, *_actor_uuids(rows)])),
        "total": total,
        "last": [audit_entry(row, secrets, action_secrets=True) for row in rows],
    }
    for _ in range(5):
        current = await service.get(case.number)
        if (
            current.subject != case.subject
            or current.progress.get("complaint_subject_revision")
            != history["subject_revision"]
        ):
            return  # Including A -> B -> A: that newer selection owns its evidence.
        if current.pending_action:
            return  # Durable pending history survives the action and its neutral outcome.
        if current.evidence.get(
            "subject_actions"
        ) == history and not current.progress.get("complaint_history_pending"):
            break
        try:
            current = await service.store.cas(
                current,
                set_fields={"evidence.subject_actions": history},
                unset_fields=("progress.complaint_history_pending",),
            )
            break
        except CaseRace:
            continue  # Revalidate the identity and revision, never reload-and-write stale fields.
    else:
        raise CaseRace()
    # Projection shares the decision lock; an older query cannot post after a new subject.
    async with service._lock(f"projection:{case.number}"):
        current = await service.get(case.number)
        if (
            current.subject != case.subject
            or current.progress.get("complaint_subject_revision")
            != history["subject_revision"]
        ):
            return
        await _post_history(service, current, history)


async def _post_history(service, case: Case, history) -> None:
    # The complete bounded list belongs in the senior post even when the embed fills up.
    locale = service.settings.cases_staff_locale
    lines = [
        t(
            locale,
            "complaints-actions-header",
            name=safe_text(
                case.subject.discord_name or case.subject.discord_id,
                secrets=case_secrets(case),
                limit=64,
            ),
        ),
        t(locale, "complaints-actions-count", count=history["total"]),
    ]
    for entry in history["last"]:
        lines.append(audit_line(entry))
    chunk = []
    for line in lines:
        if chunk and len("\n".join([*chunk, line])) > 1800:
            await service.threads.post_note(case, "\n".join(chunk))
            chunk = [lines[0]]
        chunk.append(line)
    await service.threads.post_note(case, "\n".join(chunk))


async def set_subject(context: ActionContext) -> Case | None:
    if "subject" not in context.values:
        await context.choose_user(
            t(context.locale, "complaints-select-subject"), "subject"
        )
        return None
    discord_id = context.values["subject"]
    if (
        not discord_id.isdigit()
        or int(discord_id) <= 0
        or int(discord_id) == context.service.bot.user.id
    ):
        raise InvalidForm()
    return await context.service.set_subject(
        context.case.number,
        context.member,
        Party(discord_id=discord_id, discord_name=context.values.get("subject_name")),
    )


async def resolve(context: ActionContext) -> Case:
    return await context.service.resolve(
        context.case.number,
        context.member,
        Resolution("upheld", reason_text=context.values["note"]),
    )


def outcomes(case: Case) -> list[Outcome]:
    if case.subject and case.subject.discord_id == case.opener.discord_id:
        return []
    return [
        Outcome(case.opener, "complaint_closed", "complaints-outcome", user_thread=True)
    ]


async def reconcile(service, case: Case) -> None:
    await service.exclude_subject(case.number)
    await subject_set(service, case)
    # Also removes manual/old invitations on the next repair pass. No claim of revoking
    # inherited view/Manage Threads/Administrator access is made.
    await service.project(case.number)


COMPLAINT_FLOW = flow_registry.register(
    Flow(
        kind=CaseKind.COMPLAINT,
        card_builder=card,
        panel=PanelEntry("cases-kind-complaint", emoji="⚖️", modal_factory=open_form),
        actions=(
            Action("subject", "cases-button-subject", set_subject),
            Action(
                "resolve",
                "cases-button-resolve",
                resolve,
                style=discord.ButtonStyle.primary,
                fields=NOTE_FIELDS,
            ),
        ),
        on_created=created,
        on_subject_set=subject_set,
        outcomes=outcomes,
        reconcile=reconcile,
        reconcile_open=True,
    )
)
