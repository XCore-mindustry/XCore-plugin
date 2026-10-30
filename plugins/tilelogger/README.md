# TileLogger

Mindustry v160 server plugin for tile history, player activity evidence, and bounded rollback. Requires XCore and Java 25. The matching AdminTools client supports protocol v2 and Java 17/Android.

## Commands

- `/history [size]`: toggle tap inspection; 0 toggles/off, nonzero sizes are 1–100.
- `/history tile <x> <y> [size]`, `/history player <target> [size]`: bounded history.
- `/tl select`: select two corners **on the current map**.
- `/rollback <target> [time] [--selection] [--preview]`: inspect or apply a rollback. Negative time is relative to now, positive time is elapsed session seconds, 0 means the beginning. `self` and `all` are supported. Requires the existing `admin` permission; delayed execution checks it again.
- `/tl fill <block>`: fill a completed selection. Full building footprints must fit; protected cores and conflicting/unaligned buildings are rejected.
- `tl file <path> r` (console): load an archive asynchronously, without replacing the active store on a read/validation error. The opened archive is read-only; recording and world changes stay disabled until a live session starts.
- `tl file <path> w` (console): create a **new** v2 journal and capture the map baseline. An existing file is never truncated or resumed. Automatic map sessions use unique filenames.
- `/tl memory`: memory plus writer health (`accepted`, `durable`, `queuedBytes`, or a storage failure).

World mutations are limited to 256 buildings per command. Native planning additionally limits the scan to 100,000 matching snapshots, 2,048 positions, and 4 MiB of source/destination configuration bytes. Select a smaller area when a limit is reached. World changes run on the game thread.

## Journal and recovery

New sessions write one `.history` journal under `config/tilelogs`. Paths may also be absolute for console-managed archives. Relative paths cannot escape that directory. No automatic retention or archive deletion is performed.

The journal starts with `TLJ`, version byte 2, and `CR LF SUB NUL`. Each little-endian frame has a 32-bit payload length, a 32-bit FNV-1a checksum, and a payload:

1. Header (operation 0): session start epoch seconds, session UUID, SHA-256 content manifest.
2. Tile action (operation 1): 64-bit sequence/time, 16-bit coordinates/team/block, validity/destruction, rotation, configuration kind, explicit event kind, scalar/configuration ID, UUID. A new blob definition is carried in the same frame that first references it; subsequent records reference that ID. Definitions and references never cross files.
3. Rollback commit (operation 2): position and sequence/validity decisions for the successfully applied change. Earlier frames are never rewritten.

Strings/blobs are length-prefixed. Readers validate lengths, checksums, identifiers, sequences, content manifests, and rollback references before activation. A partial final frame is ignored **with an explicit recovery status**; the original is not modified. Complete corrupt frames are rejected. Archives are limited to 1 GiB and 10 million records by the reader; payloads are limited to 1 MiB. Old `TL`/14-byte records with `.players`/`.configs` dictionaries remain readable and rebuild their latest-valid cache, but are read-only. Their original 16-bit time/ID truncation is not repairable.

An ordered native writer owns file handles and performs disk I/O off the game thread. It syncs approximately every 250 ms and on explicit background flush/shutdown. Its bounded queue holds up to 32 MiB / 4,096 operations. Queue overflow suspends admission while draining already accepted operations; disk errors are visible and prevent further world changes. Durability counters count journal operations, including the header and commits. Data accepted since the last successful sync can be lost on a process/power failure; a slow device can extend that interval. Directory entries are synced on POSIX; filesystem/platform guarantees still apply.

Opening a v2 archive requires the same content manifest, so numeric block IDs cannot silently resolve against a different mod set. V2 configuration references use content kind/name. Unknown configurations retain the action metadata and are marked unrestorable; they are never silently applied as null.

## Rollback semantics

Planning is read-only: no validity, cache, or file changes, including preview. A plan is session/revision-bound. Java validates current/destination blocks, complete footprints, cores, configuration restoration, and conflicts, then rechecks before each application. Only an applied position is committed. Protected/conflicting/unrestorable/failed positions are reported separately. Core subclasses are protected, not only the six vanilla cores.

On failure after a world mutation, the block/team/rotation/configuration before-image is restored; an unsuccessful restoration suspends recording. This is **not** an inventory/liquid/health snapshot or a crash-atomic transaction spanning the game and disk. Those building internals remain outside the rollback contract. Restarted games start new sessions rather than automatically replaying interrupted operations onto a saved world.

## Client protocol

The client advertises `tilelogger_hello: 2`; the server returns `tilelogger_session`. Requests use `tilelogger_history_request` and positive client request IDs. Responses on `tilelogger_history_v2` include explicit version 2, request/session identity, query, entries, truncation, and an opaque continuation cursor bound to the server's query. Server-initiated player-history requests use decreasing negative IDs. Cursor continuations preserve their request identity.

Each reliable page is at most 32 rows and 8,192 serialized UTF-8 bytes. Clients coalesce taps, discard stale replies, clear on connection/map changes, and expose a button for earlier entries. The client retains at most 4,096 rows. Legacy clients receive at most 16 rows within the same byte budget, using the old packet names and explicitly narrowed display time. Old clients retain their original 16-bit display limitations.

History queries return **display previews** of large configuration payloads (at most 1,028 bytes, or 16 points per row). `TileState.configBytes` and `configTruncated` identify them; `getConfig()` refuses a truncated value. `currentState()` and rollback plans return complete checked configurations. The archive always retains the original bytes.

