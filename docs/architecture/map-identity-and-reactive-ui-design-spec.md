# Architecture Specification: Map Identity, Lifecycle & Reactive UI Architecture (v2)

- **Status**: Accepted — Phase 1 (identity primitives, catalog, reactive cache/write-behind), Phase 2 (MapEntity domain schema, consolidation, reactive repository), and Phase 3 (sealed MapUiState, MapUiCmd, pure evaluator) implemented; production data migration pending
- **Domain**: Maps, Telemetry, Storage, Server-Driven UI
- **Target Component**: `XCore-plugin` (Mindustry v160, Java 25)

---

## 1. Context and Problem Statement

In Mindustry servers, the **Map** is the central entity around which gameplay, player ratings, voting (RTV), and telemetry revolve. However, the legacy map architecture in XCore suffered from acute fragility stemming from four foundational flaws:

### 1.1 The Map Identity Crisis

Mindustry's core `Map` representation (`mindustry.maps.Map`) is merely a transient in-memory handle to a file on disk (`arc.files.Fi`) with mutable, unstructured tags (`StringMap tags`). It provides **no content hashing, CRC checksums, or persistent identifiers**.

XCore attempted to bridge this by identifying maps using transient heuristics:

1. **File Name Matching**: Fails upon disk reorganization, subfolder moving, file renaming (e.g. typos fixed), and casing mismatches across OS filesystems.
2. **Display Name Matching**: Conflated raw names with Arc color tags (e.g., `[accent]`, `[#ffd37f]`) and Mindustry font glyphs (Unicode PUA `\uE000-\uF8FF`). Inconsistent stripping across call-sites created duplicate, split-brain records in MongoDB.
3. **Gamemode Identity Pollution**: Storing `game_mode` inside the composite primary key (`name|author|mode`) caused identical physical maps to have fragmented likes, popularity, and play-counts across Survival, PvP, and Attack modes. Furthermore, `registerGame` destructively mutated the record's mode upon match completion.

### 1.2 Main-Thread Tick Blocking

`MapUiController` executed synchronous, blocking MongoDB queries (`findAllAsMap`, `findById`, `findOrCreate`) directly on the 60 TPS primary game loop inside UI reducers. Whenever a player opened `/maps` or clicked a map row, database round-trip latency caused dropped frames and micro-stutters for all connected players.

### 1.3 Lifecycle Leaks and Runtime Crashes

1. **Unbounded Memory Leak**: `MapVoteObserverService.openDetailsMap` retained player UUIDs indefinitely if players disconnected or closed the client while viewing map details.
2. **`ClassCastException` Server Crashes**: Stale observer callbacks on `Core.app.post` blindly cast `session.activeUiSession()` to `UiSession<?, MapUiEvent>`. If a player navigated to `/settings` or another UI, the cast failed on the main thread, risking full server stall.
3. **Reputation Desynchronization**: The UI optimistically implemented vote revocation (un-liking), whereas `MapService.handleReputation` rejected it with `"error-already-voted"`, permanently desynchronizing client state from the database.

---

## 2. Three-Tier Map Identity Architecture

Separate exact file identity from human aliases and persisted entity identity. A raw file digest survives renames, not content edits. The following target model still requires an explicit revision-linking and legacy migration policy before production cutover:

```text
+----------------------------------------------------------------------------------------------------+
|                                THREE-TIER MAP IDENTITY ARCHITECTURE                                |
+----------------------------------------------------------------------------------------------------+
|                                                                                                    |
|  TIER 1: PHYSICAL IDENTITY (Content-Addressed)                                                     |
|  +-----------------------------------------------------------------------------------------------+ |
|  | MapContentHash (SHA-256 of exact file bytes)                                            | |
|  | • Immutable across file renames, folder moves, server transfers, and OS platforms.              | |
|  | • Computed via streaming digest at load/import time.                                          | |
|  +-----------------------------------------------------------------------------------------------+ |
|                                             |                                                      |
|                                             v                                                      |
|  TIER 2: SEMANTIC IDENTITY (Logical Slug)                                                          |
|  +-----------------------------------------------------------------------------------------------+ |
|  | MapSlug: {normalized-author}/{normalized-title} (e.g. "uylol/in-research-of-power")            | |
|  | • Stripped of color markup and PUA glyphs, lowercase, kebab-cased.                            | |
|  | • Stable human-readable namespace for routing, URLs, and Discord commands.                    | |
|  +-----------------------------------------------------------------------------------------------+ |
|                                             |                                                      |
|                                             v                                                      |
|  TIER 3: DOMAIN ENTITY & RUNTIME BINDING                                                           |
|  +-----------------------------------------------------------------------------------------------+ |
|  | MapEntity (MongoDB Document)                                                                  | |
|  | • Unique index on content_hash.                                                                | |
|  | • Global reputation (likes, dislikes, popularity) separated from gamemodes.                   | |
|  | • Embedded Map<String, GamemodeMetrics> for mode-specific durations and win-rates.            | |
|  | • MindustryRuntimeMapRef binding MapEntity <-> mindustry.maps.Map.                            | |
|  +-----------------------------------------------------------------------------------------------+ |
+----------------------------------------------------------------------------------------------------+
```

