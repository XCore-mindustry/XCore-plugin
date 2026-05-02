# Moderation Current-to-V1 Mapping

## Goal
Map the current moderation-related transport payloads used by `XCore-plugin` and `XCore-discord-bot` into the canonical moderation v1 protocol model defined in `xcore-protocol`.

## Scope
This document covers the first moderation family:

- `moderation.ban.created`
- `moderation.mute.created`
- `moderation.vote-kick.created`
- `moderation.kick-banned.command`
- `moderation.pardon.command`
- `moderation.audit.appended`

It also documents how current Redis envelope fields should map into the canonical envelope model.

## Core Migration Rules

### 1. Canonical outbound only
New producers should emit only the canonical v1 schema.

### 2. Repository scope stays canonical
Legacy spellings, old event-type variants, and temporary translation logic should live in consumer repositories during migration, not in `xcore-protocol` schemas or fixtures.

### 3. Internal models are not the wire contract
`BanData`, `MuteData`, and transport records in `TransportEvents` are source material for mapping, not the canonical protocol definition.

## Current Sources

### `XCore-plugin`
- `BanData` / `MuteData`
- `TransportEvents.KickBannedPlayer`
- `TransportEvents.PardonPlayer`
- `TransportEvents.VoteKickEvent`
- `TransportEvents.ModerationAuditAppendedEvent`

### `XCore-discord-bot`
- `BanEvent`
- `MuteEvent`
- `VoteKickEvent`
- outbound command payloads in `redis_bus.py`

## Canonical V1 Shape Summary

### Shared structure choices
- target player information is grouped under `target`
- actor/admin information is grouped under `actor`
- expiration is grouped under `expiration`
- payload timestamps use ISO-8601 UTC strings
- message identity lives in canonical fields `messageType` and `messageVersion`

---

## Ban Event Mapping

### Current plugin shape
Current plugin publish path effectively emits a `BanData`-shaped payload with fields like:
- `uuid`
- `pid` or `playerPid` (consumer-side alias handling may provide this)
- `ip`
- `name`
- `adminName` / `admin_name`
- `adminDiscordId` / `admin_discord_id`
- `reason`
- `expireDate` / `expire_date`

### Current bot interpretation
`BanEvent` currently accepts:
- `pid` / `playerPid` / `player_pid`
- `uuid`
- `ip`
- `name`
- `adminName` / `admin_name`
- `adminDiscordId` / `admin_discord_id`
- `reason`
- `expireDate` / `expire_date`

### Canonical v1 target
`moderation.ban.created.v1`

### Field mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `uuid` | `target.playerUuid` | required in canonical target |
| `pid` / `playerPid` / `player_pid` | `target.playerPid` | optional |
| `name` | `target.playerName` | required |
| `ip` | `target.ip` | optional |
| `adminName` / `admin_name` | `actor.actorName` | required |
| `adminDiscordId` / `admin_discord_id` | `actor.actorDiscordId` | optional |
| implicit actor origin | `actor.actorType` | set by mapper, e.g. `discord`, `player`, or `unknown` |
| `reason` | `reason` | required |
| `expireDate` / `expire_date` | `expiration.expiresAt` | convert to ISO-8601 UTC if needed |
| permanence implied by null expire | `expiration.permanent` | mapper derives true/false |
| current publish context server | `server` | optional in schema, expected in producer policy |
| publish time / audit time | `occurredAt` | optional in schema, expected in producer policy |

### Mapper notes
- If current payload has no explicit `occurredAt`, derive it at publication time or from the surrounding audit record when available.
- If `expireDate` is null, set `expiration.permanent=true` and omit `expiration.expiresAt`.

---

## Mute Event Mapping

### Current plugin shape
Current plugin emits `MuteData`-shaped payloads similar to ban, without IP.

### Current bot interpretation
`MuteEvent` currently accepts:
- `pid` / `playerPid` / `player_pid`
- `uuid`
- `name`
- `adminName` / `admin_name`
- `adminDiscordId` / `admin_discord_id`
- `reason`
- `expireDate` / `expire_date`

### Canonical v1 target
`moderation.mute.created.v1`

### Field mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `uuid` | `target.playerUuid` | required |
| `pid` / `playerPid` / `player_pid` | `target.playerPid` | optional |
| `name` | `target.playerName` | required |
| `adminName` / `admin_name` | `actor.actorName` | required |
| `adminDiscordId` / `admin_discord_id` | `actor.actorDiscordId` | optional |
| implicit actor origin | `actor.actorType` | mapper-provided |
| `reason` | `reason` | required |
| `expireDate` / `expire_date` | `expiration.expiresAt` | optional |
| null expiry | `expiration.permanent` | derived |
| current publish context server | `server` | optional in schema, expected in producer policy |
| publish time / audit time | `occurredAt` | optional in schema, expected in producer policy |