Dedicated wire codecs emit default fields, disable polymorphic Java class hints, and reject unknown request fields. They do not change Mindustry's global JSON settings. XCore summaries count completed builds, completed destruction/pickup, and configuration events separately; baseline/start events do not inflate activity. Legacy snapshots retain legacy semantics and are never exposed as live-map evidence after archive activation.

## Verification

```sh
./gradlew check jarRelease
./gradlew :native:check -PnativeSanitizers=true
```

`check` includes the C++ executable, deterministic/reference regression cases, Java headless tests, archive/JNI tests, and FTL compilation/consistency. JVM tests run with `-Xcheck:jni`. The sanitizer run is separate because ASan-instrumented native libraries must not be loaded by the ordinary JVM test process.

## Benchmarks

The suite compares revisions on the same machine. CPU scenarios run in an optimized Linux C++ binary (`-O3 -DNDEBUG`), separate from Gradle's debug/JNI-checked correctness tests. ASan builds and unoptimized builds are rejected.

```sh
scripts/bench-ab.sh master                         # 3 rounds, n = 1,000,000 total records
scripts/bench-ab.sh master 5 -- --n 10000000 --reps 3
BENCH_PREFIX="taskset -c 3" scripts/bench-ab.sh HEAD   # A/A against unchanged core headers
BENCH_DATA_DIR=/path/on/server-like/disk scripts/bench-ab.sh HEAD
python3 scripts/test-compare-bench.py               # report validation regressions
```

Each invocation creates an isolated directory under `build/bench/run-*`, compiles the same harness against base and working-tree headers, and alternates which side goes first. Raw output and binaries remain in that directory. Worktrees and empty data directories are removed; failed archive fixtures may remain for diagnosis. `--n` includes the 768-record griefing tail. Compiler, flags, harness hash/version, parameters and filesystem are recorded.

| Group | What it measures |
|---|---|
| `ingest.*` | Fixed-size recording workloads: `HistoryStack::Record` and memory-only `TileLogger::Record`, with repeated/unique configurations. Samples are whole-batch **mean ns per record**, not individual-record latency percentiles. |
| `last.*` | One-tile lookups with recent, deep and missing answers. |
| `page.*` | A 32-row page, a 4,096-row player window, and cursor pages at 1%, 50% and 99% depth. |
| `plan.*` | Tiny/busy/wide rollback selections, including expected limit rejections. |
| `commit.*` | Applying plans with no earlier snapshot (`fresh`) or a prior snapshot near the tail (`prior`). |
| `load.*` | Archive loading with/without rollback frames; fixture creation is untimed. |
| `journal.*` | Production queue limits: a 60 Hz workload at `--rate` records/s for `--seconds` (defaults 5,000 and 3), a burst of up to 200,000 small records, and 64 unique 1 MiB configurations. |

Timing builds use the ordinary allocator. A separate `TILELOGGER_BENCH_MEMORY` build runs only ingestion/loading to count bytes requested through C++ `new`, including containers missing from `MemoryUsage`. It does not report timing results. Requested bytes exclude allocator overhead and C-library allocations. `vmhwm_kib` is the peak RSS of the **whole process**, including fixture construction, not isolated load memory.

All result data is consumed outside timed intervals. Ingestion/load digests cover the entire history, actors and configuration bytes. Every journal repetition reopens its archive and verifies both accepted count and content against the live store, including after queue overflow. Burst admission counts depend on scheduling, so the report labels them as verified variable workloads rather than asserting identical data between sides. Durability metrics are unavailable after overflow and are never substituted with zero. An observation timeout fails the scenario.

The comparison exits nonzero for missing/duplicate scenarios or rounds, mismatched deterministic digests, invalid measurements, and harness errors. Time and memory differences between revisions are reported for review, without a fixed performance gate. Run A/A first to estimate noise, then repeat A/B before drawing conclusions. Report machine/compiler/storage and explain tradeoffs in the PR. Percentiles are over the stated sample type; for fast operations clock overhead matters, and five samples do not establish a reliable tail latency.

Journal files must live on representative storage. tmpfs is diagnosed, since it cannot measure persistence to a disk. Durability lag is sampled approximately every millisecond, can miss short-lived peaks, and includes writer scheduling and filesystem latency; 250 ms is not a guaranteed upper bound. Producer rate and elapsed time are reported separately from durable throughput.

### Java/native calls

```sh
./gradlew jmh                                     # full forked run, fail on any benchmark error
./gradlew jmhJar
java --enable-native-access=ALL-UNNAMED -jar build/libs/TileLogger-jmh.jar
```

JMH uses the release native library, without `-Xcheck:jni`, and writes JSON results under `build/results/jmh/`. The runnable benchmark jar includes its runtime dependencies and the release library. `check` compiles the benchmarks but never times them. Linux CI also runs a small native A/A and runnable-jar smoke check, with no timing thresholds.

`history32`, `history4097` and `currentState` measure complete API calls with recent answers. `record` uses single-shot, fixed batches of 16,384 records, resetting the session outside timing for every iteration; scores are normalized per record. Keep its single-shot mode and batch size of one. These include native encoding/storage work as well as argument conversion; they are not the cost of the Java/native transition alone. One JMH thread is required because the native store is global; multi-threaded runs are rejected before accessing it.

Compare revisions on the same machine/JVM, check every expected result is present, and compare uncertainty as well as scores. Crash/power-loss and disk-fault testing remain separate correctness work.

For AdminTools: `./gradlew build -Prelease`. Release jars exclude `admintools/dev/**` and include Android DEX. The existing in-engine DevHarness can capture the history view in portrait, landscape, and desktop modes, including v2 action labels, time beyond 65,535 seconds, and paging state.
