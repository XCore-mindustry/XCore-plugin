# Compatibility Checks

Cross-language compatibility checks between Java and Python live here.

This area covers the currently generated canonical protocol surface:
- maps
- chat/heartbeat
- discord
- moderation

Checks here may include fixture validation, golden-file checks, and roundtrip tests that keep generated Java and Python protocol artifacts aligned with canonical specs and committed generated outputs.

## Current Compatibility Checklist

### Canonical inputs
Compatibility checks for the current generated surface should use canonical protocol inputs only:
- `spec/messages/**/*.json`
- `spec/shared/types/*.json`
- `spec/routes/*.yaml`
- `fixtures/valid/**`
- `fixtures/invalid/**`

Legacy fixtures under `fixtures/legacy/` are out of scope for canonical compatibility checks in this repository.

### Generated artifacts in scope now
The current compatibility scope is limited to generated artifacts for:
- maps
- chat/heartbeat
- discord
- moderation

This includes:
- generated Python models and route registries
- generated Java models and route registries
- committed generated package/root aggregates where applicable

### What alignment means
For the current generated surface, compatibility checks should confirm that:
- valid canonical fixtures can be loaded through the generated model surface
- generated models serialize back to canonical payload dictionaries without alias drift
- invalid canonical fixtures still fail strict validation
- generated route registries stay aligned with canonical route manifests
- committed generated outputs are not stale relative to generator inputs

### Strictness rules
Compatibility checks here must preserve the canonical-only boundary:
- canonical specs remain the source of truth
- generated artifacts are typed consumption surfaces, not alternate protocol definitions
- no tolerant-reader behavior is implied
- no legacy alias support is implied in generated canonical models
- no consumer-side migration adapters are part of this compatibility scope

### Explicitly out of scope
This compatibility area does not currently claim:
- coverage for every protocol family
- coverage for consumer/application integration behavior
- legacy fixture acceptance in canonical generated models
- runtime orchestration, transport workers, or business logic validation
- full Java/Python parity beyond the currently generated maps, chat/heartbeat, discord, and moderation surface

Canonical specs remain the source of truth. Compatibility checks here do not imply that every protocol family or every consumer integration is already covered.
