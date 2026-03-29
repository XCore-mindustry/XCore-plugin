package org.xcore.plugin.localization;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class TranslationExecutor {

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public void execute(Runnable task) {
        virtualExecutor.execute(task);
    }

    @PreDestroy
    void shutdown() {
        virtualExecutor.shutdownNow();
    }
}
