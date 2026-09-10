# ADR: Asynchronous Storage Architecture for MongoDB and Redis

## Status
Accepted

## Context
`XCore-plugin` runs inside a headless Mindustry dedicated server. Mindustry executes its entire game simulation loop, entity logic, network packet processing, and event dispatch on a single primary thread ("main thread").

Currently, storage operations across the plugin communicate synchronously with:
1. **Redis** via `io.lettuce:lettuce-core` using blocking synchronous commands (`connection.sync()`, `commands.set()`, `commands.get()`, `commands.incr()`).
2. **MongoDB** via `org.mongodb:mongodb-driver-sync` using blocking repository calls directly on the invoking thread.

When network latency fluctuates, MongoDB executes un-indexed queries, or Redis/Valkey experiences restarts (e.g. entering the dataset loading phase: `LOADING Valkey is loading the dataset in memory`), any synchronous storage call blocks the Mindustry tick loop for hundreds or thousands of milliseconds. This causes:
- TPS drop spikes and rubberbanding for connected players.
- Missed keepalives and packet drops.
- Game event latency (e.g. core destruction stalls for seconds while saving ratings and updating observer state).

## Decision
We adopt an **Asynchronous Storage Architecture** across `XCore-plugin` using a hybrid model:

### 1. Redis: Native Lettuce Asynchronous API
Lettuce is built on Netty and is inherently non-blocking. We shift all gameplay and cache Redis operations from `commands.sync()` to `connection.async()` (`RedisAsyncCommands`).
- All Redis commands return `RedisFuture<T>`, which implements `CompletableFuture<T>`.
- Calls are guarded with strict timeouts (e.g. `orTimeout(500, TimeUnit.MILLISECONDS)`).
- Timeouts or Redis disconnections degrade gracefully to local in-memory fallbacks rather than blocking or crashing.

### 2. MongoDB: Java 21 Virtual Threads
Rather than introducing heavy reactive frameworks (such as Project Reactor or RxJava), which create invasive context-propagation overhead in a game engine, MongoDB calls are offloaded to **Java 21 Virtual Threads** (`Executors.newVirtualThreadPerTaskExecutor()`).
- Virtual threads provide non-blocking concurrency for blocking I/O with zero cognitive overhead.
- A bounded semaphore/rate-limiter guards the executor against resource exhaustion during database outages.

### 3. Dual-Track Operation Model (Write-Behind vs. Read-Forward)
To avoid polluting the codebase with nested callbacks and boilerplate `Core.app.post()` calls, storage operations are split into two clean categories:

#### Track A: Write-Behind (Fire-and-Forget Mutations — 90% of operations)
Mutations (e.g., updating PvP rating, persisting session state, recording audit logs, invalidating Redis top cache, setting spectator state in Redis) **do not require immediate database confirmation on the main thread**.
- The authoritative in-memory state (`PlayerData`, `Session`) is updated immediately on the game tick.
- The persistence operation is dispatched asynchronously to background virtual threads / Lettuce async.
- Caller code in game listeners (`MiniPvP`, `ConnectionHandler`, etc.) remains 100% clean: no callbacks, no `CompletableFuture`, no `Core.app.post()`.

#### Track B: Read-Forward via Fluent Dispatcher (`Async` helper — 10% of operations)
When the game genuinely requires data from storage to render UI or answer a command (e.g., `/top` leaderboard, viewing an offline player's `/profile`, fetching map statistics):
- A lightweight fluent helper (`Async.supply().thenMain(...)` or `Async.forPlayer(...)`) executes the query off-thread.
- The helper automatically marshals the result back to Mindustry's main loop via `Core.app.post()` internally.
- If bound to a player (`Async.forPlayer`), it verifies that the player is still connected before invoking the UI callback, eliminating `NullPointerException` risks.

### 4. Explicit Synchronous Exceptions
Synchronous blocking operations are strictly forbidden during the game tick loop, with exactly two deliberate exceptions:
1. **Server Boot & Schema Migrations:** During plugin startup (`@PostConstruct`) before the server socket opens to players, migrations and critical index verifications run synchronously to guarantee data integrity.
2. **Ingress Filters (`IngressCheck`):** Connection checks must evaluate in memory (≤ 50 nanoseconds) using local Trie/cache tables (`SubnetTable`). They never perform on-the-fly network I/O.

## Consequences

### Positive
- **Zero Tick Stalls:** Network delays, database lock contention, and Redis restarts cannot freeze the game tick loop.
- **Clean Game Code:** Game logic classes do not become nested callback mazes.
- **Robustness:** Built-in timeouts prevent runaway backpressure.

### Negative / Trade-offs
- **Eventual Consistency for Writes:** In the rare event of a server hard crash (e.g. `SIGKILL` or power loss) within milliseconds of a state change, an in-flight background write might not reach the disk. This is mitigated by bounded queue flushing on graceful shutdown.
- **Concurrency Care:** Background tasks must receive immutable snapshots or primitive parameters, never raw mutable entities that are concurrently modified on the main thread.
