# ADR: Move XCore transport contracts to a protocol-first model

## Status
Proposed

## Context
`XCore-plugin` and `XCore-discord-bot` currently share Redis-based message contracts for events, commands, and RPC-style request/response flows. Those contracts work in practice, but the protocol surface is split across multiple implementation-specific locations:

- Java transport route metadata and envelope construction in `XCore-plugin`
- Java transport event/request/response records in `TransportEvents`
- Python pydantic contract models and Redis bus logic in `XCore-discord-bot`
- Compatibility behavior encoded through aliases, legacy event names, and runtime fallback logic

This creates several problems:

1. The protocol exists as an accidental agreement between implementations instead of an explicit source of truth.
2. Cross-language compatibility depends on tolerant readers, duplicate field names, and historical knowledge.
3. Internal domain/storage models can leak into the wire format.
4. Route metadata and transport semantics are hard to evolve safely across repos.
5. Future consumers would have to reverse-engineer the protocol from application code.

## Decision
Adopt a **protocol-first** model and define a future shared repository named **`xcore-protocol`** as the canonical source of truth for XCore cross-process communication.

`xcore-protocol` will own:

- wire-level message schemas
- envelope definitions
- route/stream/RPC metadata
- message versioning and compatibility policy
- canonical fixtures/examples
- generated Java and Python protocol DTO/model artifacts
- thin handwritten validation/runtime support around generated artifacts
- cross-language compatibility tests

The first implementation step is **documentation-first** inside `XCore-plugin`, followed by a phased migration into the future `xcore-protocol` repository.

## Why `xcore-protocol`
`xcore-protocol` was chosen over names like `xcore-transport` or `xcore-contracts` because it best reflects the intended boundary:

- broader than raw schema files alone
- not permanently tied to Redis internals
- centered on the official language of communication between XCore components

## Scope Boundaries
`xcore-protocol` is intended to contain only **cross-process / cross-service wire protocol artifacts**.

It should include:

- event, command, and RPC message definitions
- envelope metadata definitions
- protocol validation helpers and fixtures
- route metadata and compatibility policy
- generated Java/Python DTOs and models derived from protocol specs
- thin Java/Python support libraries for parsing/building/validation around generated artifacts

It should not include:

- application business logic
- Discord UX or handlers
- Mongo repositories
- Mindustry runtime integration
- reconnect loops or app-specific worker orchestration
- general shared helper dumping grounds

## Contract Strategy
The immediate protocol redesign will:

1. Normalize canonical field names, time formats, and versioning rules.
2. Keep semantically distinct business messages separate.
3. Extract shared payload subtypes rather than merging unrelated messages by shape.
4. Move legacy aliases and historical event-name compatibility into dedicated compatibility adapters.
5. Stop treating internal application models as the public wire contract.
6. Generate Java and Python protocol model layers from canonical specs instead of maintaining duplicate hand-written wire DTOs in consumer repos.

## Rollout Strategy
Migration will start with the **moderation** contract family because it already crosses repository boundaries and shows the clearest compatibility pain.

Phases:

1. Documentation and target-state design in `XCore-plugin`
2. Bootstrap `xcore-protocol`
3. Migrate moderation contracts first
4. Migrate Discord linking/admin contracts
5. Migrate maps RPC contracts
6. Migrate chat/heartbeat/misc flows and clean legacy handling

## Consequences

### Positive
- One source of truth for cross-language protocol behavior
- Safer contract evolution with explicit review and compatibility checks
- Cleaner boundaries between domain models and wire models
- Better onboarding path for future consumers and future agents
- Clear governance for breaking vs additive changes
- Less DTO drift between Java and Python consumers through generated protocol artifacts

### Costs
- Requires initial design effort and documentation discipline
- Introduces a new repository and release process
- Needs explicit ownership and change governance
- Requires migration adapters during the transition period

## Alternatives Considered

### 1. Keep protocol ownership in `XCore-plugin`
Rejected as the target state because it keeps Python and future consumers secondary to a Java implementation repo.

### 2. Create a schema-only repository
Improves the current state but still leaves Java/Python wire DTOs and model layers duplicated and easier to drift.

### 3. Move the full ecosystem into a single monorepo
Rejected because the problem boundary is the shared protocol surface, not the full application estate. A full monorepo would impose much higher coordination cost than necessary.

## Acceptance Criteria For This Decision
- Documentation clearly defines target-state protocol ownership and boundaries.
- Documentation clearly defines generated protocol DTO/model ownership and consumer dependency direction.
- Future implementation work can proceed without re-deciding repo naming, scope, or migration direction.
- Moderation-first migration remains the agreed first rollout slice.