### 2.1 Tier 1: `MapContentHash`

An immutable, type-safe representation of the map's SHA-256 content digest:

- **Hashing Target**: The entire binary contents of the `.msav` file.
- **Performance**: Streamed in 16 KiB heap buffers; file I/O must run off-thread. Runtime catalog integration is a subsequent step.
- **Guarantee**: Identical file bytes yield the same hash regardless of filename. Metadata, serialization, compression, or terrain edits can change it; equivalent terrain alone does not imply identical hashes.

```java
public record MapContentHash(String asHex) {
    // Validated full SHA-256; fromBytes(), fromHex(), digest(InputStream), shortHex().
    // digest consumes but does not close the caller-owned stream.
}
```

### 2.2 Tier 2: `MapSlug`

A human-readable, canonical identifier for administrative tools, Discord bots, and console logging:

- Sanitizes display names and author strings:
  1. Strips all Arc color tags (`[accent]`, `[#ffffff]`, `[]`).
  2. Preserves meaningful text of unrecognized bracketed tags (`[2v2]`, `[PvP]`); brackets become separators.
  3. Removes glyphs and separates punctuation, including supplementary PUA code points.
  4. Lowercases with `Locale.ROOT` and normalizes to NFC, preserving accents and semantic combining marks attached to letters/digits. Leading unattached marks are removed; Indic, Cyrillic, Chinese, and Korean names remain readable.
- Arc named-color stripping depends on the registered palette; sanitize after engine palette initialization.
- Slugs are non-unique, mutable display aliases: never automatically merge or select a persistent map by slug.
- Default author namespace is `"community"` when missing or `"unknown"`.
- Format: `{author}/{name}` (e.g. `"anuke/desert-crossing"`).

### 2.3 Tier 3: `MapEntity` (Canonical Persistence Model)

Decouples map existence and global reputation from game modes:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapEntity {
    @BsonId public ObjectId id;

    @BsonProperty("content_hash")
    public String contentHash; // Unique primary index

    @BsonProperty("slug")
    public String slug;         // Secondary lookup index

    public String name;         // Plain canonical name
    public String author;       // Plain canonical author
    @BsonProperty("file_name")
    public String fileName;     // Last known filename on disk

    public int width;
    public int height;
    public int version;

    // Global ratings & reputation across ALL modes
    public int likes;
    public int dislikes;
    public int reputation;
    public double popularity;
    public double interest;

    @BsonProperty("total_play_count")
    public long totalPlayCount;

    @BsonProperty("last_played_at")
    public long lastPlayedAt;

    // Embedded per-mode metrics: survival, pvp, attack, hexed
    @BsonProperty("gamemode_stats")
    @Builder.Default
    public Map<String, GamemodeMetrics> gamemodeStats = new HashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GamemodeMetrics {
        public long plays;
        public long wins;
        public long minimumDurationMs;
        public long averageDurationMs;
        public long maximumDurationMs;
    }
}
```

---

## 3. Reactive UI Architecture (MVI / Elm)

### 3.1 Pure State Representation (`MapUiState`)

Replaces the 33-parameter monolithic `MapUiModel` record with a **discriminated sealed union** in Java 25:

```java
public sealed interface MapUiState {

    record Browser(
        String searchQuery,
        int page,
        int totalPages,
        List<MapSummary> displayedMaps,
        int totalMapsCount,
        boolean loading
    ) implements MapUiState {}

    record Details(
        String mapId,
        MapIdentity identity,
        TelemetryMatrix telemetry,
        ReputationState reputation,
        PreviewState preview,
        RtvState rtv,
        AdminState admin
    ) implements MapUiState {}

    record NotFound(String mapId, String reason) implements MapUiState {}

