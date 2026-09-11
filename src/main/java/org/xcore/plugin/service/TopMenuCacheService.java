package org.xcore.plugin.service;

import arc.util.Log;
import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.LeaderboardSlice;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Singleton
public class TopMenuCacheService {

    static final int COUNT_TTL_SECONDS = 30;
    static final int PAGE_TTL_SECONDS = 10;

    private final RedisNetworkBackend backend;
    private final Gson redisGson;
    private final TomlXcoreConfig config;

    @Inject
    public TopMenuCacheService(RedisNetworkBackend backend, @Named("redis") Gson redisGson, TomlXcoreConfig config) {
        this.backend = backend;
        this.redisGson = redisGson;
        this.config = config;
    }

    public long currentVersion() {
        return backend.withCommands(commands -> {
            String raw = commands.get(versionKey());
            if (raw == null || raw.isBlank()) {
                return 0L;
            }

            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }, 0L);
    }

    public void invalidateAll() {
        backend.withCommands(commands -> {
            commands.incr(versionKey());
            return true;
        }, false);
    }

    /** Native Lettuce async invalidation for gameplay and persistence callbacks. */
    public CompletionStage<Boolean> invalidateAllAsync() {
        return observeWrite(backend.withAsyncCommands(commands -> commands
                .incr(versionKey())
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply(value -> value != null), false));
    }

    public Long getTotalEntries(long version) {
        return backend.withCommands(commands -> {
            String payloadJson = commands.get(countKey(version));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }

            CachedCount cached = redisGson.fromJson(payloadJson, CachedCount.class);
            return cached == null ? null : cached.totalEntries();
        }, null);
    }

    public boolean putTotalEntries(long version, long totalEntries) {
        CachedCount payload = new CachedCount(totalEntries, System.currentTimeMillis());
        return backend.withCommands(commands -> {
            commands.set(countKey(version), redisGson.toJson(payload), SetArgs.Builder.ex(COUNT_TTL_SECONDS));
            return true;
        }, false);
    }

    /** Native Lettuce async count write for cache misses on the main thread. */
    public CompletionStage<Boolean> putTotalEntriesAsync(long version, long totalEntries) {
        CachedCount payload = new CachedCount(totalEntries, System.currentTimeMillis());
        return observeWrite(backend.withAsyncCommands(commands -> commands
                .set(countKey(version), redisGson.toJson(payload), SetArgs.Builder.ex(COUNT_TTL_SECONDS))
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply("OK"::equals), false));
    }

    public LeaderboardSlice<PlayerData> getTopSlice(long version,
                                                    TopCategory category,
                                                    int pageSize,
                                                    LeaderboardCursor cursor) {
        return backend.withCommands(commands -> {
            String payloadJson = commands.get(sliceKey(version, category, pageSize, cursor));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }

            CachedTopSlice cached = redisGson.fromJson(payloadJson, CachedTopSlice.class);
            if (cached == null) {
                return null;
            }

            return new LeaderboardSlice<>(cached.players(), cached.hasNext(), cached.nextCursor());
        }, null);
    }

    public CompletionStage<Boolean> putTopSliceAsync(long version,
                                                     TopCategory category,
                                                     int pageSize,
                                                     LeaderboardCursor cursor,
                                                     LeaderboardSlice<PlayerData> slice) {
        if (slice == null || slice.items() == null) {
            return CompletableFuture.completedFuture(false);
        }

        CachedTopSlice payload = new CachedTopSlice(slice.items(), slice.hasNext(), slice.nextCursor(), System.currentTimeMillis());
        return observeWrite(backend.withAsyncCommands(commands -> commands
                .set(
                        sliceKey(version, category, pageSize, cursor),
                        redisGson.toJson(payload),
                        SetArgs.Builder.ex(PAGE_TTL_SECONDS)
                )
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply("OK"::equals), false));
    }

    public boolean putTopSlice(long version,
                               TopCategory category,
                               int pageSize,
                               LeaderboardCursor cursor,
                               LeaderboardSlice<PlayerData> slice) {
        if (slice == null || slice.items() == null) {
            return false;
        }

        CachedTopSlice payload = new CachedTopSlice(slice.items(), slice.hasNext(), slice.nextCursor(), System.currentTimeMillis());
        return backend.withCommands(commands -> {
            commands.set(
                    sliceKey(version, category, pageSize, cursor),
                    redisGson.toJson(payload),
                    SetArgs.Builder.ex(PAGE_TTL_SECONDS)
            );
            return true;
        }, false);
    }

    private CompletionStage<Boolean> observeWrite(CompletionStage<Boolean> stage) {
        return stage.whenComplete((success, error) -> {
            if (error != null) {
                Log.warn("Redis top cache write failed: @", error.getMessage());
            }
        });
    }

    private String versionKey() {
        return keyPrefix() + ":version";
    }

    private String countKey(long version) {
        return keyPrefix() + ":v:" + version + ":count";
    }

    private String sliceKey(long version, TopCategory category, int pageSize, LeaderboardCursor cursor) {
        return keyPrefix() + ":v:" + version + ":slice:"
                + category.name().toLowerCase(Locale.ROOT) + ":" + pageSize + ":" + cursorKey(cursor);
    }

    private String cursorKey(LeaderboardCursor cursor) {
        if (cursor == null) {
            return "first";
        }
        return cursor.primaryValue() + ":" + cursor.secondaryValue() + ":" + cursor.pid();
    }

    private String keyPrefix() {
        return "xcore:top:cache:" + config.server.name;
    }

    record CachedCount(long totalEntries, long createdAt) {
    }

    record CachedTopSlice(List<PlayerData> players, boolean hasNext, LeaderboardCursor nextCursor, long createdAt) {
    }
}
