# JNI to FFM comparison, 2026-10-30

This is a measurement on one development machine, not a production performance guarantee.
Queries became much faster than the previous JNI implementation. Recording has a possible
small time regression and allocates more Java heap. The native storage algorithms did not change.

## Environment and method

- Intel Core i5-12450H, Linux x86_64, kernel 7.3.0-4-cachyos, glibc 2.44, GCC 16.2.1.
- OpenJDK 25.0.5.0.2, JMH 1.37, release native library (`-O3 -DNDEBUG`), no JNI checker or sanitizers.
- Baseline: JNI at e2cc21d (also included unchanged in benchmark merge bbf88f1).
- Candidate: this FFM implementation with typed downcalls and a private 4 KiB scratch frame per thread.
- Same `BoundaryBenchmark` source, fixtures, machine, one JMH thread and three JVM forks.
  No builds or other benchmarks ran concurrently during these final measurements.
- Queries: 5 warmup + 5 measurement iterations of 1 second. The 100,000-record fixture
  places answers near the end of history; this primarily exercises marshalling, not a deep search.
- Recording: memory-only sessions, 16,384 operations per single-shot iteration, reset outside
  timing. Default runs use 5 warmup + 10 measurement iterations. Both implementations were
  also measured with 20 warmup + 20 measurement iterations because recording was warmup-sensitive.
  These are API call costs including native encoding/storage, not isolated downcall latency or fsync throughput.

`measurements.json` keeps the timing scores, JMH errors/confidence intervals, per-fork timing
samples and Java allocation metrics from the four captures. Other GC profiler metrics were omitted.
Allocation is Java heap bytes/op only: it excludes native buffers and arenas.

## Results

Time is mean ns/op with JMH's reported error. Allocation is mean Java heap bytes/op.
Use the equally warmed recording pair as the primary comparison; the default pair is included
to expose the warmup sensitivity rather than select an especially favorable result.

| Scenario | JNI ns/op | FFM ns/op | FFM / JNI time | JNI B/op | FFM B/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| currentState | 6,729.9 ± 1,093.7 | 466.8 ± 27.6 | 0.069 | 160.0 | 304.0 |
| history, 32 rows | 30,036.5 ± 2,177.8 | 6,190.0 ± 204.8 | 0.206 | 5,264.2 | 6,216.0 |
| history, 4,097 rows | 2,824,108.1 ± 89,473.6 | 747,458.7 ± 27,271.5 | 0.265 | 671,971.8 | 770,469.2 |
| record scalar, 20/20 | 595.3 ± 38.7 | 653.8 ± 44.1 | 1.098 | 16.4 | 72.5 |
| record 32 bytes, 20/20 | 729.1 ± 65.5 | 748.3 ± 31.3 | 1.026 | 48.4 | 112.5 |
| record scalar, default | 541.2 ± 23.2 | 695.0 ± 88.4 | 1.284 | 16.4 | 72.4 |
| record 32 bytes, default | 751.2 ± 72.2 | 812.8 ± 53.3 | 1.082 | 48.4 | 112.5 |

The read improvements are approximately 14.4x, 4.9x and 3.8x for these fixtures. This comparison
does not show that FFM is inherently faster than an optimized JNI bridge: the former JNI code
looked up classes/fields and set Java fields individually; the new bridge returns one bounded
binary buffer and constructs objects in Java.

Recording means with longer warmup are 9.8% and 2.6% higher. Their reported intervals overlap;
do not claim a recording speedup or a proven small regression from this one experiment.
The extra Java allocation is clear (about 56/64 bytes per record), so GC behavior under real
server load remains a tradeoff to observe. Query allocations also increase by about 15–18%
for history pages. Native responses add a temporary buffer capped at 8 MiB; scratch memory
is 4 KiB per calling thread. None of that native memory appears in the GC metric.

## Reproduce

Keep a standalone JNI JMH jar from the baseline **before** rebuilding the candidate jar.
Use the same JDK, compiler and flags for each build. Preserve both jars outside their
build directories, then compare them in ABBA order with distinct result filenames:

```sh
# In a separate checkout at e2cc21d:
./gradlew jmhJar
cp build/libs/TileLogger-jmh.jar /tmp/TileLogger-jni-jmh.jar

# In the FFM checkout:
./gradlew jmhJar
cp build/libs/TileLogger-jmh.jar /tmp/TileLogger-ffm-jmh.jar

round=0
for variant in jni ffm ffm jni; do
  round=$((round + 1))
  java --enable-native-access=ALL-UNNAMED -jar "/tmp/TileLogger-$variant-jmh.jar" \
    -foe true -prof gc -rf json -rff "$variant-$round.json"
  java --enable-native-access=ALL-UNNAMED -jar "/tmp/TileLogger-$variant-jmh.jar" \
    -foe true -prof gc -wi 20 -i 20 -rf json -rff "$variant-$round-records-warmed.json" '.*record$'
done
```

For an acceptance decision on another machine, repeat with reversed/alternating implementation
order and realistic server workloads. These captures used JNI then FFM for the full runs,
then JNI and FFM for the longer recording runs; machine drift is not ruled out. The ABBA
recipe above improves future comparisons; it does not retroactively change these captures.

The separate native A/B smoke (`scripts/bench-ab.sh origin/master 1 -- --n 10000 --reps 1
--rate 120 --seconds 1`) verified all 23 scenarios: deterministic digests, requested allocation
sizes and archive sizes matched; variable burst results roundtripped. A single short round
is a correctness smoke, not evidence of a native CPU or disk speed improvement.
