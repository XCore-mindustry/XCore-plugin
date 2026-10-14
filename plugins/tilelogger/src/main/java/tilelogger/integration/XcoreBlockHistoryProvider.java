package tilelogger.integration;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.integration.blockhistory.BlockHistoryProvider;
import org.xcore.plugin.integration.blockhistory.BlockHistoryRegistry;
import org.xcore.plugin.integration.blockhistory.BlockHistorySummary;
import org.xcore.plugin.startup.ShutdownOrder;
import tilelogger.TileLogger;
import tilelogger.TileState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Current-map evidence; all reads and content lookups run on the game thread. */
@Singleton
public final class XcoreBlockHistoryProvider implements BlockHistoryProvider {
    static final int CAP = 4096;

    private final BlockHistoryRegistry registry;
    private final HistoryReader history;
    private final IntSupplier duration;
    private final IntFunction<String> blockNames;
    private BlockHistoryRegistry.Registration registration;
    private volatile boolean stopped;

    @Inject
    public XcoreBlockHistoryProvider(BlockHistoryRegistry registry) {
        this(registry, TileLogger::getHistory, TileLogger::duration,
                XcoreBlockHistoryProvider::blockName);
    }

    XcoreBlockHistoryProvider(BlockHistoryRegistry registry, HistoryReader history,
                              IntSupplier duration, IntFunction<String> blockNames) {
        this.registry = registry;
        this.history = history;
        this.duration = duration;
        this.blockNames = blockNames;
    }

    public synchronized void start() {
        if (stopped) throw new IllegalStateException("Block history provider stopped");
        if (registration == null) registration = registry.register(this);
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void stop() {
        stopped = true;
        if (registration != null) {
            try {
                registration.close();
            } finally {
                registration = null;
            }
        }
    }

    @Override
    public Optional<BlockHistorySummary> summarize(String playerUuid, int windowSeconds) {
        try {
            GameThread.require("XcoreBlockHistoryProvider.summarize");
            // An empty UUID means every player to native code, not an unknown player.
            if (stopped || playerUuid == null || playerUuid.isBlank() || windowSeconds < 1) {
                return Optional.empty();
            }
            TileState[] states = history.read((short) 0, (short) 0, (short) -1, (short) -1,
                    playerUuid, -1, -windowSeconds, CAP);
            if (states == null || states.length == 0) return Optional.empty();
            return fold(states, windowSeconds, duration.getAsInt() & 0xffff, blockNames);
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    /**
     * Native time is unsigned 16-bit steady-clock seconds, restored from history on reset.
     * Duration and record timestamps wrap every 65536 seconds. Subtract modulo 65536
     * before comparing ages, including when the clock wraps between the read and fold.
     * Native AbsTime clamps the cutoff and Last compares raw times, so the native query
     * can omit recent records across a rollover. The format also has no epoch to distinguish
     * records a full cycle apart. This fold cannot recover omitted records or those epochs;
     * fixing complete window reads requires a native history/clock change.
     *
     * Every non-destroy record counts as built, including rotation/configuration updates:
     * config_type encodes the value representation, not the event kind, so configured is 0.
     * Rollback's valid flag does not erase the original action from the evidence.
     */
    static Optional<BlockHistorySummary> fold(TileState[] states, int windowSeconds,
                                              int now, IntFunction<String> blockNames) {
        if (states == null || states.length == 0) return Optional.empty();
        int built = 0, destroyed = 0, youngest = Integer.MAX_VALUE;
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
        var blocks = new HashMap<String, int[]>();
        // Last() returns its newest capped records in chronological order.
        for (int i = Math.max(0, states.length - CAP); i < states.length; i++) {
            TileState state = states[i];
            if (state == null) continue;
            int age = ((now & 0xffff) - (state.time & 0xffff)) & 0xffff;
            if (age > windowSeconds) continue;
            if (state.destroy) destroyed++;
            else built++;
            int x = state.x & 0xffff, y = state.y & 0xffff;
            x1 = Math.min(x1, x);
            y1 = Math.min(y1, y);
            x2 = Math.max(x2, x);
            y2 = Math.max(y2, y);
            youngest = Math.min(youngest, age);

            // Native HistoryStack::Record copies the previous block into destroy records.
            // With no previous state, native drops block-0 destroys entirely; Java cannot
            // reconstruct those actions. Unknown returned IDs still contribute to totals.
            // Air/unknown IDs cannot identify the destroyed block: keep totals, omit names.
            int id = state.block;
            if (id <= 0 || id > 0x3ff) continue; // native block field is ten bits
            String name = blockNames.apply(id);
            if (name == null || name.isBlank() || name.length() > 64) continue;
            int[] counts = blocks.computeIfAbsent(name, ignored -> new int[2]);
            counts[state.destroy ? 1 : 0]++;
        }
        if (youngest == Integer.MAX_VALUE) return Optional.empty();
        var top = blocks.entrySet().stream()
                .map(entry -> new BlockHistorySummary.Entry(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingInt((BlockHistorySummary.Entry entry) -> entry.built() + entry.destroyed())
                        .reversed().thenComparing(BlockHistorySummary.Entry::block))
                .limit(8)
                .toList();
        return Optional.of(new BlockHistorySummary(windowSeconds, built, destroyed, 0,
                states.length >= CAP, top, new BlockHistorySummary.Area(x1, y1, x2, y2), youngest));
    }

    static String blockName(int id) {
        if (id <= 0 || id > 0x3ff || Vars.content == null) return null;
        // Record IDs come from live blocks, not save-file IDs. ContentLoader.block(id)
        // can use a temporary save mapper and return air for a missing mapped ID.
        var blocks = Vars.content.blocks();
        if (id >= blocks.size) return null;
        var block = blocks.get(id);
        return block == null || block.id != id ? null : block.name;
    }

    @FunctionalInterface
    interface HistoryReader {
        TileState[] read(short x1, short y1, short x2, short y2, String uuid, int teams, int time, long size);
    }
}
