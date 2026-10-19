package org.xcore.plugin.integration.blockhistory;

import arc.util.Log;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** One provider, last registration wins; closing a replaced registration cannot remove its successor. */
@Singleton
public class BlockHistoryRegistry {
    private final AtomicReference<BlockHistoryProvider> provider = new AtomicReference<>();

    private boolean stopped;

    public synchronized Registration register(BlockHistoryProvider value) {
        Objects.requireNonNull(value, "provider");
        if (stopped) throw new IllegalStateException("Block history registry stopped");
        // A wrapper makes even repeated registrations of the same instance distinct owners.
        BlockHistoryProvider owner = value::summarize;
        provider.set(owner);
        return () -> provider.compareAndSet(owner, null);
    }

    public Optional<BlockHistorySummary> summarize(String uuid, int windowSeconds) {
        GameThread.require("BlockHistoryRegistry.summarize");
        var current = provider.get();
        if (current == null) return Optional.empty();
        try {
            return Objects.requireNonNull(current.summarize(uuid, windowSeconds));
        } catch (RuntimeException | LinkageError error) {
            Log.warn("Block history unavailable: @", error.toString());
            return Optional.empty();
        }
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void stop() {
        stopped = true;
        provider.set(null);
    }

    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
