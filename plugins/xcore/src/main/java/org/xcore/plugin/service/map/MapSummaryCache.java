package org.xcore.plugin.service.map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

/** Shared, immutable, stale-while-refresh map metadata. Never waits for MongoDB. */
@Singleton
public class MapSummaryCache {
    private static final long TTL_MILLIS = 60_000;
    private final MapDataRepository repository;
    private final LongSupplier clock;
    private volatile List<Summary> snapshot = List.of();
    private long expiresAt;
    private boolean loaded;
    private CompletableFuture<List<Summary>> pending;

    @Inject
    public MapSummaryCache(MapDataRepository repository) {
        this(repository, System::currentTimeMillis);
    }

    MapSummaryCache(MapDataRepository repository, LongSupplier clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<Summary> snapshot() {
        return snapshot;
    }

    /** Optimistically updates vote tallies in the cached snapshot across all sessions for a given gamemode. */
    public synchronized void patchVoteOptimistic(String mapId, String mode, int likeDelta, int dislikeDelta) {
        if (mapId == null || mapId.isBlank() || snapshot.isEmpty()) return;
        var updated = new java.util.ArrayList<Summary>(snapshot.size());
        boolean matched = false;
        for (Summary s : snapshot) {
            boolean modeMatch = (mode == null || mode.isBlank() || mode.equalsIgnoreCase(s.mode()));
            if (modeMatch && (mapId.equals(s.id()) || mapId.equalsIgnoreCase(s.fileName()) || mapId.equalsIgnoreCase(s.name()))) {
                matched = true;
                updated.add(new Summary(
                        s.id(), s.fileName(), s.name(), s.author(), s.mode(),
                        Math.max(0, s.likes() + likeDelta),
                        Math.max(0, s.dislikes() + dislikeDelta)
                ));
            } else {
                updated.add(s);
            }
        }
        if (matched) {
            this.snapshot = List.copyOf(updated);
        }
    }

    public synchronized void patchVoteOptimistic(String mapId, int likeDelta, int dislikeDelta) {
        patchVoteOptimistic(mapId, null, likeDelta, dislikeDelta);
    }

    public synchronized CompletionStage<List<Summary>> refresh() {
        if (pending != null) return pending;
        if (loaded && clock.getAsLong() < expiresAt) {
            return CompletableFuture.completedFuture(snapshot);
        }
        var request = new CompletableFuture<List<Summary>>();
        pending = request;
        try {
            repository.findAllAsync().whenComplete((rows, error) -> {
                synchronized (this) {
                    try {
                        if (error != null) {
                            request.completeExceptionally(error);
                        } else {
                            snapshot = rows.stream().map(Summary::from).toList();
                            loaded = true;
                            expiresAt = clock.getAsLong() + TTL_MILLIS;
                            request.complete(snapshot);
                        }
                    } catch (Throwable failure) {
                        request.completeExceptionally(failure);
                    } finally {
                        pending = null;
                    }
                }
            });
        } catch (Throwable error) {
            pending = null;
            request.completeExceptionally(error);
        }
        return request;
    }

    public record Summary(String id, String fileName, String name, String author,
                          String mode, int likes, int dislikes) {
        static Summary from(MapData data) {
            return new Summary(data.id == null ? null : data.id.toHexString(), data.fileName,
                    data.name, data.author, data.gameMode, data.like, data.dislike);
        }
    }
}
