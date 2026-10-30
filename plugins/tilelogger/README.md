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

`native/src/bench/Bench.cpp` measures the native core outside Gradle and the JVM. The Gradle test binaries are debug builds and JVM tests use `-Xcheck:jni`, so neither is suitable for timing. The harness refuses to compile without optimization and `-DNDEBUG`, or with ASan. It runs on Linux only.

Compare a base revision with the working tree on the same machine:

```sh
scripts/bench-ab.sh master                         # 3 rounds, n = 1,000,000
scripts/bench-ab.sh master 5 -- --n 10000000 --reps 3
BENCH_PREFIX="taskset -c 3" scripts/bench-ab.sh HEAD   # A/A: measures the noise floor
```

The script compiles the current harness twice with the same compiler and flags: once against the base revision's headers, once against the working tree's headers. It then runs each scenario group in its own process, alternating which side goes first each round, and prints medians, head/base ratios, result digests, retained memory and archive size. Raw results go to `build/bench/results-*.tsv`.

Scenarios, all built from one seeded fixture of `n` records (`--n` is capped at the 10,000,000-record session limit), whose last 768 records are a griefing tail:

| Group | What it measures |
|---|---|
| `ingest.*` | Per-record cost and retained heap of `HistoryStack::Record` and memory-only `TileLogger::Record` (repeated vs unique configurations). No disk I/O. |
| `last.*` | One-tile, one-row lookups (`unitToPlayerInfo`) whose answer is recent, early in the session, or absent. Reported separately because the cost depends on how far back the answer is. |
| `page.*` | A 32-row page in a busy zone, the XCore per-player window (4,096 rows), and cursor pages at 1%, 50% and 99% depth. |
| `plan.*` | Rollback planning for a tiny rect, a busy zone, a wide area filtered to a player with no actions, and a wide area that exceeds the position limit (`rejected`). |
| `commit.*` | Applying every position of a plan, where the positions have no earlier snapshot (`fresh`) or one just before the griefing (`prior`). |
| `load.*` | `TileLogger::Load` of an archive written from the fixture, without and with rollback commit frames. Archives go to `build/bench/data` and are deleted afterwards. |
| `journal.*` | Journaling with the production queue limits. `sustained` submits `--rate` records per second (default 5,000) in 60 Hz ticks for `--seconds` (default 3). `burst.small` records as fast as possible, up to 200,000 records. `burst.large` records 64 unique 1 MiB configurations. Reports `Record` latency, records admitted before overflow, the largest accepted-but-not-durable gap, durability lag, drain time and archive bytes per record. |

Reading the results:

- **Digests** must be `same`. A different digest means the change altered query results, plans or loaded state. Explain that in the PR or treat it as a bug.
- **Memory** is the bytes requested through a counting `operator new` in the harness, so it includes the hash maps and dictionary copies that `MemoryUsage` does not report, but not allocator overhead (the process peak is printed as `vmhwm_kib`). Requested and archive bytes are deterministic for a given compiler and standard library, so any change in them is real.
- **Time** is only comparable between runs on the same machine. Run an A/A comparison (`HEAD` against an unchanged tree) first. Treat a head/base ratio as a regression only if it is outside the A/A spread for that scenario in at least two runs. For the scenarios that run on the game thread (`last`, `page`, `plan`, `commit`), compare p99 and max, not only p50.
- The output records the harness version, compiler, flags and data filesystem. Results from different harness versions are not comparable. Bump `kHarnessVersion` when fixtures, queries or output change.

Journal results depend on the device and filesystem, so point `build/bench/data` (or `--dir`) at the same kind of storage the server uses. On tmpfs `fsync` does nothing, and the harness prints a warning. Overflow is a result, not a harness failure: a session that overflows stops recording. Because overflow depends on when the writer happens to sync, the number of records `burst.small` admits varies widely between identical runs, so compare whether it overflows, not how many records it admitted. The summary marks a scenario `UNSTABLE` when its outcome changes between runs. The durability counters are sampled about once per millisecond, outside the timed `Record` calls. The journal digest comes from loading the archive written during warmup.

### Java/native boundary

`./gradlew jmh` runs `src/jmh/java/tilelogger/BoundaryBenchmark.java` against the release native library, without `-Xcheck:jni`. It measures `record` (scalar and 32-byte configurations), history pages of 32 and 4,097 rows, and `currentState`. The answers sit at the end of history, so these numbers are mostly argument conversion and `TileState` marshalling, not native query cost. Results are written to `build/results/jmh/`.

`check` compiles these benchmarks but never runs them. To compare two revisions, run `./gradlew jmh` in each checkout on the same machine and compare scores together with their error intervals. Use this for changes to the boundary itself, such as caching JNI class and field IDs or moving to FFM, and compare each candidate with the current JNI code on the same machine.

Not covered yet: crash and power-loss tests, and disk faults. Timing is never a CI gate.

For AdminTools: `./gradlew build -Prelease`. Release jars exclude `admintools/dev/**` and include Android DEX. The existing in-engine DevHarness can capture the history view in portrait, landscape, and desktop modes, including v2 action labels, time beyond 65,535 seconds, and paging state.
