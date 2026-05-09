# XCore Protocol Agent Playbook

## Status: Executed (see `XCore-plugin#5`, `XCore-discord-bot#1`, `xcore-protocol` main)

This playbook was written as implementation guidance for the protocol-first migration. All steps below have been completed. The document is retained as a historical record of implementation order and design decisions.

## Non-Negotiable Decisions Preserved
- The shared repository is **`xcore-protocol`**.
- `xcore-protocol` owns the cross-service wire protocol surface.
- Application repos do not independently redefine protocol contracts.
- Canonical outbound payloads use one naming style only (camelCase).
- Legacy compatibility is NOT retained — first deployment uses canonical-only payloads.
- Migration was executed family by family, starting with moderation.

## Implementation Order (Executed)

### Step 1 — Bootstrap protocol repository structure ✓
Created `xcore-protocol` tree: docs, spec, fixtures, generator config, java modules, python package, compatibility test directories.

### Step 2 — Define canonical moderation specs ✓
Created specs for ban/mute/votekick/kick-banned/pardon/audit plus shared subtypes (PlayerRefV1, ActorRefV1, etc.).

### Step 3 — Add fixtures ✓
Canonical fixtures for all message families including actor semantics fixtures.

### Step 4 — Implement generation scaffolding ✓
Python-based codegen producing Java records and Python frozen dataclasses from canonical JSON Schema specs.

### Step 5 — Implement Java protocol support ✓
- Plugin consumes generated `org.xcore.protocol.generated.*` DTOs.
- `DiscordProtocolMapper` and `ModerationProtocolMapper` produce canonical payloads.
- `RedisProtocolRouteAdapter` routes by generated types.
- `RedisNetworkBackend` serializes via `ProtocolPayload.toPayload()`.

### Step 6 — Implement Python protocol support ✓
- Bot consumes generated `xcore_protocol.generated.*` models.
- `contracts.py` uses strict `from_payload()` parsing.
- `protocol_outbound.py` builds canonical outbound payloads.

### Step 7 — Integrate route metadata ✓
Route registry maps generated types to stream patterns, event types, and RPC metadata.

### Step 8 — Compatibility tests ✓
- Schema validation
- Java fixture validation
- Python fixture validation
- Cross-language roundtrip compatibility tests
- Plugin and bot integration tests green

### Step 9 — All families migrated ✓
1. moderation ✓
2. Discord linking/admin ✓
3. maps RPC ✓
4. chat/heartbeat/misc ✓

## Deliverables Delivered
- protocol repo structure
- canonical specs for all families
- generation scaffolding and generated artifacts
- fixtures and compatibility tests
- integration changes in `XCore-plugin`
- integration changes in `XCore-discord-bot`
