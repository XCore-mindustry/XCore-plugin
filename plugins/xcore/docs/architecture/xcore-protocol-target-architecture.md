# XCore Protocol Architecture

## Goal
Define the architecture for XCore cross-service communication so that protocol behavior is explicit, language-neutral, and implementation-ready.

## Problem Summary
Before `xcore-protocol`, the Redis contract surface was spread across application code in `XCore-plugin` and `XCore-discord-bot`. The protocol behaved like an implementation detail that happened to become public, causing friction in four places:

1. **Ownership**: no single source of truth for message schemas and routing semantics.
2. **Compatibility**: consumers relied on aliases, historical event names, and tolerant parsing.
3. **Evolution**: changing payloads, names, or route semantics was risky across repos.
4. **Interoperability**: future non-Java consumers would need to reverse-engineer behavior from existing code.

## Current State
A dedicated polyglot repository named **`xcore-protocol`** is the canonical source of truth for XCore cross-service communication.

`xcore-protocol` owns:

- message schemas
- envelope structure
- route and stream metadata
- compatibility and deprecation policy
- canonical fixtures
- generated Java and Python protocol DTO/model artifacts
- thin handwritten validation/runtime support around generated artifacts
- cross-language compatibility tests

Application repositories consume the protocol instead of defining it independently.

## Architectural Boundary

### `xcore-protocol` owns
- wire-level event/command/RPC contracts
- message metadata and route manifest
- protocol fixtures and golden examples
- generator inputs and generation configuration
- generated Java/Python protocol model layers
- protocol validation helpers and testkits around the generated surface
- compatibility rules and deprecation windows

### `XCore-plugin` owns
- transport runtime backend
- subscription/request/response orchestration
- domain-to-generated-protocol mapping
- Mindustry integration boundaries
- application business logic

### `XCore-discord-bot` owns
- bot behavior and handlers
- app-specific consumer loops and reconnect strategy
- generated-protocol-to-bot presentation logic
- app-specific failure handling

## Dependency Direction

Both `XCore-plugin` and `XCore-discord-bot` depend on `xcore-protocol`:

```text
             xcore-protocol
             /            \
            /              \
    XCore-plugin      XCore-discord-bot
```

This reverses the previous accidental dependency on implementation details.

## Protocol-First Flow
1. A message family is defined in the protocol repository.
2. Canonical schemas, route metadata, fixtures, and examples are added.
3. Java and Python protocol DTO/model artifacts are generated from the canonical definitions.
4. Thin Java and Python support layers validate and expose the generated artifacts.
5. Application repos adopt the updated version and map their internal models to generated protocol DTOs/models.

## Why Not A Full Ecosystem Monorepo
The protocol is the shared boundary; the applications are not the same product. A full ecosystem monorepo would combine:

- different languages
- different operational lifecycles
- different ownership domains
- unrelated business logic

That would add coordination cost without solving the core problem as cleanly as a dedicated protocol repo.

## Protocol Design Objectives
- **Explicit wire contracts** instead of serializer-shaped payloads
- **Cross-language consistency** without duplicate field naming
- **Stable message identity** with explicit type/version metadata
- **Compatibility by policy** rather than ad hoc runtime tolerance
- **Transport awareness** without over-coupling the model to Redis internals
- **Generated consumption surfaces** so applications depend on protocol artifacts instead of re-declaring wire models

## Migration Slice
The first migrated family was **moderation** because it already crossed repository boundaries and exposed the clearest protocol consistency issues.

Moderation family messages:
- moderation ban event
- moderation mute event
- moderation vote-kick event
- kick-banned command
- pardon command
- moderation audit appended event

All families (moderation, Discord linking/admin, maps RPC, chat/heartbeat/misc) have been migrated. Legacy fallback paths have been removed.

## Success Criteria
- A future agent can identify what belongs in `xcore-protocol` versus application repos.
- The protocol boundary is documented clearly enough to proceed without architecture rework.
- Message, generator, and generated SDK/model ownership are explicit.
