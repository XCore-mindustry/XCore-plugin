# XCore Protocol Migration Record

## Status: Implemented

The migration from ad-hoc Redis contract model to canonical `xcore-protocol` model is complete across all planned message families. This document records what was done, not what remains to be done.

## Relationship to PRs

- `XCore-plugin#5` — plugin-side protocol adoption
- `XCore-discord-bot#1` — bot-side protocol adoption
- `xcore-protocol` main branch — canonical schemas, fixtures, generated Java/Python artifacts

## Completed Phases

### Phase 0 — Documentation And Design Freeze
Created the design packet in `XCore-plugin`:
- ADR (`docs/adr/ADR-redis-to-protocol-first.md`)
- target architecture (`docs/architecture/xcore-protocol-target-architecture.md`)
- message model (`docs/architecture/xcore-protocol-message-model.md`)
- repo blueprint (`docs/architecture/xcore-protocol-repository-blueprint.md`)
- migration plan (this document)
- agent playbook (`docs/implementation/xcore-protocol-agent-playbook.md`)

### Phase 1 — Bootstrap `xcore-protocol`
Created the `xcore-protocol` repository with:
- README and mission statement
- versioning and compatibility policies
- spec directories for all message families
- fixture directories
- generator and codegen pipeline
- Java module with generated protocol DTOs + runtime support (`ProtocolPayload`)
- Python package with generated models + validation helpers
- cross-language compatibility tests

### Phase 2 — Moderation Family
Implemented:
- `moderation.ban.created`
- `moderation.mute.created`
- `moderation.vote-kick.created`
- `moderation.kick-banned.command`
- `moderation.pardon.command`
- `moderation.audit.appended`

Plugin and bot both publish/consume canonical moderation DTOs via generated `org.xcore.protocol.generated.messages.moderation.*`.

### Phase 3 — Discord Linking/Admin Contracts
Implemented:
- `discord.link.confirm.command`
- `discord.unlink.command`
- `discord.link.status-changed`
- `discord.admin-access.changed.command`
- `discord.link-code-created`

### Phase 4 — Maps RPC Contracts
Implemented:
- `maps.list.request`
- `maps.list.response`
- `maps.remove.request`
- `maps.remove.response`

### Phase 5 — Chat / Heartbeat / Misc
Implemented:
- chat messages (`ChatMessageV1`)
- global chat (`ChatGlobalV1`)
- server heartbeat (`ServerHeartbeatV1`)
- player join/leave (`PlayerJoinLeaveV1`)
- server actions (`ServerActionV1`)
- player state change commands (nickname, badge, cache reload, password reset, etc.)
- server command execution (`ServerCommandExecuteCommandV1`)

Legacy fallback paths (raw transport, snake_case aliases, `TransportEvents.ServerScopedEvent`) have been removed.

## Transport Model (Current State)

- Plugin publishes and consumes only canonical generated protocol DTOs.
- `RedisRouteRegistry` registers generated protocol classes only.
- `RedisNetworkBackend` serializes protocol payloads via `ProtocolPayload.toPayload()`.
- `RedisStreamRouter` routes strictly by generated type.
- Bot uses strict `from_payload()` parsing with no legacy alias normalization.

## Validation Surface

- protocol schema validation
- canonical fixture validation (Java + Python)
- cross-language roundtrip compatibility tests
- plugin integration tests (`./gradlew test`)
- bot integration tests (`uv run pytest tests/`)

## Design Decisions Preserved

- `xcore-protocol` owns the canonical wire contract surface.
- Application repos consume generated artifacts, not self-defined DTOs.
- Canonical payloads use one field naming style (camelCase).
- `actor` = concrete initiator, `source` = provenance/authority.
- Migration was additive by family, not big-bang.
- No backward-compatibility legacy paths retained — first deployment uses canonical-only schema.
