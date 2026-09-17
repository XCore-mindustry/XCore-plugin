package org.xcore.plugin.service.map;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the single-threaded map hashing worker. Hashing stays off the game
 * tick and serialized, so files are never read concurrently by this service.
 */
@Factory
public final class MapHashWorkerFactory {

    private final ExecutorService worker;

    public MapHashWorkerFactory() {
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "xcore-map-hash");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @Singleton
    @MapContentHashService.MapHashWorker
    public Executor hashWorker() {
        return worker;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdown();
    }
}
