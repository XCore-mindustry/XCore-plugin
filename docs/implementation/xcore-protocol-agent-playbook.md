# XCore Protocol Agent Playbook

## Goal
Give a future agent a concrete, ambiguity-resistant path for implementing the protocol redesign without rediscovering major design decisions.

## Non-Negotiable Decisions Already Made
- The future shared repository is named **`xcore-protocol`**.
- `xcore-protocol` owns the cross-service wire protocol surface.
- Application repos do not independently redefine protocol contracts.
- Canonical outbound payloads use one naming style only.
- Legacy compatibility belongs in explicit adapters, not canonical schemas.
- Migration starts with the moderation family.

## Implementation Order

### Step 1 — Bootstrap protocol repository structure
Create the agreed `xcore-protocol` tree with:
- docs
- spec
- fixtures
- generator configuration
- java modules
- python package
- compatibility test directories

Do not start by implementing runtime loops. Start with the protocol boundary itself.

### Step 2 — Define canonical moderation specs
Create canonical definitions for:
- ban created
- mute created
- vote-kick created
- kick-banned command
- pardon command
- moderation audit appended

Also define any required shared subtypes.

### Step 3 — Add fixtures first
For each message:
- valid canonical fixture
- invalid fixture
- legacy fixture if migration support is required

### Step 4 — Implement generation scaffolding
In `xcore-protocol`:
- add Java/Python generation configuration
- generate protocol DTO/model artifacts from canonical definitions
- add validation around generated output

### Step 5 — Implement Java protocol support
In the Java SDK or integration layer:
- consume generated DTO/model artifacts
- add validators and serialization support around generated artifacts
- add fixture validation tests

In `XCore-plugin`:
- add mapping layer from internal models to generated protocol DTOs
- stop using internal domain/storage objects as direct wire payloads

### Step 6 — Implement Python protocol support
In the Python SDK:
- consume generated models
- add validators and fixture validation tests around generated artifacts

In `XCore-discord-bot`:
- adopt canonical outbound forms
- use compat adapters only where needed for inbound migration

### Step 7 — Integrate route metadata
Move route/source-of-truth metadata into protocol-owned definitions.
Application repos should consume generated route metadata rather than duplicate it.

### Step 8 — Run compatibility tests
At minimum:
- schema validation
- Java fixture validation
- Python fixture validation
- Java/Python roundtrip or golden compatibility tests

### Step 9 — Migrate the next family only after moderation is stable
Proceed in this order:
1. moderation
2. Discord linking/admin
3. maps RPC
4. chat/heartbeat/misc

## What Not To Decide Again
Do not reopen these decisions unless explicitly directed:
- repo name
- protocol-first direction
- moderation-first rollout
- canonical naming policy
- legacy compatibility isolation
- shared repo scope boundaries

## What The Agent Should Clarify Only If Missing
- exact final field set for a specific message schema
- whether a specific timestamp is business-time or transport-time
- whether a specific historical payload still needs a compat window

These are implementation details within the documented model, not reasons to revisit the architecture.

## Suggested Acceptance Criteria By Slice

### For each migrated message family
- canonical schema exists
- route metadata exists
- valid/invalid fixtures exist
- generated Java support exists
- generated Python support exists
- compatibility tests exist
- application repos are updated to use generated canonical protocol artifacts

### For moderation slice completion
- no new moderation producer publishes duplicate field naming styles
- canonical moderation payloads are independent from internal persistence/domain model shape
- bot-side moderation handling can validate canonical moderation messages without alias sprawl in the main path

## Validation Guidance
When app repos are updated:
- use targeted tests during iteration
- finish with broad validation appropriate to the repo
- for `XCore-plugin`, default final validation should align with repository guidance (`./gradlew test`, and `./gradlew test shadowJar` when transport/build surface is affected)

## Deliverables Expected From Implementation Work
- protocol repo structure
- initial moderation specs
- generation scaffolding and generated artifacts
- fixtures and compatibility tests
- integration changes in `XCore-plugin`
- integration changes in `XCore-discord-bot`
- migration notes for the next family

## Definition Of Done For The Planning Packet
This planning packet is considered successful if a future agent can begin implementation without asking:
- where the protocol should live
- what belongs in the shared repo
- which family to migrate first
- whether contracts should be normalized
- how compatibility should be handled
