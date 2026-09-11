# Implementation Tasks: Asynchronous Storage Architecture

## Phase 1: Core Foundation & Async Utilities
- [x] **Task 1.1: Implement `StorageExecutor`**
  - Create `org.xcore.plugin.concurrent.StorageExecutor` managed as an Avaje Inject `@Singleton`.
  - Use Java 21 `Executors.newVirtualThreadPerTaskExecutor()`.
  - Add bounded semaphore / task limit (default 64 concurrent storage tasks).
  - Implement `@PreDestroy` graceful shutdown flush.
  - Unit test `StorageExecutorTest`.
  - Implemented in `src/main/java/org/xcore/plugin/concurrent/StorageExecutor.java`.

- [x] **Task 1.2: Implement `Async` Helper**
  - Create DI-managed `org.xcore.plugin.concurrent.Async` with convenience methods.
  - Implement `Async.run(Runnable)` for fire-and-forget.
  - Implement `Async.supply(Callable)` returning fluent `AsyncStage<T>` with `thenMain(Consumer<T>)`.
  - Implement `Async.forPlayer(Player, Callable<T>, BiConsumer<Player, T>)` with online/connection guard.
  - Unit test `AsyncTest`.
  - Implemented in `src/main/java/org/xcore/plugin/concurrent/Async.java`, `AsyncStage.java`, and `MainThreadDispatcher.java`.

## Phase 2: Redis Layer Async Migration
- [x] **Task 2.1: Add Async Commands to `RedisConnectionManager`**
  - Expose `asyncCommands()` (`RedisAsyncCommands<String, String>`) in `RedisConnectionManager`.
  - Expose `asyncBinaryCommands()` (`RedisAsyncCommands<String, byte[]>`).
  - Add non-blocking connection verification (`hasAsyncCommands()`).
  - Add non-blocking `withAsyncCommands` runner in `RedisNetworkBackend`.

- [x] **Task 2.2: Make `RedisObserverStateStore` Non-Blocking**
  - Add `putAsync(playerUuid, returnTeam)` using `asyncCommands.set(...)` with 500ms timeout.
  - Add `deleteAsync(playerUuid)` using `asyncCommands.del(...)` with 500ms timeout.
  - Migrated `ObserverService` to use async write/delete without blocking the main tick.
  - Verified unit tests in `ObserverServiceTest` and `RedisAsyncWriteTest`.

- [x] **Task 2.3: Make `TopMenuCacheService` Non-Blocking**
  - Add `invalidateAllAsync()` with `asyncCommands.incr(versionKey)` and 500ms timeout.
  - Add `putTotalEntriesAsync(...)` and `putTopSliceAsync(...)` with 500ms timeout.
  - Migrated callers across `TopMenuService`, `SessionService`, and controllers.
  - Verified unit tests in `TopMenuCacheServiceTest` and `RedisAsyncWriteTest`.

## Phase 3: High-Impact Game Write-Behind (PvP & Sessions)
- [x] **Task 3.1: Decouple PvP Rating Persistence in `MiniPvP`**
  - In `MiniPvP.java`: ensure `playerDataRepository.updatePvpRating(data.uuid, data.pvpRating)` runs asynchronously without blocking the main tick.
  - In `MiniPvP.java`: ensure `defeatedPlayers` observer state caching runs without blocking.
  - Verify `MiniPvPRoundStateTest`.
  - Uses native `PlayerDataRepository.updatePvpRatingAsync(...)` backed by the Reactive Streams driver.

- [x] **Task 3.2: Make `SessionService.persistPlayer` Asynchronous**
  - Implemented `saveAsync` on `DataRepository` and `PlayerDataRepository` backed by native Reactive Streams.
  - Invalidate leaderboard cache asynchronously via `invalidateAllAsync()`.

## Phase 4: MongoDB Repositories Asynchronous Wrapping
- [x] **Task 4.1: Migrate Mutating Repository Calls to Native Reactive Mongo**
  - Added reactive collection access across `DataRepository` base class for all repositories.
  - Added `saveAsync`, `findByIdAsync`, `countAsync` to base `DataRepository`.
  - Added `findAsync`, `saveAsync`, `deleteAsync` to `BanDataRepository`.
  - Added `findByUuidAsync`, `saveAsync`, `deleteAsync` to `MuteDataRepository`.
  - Added `findByAuditIdAsync` and inherited `saveAsync` to `AuditRecordRepository`.
  - Added atomic async updates and `findByUuidAsync`, `findByPidAsync` to `PlayerDataRepository`.
  - Added `MongoAsync.list(...)` publisher collection utility.
  - Verified with `DataRepositoryAsyncTest` and `MongoAsyncTest`.

- [ ] **Task 4.2: Audit Read Repository Queries**
  - `PlayerDataRepository.findByPid`, `findByUuid`
  - Ensure callers that require game UI rendering use `Async.supply().thenMain(...)` or `Async.forPlayer(...)`.

## Phase 5: UI & Command Flows Refactoring
- [ ] **Task 5.1: Top & Leaderboard Menus**
  - Refactor `TopMenu.java` / `TopMenuService.java` to fetch pages via `Async.forPlayer(...)`.
  - Ensure loading indicators or smooth transitions are presented to the user.

- [ ] **Task 5.2: Admin & Information Menus**
  - Refactor `/profile`, `/info`, `/trace` commands to fetch offline player data via `Async.forPlayer`.

## Phase 6: Telemetry, Verification & Fault Injection
- [ ] **Task 6.1: Metric Instrumentation**
  - Expose metrics for storage execution: `xcore_storage_tasks_active`, `xcore_storage_tasks_rejected_total`, `xcore_storage_task_duration_seconds`.
- [ ] **Task 6.2: Chaos Testing**
  - Test server stability when Redis experiences disconnect / `LOADING dataset` state.
  - Test server stability when MongoDB queries experience artificial 2-second delays.
  - Verify Mindustry TPS remains solid (60.0) without tick stutters during heavy database I/O.