---

## Vote-Kick Event Mapping

### Current plugin shape
`TransportEvents.VoteKickEvent` currently includes:
- `targetName`
- `targetPid`
- `targetUuid`
- `starterName`
- `starterPid`
- `starterDiscordId`
- `reason`
- `votesFor`
- `votesAgainst`
- `status`
- `server`
- `occurredAt`

### Current bot interpretation
Bot accepts a wider alias surface:
- target aliases (`targetName`, `target_name`, `target`, `name`)
- starter aliases (`starterName`, `starter_name`, `starter`, `initiatorName`, `adminName`, etc.)
- vote list aliases (`votesFor`, `votes_for`, `yesVotes`, etc.)

### Canonical v1 target
`moderation.vote-kick.created.v1`

### Field mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `targetUuid` | `target.playerUuid` | required in canonical; enrich before publish |
| `targetPid` | `target.playerPid` | optional |
| `targetName` | `target.playerName` | required |
| `starterName` | `actor.actorName` | required |
| `starterDiscordId` | `actor.actorDiscordId` | optional |
| starter origin inferred | `actor.actorType` | mapper-provided |
| `reason` | `reason` | required |
| `votesFor[].name` | `votesFor[].playerName` | canonical uses explicit player vocabulary |
| `votesFor[].pid` | `votesFor[].playerPid` | optional |
| `votesFor[].discordId` | `votesFor[].discordId` | optional |
| `votesAgainst[]...` | `votesAgainst[]...` | same structure |
| `server` | `server` | optional in schema, expected in producer policy |
| `occurredAt` epoch millis | `occurredAt` ISO-8601 UTC | convert in mapper; expected in producer policy |
| `status` | dropped from v1 | omit unless a later dedicated contract needs it |

### Mapper notes
- Current `status` is intentionally not part of the v1 canonical vote-kick event to keep the first version focused on the core moderation fact.
- Canonical v1 publishers should enrich vote-kick payloads so `target.playerUuid` is always present before publication.

---

## Kick-Banned Command Mapping

### Current plugin shape
`TransportEvents.KickBannedPlayer` contains:
- `uuid`
- `ip`

Current route metadata targets `xcore:cmd:kick-banned:{server}`.

### Current bot outbound shape
Current bot publishes:
- `uuid`
- `ip`
- `server`

### Canonical v1 target
`moderation.kick-banned.command.v1`

### Field mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `uuid` | `target.playerUuid` | optional when only IP is available |
| `ip` | `target.ip` | optional when UUID is available; valid alternative identifier when UUID is unavailable |
| current route server | `server` | required in canonical command payload |
| publication time | `requestedAt` | canonical payload business timestamp |

### Mapper notes
- `moderation.kick-banned.command.v1` uses `PlayerCommandTargetV1`, which requires at least one of `target.playerUuid` or `target.ip`.
- Publishers should include `playerName` when it is already available, but canonical command validity no longer depends on a lookup-only name.

---

## Pardon Command Mapping

### Current plugin shape
`TransportEvents.PardonPlayer` contains:
- `uuid`

### Current bot outbound shape
Current bot publishes:
- `uuid`
- `server`

### Canonical v1 target
`moderation.pardon.command.v1`

### Field mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `uuid` | `target.playerUuid` | optional when only IP is available |
| `ip` | `target.ip` | optional when UUID is available; valid alternative identifier when UUID is unavailable |
| current route server | `server` | required |
| publication time | `requestedAt` | recommended |

### Mapper notes
- `moderation.pardon.command.v1` uses `PlayerCommandTargetV1`, which requires at least one of `target.playerUuid` or `target.ip` while still allowing command producers to omit `playerName`.

---

## Moderation Audit Mapping

### Current plugin shape
`TransportEvents.ModerationAuditAppendedEvent` currently includes:
- `auditId`
- `action`
- `targetUuid`
- `targetPid`
- `targetName`
- `ipSnapshot`
- `actorType`
- `actorId`
- `actorName`
- `reason`
- `durationMs`
- `expiresAt`
- `relatedAuditId`
- `server`
- `occurredAt`

### Canonical v1 target
`moderation.audit.appended.v1`

