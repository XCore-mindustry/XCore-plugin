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

## Planned First Slice
The first migration slice is the **moderation** family:
- ban created
- mute created
- vote-kick created
- kick-banned command
- pardon command
- moderation audit appended

## Current Status
Canonical message families are defined, and generated Java/Python protocol artifacts now cover the current maps, chat/heartbeat, discord, moderation, and canonical envelope surface. Current hardening work is focused on keeping those generated canonical artifacts and compatibility checks aligned without implying full family or consumer-integration coverage. Java consumer runtime wiring remains intentionally out of scope for this repository and should live in application repositories.

## See Also
- `docs/adr/ADR-001-protocol-first.md`
- `docs/architecture/protocol-overview.md`
- `docs/policies/versioning.md`
- `docs/policies/compatibility.md`
