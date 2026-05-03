# xcore-protocol

Canonical cross-service protocol for the XCore ecosystem.

## Mission
`xcore-protocol` is the canonical source of truth for XCore cross-service communication.

It defines:
- message schemas
- envelope structure
- route and stream metadata
- versioning and compatibility rules
- canonical fixtures
- generation inputs and tooling
- generated Java and Python protocol DTO/model artifacts
- thin handwritten validation and repository-tooling support around generated artifacts
- cross-language compatibility tests

## Scope
This repository contains only **cross-process / cross-service wire protocol artifacts**.

### In scope
- event, command, and RPC contracts
- envelope definitions
- route metadata
- shared payload subtypes
- protocol fixtures and validation helpers
- generation configuration and generated Java/Python protocol artifacts

### Out of scope
- application business logic
- Discord handlers or presentation code
- Mindustry runtime integration
- Mongo persistence
- app-specific reconnect/worker orchestration

## Repository Layout
- `docs/` — ADRs, architecture notes, policies, migration notes
- `spec/` — protocol specs, shared types, envelopes, routes, generation inputs
- `fixtures/` — valid, invalid, and legacy examples
- `generators/` — language generation config/templates/scripts
- `java/` — generated Java protocol artifacts for the canonical model surface
- `python/` — generated Python protocol package and thin helpers
- `compat/` — cross-language compatibility checks
- `scripts/` — validation and generation helper scripts

## Migrated Families

All planned message families are fully migrated and operational:

- **Moderation** — ban, mute, vote-kick, kick-banned, pardon, audit
- **Discord** — link-confirm, unlink, link-status, admin-access, link-code
- **Maps** — list request/response, remove request/response
- **Chat/Heartbeat** — chat messages, global chat, heartbeat, join/leave, server actions, player state commands

## Current Status

All canonical message families are defined with JSON Schema specs and canonical fixtures. Generated Java records (`org.xcore.protocol.generated.*`) and Python frozen dataclasses (`xcore_protocol.generated.*`) cover the full protocol surface. `XCore-plugin` and `XCore-discord-bot` both consume generated artifacts as their sole transport model. Cross-language compatibility checks and full CI validation chain are in place.

## See Also
- `docs/adr/ADR-001-protocol-first.md`
- `docs/architecture/protocol-overview.md`
- `docs/policies/versioning.md`
- `docs/policies/compatibility.md`
