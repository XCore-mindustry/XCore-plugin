package org.xcore.plugin.concurrent;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Bounded dispatcher for blocking storage work.
 *
 * <p>The virtual-thread executor keeps blocking I/O off the Mindustry tick
 * thread. The semaphore is intentional: virtual threads are cheap, but an
 * unavailable database must not create an unbounded number of waiting tasks.</p>
 */
@Singleton
public class StorageExecutor {
    public static final int DEFAULT_MAX_CONCURRENT_TASKS = 64;

    private final ExecutorService executor;
    private final Semaphore permits;
    private final int maxConcurrentTasks;

    @Inject
    public StorageExecutor() {
        this(DEFAULT_MAX_CONCURRENT_TASKS);
    }

    public StorageExecutor(int maxConcurrentTasks) {
        if (maxConcurrentTasks < 1) {
            throw new IllegalArgumentException("maxConcurrentTasks must be positive");
        }

        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.permits = new Semaphore(maxConcurrentTasks);
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    /**
     * Runs a fire-and-forget task. Rejection is explicit so callers can count
     * or log dropped persistence operations instead of silently losing them.
     */
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!permits.tryAcquire()) {
            throw new RejectedExecutionException("Storage executor is at capacity");
        }

        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    permits.release();
                }
            });
        } catch (RuntimeException error) {
            permits.release();
            throw error;
        }
    }

    /**
     * Starts a storage task without blocking the calling thread.
     * Rejected and failed tasks are represented by a failed future.
     */
    public <T> CompletableFuture<T> supply(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> result = new CompletableFuture<>();

        try {
            execute(() -> {
                try {
                    result.complete(task.call());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }

        return result;
    }

    /** Number of tasks currently occupying storage permits. */
    public int activeTasks() {
        return maxConcurrentTasks - permits.availablePermits();
    }

    /** Number of task slots currently available. */
    public int availableSlots() {
        return permits.availablePermits();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
