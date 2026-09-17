package org.xcore.plugin.service.map;

import jakarta.inject.Inject;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.map.domain.MapContentHash;
import org.xcore.plugin.model.MapData;

import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Computes map file digests away from the game tick and stores them on
 * {@link MapData}. Hashes are advisory metadata for admin tooling and future
 * reconciliation; nothing reads them for identity decisions yet.
 */
@Singleton
public class MapContentHashService {

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface MapHashWorker {
    }

    private final Executor hashWorker;

    @Inject
    public MapContentHashService(@MapHashWorker Executor hashWorker) {
        this.hashWorker = Objects.requireNonNull(hashWorker, "hashWorker");
    }

    /** Test convenience: unqualified executor. */
    public static MapContentHashService withWorker(Executor hashWorker) {
        return new MapContentHashService(hashWorker);
    }


    /**
     * Caller-owned blocking digest. Must not be invoked on the Mindustry tick thread.
     * Returns null on I/O failure instead of propagating.
     */
    public MapContentHash hash(PathLike file) {
        Objects.requireNonNull(file, "file");
        try (InputStream input = file.read()) {
            return MapContentHash.digest(input);
        } catch (Exception e) {
            PLog.warn("Map content hash failed for @: @", file.name(), e.toString());
            return null;
        }
    }

    /** Blocking digest for a repository record, swallowing I/O errors. */
    public void hashAndStore(MapData data, PathLike file) {
        MapContentHash result = hash(file);
        if (result != null) {
            data.contentHash = result.asHex();
        }
    }

    /**
     * Hashes on the worker; the completion callback also runs on the worker
     * thread and must not touch game state. Persist via the main thread if needed.
     */
    public void hashAndStoreAsync(MapData data, PathLike file, Runnable onStored) {
        hashWorker.execute(() -> {
            try {
                hashAndStore(data, file);
            } finally {
                if (onStored != null) {
                    onStored.run();
                }
            }
        });
    }

    /** Minimal readable-file abstraction so tests and engine handles share one path. */
    public interface PathLike {
        InputStream read() throws Exception;

        String name();
    }
}
