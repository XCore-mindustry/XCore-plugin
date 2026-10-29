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
import java.util.function.LongSupplier;
import tilelogger.ActionKind;

/** Current-map evidence; all reads and content lookups run on the game thread. */
@Singleton
public final class XcoreBlockHistoryProvider implements BlockHistoryProvider {
    static final int CAP = 4096;

    private final BlockHistoryRegistry registry;
    private final HistoryReader history;
    private final LongSupplier duration;
    private final IntFunction<String> blockNames;
    private BlockHistoryRegistry.Registration registration;
    private volatile boolean stopped;

    @Inject
    public XcoreBlockHistoryProvider(BlockHistoryRegistry registry) {
        this(registry,
                (x1, y1, x2, y2, uuid, teams, time, size)
                        -> TileLogger.isLive()
                        ? TileLogger.getHistory(x1, y1, x2, y2, uuid, teams, time, size)
                        : new TileState[0],
                TileLogger::duration, XcoreBlockHistoryProvider::blockName);
    }

    XcoreBlockHistoryProvider(BlockHistoryRegistry registry, HistoryReader history, LongSupplier duration,
            IntFunction<String> blockNames) {
        this.registry = registry;
        this.history = history;
        this.duration = duration;
        this.blockNames = blockNames;
    }

    public synchronized void start() {
        if (stopped)
            throw new IllegalStateException("Block history provider stopped");
        if (registration == null)
            registration = registry.register(this);
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
            TileState[] states = history.read(
                    (short) 0, (short) 0, (short) -1, (short) -1, playerUuid, -1, -windowSeconds, CAP);
            if (states == null || states.length == 0)
                return Optional.empty();
            return fold(states, windowSeconds, duration.getAsLong(), blockNames);
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    /** V2 time never wraps. Legacy snapshot semantics are preserved only for old readers. */
    static Optional<BlockHistorySummary> fold(
            TileState[] states, int windowSeconds, long now, IntFunction<String> blockNames) {
        if (states == null || states.length == 0)
            return Optional.empty();
        int built = 0, destroyed = 0, configured = 0, youngest = Integer.MAX_VALUE;
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
        var blocks = new HashMap<String, int[]>();
        // Last() returns its newest capped records in chronological order.
        for (int i = Math.max(0, states.length - CAP); i < states.length; i++) {
            TileState state = states[i];
            if (state == null)
                continue;
            ActionKind kind = state.kind();
            if (kind == ActionKind.BASELINE || kind == ActionKind.BUILD_STARTED
                    || kind == ActionKind.DESTROY_STARTED)
                continue;
            long elapsed = kind == ActionKind.LEGACY ? ((now & 0xffff) - (state.time & 0xffff)) & 0xffff
                                                     : now - state.time;
            if (elapsed < 0 || elapsed > windowSeconds)
                continue;
            int age = (int) elapsed;
            boolean build = kind == ActionKind.BUILD_COMPLETED || kind == ActionKind.DROP
                    || (kind == ActionKind.ADMIN_FILL && !state.destroy)
                    || (kind == ActionKind.LEGACY && !state.destroy);
            boolean destroy = kind == ActionKind.DESTROY_COMPLETED || kind == ActionKind.PICKUP
                    || (kind == ActionKind.ADMIN_FILL && state.destroy)
                    || (kind == ActionKind.LEGACY && state.destroy);
            if (build)
                built++;
            if (destroy)
                destroyed++;
            if (kind == ActionKind.CONFIGURE)
                configured++;
            int x = state.x & 0xffff, y = state.y & 0xffff;
            x1 = Math.min(x1, x);
            y1 = Math.min(y1, y);
            x2 = Math.max(x2, x);
            y2 = Math.max(y2, y);
            youngest = Math.min(youngest, age);

            int id = state.block;
            if ((!build && !destroy) || id <= 0 || id > (kind == ActionKind.LEGACY ? 0x3ff : 0xffff))
                continue;
            String name = blockNames.apply(id);
            if (name == null || name.isBlank() || name.length() > 64)
                continue;
            int[] counts = blocks.computeIfAbsent(name, ignored -> new int[2]);
            counts[destroy ? 1 : 0]++;
        }
        if (youngest == Integer.MAX_VALUE)
            return Optional.empty();
        var top = blocks.entrySet()
                          .stream()
                          .map(entry
                                  -> new BlockHistorySummary.Entry(
                                          entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                          .sorted(Comparator
                                          .comparingInt((BlockHistorySummary.Entry entry)
                                                                -> entry.built() + entry.destroyed())
                                          .reversed()
                                          .thenComparing(BlockHistorySummary.Entry::block))
                          .limit(8)
                          .toList();
        return Optional.of(new BlockHistorySummary(windowSeconds, built, destroyed, configured,
                states.length >= CAP, top, new BlockHistorySummary.Area(x1, y1, x2, y2), youngest));
    }

    static String blockName(int id) {
        if (id <= 0 || id > 0xffff || Vars.content == null)
            return null;
        // Record IDs come from live blocks, not save-file IDs. ContentLoader.block(id)
        // can use a temporary save mapper and return air for a missing mapped ID.
        var blocks = Vars.content.blocks();
        if (id >= blocks.size)
            return null;
        var block = blocks.get(id);
        return block == null || Short.toUnsignedInt(block.id) != id ? null : block.name;
    }

    @FunctionalInterface
    interface HistoryReader {
        TileState[] read(short x1, short y1, short x2, short y2, String uuid, int teams, int time, long size);
    }
}
