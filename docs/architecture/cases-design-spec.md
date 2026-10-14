# Technical Design Specification: Cases (reports, appeals, badge requests, staff complaints, map review)

Status: proposal. Section 12 lists the decisions; the ones marked **[owner]** need the owner's confirmation, everything else is a recommended default an implementer may rely on.
Affects: `xcore-protocol` (0.15.0), `XCore-plugin`, `TileLogger`, `XCore-discord-bot`.

Paths are relative to the workspace root (`XCore-plugin/…`, `XCore-discord-bot/…`, `xcore-protocol/…`, `TileLogger/…`). Inside a repository section the common prefix is dropped: plugin paths are relative to `XCore-plugin/src/main/java/org/xcore/plugin/`, bot paths to `XCore-discord-bot/src/xcore_discord_bot/`.

## 1. Goal

Replace the third-party ticket bot (Ticketeer) with "cases" owned by the network's own bot and plugin:

- **Reports** on players, opened in game (`/report`) or from a Discord panel, handled by staff on a card with Take / Ban / Mute / History / Reject; the reporter learns the outcome in game and by DM.
- **Appeals** bound to one specific ban, decided on a card with Unban / Shorten / Reject by someone other than the admin who issued the ban.
- **Badge requests** with Grant / Reject, plus automatic Map Maker on an accepted map.
- **Complaints about staff**, visible to senior staff only.
- **Map review** in the existing maps forum: automatic validation of the attached `.msav`, tags `Waiting` / `Accepted` / `Rejected`, Accept installs the map on a chosen server.

Scale: 5–28 reports and 1–12 appeals per month in 2026; about 1240 tickets in total so far (72% reports, 21% appeals, 7% staff complaints). The design is sized for tens of cases per month, one bot instance, no queues or workers beyond what exists.

### 1.1 Non-goals

- No general-purpose ticket system: no free-form "other" tickets, no transcripts, no SLA timers, no staff statistics.
- No ids for mutes. Mutes stay keyed by player; a case references a mute through its audit record.
- No appeals of mutes or of vanilla (`netServer.admins`) bans — those have no record to bind to (see 2).
- No in-game staff UI for cases. Staff work in Discord; in game there is only `/report` and outcome notices.
- No web panel, no rewriting of existing `/ban`, `/mute`, `/badge`, `/map upload` behaviour beyond what section 8.2 extracts.
- No deletion of anything in Discord. Closed cases are locked and archived.

### 1.2 Glossary

| Term | Meaning |
|---|---|
| Case | One report, appeal, badge request, complaint or map submission. Has a number, a kind, a status, one Discord post. |
| Case number | Network-wide integer, shown as `#57`. One sequence for all kinds. |
| Ban id | Integer identity of one ban, shown as `B-1234`. New in this spec. |
| pid | Existing public player id (`players.pid`, `XCore-plugin/…/model/PlayerData.java:29`). The only player identifier shown to users. |
| Opener | Who opened the case: reporter, appellant, requester, complainant, map author. |
| Subject | Who the case is about: reported player, banned player, badge recipient, staff member, (none for maps). |
| Card | The bot's message with the case summary and buttons; the starter message of the case's forum post. |
| Staff forum | New staff-only Discord forum channel holding report, appeal and badge posts. |
| Senior forum | New forum channel visible to senior staff only; holds complaints. |
| User thread | Private thread in `#tickets` where a Discord-origin opener talks to staff and attaches files. |
| Panel | The public multilingual message in `#tickets` with the "open a case" buttons. |
| Notice | A short outcome message for a player, stored in MongoDB until delivered in game. |
| Flow | One of the five case kinds with its handlers; each has its own on/off switch. |

## 2. What the current codebase gets in the way of

The first four rows are places where the brief for this feature did not match the code; the design follows the code.

| Problem | Where |
|---|---|
| **Brief: "ban screen keys `ban-content` / `tempban-content` must show a ban number".** `ban-content` is only shown for vanilla `netServer.admins` bans, which have no database record; every `bans` row is rendered with `tempban-content`. Only `tempban-content` can carry a ban number; `ban-content` can only gain the pid | `XCore-plugin/…/security/ingress/checks/BanCheck.java:51-108`, `XCore-plugin/src/main/resources/bundles/bundle_en.ftl:276,281` |
| **Brief: "block-history summary from TileLogger".** TileLogger depends on XCore-plugin, not the other way round (`compileOnly(libs.xcore.plugin)`, `"dependencies": ["xcore-plugin"]`), and exposes only chat commands. History is native, per map, and is reset on every map load. XCore-plugin cannot call it; an SPI in XCore-plugin is required | `TileLogger/build.gradle.kts:44`, `TileLogger/plugin.json`, `TileLogger/src/main/java/tilelogger/TileLogger.java:32-39`, `TileLogger/…/service/TileLoggerService.java:118,129` |
| **Brief: Tower Defense guidelines "road floor `metal-tiles-2`, border walls `metal-wall-2`, empty vanilla waves".** The TD plugin does not hard-code a road floor: roads are whatever floor lies under a spawn overlay; `metal-wall-2` borders are a legacy fallback used only when no spawn-marked roads exist; it ignores vanilla wave rules entirely. Vanilla replaces an *empty* spawn list with the default waves on load, so "empty waves" is not a meaningful map property | `Tower-defence-plugin/src/main/java/core/RoadPathfinder.java:73`, `Tower-defence-plugin/src/main/java/core/Main.java:90-104`, `Mindustry/core/src/mindustry/io/SaveVersion.java:198`, `Mindustry/core/src/mindustry/maps/Map.java:110` |
| **Brief: "existing permission system" of the bot.** It is three role helpers (admin, general/head admin, map reviewer) plus an optional roles-mode binding file; there is no per-action permission registry | `XCore-discord-bot/…/permissions.py:42-85`, `…/cogs/checks.py:16-67`, `…/settings.py:16,27-35` |
| Ban records have no identity: one replaceable row per `uuid OR ip`, written with `replaceOne(..., upsert)` by both the plugin and the bot, deleted on unban and on expiry | `XCore-plugin/…/database/repository/BanDataRepository.java:66-108`, `XCore-discord-bot/…/mongo_store.py:390-422,1079` |
| The plugin's `BanData` has no `pid`; only the bot writes it. The `moderation.ban.created` event carries the target IP and a null pid | `XCore-plugin/…/model/BanData.java`, `…/service/network/ModerationProtocolMapper.java:37`, `XCore-discord-bot/…/mongo_store.py:91` |
| The plugin deletes a ban by uuid only, the bot by `uuid OR ip` | `XCore-plugin/…/service/moderation/ModerationService.java:292-297`, `XCore-discord-bot/…/mongo_store.py:416` |
| Every Discord view in the bot is time-limited (30–180 s); nothing is persistent: no `add_view`, no `custom_id`, no `on_thread_create`, no forum or thread code | `XCore-discord-bot/…/moderation_views.py:49,166,231,293,336`, `…/bot.py:917-944` |
| Unban, mute, badge grant and map upload are bound to a slash-command interaction; only the ban has an interaction-free core (`perform_ban`) | `XCore-discord-bot/…/handlers_moderation.py:249,310,402`, `…/handlers_badges.py:43`, `…/handlers_misc.py:657` |
| The bot has no localisation; `flubundle` and `fluent` are Java-only and cannot be reused from Python | `XCore-discord-bot/pyproject.toml`, `flubundle/`, `fluent/` |
| Protocol decoders are strict (`additionalProperties: false`): an old reader rejects a message that carries a new optional field, and the bot sends such events to the DLQ after 5 attempts | `xcore-protocol/docs/policies/compatibility.md`, `XCore-discord-bot/…/redis_bus.py:112,481-568` |
| The plugin drops transport messages past `expires_at`; the bot does not check expiry on events | `XCore-plugin/…/service/network/RedisNetworkBackend.java:560`, `XCore-discord-bot/…/redis_bus.py:481-568` |
| Chat is not buffered anywhere; public chat and team chat take two different paths | `XCore-plugin/…/event/net/chat/ChatMessageHandler.java:39-64`, `…/service/TranslatorService.java:154` |
| There is no record of players who recently left, so a griefer who disconnects cannot be picked in a menu | `XCore-plugin/…/event/handler/ConnectionHandler.java:243` |
| The plugin working tree has an uncommitted change `support-channel = #tickets` in all nine bundles; bundle work must start from it, not overwrite it | `XCore-plugin/src/main/resources/bundles/bundle_en.ftl:312` |

Conclusion: three foundations come before any flow — ban identity, interaction-free staff actions in the bot, and a persistent-view + localisation layer in the bot.

## 3. Architecture

### 3.1 Ownership

The seasonal rating spec fixed "only the plugin writes season state". Cases use the mirror rule, because the state that matters here (posts, cards, threads, who pressed what) lives in Discord and the bot is a single instance:

**Only the bot writes the `cases` collection. The plugin never reads or writes it.**

The plugin's part is narrow: collect a report with its evidence and hand it to the bot; show notices; show ban identity; inspect map files on request.

| Data | Writer | Reader |
|---|---|---|
| `cases` | bot | bot |
| `case_notices` | bot inserts; plugin sets `delivered_at` / `delivered_server` only | plugin, bot |
| `counters{_id:"case_number"}` | bot | — |
| `counters{_id:"ban_id"}` | plugin and bot (`$inc`, atomic) | — |
| `bans` (+ `ban_id`, `pid`, `case_number`) | plugin and bot, as today | plugin, bot |
| `mutes` (+ `case_number`) | plugin and bot, as today | plugin, bot |
| `moderation_audit` (+ `details.extra.ban_id`, `details.extra.case`) | plugin and bot, as today | plugin, bot |
| `discord_user_prefs`, `case_panels` | bot | bot |

### 3.2 Processes and channels

```
  game server (XCore-plugin + TileLogger)                 Discord bot
  ───────────────────────────────────────                 ───────────
  /report menu ─ ReportService ── cases.report.submitted ──▶ reports flow ─▶ staff forum post + card
                                   (event, Redis stream)                      │ buttons (persistent)
  CaseNoticeService ◀── cases.notice.command (wake-up) ──── notify ◀──────────┘
        ▲                                                     │
        └────────────── MongoDB case_notices ◀────────────────┘ + DM
  CaseEvidenceService ◀── cases.evidence.request/response ── reports flow (Discord-origin, refresh)
  MapInspectionService ◀── maps.inspect.request/response ─── maps flow
  BanCheck (ban screen: B-n, pid) ── MongoDB bans ◀────────── appeals flow (unban / shorten)
```

### 3.3 Where the code goes

```
XCore-plugin  org.xcore.plugin
├── cases/                         new: reports, evidence, notices (7.1)
├── integration/blockhistory/      new SPI implemented by TileLogger (7.4)
├── event/transport/CasesTransportHandler            new
├── service/network/CasesProtocolMapper              new
├── service/map/MapInspectionService (+ inspect/)    new (7.9)
├── ui/menu/ReportMenu, ReportUiController           new
├── command/controller/client/ReportController       new
└── database/migration/V7__AssignBanIds              new

XCore-discord-bot  xcore_discord_bot
├── i18n/                          new: Fluent loader, locale resolution, command translator (8.6)
├── cases/                         new: model, store, service, cards, views, panel, threads, notify (8.1)
│   └── flows/{reports,appeals,badges,complaints,maps}.py
├── staff_actions.py               new: interaction-free unban, mute, shorten, badge, map upload (8.2)
└── cogs/cases.py                  new: /cases …, /language

TileLogger  tilelogger.integration.XcoreBlockHistoryProvider   new
xcore-protocol  spec/messages/cases/, spec/routes/cases.routes.v1.yaml, maps.inspect.*   new
```

## 4. Domain model

### 4.1 Kinds and resolutions

| Kind (`kind`) | Opened from | Opener | Subject | Lives in | Terminal resolutions (`resolution.code`) |
|---|---|---|---|---|---|
| `report` | game, panel | reporter(s) | reported player | staff forum | `banned`, `muted`, `rejected` |
| `appeal` | panel | appellant | banned player | staff forum | `unbanned`, `shortened`, `rejected` |
| `badge` | panel | requester | recipient player | staff forum | `granted`, `rejected` |
| `complaint` | panel | complainant | staff member | senior forum | `upheld`, `rejected` |
| `map` | post in the maps forum | map author | — | the author's own post in the maps forum | `accepted`, `rejected` |

Reject always carries a reason code and optional free text. Reason codes for reports: `no_violation`, `no_evidence`, `already_handled`, `duplicate`, `abuse` (the report itself was abusive). The reporter-facing text depends on the code and never names the staff member.

### 4.2 States and transitions

```
            ┌──────────┐  post created   ┌──────┐  Take    ┌───────┐
  insert ──▶│ creating │────────────────▶│ open │─────────▶│ taken │
            └──────────┘                 └──┬───┘◀─────────└───┬───┘
                                            │     Release      │
                                            ├──────────────────┤
                                            ▼ action           ▼ Reject
                                       ┌──────────┐       ┌──────────┐
                                       │ resolved │       │ rejected │
                                       └────┬─────┘       └────┬─────┘
                                            └── Reopen (senior) ┴──▶ open
            cancelled: opener's post deleted (maps), or creation abandoned by reconcile
```

| From | To | Trigger | Who |
|---|---|---|---|
| — | `creating` | case document inserted, number allocated | bot |
| `creating` | `open` | forum post exists, ids stored | bot |
| `open` | `taken` | Take | role for the kind (8.5) |
| `taken` | `open` | Release | assignee or senior |
| `open`, `taken` | `resolved` | Ban / Mute / Unban / Shorten / Grant / Accept / Resolve | role for the kind; never the conflicted party (4.6) |
| `open`, `taken` | `rejected` | Reject + reason | role for the kind |
| `open`, `taken` | `resolved` | matching in-game ban or mute observed for an open report (9.1 step 12) | bot |
| `resolved`, `rejected` | `open` | `/cases reopen` | senior |
| `creating`, `open` | `cancelled` | map post deleted by its author; creation abandoned after 24 h | bot |

