# Implementation Plan: Deterministic UI Client Test Harness

## Status

Stages 1–4 are **fully completed and verified by tests**:

- `mindustry-testkit`: `core` and `ui` modules with deterministic task queue, binary snapshot codec, `HeadlessMenuClient`, `DeterministicUiLoop`, `UiWireMessage`, `UiTranscript`, and actual-client oracle (`ActualDialogHideTest`, `ActualMenusOracleTest`) with SHA-256 artifact fingerprinting.
- `xcore-ui`: runtime delivery gateway adapter and `UiSessionClientIntegrationTest`.
- `XCore-plugin`: `MapUiClientIntegrationTest` covering all 10 scenarios (UI-01..UI-10).
- Fixed 2 production defects in `MapUiController`: UI-06 (stale details query race) and UI-09 (live RTV vote reset on async stats arrival), plus UI-10 (clean active-window Escape close via display generation tokens).
- ADR status transitioned to `Accepted`.
- Published to GitHub and central XCore Maven repository via GitHub Actions.

Related Documents:

- [Specification and Scenario Matrix](../architecture/deterministic-ui-client-test-harness.md)
- [ADR and Architecture Decisions](../adr/ADR-deterministic-ui-client-test-harness.md)

Execution discipline: contract → shared harness → plugin integration → verified bugfixes → CI publishing.

---

## Stage 1. Freeze the Real Client Wire Contract

- [x] Resolve Gradle dependencies in `xcore-ui` and `XCore-plugin`; record exact coordinates and SHA-256 digests of resolved JARs (`Menus.class`: `283c9b...`, `Core.class`: `c2df13...`).
- [x] Verify `Menus`, `UiTreeBuilder`, `MenuResult`, `TypeIO`, and Arc `TaskQueue` against resolved artifacts.
- [x] Document supported wire shape, flags, and lack of tokens on `Update`/`Hide` packets.
- [x] Prepare canonical transcripts: replacement before/after click, dismiss, server hide, slot patches.
- [x] Implement in-JVM feasibility spike for real `Menus` / `Scene` without rendering (`ActualDialogHideTest` and `ActualMenusOracleTest` in `mindustry-testkit`).
- [x] Prove that headless execution runs in plain JVM under Arc `Mock*` classes without requiring Xvfb or Mesa.

**Acceptance**: Independent behavioral observations of the real client obtained and documented.

---

## Stage 2. Shared Test Fixtures (`mindustry-testkit`)

Placement: Dedicated repository `mindustry-testkit` (`core` and `ui` modules), published to Maven repository and consumed via `testImplementation`.

- [x] Bootstrap `mindustry-testkit`, verify local consumption via `mavenLocal` and `--include-build`.
- [x] Implement `UiWireMessage` and wire payload serialization via native codec (`UiSnapshot`).
- [x] Implement `DeterministicUiLoop`: FIFO Server-to-Client and Client-to-Server queues + snapshot-drain `serverPost`.
- [x] Prove non-inline delivery, FIFO preservation, and nested task turn deferral via tests (`DeterministicQueueTest`, `DeterministicUiLoopTest`).
- [x] Implement `HeadlessMenuClient` matching recorded client profile; fail-fast on unsupported operations.
- [x] Implement `UiTranscript`: steps, causes, wire messages, windows, and queues.
- [x] Implement `UiSessionClientGateway` and UI-01 in `UiSessionClientIntegrationTest`.
- [x] Prove exact behavioral parity between `HeadlessMenuClient` and `ActualMenusOracleTest`.

**Acceptance**: Real reducer executes via reverse client messages; slot patch updates target slot while preserving siblings. Zero real thread sleeps, background threads, or external sockets.

---

## Stage 3. Plugin Integration Fixture

Location: `XCore-plugin/src/test/java/org/xcore/plugin/ui/MapUiClientIntegrationTest.java`.

- [x] Integrate testkit library artifacts; verify standalone resolution without composite build.
- [x] Wire real `Session`, `MenuService`, `MapMenu`, `MapUiController`, `UiSession`, and observer services.
- [x] Exercise the production builder path (`Player.con`, `globalMenuBuilderId`).
- [x] Controlled futures per details request and summary cache lookup.
- [x] Isolate callbacks and mock preview cache hits.
- [x] Redirect `Core.app.post` into `loop.serverPost()`; prevent static `Core.app` leakage across tests.
- [x] Track live observer registrations; clean up state in `@AfterEach`.

**Acceptance**: Asserts domain model, client wire tree, transport delivery, and observer registrations.

---

## Stage 4. Regression Matrix (UI-01..UI-10)

- [x] **UI-01**: Browser → click map → details opens and renders metadata.
- [x] **UI-02**: `/maps` with pending summaries shows browser immediately; async summaries patch `slot_map_table`.
- [x] **UI-03**: Immediate vs delayed details yield identical model and wire tree DSL.
- [x] **UI-04**: Full rerender replacement cancel with old token does not close active card.
- [x] **UI-05**: Late details from map A do not mutate active card B after navigation.
- [x] **UI-06**: Stale A₁ details response does not overwrite newer A₂ response (*bug fixed in product*).
- [x] **UI-07**: Close before request-post does not leak map events into a subsequent session.
- [x] **UI-08**: Explicit close hides dialog, clears session, and disables follow-ups.
- [x] **UI-09**: Live RTV vote progress is not wiped when delayed details arrive (*bug fixed in product*).
- [x] **UI-10**: Escape (dismiss without click) on active window triggers clean `Close` and server cleanup (*architectural solution via display generation tokens*).

### TDD Discipline: RED → GREEN

Every failure classified before fixing. Two real latent defects uncovered in production code (`MapUiController`) and resolved:

1. Generation counter `currentDetailsRequestId` prevents stale async responses from overwriting newer queries.
2. `loadDetailsModel` preserves active `rtv*` and `admin*` state when viewing the same map.

---

## Stage 5. CI & Publishing

- [x] Fast tests included in standard `gradle test` across all modules.
- [x] Isolate actual oracle within JVM process; eliminate global UI state leakage.
- [x] Verify standalone build on clean CI without neighbor Mindustry/Arc checkouts.
- [x] Ensure production artifacts are free of test fixtures and runtime game dependencies.
- [x] Configure GitHub Actions publishing workflows (`build.yml`, `release-publish.yml`).
- [x] Create GitHub repository `XCore-mindustry/mindustry-testkit` and publish snapshot artifacts to `maven.x-core.org/snapshots`.
- [x] Update ADR and architecture specification status to `Accepted`.

---

## Definition of Done

- [x] Scenarios UI-01 through UI-10 assert on the real server-side pipeline and have documented test results.
- [x] Semantic simulator has executable parity verified against fingerprinted v160 JARs.
- [x] "Map selection → async data → old window cancel" flow reproduced deterministically without live players or DB.
- [x] Test failures explain cause through chronological transcript rather than uninformative DSL diffs.
- [x] Both domain model and observer subscription lifecycles are asserted.
- [x] Zero artificial production guarantees hidden inside the fake.
- [x] All test suites pass 100% on GitHub Actions runners.
