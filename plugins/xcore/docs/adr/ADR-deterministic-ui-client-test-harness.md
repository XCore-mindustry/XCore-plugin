# ADR: Deterministic Client Test Harness for Reactive UI

## Status

Accepted — fully implemented and verified:

- `mindustry-testkit` (`core` and `ui`) created as an independent repository and published;
- Actual-client parity proven in plain JVM against real `mindustry.ui.Menus` and `arc.scene.ui.Dialog` (`ActualMenusOracleTest`, `ActualDialogHideTest`) with SHA-256 fingerprinting of loaded JARs;
- `DeterministicUiLoop` with two FIFO transport queues and snapshot-drain `serverPost` implemented;
- All MVP scenarios (UI-01..UI-10) implemented in `MapUiClientIntegrationTest` and passing green;
- Production defects in `MapUiController` fixed: UI-06 (stale details race) and UI-09 (live RTV reset on async details arrival), plus UI-10 (clean active-window Escape close via display generation tokens).

*Architecture note on repository placement*: The shared toolkit is hosted in its own repository `mindustry-testkit` and consumed as standard library artifacts (`org.xcore.testkit:ui` and `:core`), replacing earlier test-fixtures placement in `xcore-ui`.

## Context

Isolated reducer unit tests and static DSL assertions cannot reproduce client window replacement cancellation, reverse network callbacks, or race conditions between async storage responses and user navigation. A comprehensive test harness was required to exercise the entire server-side pipeline with a controllable client side, free from external databases, live sockets, or unpredictable thread sleeps.

The Mindustry client exhibits subtle behaviors:

- Window replacement can trigger synchronous cancellation of the old dialog;
- Button clicks set `wasHidden = true`, suppressing subsequent cancellation on dismiss;
- Token was historically reused across rerenders within the same session;
- `Update` and `Hide` packets carry no token.

The simulator must not paper over these behaviors with artificial guards that are absent in the real product.

## Decision

We establish a **deterministic headless UI test harness**:

1. **Real Server Runtime**: Real `Session`, `MenuService`, `UiSession`, `MapUiController`, and observer services.
2. **Semantic Client Simulator (`HeadlessMenuClient`)**: Accurately reproduces recorded Mindustry client dialog semantics without graphics or geometry.
3. **Deterministic Transport Loop (`DeterministicUiLoop`)**: Two separate FIFO queues (Server-to-Client and Client-to-Server) alongside an Arc snapshot-drain `serverPost` queue.
4. **Actual-Client Oracle (`ActualMenusOracleTest`)**: Headless execution of actual `mindustry.ui.Menus` in plain JVM under Arc `Mock*` graphics classes, ensuring executable parity against fingerprinted v160 JARs.
5. **Display Generation Tokens**: `UiSession` assigns a fresh monotonically increasing token on each `open()`, dropping stale window tokens in `handle()`, allowing clean Escape closing on active windows while dropping replacement cancels.

See [Specification](../architecture/deterministic-ui-client-test-harness.md) and [Implementation Plan](../implementation/deterministic-ui-client-test-harness-plan.md).

## Alternatives Considered

### Reducer-only unit tests

Retained, but insufficient on their own: they cannot test client-driven callbacks, window replacement races, or the final client-side wire state.

### Full game client (Xvfb / HeadlessApplication) for every test

Too heavy and slow for fast deterministic test suites. Used as a dedicated, independent oracle rather than the runtime environment for every test.

### Simulator with automatic filtering / auto-cleanup

Rejected: adding guards into the test fixture that do not exist in production hides real regressions.

### Bespoke MapUiCmd interpreter

Rejected: would test an alternate execution pipeline rather than the real production `UiController.update` path.

## Consequences

### Positive

- Concurrency and async races are reproducible step-by-step without live players or external infrastructure.
- Tests assert on the full chain: domain model, rendered client tree, transport delivery, and observer registrations.
- Independent actual-client oracle guards against client simulator drift.
- Fast execution: full 10-scenario suite runs in seconds.

### Trade-offs

- Semantic simulator must be maintained when upgrading Mindustry / Arc versions.
- Simulator models `menuBuilder` wire semantics; visual layout, font kerning, and texture streaming are tested separately.

## Verification

Acceptance criteria: Scenarios UI-01 through UI-10 in `MapUiClientIntegrationTest`, executable parity verified against fingerprinted v160 bytecode, and pristine CI pipeline passing on GitHub Actions.
