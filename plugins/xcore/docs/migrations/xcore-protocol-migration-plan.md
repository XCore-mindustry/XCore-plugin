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
- `RedisProtocolRouteAdapter` derives route metadata from generated `ProtocolRoutes` instead of maintaining a hand-written duplicate catalog.
- `RedisProtocolRouteCatalog` is a compatibility/assertion view over generated `ProtocolRoutes`, not an independent source of truth.
- `RedisNetworkBackend` serializes protocol payloads via `ProtocolPayload.toPayload()`.
- `RedisStreamRouter` routes strictly by generated type.
- Consumer idempotency is driven by generated route `idempotentConsumerRecommended`, not by local read-only/mutating buckets.
- Bot uses strict `from_payload()` parsing with no legacy alias normalization.

## Envelope Compatibility Follow-Up

The message payload and route catalog are protocol-first, but the Redis stream envelope is still emitted with the existing XCore Redis field names for cross-service compatibility:

- `schema_version`
- `event_type` / `rpc_type`
- `event_id` / `request_id`
- `idempotency_key`
- `server`
- `created_at` / `expires_at`
- `payload_json`

`xcore-protocol` also defines canonical envelope models with message-oriented fields such as `message_type`, `message_version`, `message_kind`, `message_id`, `target`, `correlation_id`, and `schema_ref`. Moving the live Redis envelope to those names must be a coordinated ecosystem migration because `XCore-discord-bot` and any other consumers may depend on the existing field names.

Recommended migration sequence:

1. Add a dual-read envelope adapter in every consumer that accepts both existing Redis fields and canonical protocol envelope fields.
2. Add dual-write support in producers for canonical envelope fields while retaining existing fields.
3. Add cross-repo contract tests proving plugin and bot can consume both envelope shapes.
4. Deploy tolerant readers before canonical-field writers.
5. After one release window, switch tests to require canonical fields and remove legacy aliases in a dedicated breaking-change release.

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
