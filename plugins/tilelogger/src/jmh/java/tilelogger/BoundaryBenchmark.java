package tilelogger;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Per-call cost of the Java/native boundary with the release library and without -Xcheck:jni. Native
 * query cost is kept small (answers sit at the end of history), so these mostly measure argument
 * conversion and TileState marshalling. See README "Benchmarks".
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
public class BoundaryBenchmark {
    private static final String ACTOR = "AAAAAAAAAAA=";

    private static ConfigCodec.Encoded config(String kind) {
        return switch (kind) {
            case "scalar" -> ConfigCodec.encode(7);
            case "bytes" -> ConfigCodec.encode(new byte[32]);
            default -> throw new IllegalArgumentException(kind);
        };
    }

    /** A fresh memory-only session per iteration, so history growth stays bounded. */
    @State(Scope.Thread)
    public static class Recording {
        @Param({"scalar", "bytes"})
        public String config;
        ConfigCodec.Encoded encoded;
        int next;

        @Setup(Level.Iteration)
        public void start() {
            encoded = config(config);
            next = 0;
            TileLogger.startSession("", UUID.randomUUID().toString(), "bench-content");
        }
    }

    /** 100,000 records over 64 actors; every other one carries a 32-byte configuration. */
    @State(Scope.Thread)
    public static class Filled {
        @Setup(Level.Trial)
        public void fill() {
            TileLogger.startSession("", UUID.randomUUID().toString(), "bench-content");
            var scalar = config("scalar");
            var bytes = config("bytes");
            for (int i = 0; i < 100_000; i++)
                TileLogger.record(i % 512, i / 512 % 512, "actor-" + i % 64, 1, 20, 0, i % 2 == 0 ? scalar : bytes,
                        ActionKind.CONFIGURE);
        }
    }

    @Benchmark
    public void record(Recording state) {
        int i = state.next++;
        TileLogger.record(i % 512, i / 512 % 512, ACTOR, 1, 20, 0, state.encoded, ActionKind.CONFIGURE);
    }

    /** One client history page. */
    @Benchmark
    public TileState[] history32(Filled state) {
        return TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 32);
    }

    /** The native maximum, as used by the XCore history provider. */
    @Benchmark
    public TileState[] history4097(Filled state) {
        return TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 4097);
    }

    @Benchmark
    public TileState currentState(Filled state) {
        return TileLogger.currentState(100, 100);
    }
}
