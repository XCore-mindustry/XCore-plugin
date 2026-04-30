# XCore Protocol Migration Plan

## Goal
Provide a phased migration strategy from the current Redis contract model to the future `xcore-protocol` model.

## Migration Principles
- Prefer additive migration over big-bang replacement.
- Keep current consumers operational during transition.
- Move compatibility concerns into explicit adapters.
- Migrate by message family, not by random file batches.
- Start with the highest-value cross-repo family first.

## Phase 0 — Documentation And Design Freeze
Create and approve the design packet in `XCore-plugin`.

Deliverables:
- ADR
- target architecture
- message model
- repo blueprint
- migration plan
- agent playbook

Exit criteria:
- target-state decisions no longer need to be rediscovered during implementation

## Phase 1 — Bootstrap `xcore-protocol`
Create the new repository with:

- README and mission statement
- versioning and compatibility policies
- initial spec directories
- initial fixture directories
- generator scaffolding/configuration
- Java and Python package skeletons for generated artifacts and thin support

Exit criteria:
- the protocol repository exists with agreed structure and contribution rules

## Phase 2 — Moderation Family First

### Included message families
- `moderation.ban.created`
- `moderation.mute.created`
- `moderation.vote-kick.created`
- `moderation.kick-banned.command`
- `moderation.pardon.command`
- `moderation.audit.appended`

### Work items
- define canonical schemas
- define route metadata
- define shared subtypes used by moderation
- create canonical fixtures
- create legacy compatibility fixtures for existing payload forms
- generate Java and Python models for moderation contracts
- add thin handwritten validation/runtime support around generated artifacts

### Application changes
`XCore-plugin`:
- introduce mapping to generated protocol DTOs
- stop treating internal punishment/domain objects as the wire contract

`XCore-discord-bot`:
- adopt canonical outbound payloads
- move alias-heavy parsing into compatibility adapters where still needed

Exit criteria:
- moderation contracts are defined and consumed through the protocol model

## Phase 3 — Discord Linking/Admin Contracts

### Included messages
- `discord.link.confirm.command`
- `discord.unlink.command`
- `discord.link.status-changed`
- `discord.admin-access.changed.command`

Focus:
- canonical field naming
- timestamp consistency
- command vs event separation

## Phase 4 — Maps RPC Contracts

### Included messages
- `maps.list.request`
- `maps.list.response`
- `maps.remove.request`
- `maps.remove.response`

Focus:
- explicit request/response pairing
- canonical request shape
- remove duplicate outbound field naming like `fileName` + `file_name`

## Phase 5 — Chat / Heartbeat / Misc
Migrate:
- chat messages
- global chat
- heartbeat
- server action and join/leave generated message cutovers
- raw fallback removal and remaining legacy event-name cleanup

Focus:
- normalize event type naming
- isolate historical forms into compatibility adapters

## Compatibility Strategy During Migration

### Producers
All new or upgraded producers send the canonical protocol form.

### Consumers
Consumers may temporarily support legacy payload forms, but only via explicit compatibility readers.

### Legacy handling
- legacy names and field aliases remain documented
- compat coverage must include fixtures and tests
- every compat rule gets an owner and sunset condition

## Suggested Current-To-Target Mapping Themes

### Current state patterns to remove
- duplicate canonical field spellings
- outbound duplication of multiple naming styles
- reliance on internal Java class shape for public contracts
- legacy event names handled as first-class canonical types

### Current state patterns to keep conceptually
- broadcast event vs targeted command distinction
- request/response correlation concept
- stream naming discipline as a route metadata concern
- explicit DLQ and idempotency semantics

## Validation Expectations Per Phase
- protocol specs validate
- fixtures validate
- generated Java SDK validates fixtures
- generated Python SDK validates fixtures
- cross-language compatibility checks pass
- application repos pass their targeted migration tests before broader validation

## Risks
- under-specifying compatibility windows
- moving too many families at once
- accidentally turning `xcore-protocol` into a generic utility repository
- keeping legacy aliases in canonical schemas for too long

## Risk Controls
- migrate family by family
- keep canonical schema strict
- document legacy support separately
- require schema + fixture + test updates together

## Completion Criteria
- `XCore-plugin` and `XCore-discord-bot` both consume generated protocol artifacts for the migrated families
- canonical outbound payloads are used consistently
- historical compatibility is localized rather than spread through business logic
