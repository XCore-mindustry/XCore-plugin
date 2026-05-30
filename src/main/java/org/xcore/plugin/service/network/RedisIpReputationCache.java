package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationCache;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationResult;

@Singleton
public class RedisIpReputationCache implements IpReputationCache {

    private static final String KEY_PREFIX = "xcore:ip-reputation:cache:v1";

    private final RedisNetworkBackend backend;
    private final Gson redisGson;
    private final TomlXcoreConfig config;

    /**
     * Constructs a Redis-backed IP reputation cache using the provided dependencies.
     *
     * @param backend   the RedisNetworkBackend used to execute Redis commands
     * @param redisGson the Gson instance (named "redis") used to serialize/deserialize IpReputationResult
     * @param config    configuration holding cache TTL and server scoping values
     */
    @Inject
    public RedisIpReputationCache(RedisNetworkBackend backend, @Named("redis") Gson redisGson, TomlXcoreConfig config) {
        this.backend = backend;
        this.redisGson = redisGson;
        this.config = config;
    }

    /**
     * Retrieve the cached IP reputation for the given IP address.
     *
     * If the input is null or blank, or no cached payload exists for the normalized IP, this method returns `null`.
     *
     * @param ip the IP address to look up (may contain surrounding whitespace)
     * @return the cached {@code IpReputationResult} for the normalized IP, or {@code null} if not found or the input is invalid
     */
    @Override
    public IpReputationResult get(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }

        String normalized = normalizeIp(ip);
        return backend.withCommands(commands -> {
            String payloadJson = commands.get(cacheKey(normalized));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }
            return redisGson.fromJson(payloadJson, IpReputationResult.class);
        }, null);
    }

    /**
     * Stores an IP reputation entry in Redis under a server-scoped cache key with the configured TTL.
     *
     * @param ip the IP address to cache (leading/trailing whitespace is ignored)
     * @param result the reputation data to store
     * @return `true` if the entry was issued to Redis, `false` if the input was invalid
     */
    @Override
    public boolean put(String ip, IpReputationResult result) {
        if (ip == null || ip.isBlank() || result == null) {
            return false;
        }

        String normalized = normalizeIp(ip);
        return backend.withCommands(commands -> {
            commands.set(
                    cacheKey(normalized),
                    redisGson.toJson(result),
                    SetArgs.Builder.ex(config.ipReputation.cacheTtlSeconds)
            );
            return true;
        }, false);
    }

    /**
     * Constructs the Redis cache key for a normalized IP scoped to the current server.
     *
     * @param normalizedIp the normalized (trimmed) IP address to include in the key
     * @return the Redis key in the form "xcore:ip-reputation:cache:v1:{server}:{normalizedIp}"
     */
    private String cacheKey(String normalizedIp) {
        return KEY_PREFIX + ":" + config.server.name + ":" + normalizedIp;
    }

    /**
     * Normalize an IP string by removing leading and trailing whitespace.
     *
     * @param ip the IP string to normalize
     * @return the IP string with surrounding whitespace removed
     */
    private String normalizeIp(String ip) {
        return ip.trim();
    }
}