An action does not require a prior Take; acting on an `open` case sets the assignee to the actor. Every transition is a compare-and-set on `revision` (5.1) so two staff pressing at once cannot both win.

### 4.3 Identifiers

| Id | Format | Source | Notes |
|---|---|---|---|
| Case number | integer ≥ 1; shown `#57` | `counters.findOneAndUpdate({_id:"case_number"}, {$inc:{seq:1}}, upsert, after)` — same convention as pids (`XCore-plugin/…/database/repository/PlayerDataRepository.java:126-147`) | Is the `_id` of the case. Gaps are allowed. |
| Ban id | integer ≥ 1; shown `B-1234` | `counters{_id:"ban_id"}`, same operation, from either process | A re-ban is a new id. A shortened ban keeps its id. Parsers accept `1234`, `#1234`, `B-1234`, `b1234`. |
| Submission id | string ≤ 64 | plugin: random UUID per report; bot: `discord:<interaction id>`; maps: `map:<thread id>` | Idempotency key of case creation (unique index). |
| Notice id | `"<case number>:<player uuid>:<kind>"` | bot | Natural idempotency: re-inserting the same notice is a no-op. |
| Thread title | `#57 · Report · <subject name>` (≤ 100 chars, colour codes stripped) | bot | Map posts keep the author's title. |

The prefixes keep the three public numbers apart: players are `pid 567`, cases `#57`, bans `B-1234`.

### 4.4 Parties

A party is a snapshot plus stable references:

```
party
  player_uuid    String    stable in-game reference; stored, never displayed
  player_pid     Int       displayed
  player_name    String    snapshot at the time of the case
  discord_id     String    stable Discord reference; null when unknown
  discord_name   String    snapshot
```

A report can have several reporters: a second report against the same subject while a case is open is merged into it (5.1 `dedup_key`). Each reporter still gets the outcome.

### 4.5 Evidence

Captured once, at creation, and stored as a snapshot on the case:

- `chat` — up to 30 recent chat lines from the server (7.3). Public and team chat only; never private messages or commands.
- `block_summary` — counts from TileLogger for the last 10 minutes on the current map (7.4), or absent.
- `sanctions` — counts and last entries of BAN / MUTE / KICK / WARN from `moderation_audit` for the subject, read by the bot (`XCore-discord-bot/…/mongo_store.py:466`), plus the current ban and mute (`…/handlers_misc.py:170-180`).
- `attachments` — files the opener sent with a Discord form, re-uploaded by the bot into the case post (message ids, not CDN URLs: those expire).
- `context` — server, map name, map file, game mode, whether the subject was online, `same_network` flag.

What the card never shows: IP addresses, uuids, country, client data. The only network-derived fact is the boolean "reporter and reported player share a network", computed on the server.

### 4.6 Conflict of interest

| Kind | Conflicted party | Rule |
|---|---|---|
| `appeal` | the admin who issued the ban (`bans.admin_discord_id`; if empty, any linked account of the clicking user whose name equals `bans.admin_name`) | Take, Unban, Shorten and Reject are **blocked** for that user, senior or not. If the issuer cannot be identified (console ban, unlinked admin) the card carries the flag `issuer_unknown` and nobody is blocked. |
| `complaint` | the staff member the complaint is about, once seniors have set the subject | All actions blocked for that user. |
| `report` | a staff member who is the reporter or the subject | Blocked from acting on that case. |

The check runs on the button press and again on the modal submit.

### 4.7 Audit trail

Two layers, both existing conventions:

1. `cases.events[]` — append-only list on the case: who did what, when (`created`, `merged_reporter`, `taken`, `released`, `action`, `rejected`, `reopened`, `evidence_refreshed`, `notified`, `thread_deleted`, `error`). This is the case history.
2. `moderation_audit` — every executed sanction keeps going through the existing audit writer (`XCore-discord-bot/…/mongo_store.py:593-673`, `…/handlers_moderation.py:169-207`) with additive string entries in `details.extra` (`XCore-plugin/…/model/AuditDetails.java:29`): `case` (case number), `ban_id` where applicable, and `case_action_token` for a case action attempt. Case callbacks pass the token from the claimed case's `pending_action`; it correlates a confirmed completion record with that attempt. That makes "which case caused this ban" and "everything that happened to ban B-n" one indexed query each.

## 5. Data (MongoDB)

All collections live in the existing shared database.

### 5.1 `cases` — written by the bot only

```
cases
  _id                Int       case number
  kind               String    report | appeal | badge | complaint | map
  status             String    creating | open | taken | resolved | rejected | cancelled
  active             Boolean   true while status ∈ {creating, open, taken}; the field is REMOVED on close
  dedup_key          String    report:<subject uuid> | appeal:<ban id> | badge:<player uuid>:<badge> | map:<thread id>; absent for complaints
  revision           Int       starts at 1; every write is {$inc: {revision: 1}} guarded by the previous value
  origin
    source           String    game | discord | forum
    submission_id    String
    server           String    game origin only
    locale           String    opener's locale at submission (en | ru | uk)
  opener             party     first opener
  co_openers         [party + joined_at]    merged duplicate reports
  subject            party     may be partial (name only) until staff resolve it
  payload            kind-specific, see below
  evidence
    captured_at      Date
    context          { server, map_name, map_file, game_mode, subject_online, same_network }
    chat             [ { sent_at, name, pid, role, text, team } ]
    block_summary    { window_seconds, built, destroyed, configured, truncated,
                       top_blocks[{block, built, destroyed}], area{x1,y1,x2,y2}, last_action_seconds_ago }
    sanctions        { bans, mutes, kicks, warns, last[{action, at, reason, actor_name}] }
    attachments      [ { message_id, file_name, size } ]
  discord
    forum_id         String    parent forum channel
    thread_id        String    the case post
    card_message_id  String
    user_thread_id   String    private thread with the opener; absent for game-origin cases
  assignee           { discord_id, name, taken_at } | null
  resolution         { code, reason_code, reason_text, by_discord_id, by_name, at,
                       ban_id, audit_id, duration_ms, server, file_name } | null
  flags              [String]  same_network | subject_offline | subject_unresolved | subject_is_staff |
                               issuer_unknown | unverified_identity | no_ban_record | deep_checks_pending
  events             [ { at, type, actor{type, discord_id, name}, data{} } ]
  created_at, updated_at, closed_at    Date
```

Kind-specific `payload`:

| Kind | Fields |
|---|---|
| `report` | `reason` (code), `comment`, `raw_subject` (text typed in the Discord form) |
| `appeal` | `ban_id`, `ban_snapshot{reason, admin_name, admin_discord_id, issued_at, expire_date}`, `statement`, `lookup` (`link` \| `ban_id` \| `pid`) |
| `badge` | `badge` (id from `XCore-discord-bot/…/badges.py`), `statement` |
| `complaint` | `raw_subject`, `statement`, `when` |
| `map` | `file_name`, `sha256`, `size`, `profile`, `info{…}`, `findings[{code, severity, detail, x, y, count}]`, `checked_at`, `pending_badge` |

Indexes:

- `{origin.submission_id: 1}` unique — creation idempotency.
- `{kind: 1, dedup_key: 1}` unique, partial `{active: true, dedup_key: {$type: "string"}}` — at most one open report per subject, one open appeal per ban, one open request per badge and player, one case per map post. A duplicate-key error on insert is the "merge into the existing case" signal.
- `{kind: 1, status: 1, updated_at: -1}` — lists and the reconcile scan.
- `{subject.player_uuid: 1, created_at: -1}`, `{opener.player_uuid: 1, created_at: -1}`, `{opener.discord_id: 1, created_at: -1}` — history lookups and per-opener caps.
- `{discord.thread_id: 1}` unique sparse, `{discord.user_thread_id: 1}` sparse — routing Discord events to a case.

The partial index uses a boolean because `partialFilterExpression` support for `$in` depends on the server version; `active` is removed with `$unset` on close.

Retention: cases are kept indefinitely. `evidence.chat` is cleared 180 days after `closed_at` by the reconcile task (8.8) **[owner]**.

### 5.2 `case_notices` — bot inserts, plugin marks delivery

```
case_notices
  _id               String    "<case number>:<player uuid>:<kind>"
  case_number       Int
  player_uuid       String
  kind              String    report_received | report_resolved | report_rejected | report_not_accepted |
                              appeal_accepted | appeal_shortened | badge_granted | badge_rejected |
                              map_accepted | map_rejected | complaint_closed
  params            { String: String }    e.g. number, badge, map, server — only what the bundle text needs;
                                          report_rejected also carries reason (4.1 rejection code, see 7.7)
  created_at        Date
  expires_at        Date      created_at + 30 days
  delivered_at      Date      null until shown
  delivered_server  String
  claim_token       String    plugin-owned, unique per delivery attempt
  claim_server      String    plugin-owned, server holding the delivery lease
  claim_until       Date      plugin-owned, lease expiry (120 seconds)
```

Indexes: `{player_uuid: 1, delivered_at: 1}`; TTL on `expires_at` (`expireAfterSeconds: 0`).

The plugin claims an undelivered, unexpired notice atomically only when its existing lease is absent or expired, storing a unique `claim_token`, `claim_server` and `claim_until`. After a successful server-side send, a token-guarded acknowledgement sets `delivered_at` and `delivered_server` and removes the lease fields. A token-guarded release removes only the current attempt's lease; an old owner cannot release or acknowledge a successor's claim. The text is rendered by the plugin from bundle key `case-notice-<kind with dashes>` in the player's language — the bot never sends in-game text.

This prevents concurrent delivery while a lease is valid and retries after a crash before sending. A crash or persistence failure after sending but before acknowledgement can repeat the notice; acknowledgement records server-side send success, not client receipt. Server clocks must be reasonably synchronized. The bot writes the notice content and expiry; it does not manipulate plugin lease fields.

### 5.3 `bans`, `mutes`, `moderation_audit` — additive fields

```
bans  (existing)
  + ban_id        Int      allocated on every new ban by whichever process writes it
  + pid           Int      already written by the bot (mongo_store.py:91); now also by the plugin
  + case_number   Int      set when the ban was issued from a case card

mutes  (existing)
  + case_number   Int

moderation_audit  (existing)
  + details.extra.ban_id   String   on BAN, UNBAN, and the NOTE written when a ban is shortened
  + details.extra.case     String   on every action executed from a case card
```

Indexes: `{ban_id: 1}` unique, partial `{ban_id: {$type: "int"}}` on `bans`; `{details.extra.ban_id: 1}` sparse on `moderation_audit` (created next to the existing ones, `XCore-plugin/…/database/repository/AuditRecordRepository.java:39-57`).

Migration `V7__AssignBanIds` (the next free version after `V6`, `XCore-plugin/…/database/migration/`): for every `bans` row without `ban_id`, in `_id` order, allocate an id from the counter and `$set` it with the filter `{_id, ban_id: {$exists: false}}`; back-fill `pid` from `players` by uuid; create the two indexes. It allocates through the same `$inc` as live writers, so it is safe to run while a newer bot is already issuing ids.

Self-healing. A server that still runs the old plugin replaces the whole document on ban and so writes rows without `ban_id`. Any reader that needs the id and finds none allocates one with the same guarded `$set` (plugin: `BanCheck`; bot: appeal lookup and ban log). This also covers rows written between migration and full rollout. Unknown fields are harmless to old readers: the bot has been writing `pid` into `bans` while the plugin's `BanData` has no such field.

Shortening a ban is `updateOne({ban_id}, {$set: {expire_date}})` — the id survives. `BanCheck` reads the row on every join (`BanCheck.java:51`), so no cache needs invalidating.

### 5.4 Small bot collections

```
discord_user_prefs
  _id            String    Discord user id
  locale         String    en | ru | uk
  source         String    auto (last interaction.locale) | manual (chosen in the language switch)
  updated_at     Date

case_panels
  _id            String    "main"
  channel_id     String
  message_id     String
  rendered_hash  String    hash of flags + texts; the panel is edited in place when it changes
```

Short-lived rate-limit and idempotency keys stay in Redis through the existing `claim_idempotency` (`XCore-discord-bot/…/redis_bus.py:881`).

## 6. Protocol (`xcore-protocol` 0.15.0)

Base: branch `fix/map-attribution-review` (0.14.0), which is one commit ahead of `main` and must be merged first. Everything below is additive, hence a minor version. A new family `cases` is added (`spec/messages/cases/`, `spec/routes/cases.routes.v1.yaml`, one `FamilyConfig` entry in `generators/families.py`, generated `org.xcore.protocol.generated.messages.cases.CasesMessages` and `xcore_protocol.generated.cases`).

### 6.1 New shared types (`spec/shared/types/`)

`case-chat-line.v1.json` — `CaseChatLineV1`

| Field | Type | Req. | Notes |
|---|---|---|---|
| `sentAt` | integer (int64, epoch ms) | yes | |
| `playerName` | string, 1–64 | yes | plain name, colour codes stripped |
| `playerPid` | signed integer | no | zero and negative PIDs are assigned IDs; only `PlayerPids.NONE` is omitted |
| `role` | enum `target` \| `reporter` \| `other` | yes | relative to the report |
| `text` | string, ≤ 256 | yes | |
| `team` | boolean | no | true for team chat |

`case-block-summary.v1.json` — `CaseBlockSummaryV1`

