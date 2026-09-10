# Implementation Tasks: Asynchronous Storage Architecture

## Phase 1: Core Foundation & Async Utilities
- [ ] **Task 1.1: Implement `StorageExecutor`**
  - Create `org.xcore.plugin.concurrent.StorageExecutor` managed as an Avaje Inject `@Singleton`.
  - Use Java 21 `Executors.newVirtualThreadPerTaskExecutor()`.
  - Add bounded semaphore / task limit (default 64 concurrent storage tasks).
  - Implement `@PreDestroy` graceful shutdown flush.
  - Unit test `StorageExecutorTest`.

- [ ] **Task 1.2: Implement `Async` Helper**
  - Create `org.xcore.plugin.concurrent.Async` with static convenience methods.
  - Implement `Async.run(Runnable)` for fire-and-forget.
  - Implement `Async.supply(Callable)` returning fluent `AsyncStage<T>` with `thenMain(Consumer<T>)`.
  - Implement `Async.forPlayer(Player, Callable<T>, BiConsumer<Player, T>)` with online/connection guard.
  - Unit test `AsyncTest`.

## Phase 2: Redis Layer Async Migration
- [ ] **Task 2.1: Add Async Commands to `RedisConnectionManager`**
  - Expose `asyncCommands()` (`RedisAsyncCommands<String, String>`) in `RedisConnectionManager`.
  - Expose `asyncBinaryCommands()` (`RedisAsyncCommands<String, byte[]>`).
  - Add non-blocking connection verification.

- [ ] **Task 2.2: Make `RedisObserverStateStore` Non-Blocking**
  - Update `put(playerUuid, returnTeam)` to use `asyncCommands.set(...)` with 500ms timeout.
  - Update `delete(playerUuid)` to use `asyncCommands.del(...)`.
  - Keep `get(playerUuid)` non-blocking or guarded with fallback.
  - Verify unit tests `SessionObserverStateTest` and `ObserverServiceTest`.

- [ ] **Task 2.3: Make `TopMenuCacheService` Non-Blocking**
  - Update `invalidateAll()` to fire-and-forget `asyncCommands.incr(versionKey)`.
  - Update `putTotalEntries(...)` to fire-and-forget `asyncCommands.set(...)`.
  - Update `getTotalEntries(...)` to handle timeout fallback gracefully without stalling.
  - Verify unit tests `TopMenuCacheServiceTest`.

## Phase 3: High-Impact Game Write-Behind (PvP & Sessions)
- [ ] **Task 3.1: Decouple PvP Rating Persistence in `MiniPvP`**
  - In `MiniPvP.java`: ensure `playerDataRepository.updatePvpRating(data.uuid, data.pvpRating)` runs in `StorageExecutor` without blocking the main tick.
  - In `MiniPvP.java`: ensure `defeatedPlayers` observer state caching runs without blocking.
  - Verify `MiniPvPRoundStateTest`.

- [ ] **Task 3.2: Make `SessionService.persistPlayer` Asynchronous**
  - Offload database write in `SessionService.persistPlayer(session)` to `StorageExecutor`.
  - Pass an immutable copy of `PlayerData` to prevent concurrent modification during write.
  - Invalidate leaderboard cache asynchronously.

## Phase 4: MongoDB Repositories Asynchronous Wrapping
- [ ] **Task 4.1: Wrap Mutating Repository Calls in Background Workers**
  - `BanDataRepository`: `addBan`, `removeBan`, `updateDuration` offloaded to `StorageExecutor`.
  - `MuteDataRepository`: `addMute`, `removeMute` offloaded to `StorageExecutor`.
  - `MapStatsService` & `GameDataService`: game completion recording offloaded to background threads.
  - `AuditRecordRepository`: audit log insertion offloaded to background threads.

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
