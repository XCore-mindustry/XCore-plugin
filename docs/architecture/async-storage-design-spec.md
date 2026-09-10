# Technical Design Specification: Asynchronous Storage Architecture

## 1. Overview & Goals
The objective is to eliminate all blocking database (MongoDB) and cache/transport (Redis) calls from the Mindustry single-threaded server loop, replacing them with non-blocking execution while keeping application business logic clean and readable.

```
+-------------------------------------------------------------------------------+
|                             MINDUSTRY MAIN THREAD                             |
|  (Ticks, Entity Logic, Game Events, Packets, Packet Handlers, Ingress Checks) |
+-------------------------------------------------------------------------------+
         |                                                 ^
         | Write (Fire-and-forget)                         | Safe callback dispatch
         | Snapshot / DTO                                  | (Core.app.post via Async)
         v                                                 |
+------------------------------------+          +------------------------------------+
|       ASYNC STORAGE LAYER          |          |          UI / GAME FEEDBACK        |
|  - StorageExecutor (Virtual Thr.)  |--------->|  - Menu rendering                  |
|  - Lettuce Async (Netty EventLoop) |          |  - Chat confirmation messages      |
+------------------------------------+          +------------------------------------+
         |                      |
         v                      v
+------------------+  +------------------+
|     MONGODB      |  |   REDIS/VALKEY   |
| (Async write /   |  | (Async cache /   |
|  bounded worker) |  |  streams / TTL)  |
+------------------+  +------------------+
```

---

## 2. Core Abstractions

### 2.1 `StorageExecutor`
A dedicated virtual-thread-based executor managed by Avaje Inject:
- Backed by `Executors.newVirtualThreadPerTaskExecutor()`.
- Uses a `Semaphore` (default permit count: 64) to prevent unbounded memory amplification if MongoDB latency degrades.
- Exposes clean submission methods:
  ```java
  public void run(Runnable task);
  public <T> CompletableFuture<T> supply(Callable<T> task);
  ```
- Implements `@PreDestroy` with a bounded shutdown grace period (default: 3 seconds) to flush pending write-behind operations on server stop.

### 2.2 `Async` Fluent Dispatcher
A static utility class that hides `CompletableFuture` mechanics and `Core.app.post` marshaling:
```java
public final class Async {
    // Fire-and-forget execution in StorageExecutor
    public static void run(Runnable task);

    // Read queries with main-thread callback
    public static <T> AsyncStage<T> supply(Callable<T> task);

    // Player-scoped read query with automatic online-validation guard
    public static <T> void forPlayer(Player player, Callable<T> task, BiConsumer<Player, T> consumer);
}
```

#### The `forPlayer` Disconnect Guard
When an asynchronous query finishes, the requesting player might have disconnected or been kicked during the I/O roundtrip.
`Async.forPlayer` validates:
1. `player.con != null && player.con.isConnected()`
2. `Groups.player.contains(player)`
If the player has left, the callback is discarded silently, preventing `NullPointerException` and memory leaks.

---

## 3. Redis Layer Architecture

### 3.1 Lettuce Async Migration
In `RedisConnectionManager`:
- Replace or complement `RedisCommands<String, String>` with `RedisAsyncCommands<String, String>`.
- Replace `binaryCommands` with `RedisAsyncCommands<String, byte[]>`.

### 3.2 Key Redis Services
1. **`RedisObserverStateStore`**:
   - `put(uuid, returnTeam)`: executes `asyncCommands.set(key, val, ex).orTimeout(500, ms)`. Fire-and-forget; returns immediately.
   - `delete(uuid)`: executes `asyncCommands.del(key).orTimeout(500, ms)`. Fire-and-forget.
   - `get(uuid)`: used only on player connection. Can be called asynchronously or cached locally.
2. **`TopMenuCacheService`**:
   - `invalidateAll()`: executes `asyncCommands.incr(versionKey).orTimeout(500, ms)`. Fire-and-forget.
   - `putTotalEntries(...)`: executes `asyncCommands.set(...)`. Fire-and-forget.
   - `getTotalEntries(...)`: returns `CompletableFuture<Long>` or cached value.

### 3.3 Reconnection & Timeout Safeguards
- All `RedisFuture` instances are bounded by `.orTimeout(500, TimeUnit.MILLISECONDS)`.
- If Redis is in `LOADING dataset` state or connection resets occur, failed cache operations log a single rate-limited warning and return safely without throwing exceptions on the game thread.

---

## 4. MongoDB Repositories Architecture

### 4.1 Write-Behind Mutations
In `PlayerDataRepository` and other repositories:
- Existing mutating methods (`save`, `updatePvpRating`, `updateSettings`, `updateLanguage`, `addBan`, `addMute`) execute inside `StorageExecutor.run(...)`.
- The caller on the game main thread passes either:
  1. An immutable DTO / snapshot (e.g. `PlayerDataSnapshot`).
  2. Primitive immutable parameters (`String uuid, int newRating`).
- The database update uses atomic MongoDB operators (`$set`, `$inc`) to prevent race conditions.

### 4.2 Read Queries
Read queries in repositories:
- `findByUuid(String uuid)`
- `findByPid(int pid)`
- `findLeaderboard(...)`
Are wrapped by `StorageExecutor.supply(...)` and consumed via `Async.supply().thenMain(...)`.

---

## 5. Concurrency & Data Integrity Invariants

1. **Main Thread Authoritative**: The in-memory `Session` and `PlayerData` residing in `SessionService` are the single source of truth for active online players.
2. **Immutable Offloading**: Any object passed into a background task must be deeply immutable or a newly created copy. Mutable objects (`Session`, `Player`, `TeamData`) must NEVER be modified from a background thread.
3. **Optimistic / Atomic Updates**: For numerical stats (PvP rating, blocks broken, games played), MongoDB queries must prefer `$inc` or explicit target values over blind document overwrites.
4. **No `.join()` or `.get()` on Main Thread**: Calling `.join()` or `.get()` on a `CompletableFuture` while running inside Mindustry's tick loop is strictly prohibited and will fail static/code review audits.
