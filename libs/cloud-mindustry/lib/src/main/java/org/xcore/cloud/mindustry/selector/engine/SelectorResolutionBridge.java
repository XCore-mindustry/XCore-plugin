package org.xcore.cloud.mindustry.selector.engine;

import arc.Core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Ensures thread affinity for selector resolution against Mindustry's non-thread-safe collections.
 */
public final class SelectorResolutionBridge {

    private static volatile Thread simulationThread;

    private SelectorResolutionBridge() {}

    public static void setSimulationThread(Thread thread) {
        simulationThread = thread;
    }

    public static boolean isSimulationThread() {
        Thread sim = simulationThread;
        if (sim != null) {
            return Thread.currentThread() == sim;
        }
        // Fallback: if not explicitly recorded, check if Core.app != null
        return Core.app == null || Thread.currentThread().getName().contains("main");
    }

    public static <T> CompletableFuture<T> dispatchToSimulationThread(Supplier<T> supplier) {
        if (isSimulationThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable t) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        if (Core.app != null) {
            Core.app.post(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } else {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }
}
