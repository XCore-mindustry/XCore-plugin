package tilelogger.integration;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.world.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.integration.blockhistory.BlockHistoryRegistry;
import org.xcore.plugin.integration.blockhistory.BlockHistorySummary;
import tilelogger.TileState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class XcoreBlockHistoryProviderTest {
    private final BlockHistoryRegistry registry = new BlockHistoryRegistry();
    private static final IntFunction<String> NAMES = id -> "block-" + id;

    @Test
    void foldsBuildsAndDestroysWithoutInferringConfigurationEventsOrIgnoringRollback() {
        var build = state(30, 2, 4, 1, false);
        build.config_type = 7;
        var destroy = state(40, 6, 1, 1, true);
        destroy.config_type = 7; // native copies configuration as well as block on destroy
        var rotated = state(35, 1, 8, 2, false);
        rotated.rotation = 3;
        rotated.valid = false; // a rollback invalidates state, not the historical action
        var summary = fold(new TileState[] {build, destroy, rotated}, 60, 50, NAMES);

        assertThat(summary.windowSeconds()).isEqualTo(60);
        assertThat(summary.built()).isEqualTo(2);
        assertThat(summary.destroyed()).isEqualTo(1);
        assertThat(summary.configured()).isZero();
        assertThat(summary.truncated()).isFalse();
        assertThat(summary.topBlocks())
                .containsExactly(new BlockHistorySummary.Entry("block-1", 1, 1),
                        new BlockHistorySummary.Entry("block-2", 1, 0));
        assertThat(summary.area()).isEqualTo(new BlockHistorySummary.Area(1, 1, 6, 8));
        assertThat(summary.lastActionSecondsAgo()).isEqualTo(10);
        assertThatThrownBy(() -> summary.topBlocks().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource({"32767,32760,7", "40000,39990,10", "65535,65530,5", "65535,65535,0", "0,0,0", "5,0,5",
            "0,65535,1", "5,65530,11", "65536,65535,1"})
    void usesUnsignedNativeSecondsAcrossTimerRollover(int now, int timestamp, int age) {
        var provider = provider(new TileState[] {state(timestamp, 1, 1, 1, false)}, () -> (short) now, NAMES);
        var summary = provider.summarize("player", 600).orElseThrow();
        assertThat(summary.built()).isEqualTo(1);
        assertThat(summary.lastActionSecondsAgo()).isEqualTo(age);
    }

    @Test
    void includesWindowBoundaryButRejectsStaleAndFutureTimestamps() {
        var summary = fold(new TileState[] {state(39900, 1, 1, 1, false), state(39899, 20, 20, 2, true),
                                   state(40001, 30, 30, 3, false)},
                100, 40000, NAMES);
        assertThat(summary.built()).isEqualTo(1);
        assertThat(summary.destroyed()).isZero();
        assertThat(summary.lastActionSecondsAgo()).isEqualTo(100);
        assertThat(summary.area()).isEqualTo(new BlockHistorySummary.Area(1, 1, 1, 1));
        assertThat(XcoreBlockHistoryProvider.fold(
                           new TileState[] {state(39900, 1, 1, 1, false)}, 99, 40000, NAMES))
                .isEmpty();
    }

    @Test
    void foldsReturnedRecordsOnBothSidesOfRolloverAndUsesTheYoungestAction() {
        var summary = fold(new TileState[] {state(65535, 1, 2, 1, false), state(0, 3, 4, 1, true),
                                   state(4, 5, 6, 2, false),
                                   state(65530, 7, 8, 2, true), // 11 seconds old, outside the window
                                   state(6, 9, 10, 3, false)},
                10, 5, NAMES); // future, modulo age 65535
        assertThat(summary.built()).isEqualTo(2);
        assertThat(summary.destroyed()).isEqualTo(1);
        assertThat(summary.topBlocks())
                .containsExactly(new BlockHistorySummary.Entry("block-1", 1, 1),
                        new BlockHistorySummary.Entry("block-2", 1, 0));
        assertThat(summary.area()).isEqualTo(new BlockHistorySummary.Area(1, 2, 5, 6));
        assertThat(summary.lastActionSecondsAgo()).isEqualTo(1);
    }

    @Test
    void allConfigurationRepresentationsRemainSnapshotsRatherThanSeparateActions() {
        var states = new TileState[16];
        for (int type = 0; type < 8; type++) {
            states[type * 2] = state(10, type, type, 1, false);
            states[type * 2 + 1] = state(10, type, type, 1, true);
            states[type * 2].config_type = (short) type;
            states[type * 2 + 1].config_type = (short) type;
        }
        var summary = fold(states, 100, 20, NAMES);
        assertThat(summary.built()).isEqualTo(8);
        assertThat(summary.destroyed()).isEqualTo(8);
        assertThat(summary.configured()).isZero();
        assertThat(summary.topBlocks()).containsExactly(new BlockHistorySummary.Entry("block-1", 8, 8));
    }

    @Test
    void readsUnsignedNativeCoordinates() {
        var summary = fold(new TileState[] {state(10, 40000, 65535, 1, true)}, 100, 20, NAMES);
        assertThat(summary.area()).isEqualTo(new BlockHistorySummary.Area(40000, 65535, 40000, 65535));
    }

    @Test
    void topEightUseCombinedCountsThenNameRegardlessOfInputOrder() {
        var states = new ArrayList<TileState>();
        for (int id = 1; id <= 10; id++) states.add(state(10, id, id, id, false));
        states.add(state(11, 10, 10, 10, true));
        var names = (IntFunction<String>) id -> "block-%02d".formatted(id);
        var first = fold(states.toArray(TileState[] ::new), 100, 20, names);
        Collections.reverse(states);
        var reversed = fold(states.toArray(TileState[] ::new), 100, 20, names);

        assertThat(reversed).isEqualTo(first);
        assertThat(first.topBlocks())
                .extracting(BlockHistorySummary.Entry::block)
                .containsExactly("block-10", "block-01", "block-02", "block-03", "block-04", "block-05",
                        "block-06", "block-07");
        assertThat(first.topBlocks().getFirst()).isEqualTo(new BlockHistorySummary.Entry("block-10", 1, 1));
        assertThat(first.built()).isEqualTo(10);
        assertThat(first.destroyed()).isEqualTo(1);
    }

    @Test
    void mergesIdsResolvingToTheSameBlockName() {
        var summary = fold(new TileState[] {state(10, 1, 1, 1, false), state(10, 1, 1, 2, true)}, 100, 20,
                ignored -> "router");
        assertThat(summary.topBlocks()).containsExactly(new BlockHistorySummary.Entry("router", 1, 1));
    }

    @Test
    void unknownIdsAndInvalidNamesKeepTotalsAndAreaWithoutInventingAirEntries() {
        var ids = new int[] {0, -1, 1024, 32767, 1, 2, 3, 4, 5, 1023};
        var states = new TileState[ids.length];
        var resolved = new ArrayList<Integer>();
        for (int i = 0; i < ids.length; i++) states[i] = state(10 + i, i, i, ids[i], true);
        var summary = fold(states, 100, 20, id -> {
            resolved.add(id);
            return switch (id) {
                case 1 -> null;
                case 2 -> " ";
                case 3 -> "x".repeat(65);
                case 4 -> "x".repeat(64);
                default -> "router";
            };
        });
        assertThat(summary.built()).isZero();
        assertThat(summary.destroyed()).isEqualTo(10);
        assertThat(summary.area()).isEqualTo(new BlockHistorySummary.Area(0, 0, 9, 9));
        assertThat(summary.lastActionSecondsAgo()).isEqualTo(1);
        assertThat(resolved).containsExactly(1, 2, 3, 4, 5, 1023);
        assertThat(summary.topBlocks())
                .containsExactly(new BlockHistorySummary.Entry("router", 0, 2),
                        new BlockHistorySummary.Entry("x".repeat(64), 0, 1));
    }

    @Test
    void entirelyUnidentifiedActionsStillProvideCorrectTotals() {
        var summary =
                fold(new TileState[] {state(10, 1, 1, 0, true), state(12, 2, 2, -1, false)}, 100, 20, NAMES);
        assertThat(summary.built()).isEqualTo(1);
        assertThat(summary.destroyed()).isEqualTo(1);
        assertThat(summary.topBlocks()).isEmpty();
    }

    @Test
    void emptyNullOrNullOnlyHistoryIsAbsent() {
        assertThat(provider(null, () -> {
            throw new AssertionError("Clock should not be read");
        }, NAMES).summarize("player", 600)).isEmpty();
        assertThat(provider(new TileState[0], () -> {
            throw new AssertionError("Clock should not be read");
        }, NAMES).summarize("player", 600)).isEmpty();
        assertThat(XcoreBlockHistoryProvider.fold(new TileState[] {null}, 600, 20, NAMES)).isEmpty();
        assertThat(XcoreBlockHistoryProvider.fold(null, 600, 20, NAMES)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"4095,false", "4096,true", "4097,true"})
    void capsTheFoldAndMarksReadsThatReachCap(int size, boolean truncated) {
        var states = new TileState[size];
        for (int i = 0; i < size; i++) states[i] = state(10, i, i, 1, false);
        var summary = fold(states, 100, 20, NAMES);
        assertThat(summary.built()).isEqualTo(Math.min(size, 4096));
        assertThat(summary.truncated()).isEqualTo(truncated);
        assertThat(summary.area().x1()).isEqualTo(Math.max(0, size - 4096));
        assertThat(summary.area().x2()).isEqualTo(size - 1);
    }

    @Test
    void usesExactlyTheWholeMapAllTeamsRelativeWindowAndReadCap() {
        var calls = new AtomicInteger();
        var provider = new XcoreBlockHistoryProvider(registry, (x1, y1, x2, y2, uuid, teams, time, size) -> {
            calls.incrementAndGet();
            assertThat(x1).isZero();
            assertThat(y1).isZero();
            assertThat(x2).isEqualTo((short) -1);
            assertThat(y2).isEqualTo((short) -1);
            assertThat(uuid).isEqualTo("target-uuid");
            assertThat(teams).isEqualTo(-1);
            assertThat(time).isEqualTo(-600);
            assertThat(size).isEqualTo(4096);
            return new TileState[] {state(10, 1, 1, 1, false)};
        }, () -> 20, NAMES);
        assertThat(provider.summarize("target-uuid", 600)).isPresent();
        assertThat(calls).hasValue(1);
    }

    @Test
    void invalidRequestsAndOffThreadCallsNeverReadNativeHistory() {
        var provider = new XcoreBlockHistoryProvider(registry, (x1, y1, x2, y2, uuid, teams, time, size) -> {
            throw new AssertionError("Native read forbidden");
        }, () -> 20, NAMES);
        assertThat(provider.summarize(null, 600)).isEmpty();
        assertThat(provider.summarize("", 600)).isEmpty();
        assertThat(provider.summarize(" ", 600)).isEmpty();
        assertThat(provider.summarize("player", 0)).isEmpty();
        assertThat(provider.summarize("player", -1)).isEmpty();
        GameThread.setGameThreadOverride(new Thread());
        try {
            assertThat(provider.summarize("player", 600)).isEmpty();
        } finally {
            GameThread.setGameThreadOverride(null);
        }
    }

    static Stream<Throwable> nativeFailures() {
        return Stream.of(new UnsatisfiedLinkError("native unavailable"),
                new NoClassDefFoundError("native initialization"), new IllegalStateException("read failed"));
    }

    @ParameterizedTest
    @MethodSource("nativeFailures")
    void readFailureIsAbsentWithoutLoadingRealNativeCode(Throwable failure) {
        var provider = new XcoreBlockHistoryProvider(registry, (x1, y1, x2, y2, uuid, teams, time, size) -> {
            if (failure instanceof LinkageError error)
                throw error;
            throw (RuntimeException) failure;
        }, () -> 20, NAMES);
        assertThat(provider.summarize("player", 600)).isEmpty();
    }

    @Test
    void clockAndContentFailuresAreAlsoAbsent() {
        var states = new TileState[] {state(10, 1, 1, 1, false)};
        assertThat(provider(states, () -> {
            throw new UnsatisfiedLinkError("duration unavailable");
        }, NAMES).summarize("player", 600)).isEmpty();
        assertThat(provider(states, () -> {
            throw new IllegalStateException("duration failed");
        }, NAMES).summarize("player", 600)).isEmpty();
        assertThat(provider(states, () -> 20, id -> {
            throw new IllegalStateException("content unavailable");
        }).summarize("player", 600)).isEmpty();
        assertThat(provider(states, () -> 20, id -> {
            throw new NoClassDefFoundError("content unavailable");
        }).summarize("player", 600)).isEmpty();
    }

    @Test
    void productionLookupRejectsInvalidIdsAndMissingContentWithoutAccessingGame() {
        var previous = Vars.content;
        Vars.content = null;
        try {
            for (int id : new int[] {-1, 0, 1, 1023, 1024, 32767}) {
                assertThat(XcoreBlockHistoryProvider.blockName(id)).isNull();
            }
        } finally {
            Vars.content = previous;
        }
    }

    @Test
    void productionLookupUsesRegisteredIdsAndNeverTheSaveMapperAirFallback()
            throws ReflectiveOperationException {
        var previous = Vars.content;
        var content = mock(ContentLoader.class);
        var router = mock(Block.class);
        router.id = 1;
        var nameField = Block.class.getField("name");
        nameField.setAccessible(true);
        nameField.set(router, "router");
        var mismatched = mock(Block.class);
        mismatched.id = 0;
        nameField.set(mismatched, "air");
        when(content.blocks()).thenReturn(Seq.with(null, router, null, mismatched));
        Vars.content = content;
        try {
            assertThat(XcoreBlockHistoryProvider.blockName(1)).isEqualTo("router");
            for (int id : new int[] {-1, 0, 2, 3, 4, 1023, 1024, 32767}) {
                assertThat(XcoreBlockHistoryProvider.blockName(id)).isNull();
            }
            verify(content, never()).block(anyInt());
        } finally {
            Vars.content = previous;
        }
    }

    @Test
    void startIsIdempotentAndStopCannotRemoveANewerProvider() {
        var provider = provider(new TileState[] {state(10, 1, 1, 1, false)}, () -> 20, NAMES);
        provider.start();
        assertThat(registry.summarize("player", 600)).isPresent();
        var successor = new BlockHistorySummary(
                600, 42, 0, 0, false, List.of(), new BlockHistorySummary.Area(1, 1, 1, 1), 0);
        registry.register((uuid, window) -> java.util.Optional.of(successor));
        provider.start(); // must not replace the successor
        provider.stop();
        provider.stop();
        assertThat(registry.summarize("player", 600)).contains(successor);
        assertThat(provider.summarize("player", 600)).isEmpty();
        assertThatThrownBy(provider::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stoppingTheCurrentProviderUnregistersIt() {
        var provider = provider(new TileState[] {state(10, 1, 1, 1, false)}, () -> 20, NAMES);
        provider.start();
        provider.stop();
        assertThat(registry.summarize("player", 600)).isEmpty();
    }

    @Test
    void v2CountsEventsRatherThanSnapshotsAcrossLongSessions() {
        var build = state(70000, 1, 1, 1, false);
        build.time = 70000;
        build.action_kind = tilelogger.ActionKind.BUILD_COMPLETED.id;
        var baseline = state(0, 1, 1, 1, false);
        baseline.action_kind = tilelogger.ActionKind.BASELINE.id;
        var configure = state(70001, 1, 1, 1, false);
        configure.time = 70001;
        configure.action_kind = tilelogger.ActionKind.CONFIGURE.id;
        var rotate = state(70002, 1, 1, 1, false);
        rotate.time = 70002;
        rotate.action_kind = tilelogger.ActionKind.ROTATE.id;
        var started = state(70003, 1, 1, 1, false);
        started.time = 70003;
        started.action_kind = tilelogger.ActionKind.DESTROY_STARTED.id;
        var result = XcoreBlockHistoryProvider
                             .fold(new TileState[] {baseline, build, configure, rotate, started}, 600, 70010,
                                     NAMES)
                             .orElseThrow();
        assertThat(result.built()).isEqualTo(1);
        assertThat(result.configured()).isEqualTo(1);
        assertThat(result.destroyed()).isZero();
        assertThat(result.lastActionSecondsAgo()).isEqualTo(8);
    }

    private XcoreBlockHistoryProvider provider(
            TileState[] states, LongSupplier clock, IntFunction<String> names) {
        return new XcoreBlockHistoryProvider(
                registry, (x1, y1, x2, y2, uuid, teams, time, size) -> states, clock, names);
    }

    private static BlockHistorySummary fold(
            TileState[] states, int window, int now, IntFunction<String> names) {
        return XcoreBlockHistoryProvider.fold(states, window, now, names).orElseThrow();
    }

    private static TileState state(int time, int x, int y, int block, boolean destroy) {
        var state = new TileState();
        state.time = (short) time;
        state.x = (short) x;
        state.y = (short) y;
        state.block = (short) block;
        state.destroy = destroy;
        state.valid = true;
        return state;
    }
}
