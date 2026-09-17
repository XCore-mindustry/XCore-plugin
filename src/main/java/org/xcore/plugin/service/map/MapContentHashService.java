package org.xcore.plugin.service.map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.map.domain.MapContentHash;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

/** Off-tick file hashing. Returns immutable metadata; never mutates shared map records. */
@Singleton
public class MapContentHashService {
    private final StorageExecutor worker;

    @Inject
    public MapContentHashService(StorageExecutor worker) {
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    /** Opens and closes the stream on the bounded worker. Errors remain visible on the stage. */
    public CompletionStage<MapContentHash> hashAsync(Callable<InputStream> openStream) {
        Objects.requireNonNull(openStream, "openStream");
        return worker.supply(() -> {
            try (var input = openStream.call()) {
                return MapContentHash.digest(input);
            }
        });
    }
}
