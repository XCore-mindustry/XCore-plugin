package org.xcore.plugin.service.network;

import arc.util.Log;
import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Singleton
public class RedisObserverStateStore {

    static final long OBSERVER_TTL_SECONDS = 30 * 60L;

    private final RedisNetworkBackend backend;
    private final Gson redisGson;
    private final TomlXcoreConfig config;

    @Inject
    public RedisObserverStateStore(RedisNetworkBackend backend, @Named("redis") Gson redisGson, TomlXcoreConfig config) {
        this.backend = backend;
        this.redisGson = redisGson;
        this.config = config;
    }

    public boolean put(String playerUuid, Team returnTeam) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        CachedObserverState payload = new CachedObserverState(resolveReturnTeamId(returnTeam), System.currentTimeMillis());
        return backend.withCommands(commands -> {
            commands.set(key(playerUuid), redisGson.toJson(payload), SetArgs.Builder.ex(OBSERVER_TTL_SECONDS));
            return true;
        }, false);
    }

    /** Native Lettuce async write used by gameplay event handlers. */
    public CompletionStage<Boolean> putAsync(String playerUuid, Team returnTeam) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        CachedObserverState payload = new CachedObserverState(resolveReturnTeamId(returnTeam), System.currentTimeMillis());
        return observeWrite(backend.withAsyncCommands(commands -> commands
                .set(key(playerUuid), redisGson.toJson(payload), SetArgs.Builder.ex(OBSERVER_TTL_SECONDS))
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply("OK"::equals), false));
    }

    public CachedObserverState get(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }

        return getAsync(playerUuid).toCompletableFuture().join();
    }

    public CompletionStage<CachedObserverState> getAsync(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        return backend.withAsyncCommands(commands -> commands
                .get(key(playerUuid))
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply(payloadJson -> {
                    if (payloadJson == null || payloadJson.isBlank()) {
                        return null;
                    }
                    return redisGson.fromJson(payloadJson, CachedObserverState.class);
                })
                .exceptionally(err -> null),
                null
        );
    }

    /** Native Lettuce async delete used by gameplay event handlers. */
    public CompletionStage<Boolean> deleteAsync(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        return observeWrite(backend.withAsyncCommands(commands -> commands
                .del(key(playerUuid))
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply(deleted -> deleted != null && deleted > 0), false));
    }

    public boolean delete(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        return backend.withCommands(commands -> {
            commands.del(key(playerUuid));
            return true;
        }, false);
    }

    private CompletionStage<Boolean> observeWrite(CompletionStage<Boolean> stage) {
        return stage.whenComplete((success, error) -> {
            if (error != null) {
                Log.warn("Redis observer state write failed: @", error.getMessage());
            }
        });
    }

    public Team resolveReturnTeam(CachedObserverState state) {
        if (state == null || state.returnTeamId() < 0) {
            return null;
        }
        return Team.get(state.returnTeamId());
    }

    private int resolveReturnTeamId(Team returnTeam) {
        return returnTeam == null ? -1 : returnTeam.id;
    }

    private String key(String playerUuid) {
        return "xcore:observer:" + config.server.name + ":" + playerUuid;
    }

    public record CachedObserverState(int returnTeamId, long createdAt) {
    }
}
