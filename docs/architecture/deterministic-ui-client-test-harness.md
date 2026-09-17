# Deterministic Client Test Harness for Reactive UI

## Status and Purpose

**Repository Placement Note:** The shared test infrastructure is hosted in `mindustry-testkit` (`core` and `ui` modules, consumed as standard library artifacts via `testImplementation`). `xcore-ui` provides the `UiSession` delivery gateway adapter, and `XCore-plugin` contains the plugin integration scenarios (`MapUiClientIntegrationTest`). Earlier design notes referring to `xcore-ui/src/testFixtures` have been superseded by the standalone `mindustry-testkit` repository.

Related Documents:

- [ADR](../adr/ADR-deterministic-ui-client-test-harness.md)
- [Implementation Plan](../implementation/deterministic-ui-client-test-harness-plan.md)

---

## 1. Goals & Scope

Reproduce and prevent defects across the server-client boundary: window replacement cancellations, delayed asynchronous responses, dialog closing during pending I/O, and divergences between the server-side model and the client wire tree.

Every test scenario asserts across four observation axes:

1. Server domain model and active UI session state;
2. Logical widget tree of the active client dialog;
3. Transport wire messages (`Show`, `Update`, `Hide`, `Choose`);
4. Live observer registrations, callbacks, and subscription lifecycles.

**Out of scope for MVP**: Real MongoDB/Redis I/O, live sockets, game world simulation, graphical rendering, pixel geometry, focus/scroll offsets, texture streaming, and arbitrary schedule permutations.

---

## 2. Architecture

```text
MapMenu -> MapUiController.update -> UiSession -> MenuService
                                                   |
                                      MindustryMenuGateway adapter
                                                   |
                                         FIFO server -> client
                                                   |
                                          HeadlessMenuClient
                                                   |
                                         FIFO client -> server
                                                   |
                                  MenuService.onMenuBuilderResult
```

The real production runtime executes end-to-end: `Session`, `MenuService`, `MapMenu`, `MapUiController`, `UiSession`, and observer services.

Only external system boundaries are substituted:

- **Repository**: Individual controlled `CompletableFuture` instances per query;
- **Preview**: Captured per-request callbacks;
- **Catalog**: Deterministic in-memory map fixtures;
- **Localization**: Predictable identity or formatting resolver;
- **Transport**: Isolated FIFO queues;
- **Task Dispatching**: Arc snapshot-drain `serverPost` queue (`DeterministicQueue`).

---

## 3. Components & Organization

### Shared Test Infrastructure (`mindustry-testkit`)

Hosted in `mindustry-testkit`:

- `core`: `DeterministicQueue` (single-threaded queue with snapshot-drain turn execution);
- `ui`: `HeadlessMenuClient`, `DeterministicUiLoop`, `UiWireMessage`, `UiTranscript`, `UiSnapshot`.

| Component | Responsibility |
| --- | --- |
| `DeterministicUiLoop` | Two independent FIFO transport queues, snapshot-drain `serverPost`, explicit step stepping |
| `HeadlessMenuClient` | Client dialog registry simulation, token tracking, `wasHidden` cancellation suppression |
| `UiWireMessage` | Immutable snapshots of wire messages (`Show`, `Update`, `Hide`, `Choose`) |
| `UiTranscript` | Append-only execution log capturing every transport action for assertions |
| `UiSnapshot` | Wire copy serialized via Mindustry's native binary codec (`NodeBuilder.write/read`) |

### Integration Test Suites

- `xcore-ui`: `UiSessionClientIntegrationTest.java` (exercises `UiSession` through `DeterministicUiLoop` with network delays).
- `XCore-plugin`: `MapUiClientIntegrationTest.java` (package `org.xcore.plugin.ui` for package-private `onMenuBuilderResult` access).

---

## 4. Deterministic Execution & Queues

The harness provides explicit, synchronous stepping:

```java
loop.stepServerToClient();
loop.stepClientToServer();
loop.stepServerPost();
client.click(menuId, action);
client.dismiss(menuId);
```

