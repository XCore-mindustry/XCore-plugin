# XCore Protocol Message Model

## Goal
Define the canonical message model for the `xcore-protocol` repository, including envelope rules, payload conventions, naming, versioning, and compatibility handling.

## Core Principle
The public wire contract must not be an accidental serialization of internal application models. Protocol messages are explicit transport DTOs with stable meaning, and Java/Python protocol DTO/model layers should be generated from the canonical protocol definitions rather than hand-maintained independently in consumer repos.

## Message Categories

### Event
- broadcast or fan-out notification
- producer does not wait for a response
- usually replayable for observers depending on stream retention

### Command
- targeted instruction toward a specific server or logical target
- producer does not wait for a response
- typically non-replayable as a business action

### RPC Request / Response
- request expects a response
- request and response are linked by correlation metadata
- timeouts and error semantics are part of the protocol contract

## Envelope Model

### Long-term target
The protocol should converge on a unified envelope model with explicit metadata:

- `message_kind`
- `message_type`
- `message_version`
- `message_id`
- `correlation_id` (when needed)
- `causation_id` (recommended when derived from another message)
- `producer`
- `target` (for targeted messages)
- `created_at`
- `expires_at` (when relevant)
- `schema_ref`
- `content_type`
- `payload_json`

### Current state
The Redis field layout in `XCore-plugin` and `XCore-discord-bot` follows the canonical envelope and payload rules described here. Legacy aliases and historical event-name compatibility have been removed.

## Naming Rules

### Canonical policy
- Envelope fields use **snake_case**.
- Payload fields use **camelCase**.

### Examples
- Envelope: `message_type`, `created_at`, `correlation_id`
- Payload: `playerUuid`, `adminDiscordId`, `occurredAt`

### Non-goal
The protocol must not treat multiple spellings of the same canonical field as equal in the schema. Legacy spellings are compatibility concerns, not canonical contract design.

## Time Rules

### Envelope metadata
Use epoch milliseconds for transport metadata:
- `created_at`
- `expires_at`
- `responded_at`

### Payload business timestamps
Use ISO-8601 UTC for business timestamps unless the message family has a strong reason not to.

### Rationale
This keeps transport metadata simple for timeouts/retention and keeps business timestamps readable and consistent across languages.

## Message Identity And Versioning

### Canonical identity
Every message must have:
- `messageType`
- `messageVersion`

Recommended examples:
- `moderation.ban.created` / version `1`
- `discord.link.confirm.command` / version `1`
- `maps.list.request` / version `1`

### Rule
Breaking changes require a new message version. Do not change meaning in place.

## Generated Model Strategy

### Source of truth
Canonical schemas, shared subtypes, envelope definitions, and route manifests are the authored source of truth.

### Generated outputs
`xcore-protocol` should generate Java and Python protocol DTO/model artifacts from those canonical definitions.

### Consumer rule
Application repos should depend on generated protocol artifacts and keep only thin mapping/adaptation layers between internal models and wire models.

### Non-goal
Do not generate runtime worker loops, Redis connection management, or business orchestration from protocol definitions.

## Shared Payload Subtypes
To improve consistency without merging unrelated business messages, define reusable subtypes:

- `ActorRef`
- `PlayerRef`
- `ServerRef`
- `DiscordIdentityRef`
- `ExpirationInfo`
- `MapRef`
- `AuditContext`

These subtypes should be reused across schemas where they model the same concept.

## Contract Strategy

### Normalize now
- canonical field names
- canonical time formats
- message identity/versioning
- canonical route metadata

### Keep separate
Semantically distinct business messages should remain distinct even if they share many fields.

Examples that should remain separate:
- ban vs mute
- command vs event around Discord linking
- maps list vs maps remove RPC

### Use compatibility adapters
Legacy event names, duplicate spellings, and historical payload forms should move into explicit compatibility adapters.

## Compatibility Rules

### Canonical outbound rule
All new producers publish only the canonical schema form.

### Tolerant inbound rule
Consumers may temporarily accept historical forms through dedicated compatibility logic, but canonical parsing must remain strict.

### Legacy sunset rule
Compatibility shims must be documented with a deprecation window and test coverage.

## Route Manifest Philosophy
Each message definition should include or link to route metadata describing:

- stream/channel pattern
- message kind
- target scope
- TTL policy
- idempotency expectations
- replay expectations
- DLQ policy
- owner

The route manifest becomes the single source of truth for subscription/publish semantics and should feed generated route/metadata bindings exposed by the protocol repository.

## Modeled Families
All message families are defined and migrated:
- moderation
- Discord linking/admin changes
- maps RPC
- chat/heartbeat/misc

## Success Criteria
- A future agent can implement or generate transport DTO/model layers without deciding naming, timing, or versioning policy on the fly.
- The model is strict enough to remove accidental drift but flexible enough to support compatibility adapters during migration.
