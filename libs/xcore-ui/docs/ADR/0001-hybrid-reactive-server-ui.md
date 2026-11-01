# ADR 0001: Hybrid Reactive Server UI Engine for Mindustry v160

## Status
Accepted

## Context
In Mindustry v160, upstream introduced `mindustry.ui.builder` and soft-deprecated `Menus.registerMenu`. The old paradigm (`Call.menu`, `Call.followUpMenu`, `Call.textInput`) only permitted flat 2D matrices of text buttons, lacked inline form controls (checkboxes, sliders, text fields), and forced full dialog recreation on every user interaction.

Initial exploration considered three distinct approaches:
1. **Pure Component-Based Virtual DOM** (React/Compose style with reactive signals and AST reconciliation).
2. **Type-Safe Fluent DSL with Record Lenses & Slots** (Zero-allocation Java 25 records with functional optics and typed dynamic slots).
3. **Server-Driven UI & Elm Controller with Hot-Reloadable Templates** (Model-View-Update architecture with external `.msui` live reload).

## Decision
We decide to synthesize these three approaches into a standalone library: **`xcore-ui` (XUI)**.

Key decisions:
1. **Standalone Repository**: Developed as an independent library in `XCore-mindustry/xcore-ui` rather than embedded in `XCore-plugin`. This allows reuse across `aethercore-plugin`, `TheSiege`, `AdminTools`, and community projects without dragging in heavy backend dependencies (Mongo/Redis).
2. **Hybrid Presentation Engine**:
   - **Primary**: Type-safe Java 25 Fluent DSL with Record Lenses (`Lens<S, V>`).
   - **Secondary**: External hot-reloadable `.msui` templates via Java NIO `WatchService`.
3. **Pure Virtual DOM AST (`VNode`)**: Sealed record hierarchy allowing 100% headless testing in pure JUnit 5 without OpenGL or Mindustry server runtimes.
4. **Topological Dirty-Tree Pruner (`SlotPruner`)**: When multiple dynamic slots mutate, nested descendants are dropped to eliminate redundant `Call.menuBuilderUpdate` RPCs.
5. **Content-Addressable Asset Pipeline (`TextureRegistry`)**: SHA-256 deduplication, per-connection delivery tracking via `BitSet`, and virtual-thread PNG streaming (`Vars.netServer.sendTexture`).
6. **Backward Compatibility Adapter**: `LegacyMenuScreenAdapter` translates pre-v160 `MenuScreen` records into `NodeBuilder<?>` trees so existing flows execute without code modifications.

## Consequences

### Positive
- **Zero Visual Flicker**: In-place sub-tree patching via `Call.menuBuilderUpdate` maintains dialog stability, scroll offsets, and input focus.
- **50x Wire Traffic Reduction**: Patching changed slots transmits ~80 bytes instead of ~4 KB full dialog matrices.
- **Strict Compile-Time Safety**: Record lenses eliminate string key typos and unchecked casts.
- **Zero Downtime UI Iteration**: Designers can edit `.msui` files on live servers and observe updates immediately.
- **Headless Test Speed**: Unit tests run in sub-second timeframes under standard JUnit 5.

### Negative / Trade-offs
- Two presentation formats (DSL and `.msui`) require maintaining two frontends that compile into the shared `VNode` AST.
- Dynamic slots require unique slot key paths (`_r/0/slot_name`), managed automatically via DSL builders.