### Transport Queues

Two independent FIFO queues per connection. Messages advance strictly one at a time. No packet dropping, reordering, or arbitrary interleaving is simulated without explicit step calls.

### Snapshot-Drain Server Post

`loop.stepServerPost()` snapshots tasks pending when the turn begins and executes them in FIFO order. Tasks scheduled by those tasks wait deterministically for the next turn, mirroring `Arc/arc-core/src/arc/util/TaskQueue.java`.

---

## 5. Client Wire Contract

```text
Show(menuId, token, flags, body)
Update(menuId, targetId, body)
Hide(menuId)
Choose(menuId, token, result, values)
```

Key recorded Mindustry client behaviors:

- Every `Show` instantiates a new client `MenuDialog`.
- Replacement (`hidePrevious = true`) hides the old dialog, synchronously emitting cancellation unless already marked `wasHidden`.
- Clicking any button sets `wasHidden = true`, suppressing subsequent cancellation upon dismiss or replacement.
- `Update` and `Hide` packets carry no token on the real wire.
- `UiSession` generates fresh window display tokens on each `open()`, dropping stale tokens at the session layer.

The simulator strictly mirrors these invariants. It does not synthesize artificial guards or perform unauthorized cleanup on behalf of the production code.

---

## 6. Actual-Client Oracle & Parity

To verify that `HeadlessMenuClient` does not drift from Mindustry's real client bytecode, `ActualMenusOracleTest` executes `mindustry.ui.Menus` and `arc.scene.ui.Dialog` directly in plain JVM:

- Runs headless under Arc's official `MockGL20`, `MockGraphics`, `MockAudio`, and `MockApplication`.
- Replays identical replacement and click sequences, asserting bit-for-bit parity.
- Runtime classpath assertions verify exact SHA-256 fingerprints of resolved Mindustry and Arc JARs (`Menus.class` and `Core.class`).

---

## 7. MVP Scenario Matrix

| ID | Scenario | Verified Invariant |
| --- | --- | --- |
| **UI-01** | Show → click → reducer → patch | Target slot updated, sibling slots unchanged |
| **UI-02** | `/maps` with pending summaries | Browser appears immediately; async summaries deliver granular slot `Update` |
| **UI-03** | Immediate vs delayed details | Model and rendered wire tree DSL are strictly identical |
| **UI-04** | Full rerender replacement cancel | Stale replacement cancel with old token does not close active card |
| **UI-05** | A → browser → B; late A response | Late asynchronous details from A do not mutate active card B |
| **UI-06** | A₁ → B → A₂; A₂ resolves before A₁ | Stale response A₁ does not overwrite newer data A₂ (*bug fixed in product*) |
| **UI-07** | Close before request-post | Map events do not leak into a subsequent active session |
| **UI-08** | Explicit close with pending I/O | Immediate hide, session cleared, observer unregisters viewing |
| **UI-09** | Live RTV update vs delayed details | Arriving map stats do not wipe active live RTV vote progress (*bug fixed in product*) |
| **UI-10** | Active window Escape (dismiss) | Escape on active window triggers clean `Close` and server session cleanup |

---

## 8. Verification Anchors

- `Mindustry/core/src/mindustry/ui/Menus.java`: Client menu registries and replacement listeners;
- `Mindustry/core/src/mindustry/ui/builder/UiTreeBuilder.java`: Element ID indexing and action wiring;
- `Mindustry/core/src/mindustry/ui/builder/MenuResult.java`: Wire result model (`token`, `result`, `values`);
- `Arc/arc-core/src/arc/util/TaskQueue.java`: Snapshot-drain queue semantics reference;
- `xcore-ui/src/main/java/org/xcore/ui/runtime/UiSession.java`: Token generation, session lifecycle, and stale token filtering;
- `XCore-plugin/src/main/java/org/xcore/plugin/ui/menu/map/MapUiController.java`: MVI reducer, request generation, and RTV state retention.