| Field | Type | Req. | Notes |
|---|---|---|---|
| `windowSeconds` | integer ≥ 1 | yes | |
| `built`, `destroyed`, `configured` | integer ≥ 0 | yes | action counts in the window |
| `truncated` | boolean | yes | true when the history read hit its cap |
| `topBlocks` | array ≤ 8 of `{block: string, built: integer, destroyed: integer}` | yes | by total actions |
| `area` | `{x1, y1, x2, y2}` integers | no | bounding box of the actions |
| `lastActionSecondsAgo` | integer ≥ 0 | no | |

`map-inspect-info.v1.json` — `MapInspectInfoV1`: `name`, `author`, `description` (strings), `width`, `height`, `saveVersion`, `build` (integers), `gameMode` (string), `profile` (string, the profile actually applied), `spawnCount` (integer), `coresByTeam` (object: team name → integer), `waves`, `waveTimer` (booleans), `spawnGroupCount` (integer), `dropZoneRadius` (number), `mods` (array of strings), `sha256` (string, 64 hex), `sizeBytes` (integer). Required: `name`, `width`, `height`, `sha256`, `sizeBytes`.

`map-inspect-finding.v1.json` — `MapInspectFindingV1`: `code` (string, `^[a-z0-9]+(-[a-z0-9]+)*$`, open set — new checks must not break old readers), `severity` (enum `error` \| `warning` \| `info`), `detail` (string ≤ 300, optional), `x`, `y` (integers, optional), `count` (integer, optional). Required: `code`, `severity`.

### 6.2 New messages

