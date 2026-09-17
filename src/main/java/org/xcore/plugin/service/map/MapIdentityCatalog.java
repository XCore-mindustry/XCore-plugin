package org.xcore.plugin.service.map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.MainThreadDispatcher;
import org.xcore.plugin.map.domain.MapContentHash;
import org.xcore.plugin.map.domain.MapSlug;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/** Captures metadata on the engine thread; only stream suppliers cross the I/O boundary. */
@Singleton
public final class MapIdentityCatalog {
    private final MapContentHashService hashing;
    private final MainThreadDispatcher mainThread;
    private final AtomicLong generation = new AtomicLong();
    private volatile Snapshot snapshot = new Snapshot(0, List.of(), Map.of());

    @Inject
    public MapIdentityCatalog(MapContentHashService hashing) {
        this(hashing, MainThreadDispatcher.mindustry());
    }

    MapIdentityCatalog(MapContentHashService hashing, MainThreadDispatcher mainThread) {
        this.hashing = hashing;
        this.mainThread = mainThread;
    }

    public Snapshot snapshot() { return snapshot; }

    public CompletionStage<Boolean> rebuild(List<Source> sources) {
        long revision = generation.incrementAndGet();
        var captured = List.copyOf(sources);
        var entries = new ArrayList<Entry>();
        var failures = new LinkedHashMap<String, String>();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        // Serial admission avoids exhausting the shared bounded file-I/O executor on large catalogs.
        for (Source source : captured) {
            var slug = MapSlug.of(source.author(), source.name());
            chain = chain.thenCompose(ignored -> {
                if (generation.get() != revision) return CompletableFuture.completedFuture(null);
                return hashing.hashAsync(source.open()).handle((hash, error) -> {
                    if (error == null) entries.add(new Entry(source.fileName(), source.name(), source.author(),
                            source.width(), source.height(), slug, hash));
                    else failures.put(source.fileName(), error.toString());
                    return (Void) null;
                });
            });
        }
        var result = new CompletableFuture<Boolean>();
        chain.whenComplete((ignored, error) -> mainThread.execute(() -> {
            if (error != null) result.completeExceptionally(error);
            else if (generation.get() != revision) result.complete(false);
            else {
                snapshot = new Snapshot(revision, entries, failures);
                result.complete(true);
            }
        }));
        return result;
    }

    public record Source(String fileName, String name, String author, int width, int height,
                         Callable<InputStream> open) {}
    public record Entry(String fileName, String name, String author, int width, int height,
                        MapSlug slug, MapContentHash hash) {}
    public record Snapshot(long generation, List<Entry> entries, Map<String, String> failures) {
        public Snapshot {
            entries = List.copyOf(entries);
            failures = Map.copyOf(failures);
        }
    }
}