    record MapSummary(String id, String name, String author, int width, int height, int likes, int dislikes, boolean isCurrent) {}
    record MapIdentity(String name, String author, String description, int width, int height, String mode, boolean isCurrent) {}
    record TelemetryMatrix(long plays, long playsYear, String lastPlayed, String minTime, String avgTime, String maxTime, int reputation, double popularity, double interest) {}
    record ReputationState(Boolean playerVote, int likes, int dislikes, int approvalPercent, boolean pendingSync) {}
    record PreviewState(boolean loading, String textureRegion, boolean failed) {}
    record RtvState(boolean active, int votes, int required, int remainingSeconds) {}
    record AdminState(boolean isAdmin, boolean confirming, long confirmExpireMillis) {}
}
```

### 3.2 Side-Effect Isolation (`MapUiCmd`)

The UI reducer `update(state, event)` remains a **pure function**. All I/O, database queries, and network calls are returned as explicit commands:

```java
public sealed interface MapUiCmd {
    record LoadAllSummaries(String query, int page) implements MapUiCmd {}
    record LoadMapDetails(String mapId) implements MapUiCmd {}
    record RequestPreview(String mapId) implements MapUiCmd {}
    record PersistReputationVote(String mapId, boolean like, boolean isRevoking) implements MapUiCmd {}
    record SubscribeRtv(String mapId, long sessionToken) implements MapUiCmd {}
    record UnsubscribeRtv(long sessionToken) implements MapUiCmd {}
    record TriggerRtv(String mapId, boolean force) implements MapUiCmd {}
    record CloseSession() implements MapUiCmd {}
}
```

### 3.3 Zero-Flicker Partial DOM Patching

Mindustry v160 dialogs preserve scroll position and input focus only when updated via `Call.menuBuilderUpdate` targeting named `VSlot` containers:

- `slot_map_table`: Re-rendered during browser search input and pagination without destroying the dialog shell.
- `slot_preview`: Patched asynchronously when the off-heap native Pixmap rasterizes and texture streaming finishes.
- `slot_reputation`: Patched optimistically upon Like/Dislike click.
- `slot_rtv`: Patched at 1Hz during active RTV voting to stream live vote countdowns.

---

## 4. Concurrency & Real-Time Sync Guarantees

### 4.1 Non-Blocking Shared Cache (`MapSummaryCache`)

Eliminates per-session redundant MongoDB collection scans:

- **Singleton Lifecycle**: Injected into `MapMenu` and controllers via Avaje Inject.
- **TTL Caching**: Caches full map summaries with a 60-second TTL.
- **Asynchronous Refresh**: Use native reactive MongoDB stages per [the storage ADR](../adr/ADR-async-storage-architecture.md). Reserve worker execution for file I/O or unavoidable legacy work. Return stale cache immediately during a single-flight refresh; publish immutable, versioned snapshots on the main thread.
- **Optimistic In-Memory Patching**: When any player votes, `patchVoteOptimistic(mapId, likeDelta, dislikeDelta)` updates the cached summary in memory so subsequent `/maps` views immediately reflect the new tally.

### 4.2 Safe RTV Observation (`MapVoteObserverService`)

Eliminates memory leaks and `ClassCastException` hazards:

1. **Session Token Guard**: Registrations pair `(playerUuid, sessionToken)`. When dispatching `RtvVoteUpdated`, the service verifies that `session.activeUiSession().token() == sessionToken` before invoking `dispatch()`.
2. **Self-Healing on Disconnect**: `ConnectionHandler.onPlayerLeave` explicitly invokes `observerService.unregisterViewing(player.uuid())`.
3. **Dead-Entry Pruning**: If `sessionService.get(uuid)` returns `null`, the observer automatically removes the entry.

### 4.3 Off-Heap Native Memory Safety (`MapPreviewService`)

- Native Arc `Pixmap` allocations are enclosed in `try-finally` blocks that strictly call `pixmap.dispose()`.
- Preview generation is serialized via a reentrant lock to avoid concurrency bugs in Mindustry's static tile readers.
- Large PNGs stream through chunked custom packets (`"net-xcore_img"`) to prevent TCP packet overflow.

---

## 5. Database Migration & Rollout Plan

### Phase 1: Additive Identity Infrastructure

1. Introduce validated `MapContentHash` and non-unique `MapSlug` value objects with headless tests.
2. Add an asynchronous file hashing boundary; later connect a generation-guarded catalog to startup/import/reload using immutable snapshots captured on the main thread.
3. Keep existing ObjectIds, vote references, and schema intact. Do not automatically create, merge, or relink records by slug or filename.
4. Defer a unique hash index until legacy duplicates and mode-split records have been reconciled. Sparse indexes still include explicit nulls; validate field representation before selecting index options.
5. Before persistence cutover, define stable entity IDs versus revision hashes, ambiguity handling, backups, idempotent migration, reference remapping, and rollback. Zero-downtime rollout is a goal, not yet a verified capability.

### Phase 2: Offline Legacy Consolidation

Run migration script to consolidate legacy records split by game mode into single `MapEntity` documents with nested `gamemode_stats`.

### Phase 3: Transition UI to Sealed MVI & `MapSummaryCache`

Activate the pure Elm reducer and asynchronous command runner, eliminating main-thread tick blocks.