| Message | Content |
|---|---|
| `cases.report.submitted` v1 | `submissionId` (string 1–64, req.), `server` (req.), `reporter` (`PlayerRefV1`, req., `ip` must be omitted), `target` (`PlayerRefV1`, req., `ip` omitted), `targetOnline` (boolean, req.), `reason` (string `^[a-z][a-z0-9-]{0,31}$`, req.; known values `griefing`, `toxicity`, `cheating`, `nsfw`, `evasion`, `other`), `comment` (string ≤ 300), `reporterLocale` (string ≤ 16), `mapName`, `mapFileName`, `gameMode` (strings), `sameNetwork` (boolean), `chat` (array ≤ 30 of `CaseChatLineV1`, req., may be empty), `blockSummary` (`CaseBlockSummaryV1`), `occurredAt` (date-time, req.) |
| `cases.notice.command` v1 | `server` (req.), `noticeId` (string, req.), `playerUuid` (string, req.), `requestedAt` (date-time). A wake-up only: the content is in `case_notices` |
| `maps.inspect.request` v1 | `server` (req.), `file` (`MapFileSourceV1`: `url`, `fileName`, req.), `profile` (string, optional; absent → inferred from the map's rules) |
| `maps.inspect.response` v1 | `server` (req.), `status` (enum `ok` \| `download_failed` \| `invalid_map`, req.), `detail` (string), `info` (`MapInspectInfoV1`), `findings` (array of `MapInspectFindingV1`, req., may be empty) |

| RPC | Purpose |
|---|---|
| `cases.evidence.request` v1 → `cases.evidence.response` v1 | Request: `server` (req.), `playerUuid` (req.), `chatLines` (integer 1–30), `windowSeconds` (integer 60–1800). Response: `server`, `targetOnline` (boolean, req.), `mapName`, `gameMode`, `chat` (array of `CaseChatLineV1`, req.), `blockSummary` (`CaseBlockSummaryV1`), `capturedAt` (date-time, req.). Used for Discord-origin reports and the "refresh evidence" action |
| `maps.inspect.request` → `maps.inspect.response` | Detached validation of one `.msav` by any live server (7.9) |

`reason` and finding `code` are open strings on purpose: with strict decoders a closed enum would turn every new reason into a breaking change.

### 6.3 Changed messages

| Message | Change |
|---|---|
| `moderation.ban.created` v1 | new optional `banId` (integer ≥ 1). Sender also starts filling `target.playerPid` |

Nothing else changes. Bans and mutes issued from a card are executed by the bot itself, which writes MongoDB and posts its own log (`handlers_moderation.py:249-308`), so no case reference has to travel through the protocol.

### 6.4 Routes (`spec/routes/cases.routes.v1.yaml`, additions to `maps.routes.v1.yaml`)

In the terms of the existing manifests (`xcore-protocol/spec/routes/moderation.routes.v1.yaml`, `maps.routes.v1.yaml`):

```yaml
- messageType: cases.report.submitted
  messageVersion: 1
  kind: event
  stream: xcore:evt:cases:report
  targetScope: broadcast
  ttlMs: 604800000            # 7 days: a report must survive a bot outage
  replayable: true
  idempotentConsumerRecommended: true
  owner: cases

- messageType: cases.notice.command
  messageVersion: 1
  kind: command
  stream: xcore:cmd:cases-notice:{server}
  bindings: {server: payload.server}
  targetScope: server
  ttlMs: 120000
  replayable: false
  owner: cases

- messageType: cases.evidence.request
  messageVersion: 1
  kind: rpc-request
  stream: xcore:rpc:req:{server}
  bindings: {server: payload.server}
  targetScope: server
  ttlMs: 15000
  response:
    messageType: cases.evidence.response
    messageVersion: 1
    stream: xcore:rpc:resp:{requester}
    bindings: {requester: rpc.requester}
  owner: cases

# maps.routes.v1.yaml
- messageType: maps.inspect.request
  messageVersion: 1
  kind: rpc-request
  stream: xcore:rpc:req:{server}
  bindings: {server: payload.server}
  targetScope: server
  ttlMs: 60000
  response:
    messageType: maps.inspect.response
    messageVersion: 1
    stream: xcore:rpc:resp:{requester}
    bindings: {requester: rpc.requester}
  owner: maps
```

Bot consumer group for the report stream: `xcore:cg:discord-case-report` (same naming as `discord-ban`, `XCore-discord-bot/…/redis_bus.py:293-301`).

### 6.5 Why these shapes

- **Report = event, not RPC.** The server must not depend on the bot being up. The plugin publishes with `postAwait` (`XCore-plugin/…/service/NetworkService.java:81`), which confirms the write to the stream; the bot consumes when it is alive. The bot does not enforce `expires_at` on events, and the plugin does not consume this stream, so a report waits in Redis through a bot restart. Delivery is at-least-once; the bot deduplicates on `submissionId`.
- **Acknowledgement and outcome = MongoDB notice + wake-up command.** The reporter may be offline, on another server, or the server may restart. The durable part is the `case_notices` row; `cases.notice.command` only makes delivery immediate for a player who is online. A lost wake-up costs nothing: the notice is shown on the next join.
- **Evidence RPC is optional.** It fails soft: no live server, timeout, or player offline produce a card without live evidence.

### 6.6 Compatibility and rollout order

Old strict decoders reject `moderation.ban.created` with `banId`, and old bots have no consumer for the new stream. Order:

1. Merge `fix/map-attribution-review`, release `xcore-protocol` **v0.15.0** (annotated tag publishes Maven and PyPI, `xcore-protocol/docs/releasing.md`).
2. Deploy the **bot** built against 0.15.0 with every `CASES_*_ENABLED=false`. It now accepts `banId` and writes `ban_id` on its own bans.
3. Deploy **XCore-plugin** to the servers one by one. `V7` runs once under the migration lock. From this moment servers emit `banId`; servers still on the old plugin keep working (their bans get ids lazily, 5.3).
4. Deploy **TileLogger** with the provider (a server without it simply sends reports without a block summary).
5. Discord-side setup (8.9), publish the panel, switch flows on one at a time.

Rollback: switch the flow flags off. The new fields and collections are additive; an old bot must not be redeployed while servers emit `banId` (it would dead-letter ban events) — roll the servers back first.

For work before the tag exists use `XCore-plugin/gradle/local-protocol.init.gradle` and the sibling generated Python sources, as for 0.14.0 (`XCore-plugin/docs/architecture/map-discord-attribution.md`).

## 7. Plugin design (`XCore-plugin`, `TileLogger`)

### 7.1 Packages

```
org.xcore.plugin.cases
├── ReportReason              enum GRIEFING, TOXICITY, CHEATING, NSFW, EVASION, OTHER; wire code = lower-case name
├── ReportDraft               record: reporter uuid, target snapshot, reason, comment
├── ReportResult              sealed: Sent | Cooldown(seconds) | SameTarget | DailyLimit | InvalidTarget | InsufficientPlaytime(remainingMinutes) | Disabled | Failed
├── ReportService             validate → rate-limit → capture evidence (game thread) → publish (worker)
├── ReportRateLimiter         in-memory sliding windows (7.6)
├── RecentChatBuffer          ring buffer of recent chat lines (7.3)
├── RecentPlayers             players who left in the last N minutes (uuid, pid, name, ip, leftAt)
├── CaseEvidenceService       chat excerpt + block summary for one player; used by reports and the evidence RPC
├── CaseNotice                POJO for case_notices
├── CaseNoticeRepository      findPending(uuid, limit), claim(id, server), release(id)
└── CaseNoticeService         delivery on join and on wake-up (7.7)

org.xcore.plugin.integration.blockhistory
├── BlockHistoryProvider      SPI: Optional<BlockHistorySummary> summarize(String playerUuid, int windowSeconds)
├── BlockHistoryRegistry      register(provider) → Registration (AutoCloseable)
└── BlockHistorySummary       record mirroring CaseBlockSummaryV1

org.xcore.plugin.event.transport.CasesTransportHandler     notice command, evidence RPC
org.xcore.plugin.service.network.CasesProtocolMapper       domain ↔ generated messages
org.xcore.plugin.service.map.MapInspectionService          detached .msav validation (7.9)
org.xcore.plugin.service.map.inspect.*                     MapCheckProfile, MapCheck implementations, MapSnapshot
org.xcore.plugin.ui.menu.ReportMenu, ReportUiController    the /report menu (7.10)
org.xcore.plugin.command.controller.client.ReportController
```

All services are Avaje `@Singleton` beans. `CasesTransportHandler` is registered where the other handlers are (`event/TransportService.java:134-141`) and follows `RatingTransportHandler` for RPC (server filter, `REJECTED` / `FAILED` through `NetworkService.respondError`, `service/NetworkService.java:142`) and `ModerationTransportHandler` for marshalling onto the game thread.

### 7.2 Configuration

Shared across servers, in `secrets.toml` (`config/TomlSecretsConfig.java`, a new `cases` section next to `rating`, `:25,189`; map checks under a new `maps` section):

```toml
[cases.reports]
enabled = false
cooldownSeconds = 60            # per reporter
sameTargetCooldownMinutes = 30  # same reporter, same target
dailyLimit = 10                 # per reporter, rolling 24 h
networkBurstLimit = 6           # per address, all accounts together, per 10 minutes
minPlayTimeMinutes = 0          # 0 = anyone may report
chatLines = 30
chatWindowMinutes = 10
blockWindowSeconds = 600
recentLeaverMinutes = 10

[cases.notices]
maxPerJoin = 5

[maps.checks]
maxFileBytes = 4194304
maxTiles = 360000
severities = {}                 # check id -> "error" | "warning" | "info", e.g. { "td-road-floor-mixed" = "warning" }

[maps.checks.profiles.tower-defense]
roadFloor = "metal-tiles-2"     # empty = any single floor type
borderWall = "metal-wall-2"     # empty = border check off
borderTolerance = 0.05
requireDropZoneRadiusZero = true
minWidth = 0                    # inclusive bounds in tiles, 0 = no bound; any profile table may carry them,
maxWidth = 0                    # e.g. [maps.checks.profiles.pvp]
minHeight = 0
maxHeight = 0
```

As implemented (P0): the TOML loader maps keys in snake_case and silently ignores unknown ones, so the real keys are the snake_case forms of the names above (`cooldown_seconds`, `same_target_cooldown_minutes`, `max_per_join`, `max_file_bytes`, `road_floor`, …), as the `rating` section does. The Java fields keep the camelCase names: `secrets.cases.reports.cooldownSeconds`, `secrets.cases.notices.maxPerJoin`, `secrets.maps.checks.maxTiles`, `secrets.maps.checks.profiles.get("tower-defense").roadFloor`.

Map check policy (P4 corrections). `severities` and the four bounds default to "nothing configured", which is exactly the behaviour of the table in 7.9 and the global `max_tiles`. The check ids are the sixteen codes of that table. Bounds belong to a profile id (`pvp`, `survival`, `attack`, `sandbox`, `editor`, `tower-defense`); the road, border and drop-zone keys act only under `tower-defense`, whose defaults stay in force when the file defines another profile's table only. An invalid value never stops start-up: when `secrets.toml` is loaded it is repaired and reported once per value as a `[Config] maps.checks: …` warning in the server log — an unknown check id or an unknown severity is ignored (the default severity applies), a negative bound becomes `0`, and a maximum below its minimum is raised to that minimum. A value of the wrong TOML type is a load error, as for every other key. `InspectionSettings` captures the repaired values into immutable records on the game thread for each request.

Server-local kill switch: `"reports"` in `runtime.disabledFeatures` (`config/TomlXcoreConfig.java:264`, checked the way `service/MapService.java:500` does). `/report` is available when the shared flag is on and the local switch is not set.

### 7.3 Recent chat buffer

- Two hooks, because public and team chat are separate paths: `event/net/chat/ChatMessageHandler.java:39` (after the mute check at `:57`, before the transport post at `:64`) and `service/TranslatorService.java:154` (`/t`, marked `team = true`).
- A ring of 200 lines; lines older than 15 minutes are discarded on write. Each line: time, uuid, pid, plain name, text (≤ 256), team flag.
- Touched only on the game thread. Messages blocked by the mute check, private messages and commands never reach it.
- Excerpt for a report: lines inside `chatWindowMinutes`; up to 20 most recent lines by the target, the remaining slots filled with the most recent lines by anyone else; sorted by time; at most `chatLines`. Each line gets `role` = `target`, `reporter` or `other`. Uuids stay on the server.

### 7.4 Block history SPI and the TileLogger provider

XCore-plugin defines the SPI; TileLogger implements it. The registry copies `integration/profile/ProfileSectionRegistry.java:21` (register returns an `AutoCloseable` registration; at most one provider, last wins). `summarize` is called on the game thread only.

TileLogger (`tilelogger.integration.XcoreBlockHistoryProvider`), registered in `TileLoggerPlugin.init()` next to the existing registrations and closed in `disable()`:

- reads `TileLogger.getHistory((short) 0, (short) 0, (short) -1, (short) -1, uuid, -1, -windowSeconds, CAP)` — whole map, all teams, one player, the same call shape as `TileLoggerService.showHistory` (`TileLogger/…/service/TileLoggerService.java:129`); a negative `time` is relative to now (`TileLogger/native/src/main/headers/TileLogger.h:93-96`); `CAP` = 4096 entries, `truncated = true` when reached;
- folds the `TileState` entries into counts: `destroy` → destroyed, otherwise built; block name from the content registry by `block` id; bounding box from `x`, `y`; `time` is a signed short holding unsigned seconds — read it with `& 0xffff`. Whether a configuration-only action is distinguishable through `config_type` must be confirmed against the native header in the work package; if not, `configured` is 0;
- history is per map: after a map change the summary is simply empty for the old map. An absent provider, a native failure or a missing library yield `Optional.empty()` and the report is sent without a block summary.

### 7.5 Report submission

The implemented menu API is `ReportService.enabled()`, `reasons()`, `targets(Session)` and
`CompletionStage<ReportResult> submit(Session, ReportDraft)`. Target listing and submission
require the game thread; normal result completion returns to it. `ReportTarget` contains
`uuid`, nullable signed `pid`, plain `name`, `online`, and epoch-ms `leftAt` (zero online).
`ReportDraft` contains reporter UUID, target snapshot, reason and comment. UUIDs stay in
server-side menu state; submit re-resolves the target instead of trusting the snapshot.

`InsufficientPlaytime(remainingMinutes)` is an additive result introduced during P2 review,
so a playtime refusal is not shown as an invalid target. Render it with
`report-result-playtime` and argument `minutes`. The new key exists in en, ru and uk_UA.

1. The menu (or `/report <target>`) calls `ReportService.submit(session, draft)` on the game thread.
2. Checks, in order: flow enabled; target ≠ reporter; target is not staff (holder of `PermissionNodes.MODERATION_BAN`, `permission/PermissionNodes.java:41`); `minPlayTimeMinutes`; rate limiter. A refusal returns a typed `ReportResult` so the menu can show the remaining seconds.
3. Still on the game thread: snapshot of the context (server, map name and file, game mode, whether the target is online, `sameNetwork` = reporter address equals the target's current or last address), the chat excerpt, the block summary.
4. On a worker: `network.postAwait(CasesReportSubmittedV1)` (`service/NetworkService.java:81`; it blocks and throws when the write is not confirmed).
5. Back on the game thread through `Async.thenMain`: success → "sent" feedback; failure → the rate-limit slot is returned and the menu shows "could not send, try again".

The plugin never talks to MongoDB for a report. The case number arrives seconds later as a `report_received` notice (7.7).

### 7.6 Rate limiting and spam

The plugin limiter is the fast, local feedback; the bot is the authority (8.8) because a player can hop between servers.

| Window | Key | Default | Why |
|---|---|---|---|
| Cooldown | reporter uuid | 60 s | brief: about one report a minute |
| Same target | reporter uuid + target uuid | 30 min | one report per incident; duplicates would be merged anyway |
| Daily | reporter uuid | 10 / 24 h | serial reporters |
| Network burst | reporter address | 6 / 10 min | account rotation from one machine |

Shared NAT: every limit that can refuse an honest player is keyed by uuid, so several players behind one address each get their own allowance. The address bucket is deliberately loose and only stops rotation of throw-away accounts. Addresses are compared in memory and never leave the server; the report carries a boolean.

### 7.7 Notices in game

- **On join.** `ConnectionHandler.prepareJoin` already runs off the game thread and loads the unread-message count; it additionally loads up to `maxPerJoin` pending notices without claiming them. After session registration, `completeJoin` starts delivery: claim on a worker, then show on the game thread next to the private-message notification. Notice load failures do not prevent login.
- **On wake-up.** `CasesTransportHandler` receives `cases.notice.command`; if the player is online on this server it loads that one notice on a worker and shows it.
- **Delivery across servers.** A unique-token lease claims an undelivered notice on a worker. The game-thread callback checks the same active session, connection liveness and lease expiry before sending. If the player is gone, `release` frees that attempt's lease; after sending, a worker acknowledges delivery. A notice claimed by another server is skipped. Expired claims recover on a later join or wake-up; a crash after send before acknowledgement may cause repeat delivery (5.2).
- **Feature gate.** The implementation delivers notices on servers with `cases.reports.enabled = true` and without the local `reports` kill switch. This includes notices for appeals, badges, maps and staff complaints. Disabled servers remain inert.
- **Form.** One chat line per notice through the session's localisation (`locale.send`, as at `:206`), key `case-notice-<kind>`, args from `params`. Example: `Report #57 reviewed: action was taken. Thank you.`
- **Arguments.** Only `number`, `badge` and `map` are passed to the bundle; no other value from `params` reaches chat. For `report_rejected` the plugin additionally passes `reason`, and only as one of the 4.1 rejection codes `no_violation`, `no_evidence`, `already_handled`, `duplicate`, `abuse`; any other or missing value is replaced by the constant `other`. `case-notice-report-rejected` is a Fluent selector on `$reason` with a generic default branch (`Report #57 reviewed: no action was taken. Thank you.`), in every bundle that defines the key (en, ru, uk_UA). The wording never names staff and never mentions a sanction; the `abuse` branch tells the reporter that the report itself broke the rules.

### 7.8 Ban identity and the ban screen

- `model/BanData.java` gains `ban_id` (Integer), `pid` (Integer), `case_number` (Integer).
- `BanDataRepository` gains `nextBanId()` (the counter), `assignBanIdIfMissing(BanData)` (guarded `$set`, 5.3), `findByBanId(int)`.
- `ModerationService.executeBan` (`service/moderation/ModerationService.java:240-290`) allocates the id before `banDataRepository.save` (`:256`), stores the pid it already receives, and writes `ban_id` into the BAN audit's `details.extra`. `unbanPlayer` (`:292-304`) reads the row first and copies its `ban_id` into the UNBAN audit.
- `ModerationProtocolMapper.toBanCreated` (`service/network/ModerationProtocolMapper.java:35-44`) sends `banId` and the pid.
- `BanCheck` (`security/ingress/checks/BanCheck.java`) runs on the ingress executor, not the game thread, so it may do the guarded write and one `players` lookup:
  - `tempban-content` (`:71-78`) gets two new arguments: `banId` (already formatted, `B-1234`) and `pid`;
  - `ban-content` (`:99-108`, vanilla bans) gets `pid` only;
  - an unknown pid (address ban of someone who never joined) is passed as `-`.
- Bundles: `tempban-content` gains one line with the ban number and the player's ID, and its appeal sentence tells the player to quote the ban number in `{ support-channel }`; `ban-content` gains the ID line. Both in all nine locales — these are existing keys, so untranslated variables would otherwise never appear.

### 7.9 Map inspection

`MapTransportHandler` gains a responder for `maps.inspect.request` beside the upload path (`event/transport/MapTransportHandler.java:215-270`). The work never touches the running world:

1. Capture immutable content descriptors, aliases, team names, rule defaults and inspection settings on the game thread. The RPC worker waits at most five seconds for this capture. Download on the worker to a temporary file (one 30 s deadline, `maxFileBytes`), hash the compressed bytes with SHA-256, and delete the file afterwards. Download failure → `status: download_failed`.
2. Under the shared `DetachedMapReadLock` used by preview and inspection, decode with the independent, bounded `DetachedMsavReader`. The originally proposed `MapIO.createMap` / `SaveVersion.readMap` path installs a global temporary content mapper and can create buildings or invoke engine callbacks; sharing a worker lock cannot protect game-thread readers from those changes. Inspection therefore uses only the captured value descriptors and never swaps globals or runs engine deserialization. The result is a `MapSnapshot`: floor, overlay and block arrays, core teams and detached rule data. Unreadable or unsupported variants, a truncated or malformed shared building base, and inconsistent building footprints → `status: invalid_map`. More than `maxTiles` → finding `size-out-of-range` without tile allocation; the response explicitly marks the partial inspection and omits unobserved spawn/core counts.
3. The profile is the requested one, else inferred from the rules' game mode. Each `MapCheck` is a pure function `MapSnapshot → findings`.

P4 supports save versions 1–13, with a 64 MiB inflation ceiling. Gameplay patches and embedded/external gameplay content assets are rejected because applying them would mutate live content, while ignoring them could change the findings. Entity and building subclass payloads remain bounded opaque data; this is not full validation of gameplay serialization.

Buildings and cores (P4 corrections; the byte layout is listed in the inspection README):

- **Shared base.** For every block the registry knows as a native building, the part of the chunk that `BuildingComp.readBase` reads (`Mindustry/core/src/mindustry/entities/comp/BuildingComp.java`) — or, for save versions 1–3, the legacy header of `io/versions/LegacySaveVersion.java` — is parsed inside the chunk bounds: base version announced by the rotation high bit, module flags, the item / power / liquid modules, and the version-dependent tail. A building is recorded and a core counted only after the whole base was read; a short or malformed base is `invalid_map`. Values with one sane form are checked more strictly than the engine does (base version above 4, unused module flag bits, boolean bytes other than 0/1, negative counts, non-finite health).
- **Footprints.** The core list is meant to equal the cores that exist after the engine reads the same file. `Tile.setBlock` clears every tile of a new footprint and removes whatever building touched it, so a single later record can erase a whole core. Inconsistent files are rejected, not reconciled: overlapping native building footprints, a later non-part record or run on an earlier footprint, an orphan part record, or a part naming a different block are `invalid_map`. Sizes and the anchor offset come from the registry and `Tile.setBlock` (`core-shard` is 3×3; even sizes extend right/up; footprints are clipped at the map edge).
- **Limit.** The engine does not compare what a building reader consumed with its chunk length (`io/SaveFileReader.java` `readChunk`), and the subclass tail is opaque here. A deliberately crafted file can therefore hide tile records in the tail of a native building chunk and make the engine see fewer cores than the inspection reported. Closing this needs every block's own read format. The inspection result is advice for staff, not proof that the map loads as described; a map should still be loaded on a server once before it enters rotation.

Expected results for these rules are checked against the real v160 `SaveVersion.readMap` in a test-only headless world (`EngineCoreOracleTest`). Of the 114 official v160 fixture maps, 111 decode and three are expected unsupported rejections. Preview's pre-existing engine/global-state behavior remains outside P4; the shared lock does not claim to protect its game-thread readers.

| Code | Profile | Default severity | Computed from |
|---|---|---|---|
| `missing-name`, `missing-author` | all | warning | meta tags |
| `size-out-of-range` | all | error | width × height against profile bounds |
| `requires-mods` | all | error | meta tag `mods` not empty (`Mindustry/core/src/mindustry/io/SaveVersion.java:114-144`) |
| `unknown-content` | all | error | content header names missing from this server's registry |
| `no-core` | all | error | no core of the default team |
| `no-spawn` | survival, tower-defense | error | no `spawn` overlay |
| `pvp-single-team` | pvp | error | cores of fewer than two teams |
| `attack-no-enemy-core` | attack | error | no core of the wave team |
| `td-road-floor-mixed` | tower-defense | error | spawn overlays lie on more than one floor type (the TD plugin treats the floor under a spawn as road, `RoadPathfinder.loadRoadFloors`) |
| `td-road-floor-unexpected` | tower-defense | warning | the road floor differs from the configured `roadFloor` |
| `td-road-disconnected` | tower-defense | error | flood fill over road tiles from a spawn does not reach a tile next to a core |
| `td-spawn-not-at-road-start` | tower-defense | warning | a spawn tile has more than one road neighbour (it is in the middle of a road) |
| `td-border-wall-missing` | tower-defense | warning | share of tiles bordering the road whose block is not `borderWall` exceeds `borderTolerance`; first five coordinates reported |
| `td-drop-zone-radius` | tower-defense | warning | `rules.dropZoneRadius ≠ 0` (`Mindustry/core/src/mindustry/game/Rules.java:151`) |
| `td-waves-enabled` | tower-defense | info | `rules.waves` or `rules.waveTimer` set (`Rules.java:33,37`); informational because the TD plugin ignores vanilla waves |

"Empty vanilla waves" from the guidelines is not checked: an empty spawn list is replaced by the default waves on load (section 2). Severities and the two TD constants are configuration, pending the owner's confirmation of the guideline **[owner]**.

Configuration (7.2) supplies the global file/tile limits, the TD floor, border and radius policy, a severity override per check id (`maps.checks.severities`), and inclusive dimension bounds per profile (`min_width`, `max_width`, `min_height`, `max_height`). A map outside its profile's bounds gets `size-out-of-range` with the violated key in the detail; unlike the `max_tiles` limit this does not make the inspection partial. A severity override changes only the severity of that finding, never whether the check runs. With nothing configured the table above and the global `max_tiles` apply unchanged.

Why the plugin and not the bot: block names, the tile layout and rule defaults are defined by the game version the servers run; the server already has the reader and the content registry. A Python reader would be a second implementation of a format that changes with the game. The bot does only container-level sanity checks (8.1 `msav.py`).

### 7.10 The `/report` menu

Command: `@Command("report [target]")` in `ReportController` (`implements CloudClientController`, like `command/controller/client/SeasonController.java`); no permission node — every player may report. With a `target` (name or `#pid`) the menu opens on screen 2.

The menu is one `UiController<Model, Event>` in the style of `ui/menu/SeasonUiController.java`, opened through `ReportMenu` exactly as `ui/menu/SeasonMenu.java` does: bump `session.nextUiVersion()`, build the model, `menuService.openUi(session, controller, model, true)` (`ui/MenuService.java:249`, `SeasonMenu.java:74`) — **fill-screen**. Rendering goes through `Screen.compact` (`ui/kit/Screen.java:80`): one column on every width class, since all three screens are short lists.

Phone rules that apply to every screen (the menus are tested on a phone in portrait and on PC):

- every label and row gets an explicit width derived from the `Screen` (`NARROW` 450, `SMALL` 330, `Screen.java:31,33`); player names are fitted with `TextWidth`, never left to wrap;
- buttons and rows use the kit heights (`Kit.BUTTON_HEIGHT` 42, `Kit.FIELD_HEIGHT` 44) — large enough for a finger;
- icons only from `Iconc`; no characters outside the game font (`✓`, `→`, `✕` are missing);
- action ids sent to the client carry an index into the model's candidate list, never a uuid.

| Screen | Content | Kit elements |
|---|---|---|
| 1. Who | Header "Report a player". Online players except the reporter and staff, then a "Left recently" group from `RecentPlayers` with "left N min ago". Each row: name, `pid`, team colour. 8 rows per page. Empty state: "Nobody to report" + Close. A one-line hint: complaints about staff go through Discord | `Kit.window` / `Kit.header`, `Kit.row("action:pick:<index>", …)` (`ui/kit/Kit.java:318`, usage `ui/menu/TopUiController.java:625`), `Kit.pager` (`Kit.java:332`), `Kit.note` |
| 2. Why | The picked player as a card. Six reasons, one per row. Optional comment field, ≤ 300 characters; required for "Other". Back / Next | `Kit.card`, `Kit.options(content, width, "reason", options)` (`Kit.java:259`), `content.field("field_comment", …)` with `FIELD_HEIGHT` (pattern `ui/menu/PlayerSettingsUiController.java:714`, value read from `result.values`, `:393`), `Kit.actions` (`Kit.java:271`) |
| 3. Confirm | Summary (player, reason, comment), a note that the last minutes of chat and building history are attached and that false reports are punished. Send / Back | `Kit.card`, `Kit.note`, `Kit.actions` |
| Result | The same window re-rendered with a feedback line: sent, cooldown with seconds left, daily limit, already reported, failed. One Close button | `Kit.feedback` (`Kit.java:154`) |

Events: `action:pick:<i>`, `action:page:<n>`, `action:reason:<code>`, `action:next`, `action:back`, `action:send`, `action:close`. `update` returns `UpdateResult.rerender` between screens and `close` at the end; the async result of `submit` re-renders only if `session.uiVersion()` is unchanged.

### 7.11 Bundle keys

New keys in `en`, `ru`, `uk_UA`; other locales fall back to English (the convention of the seasonal rating spec). Changed existing keys (`ban-content`, `tempban-content`) in all nine.

```
report-menu-title, report-menu-pick-player, report-menu-left-recently, report-menu-left-ago,
report-menu-empty, report-menu-staff-hint, report-menu-pick-reason, report-menu-comment,
report-menu-comment-required, report-menu-confirm-title, report-menu-confirm-note,
report-menu-next, report-menu-back, report-menu-send, report-menu-close,
report-result-sent, report-result-cooldown, report-result-same-target, report-result-daily-limit,
report-result-invalid-target, report-result-playtime($minutes), report-result-disabled, report-result-failed,
report-reason-griefing, report-reason-toxicity, report-reason-cheating, report-reason-nsfw,
report-reason-evasion, report-reason-other,
case-notice-report-received, case-notice-report-resolved, case-notice-report-rejected,
case-notice-report-not-accepted, case-notice-appeal-accepted, case-notice-appeal-shortened,
case-notice-badge-granted, case-notice-badge-rejected, case-notice-map-accepted,
case-notice-map-rejected, case-notice-complaint-closed
```

### 7.12 Tests

- `ReportMenuTest`: every screen × `Screen.NARROW`, `Screen.SMALL`, `Screen.WIDE` × `en`, `ru`, `uk_UA` through `LayoutAssert.assertLaidOut` and `assertFitsPacket` (`src/test/java/org/xcore/plugin/ui/kit/LayoutAssert.java:56,98`, usage `ui/menu/SeasonMenuTest.java:187-214`) with 8 rows of maximum-length names.
- `ReportRateLimiterTest` (each window, shared address with different uuids, slot returned on failure), `RecentChatBufferTest` (window, excerpt selection, roles), `CaseNoticeServiceTest` (claim / release), `BanDataRepository` id allocation and guarded heal against a real MongoDB as the existing repository tests do, `V7` idempotency, mapper round-trips for the new messages, `MapInspectionService` on small fixture maps (one per finding code), `FtlBundleCompilationTest` for the new keys.

## 8. Bot design (`XCore-discord-bot`)

### 8.1 Module layout

```
xcore_discord_bot/
├── i18n/
│   ├── __init__.py        t(locale, key, **args); SUPPORTED = ("en", "ru", "uk"); DEFAULT = "en"
│   ├── loader.py          one FluentBundle per locale from locales/<locale>/*.ftl; missing key → English → the key itself (logged)
│   ├── resolve.py         locale for an interaction / for a Discord user / for a player (8.6)
│   ├── translator.py      discord.app_commands.Translator for command names and descriptions
│   └── locales/{en,ru,uk}/{core,reports,appeals,badges,complaints,maps}.ftl
├── cases/
│   ├── model.py           CaseKind, CaseStatus, Case / Party / Resolution dataclasses, the transition table of 4.2
│   ├── store.py           CaseStore: cases, case_notices, discord_user_prefs, case_panels, the case_number counter
│   ├── ids.py             custom_id encode / decode; parse and format of case numbers and ban ids
│   ├── access.py          role mapping (8.5), blacklist, conflict-of-interest rules (4.6)
│   ├── service.py         CaseService: open / merge / take / release / resolve / reject / reopen — CAS + events
│   ├── registry.py        FlowRegistry: a flow registers its kind, panel entry, card builder, actions, event hooks
│   ├── cards.py           shared embed pieces: header, parties, status line, sanctions block, footer
│   ├── views.py           the two DynamicItem classes and view assembly from the registry (8.3)
│   ├── modals.py          shared modals: reject reason, free-text note
│   ├── panel.py           the public panel message and the language switch
│   ├── threads.py         forum posts, tags, archive / lock, user threads (8.4)
│   ├── notify.py          outcome delivery: case_notices row, wake-up command, DM
│   ├── reconcile.py       periodic repair task (8.8)
│   ├── msav.py            container-level .msav sanity: zlib stream, "MSAV" magic, version, meta string map
│   └── flows/
│       ├── __init__.py    imports every module in the package (pkgutil) so a flow registers itself
│       └── reports.py, appeals.py, badges.py, complaints.py, maps.py
├── staff_actions.py       interaction-free moderation and content actions (8.2)
├── daemons/cases_reconcile_daemon.py
└── cogs/cases.py          /cases panel publish | find | reopen | subject, /language
```

Flows are plug-ins of the cases core: a flow module adds its own file, its own locale file and its own tests and edits nothing shared. This is what makes the five flows independent work packages (section 10). `CaseStore` and `CaseService` are created in `container.py` (`ServiceContainer.create`) and reached as `bot.container.cases`; no new pass-through methods on the bot class are needed.

### 8.2 Interaction-free staff actions (`staff_actions.py`)

A button on a persistent card has no slash-command context, and the same action must be callable from a command and from a card. Each function takes the duck-typed `bot`, plain arguments and an `Actor(discord_id, name)`, returns a result object, raises typed errors, and never touches an interaction:

| Function | Extracted from | Notes |
|---|---|---|
| `perform_ban` (existing) | `handlers_moderation.py:249-308` | gains `case_number`; allocates `ban_id`; audit `extra.case` / `extra.ban_id` |
| `perform_unban(ban, actor, case_number)` | `cmd_unban`, `handlers_moderation.py:310-372` | `delete_ban` + `publish_pardon_player` (`redis_bus.py:750`) + UNBAN audit |
| `perform_shorten_ban(ban_id, new_expiry, actor, case_number)` | new | guarded `$set` of `expire_date` (must be earlier than the current one); audit action `NOTE` with `extra.kind = "ban_shortened"`; log to the bans channel |
| `perform_mute(player, duration, reason, actor, case_number)` | `cmd_mute`, `handlers_moderation.py:402-475` | the 30-second undo view stays in the command wrapper only |
| `perform_badge_grant(player, badge_id, actor, case_number)` | `_cmd_badge_change`, `handlers_badges.py:43-115` | MongoDB grant (`mongo_store.py:695`) + inventory event (`redis_bus.py:793`) |
| `perform_map_upload(server, files, uploader, creator)` | `cmd_upload_map`, `handlers_misc.py:657-760` | builds the `discordUpload` attribution, calls `rpc_upload_maps` (`redis_bus.py:1217`), returns per-file statuses |

The slash commands become thin wrappers around these (behaviour unchanged, existing tests keep passing). Store additions in `mongo_store.py`: `ban_id` / `case_number` on `BanDoc` (`:91`) and `BanRecord` (`dto.py`), `next_ban_id()`, `find_ban_by_id()`, `ensure_ban_id()`, `update_ban_expiry()`, `case_number` on mutes. `post_ban_log` (`handlers_moderation.py:869-918`) adds a "Ban" field (`B-1234`) and, when present, a "Case" field (`#57`); for bans coming from the game the id is taken from the event's `banId` (`runtime_consumers.py:181-205`).

### 8.3 Persistent views and the `custom_id` scheme

The locked dependency is discord.py 2.7.1 (`uv.lock`), which has `discord.ui.DynamicItem`; the declared floor `discord.py>=2.4.0` (`pyproject.toml:11`) is raised to `>=2.7` because modal file uploads (8.4) need it.

```
xc:1:case:<verb>:<case number>          take | release | history | reject | refresh |
                                        ban | mute | unban | shorten | grant | accept | resolve | recheck | subject
xc:1:panel:open:<kind>                  report | appeal | badge | complaint
xc:1:panel:lang
```

- `xc` namespaces the bot, `1` is the scheme version: a later layout can register a second pattern and old messages keep working. Ids stay far below Discord's 100-character limit and carry no state.
- Two classes, `CaseActionButton` and `PanelButton`, each a `DynamicItem[discord.ui.Button]` with a regular-expression template, registered once in `setup_hook` (`bot.py:917`) with `add_dynamic_items`. Nothing is stored per message; after a restart any button on any old card resolves through the pattern.
- The handler always: parses the id → loads the case from MongoDB → checks the flow flag, the role for that verb and the conflict rules → either answers immediately (modal or ephemeral) or defers, then calls `CaseService` → re-renders the card from the stored case. The message is a projection of the database, never the source of state.
- A button that leads to a form (Reject, Ban, Mute, Shorten, Resolve) answers with a modal as its first response, after role checks that need no I/O; the state check is repeated on submit. Modals, the follow-up selects (server for a map, badge to request, language) and confirmations are ordinary short-lived views: they are private to one user for a minute and losing them on restart costs one more click.
- A stale press (case already closed, lost CAS) gets an ephemeral "already handled by …" and a card refresh.
- Executing an action: Redis claim and durable pending guard → `staff_actions.*` (confirmed mutation or upload response, then completion audit with `extra.case` and the attempt token) → CAS the case to its terminal state → notify → re-render, re-tag, archive. Once the completion audit is durable, reconciliation can finish a failed close without repeating the mutation or upload. Recovery checks the attempt, action kind and expected subject; map acceptance additionally requires `installed`, server, file name and the reviewed content identity when recorded.
- Mutation/install and audit are separate writes. A crash or audit failure between them can leave a real effect without durable completion evidence. A missing ban, unchanged badge grant, pending guard or upload timeout alone does not prove that this attempt succeeded. Such an outcome requires staff inspection; reconciliation does not replay the helper. Completion audits also do not acknowledge delivery of Redis commands or Discord messages. The local recovery changes do not provide exactly-once execution or guaranteed follow-up delivery.

### 8.4 Forums, threads and attachments

**Staff forum** (reports, appeals, badges). One post per case, created with `ForumChannel.create_thread(name, embed, view, applied_tags)`; the starter message is the card. Long evidence does not fit an embed (30 chat lines can exceed the 6000-character embed budget), so the chat excerpt and the block summary are posted as follow-up messages in the post, in code blocks split under 1900 characters. Staff discuss in the post.

Tags are resolved by **name** from `forum.available_tags` at use; no tag ids exist in code or settings. For the two bot-owned forums the names are fixed (`Report`, `Appeal`, `Badge`, `Complaint`, `Open`, `Taken`, `Resolved`, `Rejected`) and created by the bot at start-up if missing. For the existing maps forum the three moderated tag names come from settings (8.7).

**Senior forum** (complaints). A separate channel, because a forum cannot hide individual posts from people who can see the forum.

**Closing.** Edit the card (no action buttons, resolution line), swap the status tag, then `thread.edit(archived=True, locked=True)`. Nothing is ever deleted. `/cases reopen` unlocks and unarchives. If someone deletes a post by hand, `on_raw_thread_delete` records a `thread_deleted` event; an open case gets a new post from the reconcile task.

**Lifetime.** Forum posts auto-archive after inactivity; the reconcile task unarchives the posts of open cases so their buttons stay reachable.

**User thread** — the answer to "where does a Discord-origin opener attach screenshots and talk to staff":

- The form itself has an optional file field (`discord.ui.FileUpload`, up to 5 files). The bot downloads the files and re-uploads them into the case post, because interaction attachment URLs expire.
- For every Discord-origin case the bot creates a **private thread in `#tickets`** (`type=private_thread`, `invitable=False`, 7-day auto-archive) named `#57 · <kind>`, adds the opener, and posts a localised note: number, what happens next, "add screenshots or details here". Staff who press Take are added to it. On close the bot posts the outcome there, locks and archives it.
- Messages and files the opener posts in the user thread are announced in the case post with a jump link (not copied); staff answer in the user thread. Staff-internal talk stays in the forum post, which the opener never sees.
- Complaint threads live in a channel where ordinary staff cannot see private threads (8.9); only senior staff are added.
- Game-origin reporters get no thread: the outcome reaches them in game and by DM.

Rejected alternatives: a DM relay (DMs are often closed, and the bot's DM handler already carries the link-code flow, `bot.py:981-1034`); a public thread (exposes the reporter); a post in the staff forum (the opener would see other cases).

**Maps forum.** Existing, public. The bot does not create posts there; it reacts to them (9.5), adds its review card as a reply in the author's post, and manages the three moderated tags.

### 8.5 Permission mapping

Cases reuse the existing helpers, so both `PERMISSIONS_MODE` values keep working (`settings.py:16`, `permissions_config.py`).

| Action | Check | Existing precedent |
|---|---|---|
| Report and appeal cards: Take, Release, History, Reject, Ban, Mute, Unban, Shorten, Refresh | `admin_role_ids` (`permissions.py:42`) | `/ban`, `/mute` (`cogs/admin.py:78-135`, `cogs/checks.py:16`) |
| Badge cards: Grant, Reject | `general_admin_role_ids` (`permissions.py:46`) | `/badge grant` (`cogs/admin.py:223-250`) |
| Complaint cards: everything; `/cases reopen`, `/cases subject`, `/cases panel publish` | `head_admin_role_ids` (`permissions.py:55`) | `head_admin_check` (`cogs/checks.py:52`) |
| Map cards: Accept, Reject, Re-check | `map_reviewer_role_ids` (`permissions.py:65`) | `/map upload` (`cogs/maps.py:53-105`) |
| `/cases find` | any of the above | — |
| Opening any case from Discord | member does **not** hold `CASES_BLACKLIST_ROLE_ID` (`member_role_ids`, `permissions.py:17`) | new |

Checks run through `ensure_any_role` (`permissions.py:85`) inside the button handler, the way `StatsActionsView.interaction_check` does today (`moderation_views.py:324-387`). The Discord permissions of the forums are the first line of defence; the role check on every press is the second and is the one that counts.

Which Discord roles sit behind the three settings (Mindustry Admin / Moderator, the senior roles, Map Reviewer) is deployment configuration **[owner]**.

### 8.6 Localisation

- **Library.** `fluent.runtime` from PyPI: the same `.ftl` syntax as the plugin's bundles, so translators and Weblate work the same way. One file per locale and domain, `i18n/locales/<locale>/<domain>.ftl`; domains match the flows so parallel work never edits the same file. Hatchling packages non-Python files inside the package; a test loads the bundles through `importlib.resources`.
- **Languages.** `en`, `ru`, `uk` at launch. Discord's `interaction.locale` maps `ru` → `ru`, `uk` → `uk`, everything else → `en`.
- **Resolution order for an interaction:** the user's manual choice (`discord_user_prefs.source = "manual"`) → `interaction.locale` → `en`. Every interaction refreshes the `auto` row, which is how the bot knows a language for later DMs.
- **Resolution order for a DM:** `discord_user_prefs` → the linked player's in-game `language` (`dto.py` `PlayerRecord.language`) → `en`.
- **Language switch.** A panel button (`xc:1:panel:lang`) and `/language` open an ephemeral select; the choice is stored as `manual`.
- **What is in which language.** Forms, modals, ephemeral replies, user-thread messages, DMs: the user's language. The public panel: one message with the short text in all three languages and language-neutral buttons (emoji + English word). Staff cards and staff-forum messages: one language, `CASES_STAFF_LOCALE`. Map review cards (public): the author's language, falling back to English. Free text typed by people is never translated.
- **Slash commands.** `tree.set_translator(...)` with a `Translator` backed by the same bundles. New commands (`/cases`, `/language`) get localised names and descriptions; existing commands get localised descriptions only — renaming `/ban` per language would hurt staff muscle memory for no gain.
- **Tests.** Key parity between `en`, `ru`, `uk` for every domain; every key referenced as a literal in code exists; every message formats with its declared arguments.

### 8.7 Settings and feature flags

All new keys go in `settings.py` in the existing style (`Field(validation_alias=…)`, blank id → 0 through the existing validators, `settings.py:94-104`). `0` or empty always means "not configured"; a flow whose required ids are missing refuses to start and logs why.

| Key | Default | Purpose |
|---|---|---|
| `CASES_REPORTS_ENABLED`, `CASES_APPEALS_ENABLED`, `CASES_BADGES_ENABLED`, `CASES_COMPLAINTS_ENABLED`, `CASES_MAPS_ENABLED` | `false` | one switch per flow: its panel button, its consumer, its event hooks |
| `CASES_PANEL_CHANNEL_ID` | 0 | `#tickets` — panel and user threads |
| `CASES_STAFF_FORUM_ID` | 0 | reports, appeals, badges |
| `CASES_SENIOR_FORUM_ID` | 0 | complaints |
| `CASES_COMPLAINT_THREAD_CHANNEL_ID` | 0 | parent of complaint user threads; 0 → the panel channel, subject to the start-up check in 8.9 |
| `CASES_BLACKLIST_ROLE_ID` | 0 | `Tickets Blacklist` |
| `CASES_STAFF_LOCALE` | `en` | language of staff cards |
| `CASES_OPEN_COOLDOWN_SECONDS` | 60 | per Discord user and kind |
| `CASES_MAX_OPEN_PER_USER` | 3 | open cases per opener and kind |
| `CASES_REPORT_BURST_LIMIT` / `CASES_REPORT_BURST_WINDOW_SECONDS` | 20 / 600 | circuit breaker on new report cases |
| `CASES_APPEAL_COOLDOWN_DAYS` | 14 | new appeal of the same ban after a rejection |
| `CASES_BADGES_REQUESTABLE` | `translator,map-maker,contributor,bug-finder,event-winner,veteran,developer` | subset of `badges.py` |
| `CASES_EVIDENCE_RETENTION_DAYS` | 180 | 5.1 |
| `CASES_NOTICE_TTL_DAYS` | 30 | 5.2 |
| `DISCORD_MAPS_FORUM_ID` | 0 | the existing maps forum |
| `MAPS_TAG_WAITING`, `MAPS_TAG_ACCEPTED`, `MAPS_TAG_REJECTED` | `Waiting`, `Accepted`, `Rejected` | moderated tag names |
| `MAPS_IGNORED_TAGS` | `Report a map,Balance a map` | posts with these tags are not submissions |
| `MAPS_TAG_PROFILES` | `Tower Defence=tower-defense,PvP=pvp,Survival=survival,Attack=attack` | forum tag → check profile |
| `MAPS_MAX_FILE_BYTES` | 4194304 | |
| `DISCORD_MAP_MAKER_ROLE_ID` | 0 | Discord role given with the badge; 0 = badge only |

The in-game side has its own switch (7.2). A report sent from a server while the bot's report flow is off stays in the stream until the flow is switched on (within the stream's retention).

### 8.8 Robustness, limits, errors

**Creation is resumable.** Insert the case as `creating` (number allocated, unique `submission_id`) → create the post → store the ids and set `open` → evidence follow-ups → user thread → acknowledgement. Each step is idempotent on stored state; a redelivered event or a retried interaction finds the existing case by `submission_id`.

**Reconcile task** (`daemons/cases_reconcile_daemon.py`, at start-up and every 5 minutes):

1. `creating` cases older than a minute: look for a post in the forum whose title starts with `#<number> `, adopt it or create it; after 24 h mark `cancelled`.
2. Open cases whose post is archived: unarchive.
3. Open cases with a matching confirmed completion audit (`details.extra.case`, attempt token, action and subject): finish the interrupted close. Apply the attempt filter before limiting audit results; an audit from before a reopen cannot finish a new attempt. Legacy records without a token use conservative action/subject and creation/reopen time checks.
4. Map cases with `deep_checks_pending`: retry the inspection; with `pending_badge`: retry the grant when the author has linked an account (up to 30 days).
5. Clear `evidence.chat` past retention.

**Abuse limits on the bot** (authoritative; the plugin's limiter is only local):

- Game reports: one per reporter uuid per 60 s through a Redis claim; a reporter whose linked Discord account holds the blacklist role gets a `report_not_accepted` notice and no case; duplicates on the same subject merge into the open case; above the burst limit new report cases are not created, the event is acknowledged, counted, and one alert is posted in the staff forum.
- Discord forms: blacklist role → ephemeral refusal; cooldown and open-case cap checked on submit.

**Errors.**

| Situation | Behaviour |
|---|---|
| Discord API error while creating a post | case stays `creating`; reconcile retries; the opener of a Discord form is told the request is registered under its number |
| Lost CAS / stale button | ephemeral "already handled", card refreshed |
| `staff_actions` raises (player not found, ban already gone, RPC rejected) | case unchanged, ephemeral localised reason, `error` event on the case |
| Evidence or inspect RPC: `NoLiveServerError`, timeout (`rpc/mindustry_rpc.py:35,93`) | card without live evidence / flag `deep_checks_pending`; never blocks creation |
| DM forbidden | ignored; in-game notice and user thread remain |
| Malformed event | existing DLQ path (`redis_bus.py:481-568`) |
| Forum or channel id missing or wrong type | the flow does not start; `/cases panel publish` reports which key is wrong |
| Interaction older than 3 s before a slow step | every slow path defers first; modals are sent before any I/O |

### 8.9 Discord-side setup and start-up checks

Done by the owner once; the bot verifies and refuses to enable a flow otherwise:

- Staff forum: not visible to `@everyone`; visible to the roles behind `admin_role_ids` and `general_admin_role_ids`; the bot can create posts, manage threads and tags.
- Senior forum: visible only to the roles behind `head_admin_role_ids`.
- `#tickets`: members can view and write in threads they are added to; the bot can create private threads and manage threads.
- Complaint threads: any role with *Manage Threads* on the parent channel can read every private thread in it. If a non-senior staff role has that permission on the configured parent, the complaints flow does not start. The simplest layout is a dedicated, otherwise empty channel for complaint threads.
- Maps forum: the bot can manage threads (required to apply moderated tags and to lock).

Deployment values supplied by the owner (environment only, never in code): guild `1058023472661549176`; `#tickets` `1234108661001879656`; maps forum `1179062537048830025`; bans channel `1058024031233789982`; mutes `1479573330595025050`; votekicks `1487143154414256178`; proofs `1317835656986230885`; staff category `1356906082445824040` (the two new forums go here). Role ids for `Tickets Blacklist`, `Map Maker` and the staff tiers are to be read from the guild.

### 8.10 What is unit-testable with the existing setup

The test suite uses pytest with `asyncio_mode = auto`, hand-written fakes for the bot and interactions (`tests/test_handlers_badges.py`), a hand-written fake MongoDB collection (`tests/test_rating_store.py`) and `fakeredis`. With that:

| Testable in unit tests | Not unit-testable — staging guild checklist |
|---|---|
| transition table, CAS, merge of duplicate reports (`CaseService` over a fake store) | forum post creation, tags, archive / lock |
| `custom_id` round-trip and rejection of malformed ids | buttons still working after a real restart |
| access rules: role per verb, blacklist, issuer conflict | channel permission layout (8.9) |
| card and message rendering (embeds are plain objects), splitting of long evidence | private-thread membership and visibility |
| i18n parity and formatting | Discord-side locale of slash commands |
| `staff_actions` with the fake bot, including idempotency | modal file upload end to end |
| report consumer: dedup, rate limit, burst breaker (`fakeredis`) | DM delivery |
| `msav.py` on byte fixtures built in the test | real `.msav` from the forum |
| reconcile decisions over a fake store and a fake forum object | |

The fake collection has to grow `find_one_and_update`, partial-unique behaviour and `$push` / `$unset`; that is part of the cases-core package.

## 9. Flows end to end

### 9.1 Report from the game

1. A player runs `/report` (or `/report <name|#pid>`). `ReportMenu` opens fill-screen with the list of online players and recent leavers.
2. The player picks a target, a reason, optionally types a comment, and confirms.
3. `ReportService` validates and consults the rate limiter. A refusal is shown in the menu with the remaining time; nothing is sent.
4. On the game thread the service snapshots the context, the chat excerpt and the block summary (through the SPI; absent if TileLogger is not installed).
5. On a worker it publishes `cases.report.submitted` with a fresh `submissionId` and waits for the stream write to be confirmed. The menu shows "sent" or "could not send".
6. The bot's consumer (`xcore:cg:discord-case-report`) receives the event. If the report flow is off, the consumer is not running and the event waits.
7. The bot applies its own checks: reporter cooldown, blacklist role of a linked reporter, burst breaker. A blocked reporter gets a `report_not_accepted` notice; processing stops.
8. The bot allocates a case number and inserts the case (`creating`, `dedup_key = report:<target uuid>`). A duplicate `submission_id` means a redelivery: acknowledge and stop. A duplicate `dedup_key` means an open case on the same player: push the reporter into `co_openers`, post "+1 report from … : reason" in the existing post, continue at step 11 with the existing number.
9. The bot reads the subject's sanction history and current ban / mute from MongoDB, creates the post in the staff forum (tags `Report`, `Open`) with the card and the buttons Take, Ban, Mute, History, Reject, then posts the chat excerpt and block summary as follow-ups. The case becomes `open`.
10. Card content: reported player (name, pid, linked Discord mention if any), reporter (same), reason and comment, server / map / mode, online or left, `same network` flag, sanction counts with the last three entries, active ban or mute. No uuid, no address.
11. The bot inserts a `report_received` notice for the reporter and sends `cases.notice.command` to the origin server; the reporter sees "Report registered as #57" in chat within seconds, or on the next join.
12. Staff act:
    - **Take** → assignee set, tag `Taken`.
    - **History** → ephemeral list of the subject's last audit entries.
    - **Ban** / **Mute** → modal (player ID pre-filled, duration, reason pre-filled with the report reason and `case #57`) → `perform_ban` / `perform_mute` with `case_number` → the sanction is written, logged in the bans / mutes channel with the case reference, the player is kicked or muted on the servers as today → case `resolved` (`banned` / `muted`, with `ban_id` and `audit_id`).
    - **Reject** → modal with a reason code and optional text → case `rejected`.
    - A ban or mute issued in game or by slash command for the same player while the case is open is seen by the bot's existing ban / mute consumers; the bot posts it in the case post and resolves the case with that sanction.
13. On close the bot edits the card, swaps the tag, locks and archives the post, and for every reporter (opener and co-openers) inserts a `report_resolved` or `report_rejected` notice, sends the wake-up to the server where that player is online, and, if the reporter has a linked Discord account, sends a DM in their language.
14. The reporter reads the outcome in game ("action was taken" / "no violation found" / "not enough evidence") — without the staff member's name and, by default, without the sanction details. The reported player is never told who reported.

### 9.2 Report from Discord

1. A member presses **Report** on the panel. Checks: flow on, no blacklist role. The bot answers with a modal in the member's language: who (player ID or nickname), what happened, when and where (optional), files (optional).
2. On submit: cooldown and open-case cap. The bot resolves the subject — digits or `#n` as a pid, otherwise an exact name match. Not exactly one match → the case is still created, with `raw_subject` and the flag `subject_unresolved`; Ban and Mute then ask for the player ID in their modal.
3. The case is inserted (`submission_id = discord:<interaction id>`); the same merge rule applies when the subject is resolved.
4. If the subject is online (`players.online_server`), the bot calls `cases.evidence.request` on that server and stores the chat excerpt and block summary; otherwise the card says there is no live evidence.
5. The post and card are created as in 9.1; attached files are re-uploaded into the post.
6. The bot creates the user thread in `#tickets`, adds the member, and replies to the modal ephemerally with the number and a link to the thread.
7. Staff act as in 9.1 step 12; **Take** also adds the staff member to the user thread. **Refresh** re-runs step 4.
8. On close the outcome is posted in the user thread (then locked and archived) and sent by DM; if the member has a linked game account, an in-game notice is inserted too.

### 9.3 Appeal

1. A banned player sees the ban screen: reason, admin, expiry, **ban number `B-1234`**, **their ID**, and the instruction to quote the number in `#tickets`.
2. In Discord they press **Appeal**. The bot looks up active bans of the member's linked accounts (`find_players_by_discord_id`, `mongo_store.py:200`, then `find_ban`).
   - Exactly one → the modal is titled with that ban.
   - Several → an ephemeral select first.
   - None or not linked → the modal has a field "ban number or player ID".
3. On submit the bot finds the ban: by ban id (`find_ban_by_id`), else by pid → uuid → `find_ban`. A row without `ban_id` gets one (5.3). Outcomes:
   - No active ban found → ephemeral explanation, no case.
   - An appeal for that ban is already open, or was rejected less than `CASES_APPEAL_COOLDOWN_DAYS` ago → ephemeral explanation with the existing number.
   - Found through a ban number or pid by a member whose Discord account is not linked to the banned account → the case is created with the flag `unverified_identity`; the member is told only that the appeal is registered (no ban details are echoed back).
4. The case is inserted with `dedup_key = appeal:<ban id>` and a snapshot of the ban. Card: banned player, ban number, reason, issuing admin, issue and expiry dates, earlier sanctions, earlier appeals of this player, the statement, flags. Buttons: Take, Unban, Shorten, History, Reject. A user thread is created.
5. The issuing admin is blocked from every button (4.6); the bot mentions on the card who is excluded. With `issuer_unknown` nobody is blocked and the card says so.
6. Decision:
   - **Unban** → confirmation → `perform_unban` (row deleted, pardon command to the servers, UNBAN audit with `ban_id` and `case`) → `unbanned`.
   - **Shorten** → modal with a positive new duration counted from now; for a finite ban the new expiry must precede the current expiry, while a permanent ban becomes finite → `perform_shorten_ban` (same ban id, NOTE audit including remaining duration and expiry) → `shortened`. The helper refuses an expiry at or before now, and the DB update still rejects lengthening after a concurrent shortening.
   - **Reject** → reason → `rejected`.
7. The bans channel gets a line "Ban B-1234 lifted / shortened to … — case #60".
8. Outcome to the appellant: user thread and DM in every case; for `unbanned` and `shortened` also an in-game notice for the next join.

### 9.4 Badge request

1. A member presses **Badge**. If their Discord account is not linked to a game account the bot explains how to link and stops.
2. An ephemeral select lists requestable badges (`CASES_BADGES_REQUESTABLE` minus the ones already owned) and, if several accounts are linked, the account. Then a modal: why, with an optional file.
3. The case is inserted (`dedup_key = badge:<uuid>:<badge>`), posted in the staff forum (tags `Badge`, `Open`). Card: requester, account (name, pid), badge, badges already held, statement. Buttons: Take, Grant, Reject. A user thread is created.
4. **Grant** → `perform_badge_grant` (MongoDB + inventory event to the servers, as `/badge grant`) → `granted`. **Reject** → reason → `rejected`.
5. Outcome: user thread, DM, in-game notice (`badge_granted` / `badge_rejected`).
6. **Map Maker automatically**: on Accept in the map flow (9.5 step 8) the bot calls `perform_badge_grant(map-maker)` for every game account linked to the map author and, if `DISCORD_MAP_MAKER_ROLE_ID` is set, adds the Discord role. An author without a linked account is told how to link; the grant is retried by the reconcile task for 30 days.

### 9.5 Map review

1. Someone creates a post in the maps forum. `on_thread_create` fires; the bot fetches the starter message (retrying briefly — the message can arrive after the thread).
2. Posts carrying a tag from `MAPS_IGNORED_TAGS` are left alone. A post without a `.msav` attachment gets a reply asking for the file; the flow starts later when the author posts one in the thread.
3. The bot inserts the case (`kind = map`, `submission_id = map:<thread id>`, `discord.thread_id` = the author's post) and runs its container-level checks on the downloaded bytes: extension, size, inflating zlib stream with a hard cap on inflated bytes, `MSAV` magic, save version, readable meta.
4. It asks any live server for `maps.inspect.request` (profile from the post's gameplay tag via `MAPS_TAG_PROFILES`, tried on up to three servers as other RPCs are, `rpc/mindustry_rpc.py:32,93`). No live server → flag `deep_checks_pending`, retried by the reconcile task.
5. The bot replies in the post with the review card: map name, author, size, mode, findings grouped by severity with coordinates, and the buttons Accept, Reject, Re-check (usable only by map reviewers). It sets the tag `Waiting` and leaves the gameplay tags alone.
6. A new `.msav` from the author while the case is open repeats steps 3–5 on the newest file and refreshes the card.
7. **Reject** → modal with the reason → tag `Rejected`, post locked, case `rejected`, author notified in the post and by DM.
8. **Accept** → a select of live servers (the ones whose mode matches the profile first). Error-level findings require an explicit "accept anyway". The bot re-reads the attachment for a fresh URL and calls `perform_map_upload(server, file, uploader = reviewer, creator = post author)` — exactly what `/map upload` sends, with the post author as creator. On `installed`: tag `Accepted`, card updated with server and file name, case `accepted`, Map Maker badge (9.4 step 6), author notified. Any other status leaves the case open and shows the per-file status to the reviewer.
9. Posts that existed before the flow was enabled are not touched. A reviewer can start the flow on one with `/map review` inside the post.

### 9.6 Complaint about staff

1. A member presses **Staff complaint**. Modal: who (free text), what happened, when, optional files.
2. The case is inserted (no `dedup_key`), posted in the **senior forum** (tags `Complaint`, `Open`). The user thread is created under the complaint thread channel with the member and nobody else; senior staff join on Take.
3. Card: complainant, the text naming the staff member, statement, the complainant's own sanction history (context for retaliatory complaints). Buttons: Take, Set subject, Resolve, Reject.
4. **Set subject** opens a user select; once set, that staff member is blocked from the case (4.6) and their recent moderation actions against the complainant are listed in the post.
5. **Resolve** (note required) → `upheld`; **Reject** (reason) → `rejected`. Consequences for the staff member are handled by people, not by the bot.
6. Outcome to the complainant: user thread and DM with a neutral text. The staff member is not notified by the bot.

## 10. Work breakdown

Complexity: `hard` — needs a top model and careful review; `medium`; `easy`. Two packages are "parallel-safe" only if they touch disjoint sets of files; the lists below are exhaustive for shared files. **X1, B1 and B2 can start immediately.** P0 can start as soon as X1's spec is on a branch, using the local protocol build.

### 10.1 Packages

**X1 — protocol 0.15.0** · `xcore-protocol` · medium · depends on: — · parallel-safe with: B1, B2, B3

- Files: `spec/messages/cases/*.v1.json` (4 messages), `spec/messages/maps/maps.inspect.{request,response}.v1.json`, `spec/messages/moderation/moderation.ban.created.v1.json`, `spec/shared/types/{case-chat-line,case-block-summary,map-inspect-info,map-inspect-finding}.v1.json`, `spec/routes/cases.routes.v1.yaml`, `spec/routes/maps.routes.v1.yaml`, `generators/families.py`, generated Java and Python (incl. `ProtocolRoutes`), `fixtures/{valid,invalid}/{cases,maps,moderation}/`, generated-model and route tests, `docs/architecture/protocol-overview.md`, `CHANGELOG.md`, version in `pyproject.toml` / Gradle, `uv.lock`.
- Acceptance: generation is reproducible; valid and invalid fixtures for every new message and for `banId`; Java ↔ Python round-trip tests pass; a 0.14.0 `moderation.ban.created` fixture still validates; routes resolve to the streams in 6.4.
- Not in scope: tagging and publishing — owner.

**P0 — plugin scaffolding** · `XCore-plugin` · easy · depends on: X1 · parallel-safe with: all B

- Files: `gradle/libs.versions.toml` (0.15.0), `service/network/ModerationProtocolMapper.java` (compile against the new constructor, `banId` null), `config/TomlSecretsConfig.java` (complete `cases` and `maps.checks` sections of 7.2), `bundles/bundle_{en,ru,uk_UA}.ftl` (all new keys of 7.11).
- Acceptance: build and existing tests green; `FtlBundleCompilationTest` passes; defaults leave every new feature off. Start from the working tree's `support-channel` change, do not revert it.

**P1 — ban identity** · `XCore-plugin` · medium · depends on: P0 · parallel-safe with: P2, P3, P4, T1, all B

- Files: `model/BanData.java`, `database/repository/BanDataRepository.java`, `database/repository/AuditRecordRepository.java`, `database/migration/V7__AssignBanIds.java` (+ its registration), `service/moderation/ModerationService.java`, `service/network/ModerationProtocolMapper.java`, `security/ingress/checks/BanCheck.java`, `bundles/bundle_*.ftl` (only `ban-content`, `tempban-content`, nine locales), tests.
- Acceptance: a new ban has a unique `ban_id` and `pid`; a re-ban gets a new id; `V7` is idempotent and safe with concurrent writers; a row written without an id is healed on the next join; the ban screen shows `B-n` and the ID (rendered-text test in three languages); the ban event carries `banId`; UNBAN audit carries `ban_id`.

**P2 — plugin cases core** · `XCore-plugin` · hard · depends on: P0 · parallel-safe with: P1, P4, all B

- Files: `cases/**`, `integration/blockhistory/**`, `event/transport/CasesTransportHandler.java`, `service/network/CasesProtocolMapper.java`, `event/TransportService.java`, `event/net/chat/ChatMessageHandler.java`, `service/TranslatorService.java`, `event/handler/ConnectionHandler.java`, tests. No bundle files.
- Acceptance: `ReportService.submit` returns each `ReportResult`; evidence is captured on the game thread and published off it; the limiter tests of 7.12; notices are shown on join and on wake-up, once, and survive a disconnect between claim and show; the evidence RPC answers for an online, an offline and an unknown player; with no block-history provider a report is still sent.

**P3 — `/report` menu** · `XCore-plugin` · medium · depends on: P2 · parallel-safe with: P1, P4, T1, all B

- Files: `ui/menu/ReportMenu.java`, `ui/menu/ReportUiController.java`, `command/controller/client/ReportController.java`, tests. No bundle files (keys exist from P0; wording fixes go in a follow-up after P1).
- Acceptance: the three screens and the result state pass `LayoutAssert.assertLaidOut` and `assertFitsPacket` on `NARROW`, `SMALL`, `WIDE` in `en`, `ru`, `uk_UA` with 8 maximum-length names; no uuid in any action id; staff and the reporter are absent from the list; recent leavers present; manual check on a phone in portrait and on PC.

**T1 — TileLogger provider** · `TileLogger` · medium · depends on: P2 · parallel-safe with: P1, P3, P4, all B

- Files: `src/main/java/tilelogger/integration/XcoreBlockHistoryProvider.java`, `TileLoggerPlugin.java`, the XCore-plugin dependency version, tests for the fold.
- Acceptance: counts, top blocks and bounding box are correct on a synthetic `TileState` list (including times above 32767 s); registration is removed on disable; a native failure yields an empty result, not an exception on the game thread.

**P4 — map inspection** · `XCore-plugin` · hard · depends on: P0 · parallel-safe with: P1, P2, P3, T1, all B

- Files: `service/map/MapInspectionService.java`, `service/map/inspect/**`, `service/map/MapPreviewService.java` (shared lock), `event/transport/MapTransportHandler.java` and the maps mapper it uses, fixture maps and tests.
- Acceptance: one fixture per finding code; a corrupt file and an oversized file are answered with `invalid_map` / a finding without touching the world; the running game is unaffected while an inspection runs (lock shared with preview generation); download limits and timeout enforced; inflated size capped.

**B1 — bot localisation** · `XCore-discord-bot` · medium · depends on: — · parallel-safe with: B2, X1, all P

- Files: `i18n/**` (incl. `locales/{en,ru,uk}/core.ftl`), `pyproject.toml` (`fluent.runtime`, `discord.py>=2.7`), `uv.lock`, `tests/test_i18n.py`.
- Acceptance: `t()` with fallback chain; locale resolution functions (pure, store passed in); `Translator` returns localised strings for `ru`, `uk` and `None` otherwise; parity and formatting tests; bundles load from the installed package.

**B2 — interaction-free staff actions** · `XCore-discord-bot` · medium · depends on: — · parallel-safe with: B1, X1, all P

- Files: `staff_actions.py`, `handlers_moderation.py`, `handlers_badges.py`, `handlers_misc.py`, `mongo_store.py`, `dto.py`, `store_mappers.py`, `bot.py` (only pass-throughs for the new store methods), tests.
- Acceptance: the functions of 8.2 exist and are covered with the fake bot; `/ban`, `/unban`, `/mute`, `/badge grant`, `/map upload` behave as before (existing tests unchanged and green); bot-issued bans carry `ban_id`; shortening keeps the id and refuses to lengthen; the bans-channel embed shows `B-n` and the case.

**B3 — cases core** · `XCore-discord-bot` · hard · depends on: B1, B2 · parallel-safe with: X1, all P

- Files: `cases/**` except the five flow modules, `cases/flows/__init__.py`, `cogs/cases.py`, `daemons/cases_reconcile_daemon.py`, `settings.py`, `container.py`, `bot.py` (cog, dynamic items, translator, dispatch of `on_thread_create` / `on_message` / `on_raw_thread_delete` to the registry), `i18n/locales/*/core.ftl`, `README.md` (settings), the fake-collection helpers in tests, tests.
- Acceptance: everything in the left column of 8.10 that is not flow-specific; a dummy flow registered in a test goes through open → take → resolve → archive against fakes; the panel is published and edited in place; flags off ⇒ no button, no hook; start-up checks of 8.9 refuse misconfigured flows; staging: a button pressed after a restart works.

**B4 — bot transport for cases** · `XCore-discord-bot` · medium · depends on: X1, B3 · parallel-safe with: all P

- Files: `pyproject.toml`, `uv.lock` (protocol 0.15.0), `redis_bus.py`, `protocol_outbound.py`, `runtime_consumers.py`, `daemons/stream_supervisor.py`, `rpc/mindustry_rpc.py`, `bot.py` (consumer pass-through), tests.
- Acceptance: consumer for `cases.report.submitted` in its own group, started only when the report flow is on, dispatching to the registered report flow; `publish_case_notice`, `rpc_case_evidence`, `rpc_inspect_map` with the existing error types; ban events with `banId` are accepted and logged with the number; `fakeredis` tests for delivery, redelivery and DLQ.

**B5 — reports flow** · `XCore-discord-bot` · hard · depends on: B3, B4 · parallel-safe with: B6, B7, B8, B9, all P

- Files: `cases/flows/reports.py`, `i18n/locales/*/reports.ftl`, `tests/test_cases_reports*.py`.
- Acceptance: 9.1 and 9.2 against fakes — dedup on `submissionId`, merge of a second reporter, cooldown, blacklist, burst breaker, card content without uuid or address, Ban and Mute resolving the case with `case_number` on the sanction, auto-resolution on an external ban, outcome notices for every reporter, DM in the stored language; staging run-through with a real server.

**B6 — appeals flow** · `XCore-discord-bot` · hard · depends on: B3 (P1 deployed for numbers on the ban screen) · parallel-safe with: B5, B7, B8, B9, all P

- Files: `cases/flows/appeals.py`, `i18n/locales/*/appeals.ftl`, tests.
- Acceptance: lookup by link, ban number and pid; one open appeal per ban; cooldown after rejection; issuer blocked on every button including by name match; `issuer_unknown` and `unverified_identity` flags; Unban and Shorten execute and audit with `ban_id` and `case`; no ban details echoed to an unverified member.

**B7 — badge requests** · `XCore-discord-bot` · medium · depends on: B3 · parallel-safe with: B5, B6, B8, B9, all P

- Files: `cases/flows/badges.py`, `i18n/locales/*/badges.ftl`, tests.
- Acceptance: unlinked member refused with instructions; owned badges not offered; Grant writes MongoDB and publishes the inventory event; duplicate open request refused; exports `grant_map_maker(discord_id)` for the map flow.

**B8 — map review** · `XCore-discord-bot` · hard · depends on: B3, B4, P4 (and B7 for the automatic badge) · parallel-safe with: B5, B6, B9, all P

- Files: `cases/flows/maps.py`, `cases/msav.py`, `cogs/maps.py` (`/map review`), `i18n/locales/*/maps.ftl`, tests.
- Acceptance: 9.5 against fakes — ignored tags, missing attachment, re-validation on a new file, pending deep checks, Accept installs through the same payload as `/map upload` with the post author as creator, tags switched by name, Reject locks; `msav.py` rejects truncated, non-zlib, wrong-magic and over-inflating inputs.

**B9 — staff complaints** · `XCore-discord-bot` · medium · depends on: B3 · parallel-safe with: B5, B6, B7, B8, all P

- Files: `cases/flows/complaints.py`, `i18n/locales/*/complaints.ftl`, tests.
- Acceptance: posts only in the senior forum; only senior roles can act; the subject, once set, is blocked; the flow refuses to start when the thread parent leaks to non-senior staff (8.9).

### 10.2 Order and milestones

```
X1 ──▶ P0 ──┬─▶ P1
            ├─▶ P2 ──┬─▶ P3
            │        └─▶ T1
            └─▶ P4
B1 ─┐
B2 ─┴─▶ B3 ──▶ B4 (needs X1) ──┬─▶ B5
               │               └─▶ B8 (needs P4, B7)
               └─▶ B6, B7, B9
```

| Milestone | Packages | Ships |
|---|---|---|
| **M1 — foundation + reports** | X1, P0, P2, P3, B1, B2, B3, B4, B5; T1 and P1 recommended but not blocking | `/report` in game, the panel with the Report button, staff forum, outcome notices. Without T1 reports have no block summary; without P1 bans have numbers only when issued by the bot. Shippable on its own |
| M2 — appeals | P1, B6 | ban numbers on the ban screen, appeals |
| M3 — badges | B7 | badge requests |
| M4 — maps | P4, B8 | map review with validation and automatic Map Maker |
| M5 — complaints | B9 | staff complaints |

## 11. Risks

| Risk | Mitigation |
|---|---|
| Strict decoders: an old bot dead-letters ban events carrying `banId` | Rollout order of 6.6; never roll the bot back before the servers |
| A server on the old plugin replaces a ban row and drops `ban_id` | Guarded lazy assignment by every reader that needs the id (5.3) |
| Two processes write `bans`; unban semantics differ (uuid vs uuid-or-address) | Case actions go through the bot's existing path only; the id is allocated atomically from one counter |
| Private threads are readable by any role with *Manage Threads* on the parent | Start-up check and a dedicated parent for complaint threads (8.9) |
| Buttons on archived posts | Posts of open cases are unarchived by the reconcile task; closed cards have no buttons |
| Third parties' chat lines end up on a staff card | Staff-only forum; public and team chat only; excerpt limited to 30 lines and 10 minutes; stored copy cleared after the retention period |
| Mass or retaliatory reporting | Per-uuid limits, merge into one case, burst breaker, blacklist role; a report never triggers an automatic sanction |
| An unlinked in-game player who abuses reports cannot be given the Discord blacklist role | Staff sanction in game (mute or ban for false reports); a per-player in-game block is a possible follow-up, not in scope |
| Appeals by someone who merely knows a ban number | `unverified_identity` flag, no ban details echoed, staff decide |
| Hostile `.msav` (inflation bomb, absurd dimensions, crafted content) | Size and inflated-size caps in both the bot and the plugin, `maxTiles`, detached read under the existing lock, no load into the world |
| The inspect and upload RPCs make a server fetch a URL | Only `https` URLs on Discord's attachment hosts are accepted by the inspection path; apply the same allow-list to the upload path if it has none |
| The native history read runs on the game thread | Capped at 4096 entries, one read per report, reports limited to one a minute per player |
| Report stream backlog while the bot is down | 7-day TTL in the route, bot ignores expiry, stream cap 50 000 (`redis_bus.py:105`) is far above the volume |
| Modal file upload is a recent Discord feature | It is optional in every form; the user thread is the guaranteed path |
| TD guideline constants do not match what the TD plugin actually requires | Checks are configuration with severities; confirm with the owner before enabling M4 |
| A deleted or renamed forum tag | Tags resolved by name at use; a missing tag is logged and skipped, never fatal |

## 12. Decisions

### 12.1 The four open questions of the brief

| Question | Decision |
|---|---|
| Where does a Discord-origin reporter attach screenshots and talk to staff? | An optional file field in the form, plus a private thread per case in `#tickets` with the opener and the staff who took the case. Staff-internal discussion stays in the forum post. Complaint threads use a parent that ordinary staff cannot read. **[owner]** — requires the channel permission layout of 8.9 |
| Who owns the case collection? | The bot, exclusively. The plugin sends reports as events and only marks notices as delivered |
| Ban identity | Sequential integer from `counters{_id:"ban_id"}`, stored as `bans.ban_id`, shown as `B-1234`; migration `V7` plus lazy assignment; shown on the `tempban-content` screen (with the pid) and in the bans channel; carried by `moderation.ban.created.banId` and by audit records. Vanilla bans (`ban-content`) have no number and are not appealable through the bot |
| How does an in-game report reach the bot and the outcome return? | Out: event `cases.report.submitted` on `xcore:evt:cases:report`, published with confirmation, consumed at-least-once, deduplicated by `submissionId`. Back: a `case_notices` document (durable, shown on the next join on any server) + `cases.notice.command` wake-up for an online player + DM when a Discord account is linked |

### 12.2 Other decisions taken

1. One case-number sequence for all kinds; the number is the `_id`.
2. Duplicate reports on one player merge into the open case; every reporter gets the outcome.
3. A ban or mute issued outside the card resolves the matching open report automatically.
4. Buttons are `DynamicItem`s with stateless ids `xc:1:…`; the card is always re-rendered from MongoDB.
5. Tags by name, never by id; channel and role ids only in settings.
6. Bot localisation with `fluent.runtime`, one `.ftl` per locale and flow.
7. Deep map checks run on a game server over RPC; the bot checks only the container.
8. Block history reaches XCore-plugin through an SPI implemented by TileLogger.
9. Evidence shows names and pids only; network data is reduced to one boolean computed on the server.
10. Shortening a ban keeps its id and is audited as a `NOTE`, not as a second `BAN`.

### 12.3 Questions for the owner

| # | Question | Recommended default |
|---|---|---|
| O1 | Map guidelines per mode: are `metal-tiles-2` / `metal-wall-2` / drop-zone radius 0 really required for Tower Defence, given that the TD plugin accepts any floor under a spawn? What does "empty waves" mean? May a reviewer accept a map with error-level findings? | Checks as in 7.9, constants configurable, waves informational, "accept anyway" allowed |
| O2 | Create the staff forum, the senior forum and the parent for complaint threads; which roles are regular staff, senior staff, badge granters | Two forums in the staff category; `Mindustry Admin` + `Mindustry Moderator` + `Discord Moderator` as staff; `Owner`, `Manager`, `Overseer` as senior |
| O3 | Language of staff cards | English |
| O4 | Merge `fix/map-attribution-review` and publish `xcore-protocol` v0.15.0 | Owner tags after X1 is reviewed |
| O5 | Reports on staff from the game | Not offered in the menu; hint to use the Discord complaint form |
| O6 | Does the reporter learn the sanction (ban length, reason)? | No — only "action taken" / "no violation" / "not enough evidence" |
| O7 | Issuing admin and their own ban's appeal: block or only flag? | Block, including senior staff |
| O8 | May a member whose Discord is not linked appeal by ban number? | Yes, flagged `unverified_identity` |
| O9 | Retention of chat evidence; are Discord posts kept forever? | 180 days in MongoDB; posts kept, locked and archived |
| O10 | Report limits and a minimum playtime to report | 60 s / 30 min same target / 10 a day; no minimum playtime |
| O11 | Appeal again after a rejection | After 14 days |
| O12 | Which badges can be requested; give the `Map Maker` Discord role together with the badge? | All non-system badges; yes |
| O13 | Old Ticketeer tickets and numbering | No import; seed `case_number` at 2000 so new numbers never collide with old ticket numbers |
