# Server-Local Config Refactor Spec

## 1. Motivation & Context

The current TOML redesign solved the user-facing file-format problem, but it did
not complete the architectural refactor.

Today, server-local configuration still has two competing shapes:

- `TomlXcoreConfig` — the structured TOML shape used for persistence and file
  semantics
- `Config` — the legacy runtime shape used by commands, services, and tests

`ConfigTomlMapper` exists because those two shapes do not match. As a result,
the current design keeps routing almost every config workflow through an adapter
layer:

- startup load: `TomlXcoreConfig -> Config`
- legacy JSON migration: `Config -> TomlXcoreConfig -> Config`
- persistence: `Config -> TomlXcoreConfig`
- `xconfig` edit: `Config -> TomlXcoreConfig -> edited tree -> Config`
- `xconfig` show: `Config -> TomlXcoreConfig -> TOML`

This creates several problems:

1. The mapper is the architectural center instead of a boundary detail.
2. `ConfigTomlLoader` owns too many responsibilities: file resolution,
   migration, template creation, TOML I/O, and runtime-model creation.
3. `DataController`, `RuntimeToggleConfigService`, and related helpers mutate a
   legacy runtime model that is no longer the real persistence shape.
4. The runtime model leaks historical field layout into new code and tests.
5. The current design makes future config evolution depend on adapter work
   instead of a clear source of truth.

The user has explicitly rejected the mapper-centric end state and wants a
proper refactor.

## 2. Decision Summary

Server-local configuration should move to a **single structured runtime model**
that matches the TOML boundary closely enough to be the source of truth for:

- startup load
- runtime inspection
- runtime mutation
- persistence back to `xcore.toml`

`ConfigTomlMapper` must stop being the design center. During migration, a
compatibility adapter may still exist temporarily for legacy consumers, but it
must become an edge concern rather than the primary flow for every config
operation.

The first implementation slice will refactor **server-local config only**. It
will not refactor `GlobalConfig` / `TomlSecretsConfig` in the same slice.

## 3. Goals

- Establish a clear server-local config source of truth.
- Remove adapter round-trips from the `xconfig` read/edit/persist flow.
- Reduce the responsibilities currently concentrated in
  `ConfigTomlLoader` and `ConfigTomlMapper`.
- Preserve TOML-first loading and legacy JSON migration behavior.
- Keep command UX stable while refactoring internals.

## 4. Non-Goals

- No hot reload.
- No simultaneous full refactor of `GlobalConfig`.
- No removal of legacy JSON migration in the first slice.
- No broad rewrite of every direct `Config` consumer in one step.
- No command registration redesign or unrelated controller cleanup.

## 5. Current Architecture

### 5.1 Current Source of Truth Problem

The current codebase behaves as if server-local config has two owners:

- `TomlXcoreConfig` owns persistence structure and TOML semantics.
- `Config` owns runtime mutation and most consumers.

Because ownership is split, the mapper became a permanent bridge instead of a
temporary migration device.

### 5.2 Current Coupling Surfaces

The current server-local flow crosses these classes:

- `ConfigFactory`
- `ConfigTomlLoader`
- `ConfigTomlMapper`
- `TomlXcoreConfig`
- `Config`
- `ServerLocalConfigTomlStore`
- `ServerLocalConfigTomlRenderer`
- `ServerLocalConfigPathEditor`
- `DataController`
- `RuntimeToggleConfigService`
- `MaintainController`

The most problematic hotspots are:

1. **`ConfigTomlLoader`**
   - resolves files
   - migrates legacy JSON
   - writes TOML
   - reads TOML
   - creates runtime models

2. **`ConfigTomlMapper`**
   - used in both directions
   - used in startup, migration, render, edit, and persistence

3. **`ServerLocalConfigPathEditor`**
   - edits by converting `Config -> TomlXcoreConfig -> JSON tree -> TomlXcoreConfig -> Config`

4. **`DataController` / `RuntimeToggleConfigService`**
   - mutate a legacy model, then depend on TOML mapping during persistence

## 6. Target Architecture

### 6.1 Target Boundary

Server-local config should have one runtime owner, referred to in this spec as
`ServerLocalConfigModel`.

This model should:

- represent the structured server-local config shape
- carry runtime defaults and normalization rules
- be directly used by load/render/edit/persist flows
- be stable enough for operator-facing commands

In the target state:

- TOML file I/O reads/writes `ServerLocalConfigModel` directly, or through a
  very thin serialization DTO if a separate DTO still proves necessary
- `ServerLocalConfigPathEditor` edits `ServerLocalConfigModel` directly
- `ServerLocalConfigTomlRenderer` renders `ServerLocalConfigModel` directly
- `ServerLocalConfigTomlStore` persists `ServerLocalConfigModel` directly
- `DataController` and `RuntimeToggleConfigService` operate on the same model

### 6.2 Allowed Transitional Compatibility

During rollout, legacy consumers that still depend on `Config` may use a
**temporary compatibility adapter**. That adapter must be constrained to the
consumer boundary and must not remain the center of every config workflow.

Acceptable temporary pattern:

`ServerLocalConfigModel -> legacy Config view`

Not acceptable as end state:

`Config <-> TomlXcoreConfig` in every load/edit/store/render path.

### 6.3 Desired Dependency Direction

Target direction for server-local config:

```text
DataController / RuntimeToggleConfigService
    -> ServerLocalConfigRuntime boundary
        -> persistence/render/edit helpers
            -> TOML file boundary
```

The controller should not be the architecture. The runtime config boundary must
own mutation semantics, while file I/O remains an adapter concern.

## 7. Ownership & Responsibilities

### 7.1 `ConfigFactory`
- remains the startup composition boundary
- wires the runtime config owner and related helpers
- should not assemble config behavior from multiple competing shapes forever

