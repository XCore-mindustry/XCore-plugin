# ADR: Move XCore transport contracts to a protocol-first model

## Status
Accepted

## Context
`XCore-plugin` and `XCore-discord-bot` share Redis-based message contracts for events, commands, and RPC-style request/response flows. The protocol surface is owned by a dedicated shared repository named **`xcore-protocol`**, which serves as the canonical source of truth for XCore cross-process communication.

Before `xcore-protocol`, the contract surface was split across implementation-specific locations:

- Java transport route metadata and envelope construction in `XCore-plugin`
- Java transport event/request/response records in `TransportEvents`
- Python pydantic contract models and Redis bus logic in `XCore-discord-bot`
- Compatibility behavior encoded through aliases, legacy event names, and runtime fallback logic

This caused the protocol to behave as an accidental agreement between implementations rather than an explicit source of truth, made cross-language compatibility depend on tolerant readers and historical knowledge, and allowed internal domain models to leak into the wire format.

## Decision
XCore uses a **protocol-first** model. The shared repository **`xcore-protocol`** owns the canonical cross-service wire protocol surface.

`xcore-protocol` owns:

- wire-level message schemas
- envelope definitions
- route/stream/RPC metadata
- message versioning and compatibility policy
- canonical fixtures/examples
- generated Java and Python protocol DTO/model artifacts
- thin handwritten validation/runtime support around generated artifacts
- cross-language compatibility tests

Application repositories consume generated artifacts and keep only thin mapping layers between internal models and wire models.

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
Migration was executed family by family, starting with moderation because it already crossed repository boundaries and showed the clearest compatibility pain.

Completed phases:

1. Documentation and target-state design in `XCore-plugin`
2. Bootstrap `xcore-protocol`
3. Migrate moderation contracts first
4. Migrate Discord linking/admin contracts
5. Migrate maps RPC contracts
6. Migrate chat/heartbeat/misc flows and clean legacy handling

Legacy fallback paths (raw transport, snake_case aliases, `TransportEvents.ServerScopedEvent`) have been removed. All current producers and consumers publish and parse only canonical generated protocol DTOs.

## Consequences

### Positive
- One source of truth for cross-language protocol behavior
- Safer contract evolution with explicit review and compatibility checks
- Cleaner boundaries between domain models and wire models
- Better onboarding path for future consumers and future agents
- Clear governance for breaking vs additive changes
- Less DTO drift between Java and Python consumers through generated protocol artifacts

### Costs
- Requires ongoing documentation discipline and explicit ownership for protocol changes
- Introduces a shared repository and release process that must be coordinated across consumers

## Alternatives Considered

### 1. Keep protocol ownership in `XCore-plugin`
Rejected because it keeps Python and future consumers secondary to a Java implementation repo.

### 2. Create a schema-only repository
Improves the current state but still leaves Java/Python wire DTOs and model layers duplicated and easier to drift.

### 3. Move the full ecosystem into a single monorepo
Rejected because the problem boundary is the shared protocol surface, not the full application estate. A full monorepo would impose much higher coordination cost than necessary.

## Acceptance Criteria For This Decision
- Documentation defines protocol ownership and boundaries.
- Documentation defines generated protocol DTO/model ownership and consumer dependency direction.
- Implementation work proceeds without re-deciding repo naming, scope, or migration direction.
- Moderation-first migration was the agreed and completed first rollout slice.
