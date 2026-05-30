package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import org.xcore.plugin.config.TomlXcoreConfig;

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

    public CachedObserverState get(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }

        return backend.withCommands(commands -> {
            String payloadJson = commands.get(key(playerUuid));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }
            return redisGson.fromJson(payloadJson, CachedObserverState.class);
        }, null);
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
