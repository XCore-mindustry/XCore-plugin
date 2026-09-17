# ADR: Three-Tier Map Identity and Non-Blocking Reactive UI Architecture

## Status

Proposed — implementation started with identity value objects only; runtime integration and migration are pending.

## Context

In Mindustry v160, maps have no native persistent identifiers or content checksums; maps in the engine are transient filesystem handles with arbitrary mutable string tags.

Previously, `XCore-plugin` tracked maps in MongoDB using composite keys (`name|author|mode` or `file_name`). This led to:

1. **Broken Identity**: Renaming map files or fixing typos on disk permanently orphaned historical statistics, telemetry, and player votes.
2. **Gamemode Collision & Split-Brain Records**: Storing `game_mode` in the primary key split likes and play-counts when the same physical map was played across Survival, PvP, and Attack. Match registration mutated and overwrote the map's mode in MongoDB.
3. **Display Name Instability**: Inconsistent handling of Arc color tags (`[accent]`) and Mindustry Unicode glyphs created duplicate records.
4. **Tick-Thread Blocking**: `MapUiController` performed synchronous MongoDB queries inside UI reducers on the 60 TPS game tick loop, causing client frame drops.
5. **Lifecycle Leaks & Crashes**: `MapVoteObserverService` leaked disconnected player UUIDs and risked main-thread `ClassCastException` crashes when players switched menus during active background dispatches.

## Decision

We adopt a **Three-Tier Map Identity Model** and a **Non-Blocking Reactive MVI UI Architecture**:

### 1. Three-Tier Map Identity

- **Tier 1 (Physical Identity - `MapContentHash`)**: SHA-256 digest of the `.msav` binary payload. Canonical, deterministic, and immutable across renames, folder restructurings, and OS environments.
- **Tier 2 (Semantic Identity - `MapSlug`)**: Human-readable, normalized slug `{author}/{name}` (kebab-cased, lowercase, with Arc color tags and PUA glyphs stripped). Used for Discord bots, administrative commands, and telemetry routing.
- **Tier 3 (Domain Entity - `MapEntity`)**: Single document in MongoDB indexed uniquely on `content_hash`. Global reputation (likes, dislikes, popularity) is decoupled from game modes. Mode-specific metrics (plays, average duration, win rates) are stored in an embedded `Map<String, GamemodeMetrics>`.

### 2. Pure MVI / Elm Server-Driven UI

- State is modeled as a Java 25 sealed interface (`MapUiState.Browser`, `MapUiState.Details`, `MapUiState.NotFound`).
- The UI reducer `update(state, event)` is pure: it performs zero database I/O and emits explicit `MapUiCmd` side-effect descriptors.
- MongoDB I/O uses native reactive stages, consistent with [the accepted storage ADR](ADR-async-storage-architecture.md). Worker execution is reserved for file I/O and unavoidable legacy operations.
- High-frequency live voting countdowns and preview textures continue using zero-flicker partial slot patching (`slot_rtv`, `slot_preview`, `slot_reputation`, `slot_map_table`) via `Call.menuBuilderUpdate`.

### 3. Shared Asynchronous Cache (`MapSummaryCache`)

- A singleton cache with a 60-second TTL prevents duplicate full-collection MongoDB scans on every `/maps` command.
- Optimistic in-memory patching immediately updates like and dislike counters across the browser list when a player votes.

### 4. Lifecycle-Safe Observer Dispatch

- Registrations in `MapVoteObserverService` carry a unique `sessionToken`.
- Dispatches on `Core.app.post` verify token identity, preventing `ClassCastException` when a player opens another menu before an async callback fires.
- `ConnectionHandler.onPlayerLeave` explicitly unregisters viewing sessions upon disconnect, preventing memory leaks.

## Consequences

### Positive

- **Complete Rename Immunity**: Maps can be renamed on disk, moved into subfolders, or transferred between servers without losing historical telemetry, popularity, or votes.
- **Unified Reputation**: A map's likes, dislikes, and popularity are preserved globally across all gamemodes.
- **Zero Tick Stalls**: MongoDB queries for map browsing and inspection are decoupled from the 60 TPS Mindustry tick thread.
- **Stable UI & Robust Lifecycle**: Zero visual flicker, preserved text input focus, and no memory leaks on disconnect.

### Negative / Trade-offs

- **Hashing Overhead**: Exact file hashing must run off the game tick; latency depends on map size and storage and has not been benchmarked.
- **Revision Identity**: A raw digest changes with metadata edits or reserialization. It is not a stable logical entity ID across revisions.
- **Ambiguous Aliases**: Slugs are non-unique and cannot authorize automatic record merges. Preserve Unicode letters and meaningful bracketed text.
- **Migration Gate**: The unique hash index and revision-linking policy are deferred until duplicate records, existing ObjectId references, backups, and rollback are handled.
- **Schema Migration Required**: Legacy split records in `maps` collection must be consolidated into the new canonical `MapEntity` schema.
