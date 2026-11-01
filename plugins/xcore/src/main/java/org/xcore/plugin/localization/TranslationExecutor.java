package org.xcore.plugin.localization;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Singleton
public class TranslationExecutor {

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public void execute(Runnable task) {
        virtualExecutor.execute(task);
    }

    /** Runs blocking provider I/O on a virtual thread. */
    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, virtualExecutor);
    }

    @PreDestroy
    void shutdown() {
        virtualExecutor.shutdownNow();
    }
}
