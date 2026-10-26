# Cases core: the contract for flow and transport modules

The core owns the case record, its numbering, the state machine, the Discord projection (post,
card, buttons, tags, archive and lock, the opener's private thread, the panel), access checks,
outcome delivery and repair. A flow supplies what is specific to one kind: its forms, its card
fields, its buttons and what they do, its outcome texts.

The stored case is the only source of truth. A flow never edits a card, a tag or a thread
itself: it changes the case through `CaseService` and the core re-renders.

## Registering a flow

A flow is one module in `cases/flows/` that ends with `flow_registry.register(Flow(...))`.
`flows/__init__.py` imports every module of the package, so nothing else has to be edited. One
flow per `CaseKind`. A flow is switched on by its setting (`CASES_REPORTS_ENABLED`, ...) and only
if the start-up checks pass; until then none of its hooks, buttons or events run.

A flow also adds `i18n/locales/{en,ru,uk}/<flow>.ftl` (ids are global within a locale, so prefix
them: `reports-...`) and its own tests. `tests/cases_fakes.py` has the fakes (`core(flow=...)`),
`tests/cases_dummy_flow.py` a complete flow used by the core's tests.

```python
Flow(
    kind: CaseKind,
    card_builder: (case: Case, locale: str) -> discord.Embed,
    panel: PanelEntry | None = None,          # the button on the public panel
    actions: tuple[Action, ...] = (),         # the flow's own card buttons
    takeable: bool = True,                    # False: no Take / Release buttons
    reject_reasons: tuple[Choice, ...] = (),  # reason codes offered by the core's Reject form
    on_thread_create: async (service, thread: discord.Thread) -> None,
    on_message: async (service, message: discord.Message) -> None,
    on_thread_delete: async (service, payload: discord.RawThreadDeleteEvent) -> None,
    on_created: async (service, case) -> None,
    on_merged: async (service, case, joined: dict) -> None,
    on_subject_set: async (service, case) -> None, # after button or command subject change
    on_closed: async (service, case) -> None,
    outcomes: (case) -> Sequence[Outcome],
    reconcile: async (service, case) -> None,
    reconcile_open: bool = False,             # also call reconcile for every open case
    event_hooks: dict[str, async (service, payload) -> None] = {},
)
```

- `on_thread_create`, `on_message`, `on_thread_delete` receive every such gateway event of the
  guild while the flow is on. Return quickly for events that are not yours (compare
  `thread.parent_id` or `message.channel.parent_id` with a configured id before any I/O). An
  exception is logged and does not reach other flows or the rest of the bot.
- `on_created` runs once the post, the evidence messages and the user thread exist.
  `on_merged` runs for every later reporter merged into a report, after that reporter's own
  thread exists (see Private threads); `joined` holds the `Party` fields plus `source`,
  `joined_at`, `submission_id`, `locale`, `server`, `payload`. `on_closed` runs after
  a case is resolved or rejected, before the outcomes are sent. All three **must be idempotent**:
  a failure after the hook repeats it later.
- `outcomes(case)` is pure: it returns who is told what about a closed case (see Outcomes).
- `reconcile(service, case)` is called by the repair task every five minutes for an active case
  flagged `deep_checks_pending`, and for a closed case with `payload.pending_badge` during 30
  days after the close. Clear the flag or the payload key when done. With `reconcile_open=True`
  it is also called for every open case that has no action in progress (the report flow looks
  for a sanction issued outside the card); keep that check cheap, it runs for each one.
- `event_hooks` maps a transport event name to a handler; the transport package calls
  `registry.dispatch_event(name, service, payload)`. Errors propagate to the caller.

## Opening a case

From the panel. `PanelEntry(label_key, emoji=None, modal_factory=None, handler=None)`:

- `modal_factory(context: PanelContext) -> discord.ui.Modal` — the modal is the first response
  to the press, so the factory must do no I/O. Use
  `OpenCaseModal(context, title_key=..., fields=(FormField, ...), submit=..., files=False)`.
  `submit(context, values: dict[str, str]) -> Case` validates, looks things up and calls
  `context.open(...)`. The wrapper defers, re-checks the flag and the blacklist, copies uploaded
  files into the post, and answers with the case number and the submitter's own thread (on a
  merged report that is not the opener's). `values` holds one
  string per field name. To refuse, raise `ActionFailed(text)` with a localised text.
- `handler(context: PanelContext) -> None` — for an entry that needs a lookup before any form
  (the appeal flow lists the member's bans). The press is already deferred; answer with
  `context.interaction.followup.send(..., ephemeral=True)`, for example a
  `views.ChoiceView(on_choice, locale, options, requester_id=...)` whose `on_choice(interaction,
  value)` receives an unanswered interaction and can send a modal.

`PanelContext` has `service`, `interaction`, `kind`, `locale` and

```python
await context.open(*, opener: Party, subject: Party | None = None, payload=None,
                   evidence=None, dedup_key: str | None = None, flags=()) -> Case
```

which opens with `Origin("discord", "discord:<interaction id>", locale)`. Cooldown, the cap on
open cases and the blacklist are applied here; the failures are `Cooldown`, `OpenLimit`,
`OpeningBlocked`, and the wrapper shows them.

From anywhere else (a transport event, a forum post):

```python
await service.open(*, kind, origin: Origin, opener: Party, subject=None, payload=None,
                   evidence=None, dedup_key=None, flags=(), member=None, discord=None) -> Case
```

- `Origin(source, submission_id, locale="en", server=None)`; `source` is `game`, `discord` or
  `forum`. `submission_id` (1–64 characters) is the idempotency key: opening again with the same
  id returns the same case and finishes whatever step of its creation failed, so a consumer can
  simply call `open` again on redelivery.
- `dedup_key` — at most one active case per kind and key. A second **report** with the key of an
  open one is merged into it (`co_openers`); for other kinds `CaseRace` is raised.
- `member` — the `discord.Member` behind a Discord submission. Required for `source ==
  "discord"`. Whenever it is given it must be the opener and must not hold the blacklist role.
- `discord={"forum_id": ..., "thread_id": ...}` — for a map: the author's existing post. The
  core then adds the card as a reply instead of creating a post.
- `evidence={"chat": [...], "block_summary": {...}, ...}` — `chat` lines are posted under the
  card as code blocks and cleared `CASES_EVIDENCE_RETENTION_DAYS` after the close.

The returned case can still be `creating` when Discord failed; the repair task finishes it.

### Private threads

A private thread in the panel channel belongs to one person, who is added to it and nobody
else but the staff member holding the case. Nobody's thread shows another person's messages
or outcome.

- The opener of a case opened from Discord has `discord.user_thread_id`. A case opened from the
  game or a forum has no opener thread, even when the opener's game account is linked.
- A reporter who joins a report from Discord (`joined["source"] == "discord"`) gets a thread
  of their own in `discord.reporter_threads` (`{discord_id, thread_id, done}`); the opener and
  the other reporters are not in it. The case's `origin` and `opener` do not change. The
  opener reporting again keeps their thread, and a person reporting twice has one.
  `CaseService.owes_thread(case, joined)` says whether one is still missing, and
  `creation_complete(case)` is false until none is.
- `Case.user_threads` maps a Discord id to that person's thread id (opener and reporters);
  `Case.reporters` is the opener and the merged reporters, each person once. A card can link
  each reporter's thread from these.
- Whoever takes the case is added to every thread. A closed case archives and locks them all;
  a reopen opens them again. `store.by_thread` finds the case from any of them. A deleted
  thread is forgotten and made again by the next creation pass.
- A message in a thread is announced on the post only when its author owns that thread.

Stored private-thread ids are checked during creation/repair and terminal delivery, including
after an offline deletion without a gateway event. Closing creates any owed reporter threads
before it marks outcomes complete. A missing thread invalidates only that owner's thread
receipt; successful DM and notice receipts survive. One channel or recipient failure does not
block other deliveries, and failed closing work remains pending after 24 hours.

Explicit staff invitations are managed in `discord.managed_memberships` (thread id, staff ids,
completion marker). Intent is persisted before adding a member. Release, Reopen, reassignment,
role loss and newly detected conflicts remove obsolete managed invitations through
`Thread.remove_user`; an owner is preserved even if an old record also lists them as staff.
Membership lookup failure removes managed staff rather than inviting an unverified assignee.
This does not remove read access granted by Discord Administrator or Manage Threads: those
permissions must be restricted on the parent channel (especially for complaints).

## Card buttons

The core adds Take / Release (unless `takeable=False`) and Reject. Reject opens a form: a
required reason text, or with `reject_reasons` a select of the codes plus optional details; the
code is stored in `resolution.reason_code` (`other` without reasons). `take`, `release`,
`reject`, `reopen` are reserved verbs.

```python
Action(
    verb,
    label_key,
    handler,
    style=discord.ButtonStyle.secondary,
    fields=(),
    visible=None,
)
```

- `verb` — a lower-case letter, then letters, digits or `_` (32 at most); it is part of the
  button id
  `xc:1:case:<verb>:<number>`. Which role may press it comes from `access.roles_for(settings,
  kind, verb)`: complaint verbs are senior-only, badge verbs need a general admin, map verbs a
  map reviewer, everything else an admin. The conflict-of-interest rules (design spec 4.6)
  apply to every verb.
- `fields` — the form the button opens first: a tuple of up to five `FormField`, or
  `(case, locale) -> fields` when the form depends on the case. The builder runs before the
  press is acknowledged: no I/O in it.
  `FormField(name, label_key, required=True, paragraph=False, max_length=1000, default=None,
  choices=())`; `choices=(Choice(value, label_key), ...)` makes it a select.
- `visible(case) -> bool` — hide the button for some cases; a hidden verb is also refused.
- `handler(context: ActionContext) -> Case | str | None` — called after the press (or the form)
  is acknowledged and after flag, role, conflict and state were checked against the stored case.
  Return the closed `Case` (the person sees "Case #n updated"), a ready localised `str` (shown as
  is; for read-only buttons such as History), or `None` after answering through the context.

`ActionContext`:

| Member | Meaning |
|---|---|
| `service`, `case`, `member`, `locale`, `verb` | the stored case as just read; `locale` is the staff member's |
| `values: dict[str, str]` | the submitted form, stripped and checked against `fields`, plus the answers of earlier steps |
| `actor` | `staff_actions.Actor` of the member |
| `interaction` | the acknowledged interaction (`followup` only) |
| `await run(action) -> Case` | the action pipeline, see below |
| `await reply(text, *, view=None)` | an ephemeral answer of your own |
| `await confirm(text, *, name="confirmed")` | Confirm / Cancel; on Confirm the handler runs again with `values[name] == "yes"` |
| `await choose(text, name, options)` | a select of `discord.SelectOption`; the handler runs again with `values[name]` |
| `await choose_user(text, name)` | a member select; sets `values[name]` (id) and `values[name + "_name"]` |

After `confirm` / `choose` / `choose_user` return `None`. Each step runs the whole handler again
from the start with the collected values, and every run is authorised afresh.
Core choice views use the localized default placeholder. `ConfirmView` accepts an `ActionStep`,
binds its requester and always uses the danger style; `ChoiceView` also supports a callable
with an explicit `requester_id`.

### The action pipeline

```python
await context.run(action)  # in a handler
await service.execute_action(number, verb, member, action)  # the same, elsewhere
# action: async (case: Case) -> Resolution
```

`action` performs the side effect — `staff_actions.perform_*(service.bot, ..., actor=context.actor,
case_number=case.number, case_action_token=case.pending_action["token"])` — and returns the `Resolution` (`code` must be one of
`model.RESOLUTIONS[kind]`; add `audit_id`, `ban_id`, `duration_ms`, `server`, `file_name` when
known). The pipeline:

1. authorises against the stored case; refuses a closed case or one with an action in progress
   (`CaseRace`, shown as "already handled by ...");
2. takes the Redis claim `case-act:<number>:<verb>:<revision>` and stores a guard on the case,
   so a double press or a different button pressed by someone else runs nothing;
3. calls `action`;
4. closes the case with the resolution (the actor becomes the assignee of an untaken case);
5. runs `on_closed`, sends the `outcomes`, re-renders, archives and locks.

If `action` raises without matching completion evidence, the case stays open: the guard is
removed, an `error` event is recorded and the exception reaches the person as a text
(`modals.ACTION_ERROR_KEYS` by exception class, `ActionFailed(text)` for your own). This does
not prove that the external mutation did not happen. When the audit trail holds a durable
completion for this exact attempt, the core closes from that record without replaying the
mutation. The repair task does the same for a process that died in step 3 or 4.

Pass `case_number=claimed.number` and
`case_action_token=claimed.pending_action["token"]` to every `perform_*` callback. The generated
token is stored in both the pending guard and the `action_started` event. Recovery matches the
token, verb, audit action/kind and expected target: subject UUID, appeal ban ID, canonical badge
ID, or map filename and reviewed hash. Mongo filters by token and cutoff before sorting and
limiting. After a guard is released, the latest token-bearing start after the last reopen is
used; event order handles same-millisecond reopens. Earlier attempt tokens never complete a
later attempt. Older untokened records retain conservative recovery with kind/verb/target and
creation/reopen cutoffs; a legacy pending guard also requires its verb and start cutoff.

Case badge grants audit acknowledged changes before inventory lookup/publish. An unchanged
grant must verify canonical membership from a fresh player read, then audits `changed="false"`;
a false modification result alone is not completion. Slash badge commands keep their existing
behavior and add no case audits.

A map case uploads exactly one eligible `.msav` file (checked before RPC). Use the optional
`request_id` and `reviewed_sha256=claimed.payload["sha256"]` alongside the case/token arguments.
The reviewed hash identifies the immutable attachment B8 reviewed; it is not a server-confirmed
hash. Upload that same attachment. After response validation against the requested server and
ordered filenames, only `installed` writes a `NOTE` with `kind="map_uploaded"` and returns a
completion `audit_id`. Return `Resolution("accepted", audit_id=result.audit_id, server=server,
file_name=result.results[0].fileName)` only for that status. All other statuses write a
nonterminal `map_upload_result` note and must raise localized `ActionFailed` rather than accept.
`metadata_failed` means the map file may be installed but attribution failed; it remains open,
as does `superseded`. Legacy slash uploads remain multifile and add no case audits. Map Maker
grants belong in the post-close/reconcile hook and cannot prove map acceptance.

B8 persists `payload.upload_attempt` before sending the upload, initially with `state="unknown"`,
the claimed token and requested server; `payload.server` also binds recovery to that server.
Confirmed noninstalled results set `state="result"` and `payload.last_upload`; an installed
result sets `state="installed"` and `pending_badge` before closing. Recovery also changes an
unknown marker to installed from the accepted resolution. No unknown upload is replayed:
Accept, Re-check and file replacement refuse to overwrite its reviewed attachment/hash. Staff
can Reject an unresolved case; automatic acceptance requires its matching completion audit.
`pending_badge` is cleared only for a complete Map Maker result, with retries through 30 days
after durable acceptance. Badge failures do not prevent map outcome delivery.

Map authors conflict with every case action, including senior actions. `CaseService.started_at`
is the process timestamp; successful map startup stores `service.maps_enabled_at`, the actual
intake boundary. Untracked posts older than that boundary are ignored until `/map review`.
The maps flow owns `case_map_intake` (`_id` = thread id, author id, first intake timestamp) so a
missing-file prompt and later author upload remain eligible after restart. No old posts are
walked automatically. Map status tags are found by name at projection time. Discord's five-tag
limit reserves one slot for status, keeping the first four other tags when all five are full.
First map creation sends its card directly. A retry searches oldest-first after the starter
with no history limit, stopping at its own footer; a lost card-id write therefore adopts the
original card even after more than 200 later messages. Other forum adoption is unchanged.

The recovery boundary is the durable completion audit. Mutation/install → audit failure,
process death or lost acknowledgement remains uncertain; no intention, missing ban, false badge
modification result or filename existence can prove this attempt completed. Keep the case open
and require staff inspection before deciding on another action. No helper is automatically
retried. A hard-killed guard becomes available after ten minutes for a manual decision; that is
not an exactly-once guarantee. A map timeout may follow a remote installation and must not
trigger an automatic reupload.

After audit persistence, recovery can finish logical closure without repeating a sanction or
upload. This provides no external delivery guarantee: Redis enqueue does not acknowledge game
handling, and missed pardon/kick/inventory/log delivery is not repaired by reordering. Any
separate delivery repair must read current DB state; never send stale inventory or pardon a
newer replacement ban. Discord sends can duplicate across send/checkpoint crashes. Close hooks,
outcomes and archive checkpoints remain resumable, not a distributed transaction.

Without a staff action: `service.resolve(number, member, Resolution(code), verb=...)`. For a
close the bot observed elsewhere (a ban issued in game), use
`service.close_validated(case, resolution)` — no role check, the caller is the bot;
`Resolution` carries any known actor attribution.

When a flow has validated an observed sanction's subject and filing/reopen cutoff against a
particular revision, use `service.close_validated(case, resolution)`. It performs one CAS
against that revision and the absence of a pending action. A race leaves the case for retry;
the next pass must repeat all subject/time checks. Unknown event timestamps need a matching
fresh audit, or the report stays open.

## `CaseService`

Attributes: `store`, `settings`, `registry`, `threads`, `notifier`, `claims` (the Redis bus:
`claim_idempotency(key, ttl_seconds)`), `player_store` (`MongoStore`), `punishments`
(`PunishmentStore`: bans and mutes), `actions` (`CaseActions`: the action guard, the close it
ends in and recovery from the audit trail; `cases/actions.py`), `transport`, `bot`, `now()`.

| Method | Use |
|---|---|
| `get(number) -> Case` | the stored case; `CaseNotFound` |
| `open(...)`, `merge(number, *, opener, origin, payload=None)` | see Opening a case |
| `take(number, member)`, `release(number, member)` | the core's buttons |
| `reject(number, member, *, reason_code, reason_text=None)` | close as rejected |
| `resolve(number, member, resolution, *, verb="resolve")` | close without a staff action |
| `execute_action(number, verb, member, action)` | the pipeline |
| `close_validated(case, resolution)` | close observed by the bot at the validated revision; no reload/retry |
| `reopen(number, member)`, `set_subject(number, member, subject)` | senior commands |
| `cancel(number, *, reason)` | a case that cannot proceed (the author deleted the map post) |
| `checkpoint(number, *, set_fields=None, unset_fields=(), event_type=None, actor=None, data=None)` | any write that is not a decision: `payload.*`, `evidence.*`, `flags`; appends an event when `event_type` is given; retried on a lost compare-and-set |
| `project(number)` | re-render after a `checkpoint` that changes what the card shows |
| `authorize(case, member, verb)` | flag, role and conflict; raises `FlowDisabled` / `AccessDenied` |
| `dispatch_action(number, verb, member, locale, values=None, *, interaction=None)` | what a button press runs; useful in tests |
| `resume_creation(number)`, `finish_close(number)` | repair entry points, idempotent |
| `owes_thread(case, joined)`, `creation_complete(case)` | whether a merged reporter's thread, or any creation step, is still missing |

Every state change is a compare-and-set on `revision` with one appended event. Errors derive
from `model.CaseError` (a `ValueError`) and carry `.key`, the locale id of their message.

Report authorization also fetches the actor's current linked game UUIDs and compares them
with the opener, co-openers and subject, on every authoritative step. `precheck` remains an
I/O-free check of the stored snapshot. Resolving an unknown report subject claims
`report:<uuid>` in the same guarded checkpoint as the subject; an active-key collision refuses
the action before mutation. Case presentation redacts every known party UUID (including opaque
Mindustry identifiers); sanction logs also redact known player/actor UUIDs and IPs. Raw records
and completion audits retain their original target, nickname and reason.

Game-report admission uses the existing `case_report_limits` reporter row and a small
submission decision (`decision:<submission_id>`, TTL = notice retention). The reporter row's
revision CAS writes the decision witness together with cooldown ownership: that is the
linearization point. Before replacing a witness, its immutable submission decision must be
acknowledged. A failed or lost acknowledgement may mean the write happened; retry promotes
the retained witness before evaluating another submission. Refusal notices and case creation
are effects after this decision, so window expiry cannot change a durable denial to admission.
No external mutation is inferred absent from a database exception. The global burst count
still assumes the documented single bot/sequential game consumer, without workers or a
distributed transaction. Submission decisions have the same bounded retention as notices.

## `CaseStore`

`get(number)`, `by_submission(submission_id)`, `by_dedup(kind, dedup_key)`, `by_thread(thread_id)`
(the post, the opener's thread or a reporter's thread), `list_cases(query, *, limit)`, `count_open(discord_id, kind)`,
`latest_submission_at(discord_id, kind)`, `audits_for(case, *, action_token=None, since=None)`, `insert_notice(row) -> bool`,
`cas(case, *, set_fields=None, unset_fields=(), event=None, push_fields=None, action_token=None)`
(prefer `service.checkpoint`), `peek(number)` (the cached copy of an active case, no I/O), and the
language preference: `get_locale_pref(discord_id)`, `set_locale_pref(discord_id, locale, *,
source)`, `interaction_locale_now(interaction)` (no I/O), `remember_interaction(interaction)`
(writes the `auto` row when it changed; never over a `manual` one). `store.database` is the
Motor database for a flow's own collections.

## Outcomes

```python
Outcome(recipient: Party, kind: str, message_key: str, params: dict = {},
        player_language: str | None = None, server: str | None = None, user_thread: bool = False)
```

`kind` is the `case_notices` kind the game renders (design spec 5.2). `message_key` is the Discord text; it
receives `number` and `params`. For each outcome the core writes the notice row
(`<number>:<uuid>:<kind>`, idempotent), wakes a game server, posts in the recipient's own
thread when `user_thread=True` and they have one, and sends a DM — each once, recorded on the
case. `player_language` and `server` are what was known at the submission; what the player
record says now comes first:

- The server woken is the one the player is on now, then `server`, then `origin.server`. With
  none the notice waits for the player's next join.
- The DM language is the stored preference (a manual choice, or the client language the bot
  last saw), then the language the player plays in now, then `player_language`, then — for the
  opener — the language the case was opened in, then English. A value that is not a supported
  language (the in-game `auto`) is skipped.

Outside a close: `await service.notifier.send(service, case, outcome)`.

## Cards

`card_builder(case, locale)` gets `CASES_STAFF_LOCALE` (for a map, the author's language). Start
from `cards.base_card(case, locale, reasons=your_reject_reasons)` and add fields. Helpers:
`party_text(party, locale)`, `safe_text(value, secrets=())`, `add_sanctions(embed, case, locale)`
(reads `evidence.sanctions`). Show a player as name and ID only. The core passes every card
through `sanitize_card`, which removes the uuids of the parties, anything shaped like a uuid or
an IP address, and trims to Discord's limits — a safety net, not a licence to put them there.

## Locale strings

Every text a person sees goes through `t(locale, key, **args)`. Add the key to the flow's `.ftl`
file in all three locales with the same arguments; `tests/test_i18n_catalog.py` fails otherwise.
Shared labels (`cases-button-ban`, `cases-reason`, `cases-note`, ...) are in `core.ftl`.

## Transport

`transport.py` holds the interface and its no-op default; nothing in it needs protocol 0.15.

```python
class CaseTransport(Protocol):
    async def publish_case_notice(self, *, notice_id: str, player_uuid: str, server: str) -> None
    async def rpc_case_evidence(self, *, server: str, player_uuid: str, chat_lines: int = 30,
                                window_seconds: int = 600) -> dict | None
    async def rpc_inspect_map(self, *, url: str, file_name: str, profile: str | None = None,
                              server: str | None = None) -> dict | None
```

The RPC methods return the response payload, raise the existing RPC errors, and return `None`
when no transport is installed (`NoCaseTransport`). The transport package installs the real one
where the container is built: `container.cases.set_transport(transport)`. Outcome wake-ups call
`service.transport.publish_case_notice` directly, so they always use the current adapter. Its stream consumers hand events to the flows with
`await service.registry.dispatch_event("cases.report.submitted", service, payload)`, which does
nothing for a flow that is off and lets a handler's exception propagate for retry or
dead-lettering. Flows call `service.transport.rpc_*` and must cope with `None`.

## A complete minimal flow

`tests/test_cases_contracts.py` runs this module as written.

```python
"""Reports from the panel, resolved by a mute."""

from __future__ import annotations

import discord

from ...i18n import t
from ...staff_actions import InvalidDuration, perform_mute
from ...utils.duration import parse_duration
from ..cards import base_card, safe_text
from ..modals import OpenCaseModal
from ..model import ActionFailed, Case, CaseKind, Party, Resolution
from ..notify import Outcome
from ..registry import (
    Action,
    Choice,
    Flow,
    FormField,
    PanelContext,
    PanelEntry,
    flow_registry,
)
from ..views import ActionContext

REASONS = (
    Choice("no_violation", "reports-reason-no-violation"),
    Choice("no_evidence", "reports-reason-no-evidence"),
)
OPEN_FIELDS = (
    FormField("player", "reports-form-player", max_length=10),
    FormField("statement", "reports-form-statement", paragraph=True),
)


def card(case: Case, locale: str) -> discord.Embed:
    embed = base_card(case, locale, reasons=REASONS)
    embed.add_field(
        name=t(locale, "reports-statement"),
        value=safe_text(case.payload["statement"]),
        inline=False,
    )
    return embed


async def submit(context: PanelContext, values: dict[str, str]) -> Case:
    player = None
    if values["player"].isdigit():
        player = await context.service.player_store.find_player_by_pid(
            int(values["player"])
        )
    if player is None:
        raise ActionFailed(t(context.locale, "reports-player-unknown"))
    user = context.interaction.user
    return await context.open(
        opener=Party(discord_id=str(user.id), discord_name=user.display_name),
        subject=Party(
            player_uuid=player.uuid, player_pid=player.pid, player_name=player.nickname
        ),
        payload={"statement": values["statement"]},
        dedup_key=f"report:{player.uuid}",
    )


def open_form(context: PanelContext) -> discord.ui.Modal:
    return OpenCaseModal(
        context,
        title_key="cases-kind-report",
        fields=OPEN_FIELDS,
        submit=submit,
        files=True,
    )


def mute_form(case: Case, locale: str) -> tuple[FormField, ...]:
    return (
        FormField("duration", "reports-form-duration", default="1d", max_length=10),
        FormField(
            "reason",
            "cases-reason",
            default=case.payload["statement"][:200],
            max_length=200,
        ),
    )


async def mute(context: ActionContext) -> Case | None:
    try:
        duration = parse_duration(context.values["duration"])
    except ValueError as error:
        raise InvalidDuration(str(error)) from error
    if context.values.get("confirmed") != "yes":
        name = context.case.subject.player_name
        await context.confirm(t(context.locale, "reports-mute-confirm", name=name))
        return None

    async def staff_action(case: Case) -> Resolution:
        service = context.service
        player = await service.player_store.find_player_by_uuid(
            case.subject.player_uuid
        )
        result = await perform_mute(
            service.bot,
            player,
            duration,
            context.values["reason"],
            context.actor,
            case.number,
            case_action_token=case.pending_action["token"],
        )
        return Resolution(
            "muted",
            audit_id=result.audit_id,
            duration_ms=int(duration.total_seconds() * 1000),
        )

    return await context.run(staff_action)


def outcomes(case: Case) -> list[Outcome]:
    rejected = case.resolution.code == "rejected"
    return [
        Outcome(
            case.opener,
            "report_rejected" if rejected else "report_resolved",
            "reports-outcome-rejected" if rejected else "reports-outcome-resolved",
            user_thread=True,
        )
    ]


flow_registry.register(
    Flow(
        kind=CaseKind.REPORT,
        card_builder=card,
        panel=PanelEntry("cases-kind-report", emoji="📣", modal_factory=open_form),
        actions=(
            Action(
                "mute",
                "cases-button-mute",
                mute,
                style=discord.ButtonStyle.primary,
                fields=mute_form,
            ),
        ),
        reject_reasons=REASONS,
        outcomes=outcomes,
    )
)
```

## Complaint privacy and subject changes (B9)

A complaint requires a senior role distinct from the ordinary admin role. Its senior forum
must exclude every non-senior role except the bot's own roles. The private-thread parent must
allow ordinary members to view and write in their invited threads, deny view to configured
non-senior staff roles, allow seniors to view, and grant Manage Threads only to seniors and
the bot's own roles. Use an otherwise empty dedicated parent; ordinary members can see that
parent, so do not post complaint content there. Startup refuses an unsafe layout and logs
only configuration diagnostics. Member-specific overwrites, combined member roles, guild
ownership and subsequent permission changes still require an operator's guild check.

`Flow.on_subject_set(service, case)` is an optional, idempotent hook after the stored subject
changes, including through `/cases subject`, and before projection. A failed hook logs a safe
diagnostic; flows that use it must provide their own reconciliation retry. Complaints refresh
subject moderation evidence immediately and retry on every open-case repair pass. A subject
selection stores a pending-history marker and a selection revision in the same CAS. The
history writer validates both identity and selection revision on each CAS retry, including
A → B → A changes. Failed latest history remains discoverable by repair after the case closes;
neutral outcomes proceed independently of evidence lookup failures. History matches all
complainant target accounts by stable Discord actor id OR the selected subject's linked game
UUIDs, counts each matching row once, and displays the latest ten plus the total. Names alone
are never evidence of actor identity; records with neither stable identity remain omitted.

For complaints, `set_subject` atomically clears an assignee matching the subject, returns a
taken case to open, and removes that subject from `progress.user_thread_staff`. Projection
removes the subject's explicit membership from every bot-managed user thread before rendering.
Subject changes also attempt membership removal before any flow evidence lookup.
Subject CAS, privacy cleanup and invitation projection share the per-case projection lock;
repair uses `CaseService.exclude_subject(number)` to reload the current subject under that
lock. This prevents delayed removal for an old subject from ejecting a now-eligible holder.
These locks coordinate this service instance; Mongo CAS and final reads do not constitute an
atomic transaction with Discord or across distributed bot instances. Welcome/staff invitations
and `CaseNotifier.send` suppress complaint subjects; the notifier checks the current stored
case before creating game notices, thread messages or DMs. Other kinds retain their behavior.
Presentation uses `cards.case_secrets(case)` to collect party/account UUIDs and available
audit-actor UUIDs. Complaint titles, stale-action replies, party labels in private-message
announcements, cards, evidence and staff follow-ups redact these known secrets plus ordinary
UUID/IP patterns. Raw submitted text, party snapshots and moderation audit records remain
internal storage. The secret collection must never be logged.

Long sanitized statements are per-post evidence: content deduplication is scoped to the
actual post, and `progress.complaint_statement_post` records that post id. Both event-driven
post deletion and offline missing-post repair restore the full bounded chunks in the replacement
senior post. Other flows' creation hooks are not reset.

Complaint errors use `_privacy.log_failure`: diagnostic context and exception class only,
without the exception message or traceback. Production logging must retain the application's
INFO level: discord.py HTTP/gateway DEBUG logs include request/event payloads and must not be
enabled for sensitive channels. Gateway hooks without a known kind also use this
safe logging path. `/cases find`, `reopen` and `subject` route unexpected callback failures
through the actual bot app-command handler. That boundary reads the stored target kind and
fails private when the read fails; cached or unrelated kinds cannot enable traceback logging.
For complaint/unknown cases it sends only a generic response and fixed error-reference
diagnostics, excluding exception arguments, tracebacks, command values and actor names from
ordinary logs and the configured error channel. Known other kinds retain ordinary diagnostics.

When integrating the separate B5 thread/membership repairs, preserve current-subject exclusion
before every invitation (even for a thread owner or senior), current recipient suppression,
and cleanup before history/link lookup. B5 owns former-assignee reconciliation and missing-user-
thread delivery recovery. Apply the shared core hunks narrowly alongside B8 map projection;
keep the optional hook's default `None` and other-flow creation side effects unchanged.

Discord cannot hide one forum post from a member with access to its forum. A subject who is
already a senior, has Manage Threads/Administrator or owns the guild can retain preexisting
read access. Removing an explicit private-thread invitation cannot revoke those permissions.
Bot action exclusion and notification suppression are enforced regardless of senior status;
read privacy against a privileged subject must be handled by guild administrators. Raw
complaint text, staff notes and attachment copies belong only in the senior case post. The
opener gets neutral outcome text. Files are opaque uploads and are not content-sanitized.