### Field mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `action` | `entryType` | map values into canonical enum (`ban`, `mute`, `voteKick`, `pardon`, `other`) |
| `targetUuid` | `target.playerUuid` | optional if unavailable |
| `targetPid` | `target.playerPid` | optional |
| `targetName` | `target.playerName` | optional when only IP identity is known |
| `ipSnapshot` | `target.ip` | optional alternate target identity |
| `actorName` | `actor.actorName` | required |
| `actorId` | `actor.actorDiscordId` or future actor id field | for v1, map Discord-capable ids into `actorDiscordId` where meaningful |
| `actorType` | `actor.actorType` | direct semantic mapping |
| `reason` | `reason` | required |
| `server` | `server` | optional in schema, expected in producer policy |
| `occurredAt` | `occurredAt` | convert `Instant` to ISO-8601 UTC; expected in producer policy |
| `durationMs` | `details.durationMs` | keep inside `details` |
| `expiresAt` | `details.expiresAt` | keep inside `details` in v1 |
| `relatedAuditId` | `details.relatedAuditId` | keep inside `details` |
| `auditId` | `details.auditId` | keep inside `details` |

### Mapper notes
- Audit v1 intentionally keeps extended audit metadata inside `details` to avoid overloading the top-level contract in the first version.
- `moderation.audit.appended.v1` uses `ModerationTargetRefV1`, which requires at least one of `target.playerUuid` or `target.ip` while keeping `playerName` optional for IP-only audit records.

---

## Current Redis Envelope To Canonical Envelope Mapping

### Current event envelope fields
- `schema_version`
- `event_type`
- `event_id`
- `idempotency_key`
- `producer`
- `created_at`
- `expires_at`
- `server`
- `payload_json`

### Current RPC request envelope fields
- `schema_version`
- `rpc_type`
- `correlation_id`
- `request_id`
- `idempotency_key`
- `reply_to`
- `requested_by`
- `server`
- `timeout_ms`
- `created_at`
- `expires_at`
- `payload_json`

### Current RPC response envelope fields
- `schema_version`
- `rpc_type`
- `correlation_id`
- `server`
- `status`
- `error_code`
- `error_message`
- `responded_at`
- `payload_json`

### Canonical envelope mapping table

| Current field | Canonical field | Notes |
|---|---|---|
| `schema_version` | `schema_version` | unchanged |
| `event_type` / `rpc_type` | `message_type` | canonical unified field |
| payload internal version or mapper config | `message_version` | set explicitly by protocol mapper |
| `event_id` / `request_id` | `message_id` | unified identifier field |
| `producer` / `requested_by` | `producer` | normalize source identity |
| `server` | `target` for commands/RPC or omit into payload/server context | depends on message kind and route semantics |
| `created_at` | `created_at` | unchanged semantic; epoch millis string |
| `expires_at` | `expires_at` | unchanged semantic |
| `correlation_id` | `correlation_id` | unchanged semantic |
| reply-chain metadata if available | `causation_id` | usually newly derived, not directly available today |
| `reply_to` | `reply_to` | rpc request only |
| `timeout_ms` | `timeout_ms` | rpc request only |
| `status` | `status` | rpc response only |
| `error_code` | `error_code` | rpc response only |
| `error_message` | `error_message` | rpc response only |
| `responded_at` | `responded_at` | rpc response only |
| `payload_json` | `payload_json` | unchanged |

### Important migration note
Canonical envelope `target` is not always the same as current payload `server`. For command and RPC messages, `target` should represent the routing target, while business payload fields such as `server` may still remain inside the payload where they are meaningful to consumers.

---

## Implementation Notes For `XCore-plugin`

### Ban and mute
- Introduce explicit protocol DTO mappers rather than publishing `BanData` / `MuteData` directly.
- Prefer deriving `occurredAt` from the moderation action time or audit event time.

### Kick-banned / pardon commands
- Add protocol mappers from `KickBannedPlayer` and `PardonPlayer` into canonical command DTOs.
- Revisit whether `PlayerRefV1` is too strict for command-only payloads with UUID-only identity.

### Audit
- Map the rich audit event into v1 with top-level essentials plus a `details` bag.

## Implementation Notes For `XCore-discord-bot`

### Inbound ban/mute/vote-kick
- Parse canonical v1 first on the main path.

### Outbound kick-banned / pardon
- Publish canonical v1 command payloads.

## Open Follow-Up Questions

### 1. Should non-moderation payloads gain UUID-or-IP target refs too?
Moderation commands and audit records now use moderation-specific target shapes for UUID-or-IP identity, while `PlayerRefV1` remains intentionally strict for event families like ban, mute, and vote-kick.

### 2. Should `occurredAt` become required for all moderation events?
Recommended yes for canonical event payloads, but some current producers may need mapper-generated timestamps during migration.

### 3. Should audit `details` be typed further in v2?
Probably yes after the first migration wave stabilizes.

## Recommended Next Step
Before integrating app code, add fixture-driven validation and, if needed, refine shared target subtypes for commands versus full player references.