### 7.2 Loader Boundary
- resolve `xcore.toml`
- handle legacy `xcconfig.json` migration
- deserialize into the structured runtime model
- keep migration/backup behavior explicit

### 7.3 Runtime Config Boundary
- own normalization/default rules relevant to runtime behavior
- expose stable mutation and read surfaces for controllers/services
- prevent file-format details from leaking into unrelated consumers

### 7.4 Renderer / Store / Path Editor
- act on the structured runtime model
- stop depending on a central `ConfigTomlMapper`
- remain focused helpers, not mini-loaders

## 8. First Vertical Slice Scope

The first slice should refactor the server-local config lifecycle end-to-end.

### In Scope

- `ConfigTomlLoader`
- `ConfigFactory`
- `TomlXcoreConfig`
- `Config`
- `ConfigTomlMapper` (reduction / edge-only usage)
- `ServerLocalConfigTomlStore`
- `ServerLocalConfigTomlRenderer`
- `ServerLocalConfigPathEditor`
- `DataController`
- `RuntimeToggleConfigService`
- `MaintainController`

### Out of Scope

- `GlobalConfig`
- `TomlSecretsConfig`
- secrets/global provider refactor
- cross-repo config consumers
- removal of legacy JSON migration support

### Slice Success Criteria

The first slice is successful when:

1. `xconfig show` renders from the new runtime owner without adapter round-trip.
2. `xconfig edit` mutates the new runtime owner without adapter round-trip.
3. runtime toggles persist through the new runtime owner.
4. startup still loads TOML first and still migrates legacy JSON safely.
5. any temporary compatibility adapter is reduced to a narrow legacy-consumer edge.

## 9. Persistence & File-Format Semantics

The file-level behavior must stay stable during the refactor:

- `xcore.toml` remains the primary server-local config file.
- `xcconfig.json` remains a legacy migration source only.
- migration still creates `.bak-YYYYMMDD-HHMMSS` backups.
- normalization/default semantics remain compatible with current TOML behavior.
- unknown TOML fields should remain rejected by strict parsing.

This refactor changes runtime ownership, not the user-facing TOML contract.

## 10. Proposed Rollout Plan

### Phase 1 — Design Boundary
- introduce and document the structured runtime owner for server-local config
- define where compatibility adapters are still temporarily allowed

### Phase 2 — Internal Server-Local Runtime Slice
- make store/render/path editor operate on the new runtime owner
- move `DataController` and `RuntimeToggleConfigService` to that owner
- keep startup and persistence behavior stable

### Phase 3 — Legacy Consumer Reduction
- identify remaining direct `Config` consumers in server-local paths
- migrate them subsystem by subsystem
- reduce `ConfigTomlMapper` to a temporary compatibility edge

### Phase 4 — Evaluate Global Config Strategy
- decide whether `GlobalConfig` gets the same structured-runtime treatment
- do not start this until the server-local slice proves the pattern

## 11. Validation Strategy

### Targeted Validation
- config loader tests
- path editor tests
- `DataControllerTest`
- `MaintainControllerTest`
- TOML store / render tests

### Broad Validation
- `./gradlew test`
- `./gradlew test shadowJar` for packaging/startup-sensitive slices

### Behavioral Invariants
- no regression in TOML-first startup behavior
- no regression in JSON migration/backups
- no regression in `xconfig` path support
- no regression in dedicated toggle-owned path guard behavior

## 12. Risks & Mitigations

### Risk: Widespread `Config` field usage
**Mitigation:** constrain the first slice to server-local operator flows and
runtime toggles before touching broader consumers.

### Risk: Breaking startup ordering
**Mitigation:** keep `ConfigFactory` as the composition root and preserve the
current load ordering contract.

### Risk: Replacing one adapter maze with another
**Mitigation:** make the new runtime owner the design center; do not create a
second permanent compatibility layer.

### Risk: Behavior drift in normalization/defaults
**Mitigation:** keep current TOML contract stable and lock it with targeted
tests before and after the slice.

## 13. Open Questions / Deferred Work

1. Should the long-term public runtime type still be named `Config`, or should a
   new explicit server-local config type own the boundary?
2. Should `TomlXcoreConfig` become the runtime model directly, or should a new
   structured runtime model replace both `TomlXcoreConfig` and legacy `Config`?
3. How much compatibility is required for direct field access in existing
   server-local consumers during rollout?
4. When should `GlobalConfig` receive the same treatment?
5. When can `ConfigTomlMapper` be deleted entirely instead of reduced to an
   edge adapter?

## 14. First Vertical Slice Planning Inputs

The first implementation plan should reference at least these files:

- `src/main/java/org/xcore/plugin/config/ConfigFactory.java`
- `src/main/java/org/xcore/plugin/config/ConfigTomlLoader.java`
- `src/main/java/org/xcore/plugin/config/ConfigTomlMapper.java`
- `src/main/java/org/xcore/plugin/config/TomlXcoreConfig.java`
- `src/main/java/org/xcore/plugin/config/Config.java`
- `src/main/java/org/xcore/plugin/config/ServerLocalConfigTomlStore.java`
- `src/main/java/org/xcore/plugin/config/ServerLocalConfigTomlRenderer.java`
- `src/main/java/org/xcore/plugin/config/ServerLocalConfigPathEditor.java`
- `src/main/java/org/xcore/plugin/command/controller/server/DataController.java`
- `src/main/java/org/xcore/plugin/command/controller/server/RuntimeToggleConfigService.java`
- `src/main/java/org/xcore/plugin/command/controller/server/MaintainController.java`

This list is intentionally server-local only. `GlobalConfig` and
`TomlSecretsConfig` remain a later decision.
