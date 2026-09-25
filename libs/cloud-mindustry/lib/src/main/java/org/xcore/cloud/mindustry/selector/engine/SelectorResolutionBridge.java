package org.xcore.cloud.mindustry.selector.engine;

import arc.Core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
        if (Core.app == null) {
            return true;
        }
        String name = Thread.currentThread().getName();
        if (name.equalsIgnoreCase("HeadlessApplication") || name.contains("main") || name.equals("Server")) {
            simulationThread = Thread.currentThread();
            return true;
        }
        try {
            java.lang.reflect.Field f = Core.app.getClass().getDeclaredField("mainLoopThread");
            f.setAccessible(true);
            Thread t = (Thread) f.get(Core.app);
            if (t != null) {
                simulationThread = t;
                return Thread.currentThread() == t;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Resolves the given supplier on the Mindustry simulation thread.
     * If already on the simulation thread, evaluates immediately inline.
     * If off-thread, posts to {@link Core#app} and safely awaits the result.
     */
    public static <T> T resolveSync(Supplier<T> supplier) {
        if (isSimulationThread() || Core.app == null) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Core.app.post(() -> {
            simulationThread = Thread.currentThread();
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while resolving target selector on simulation thread", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException("Error resolving target selector on simulation thread", cause);
        }
    }
}
