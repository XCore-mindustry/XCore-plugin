package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationCache;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationResult;

@Singleton
public class RedisIpReputationCache implements IpReputationCache {

    private static final String KEY_PREFIX = "xcore:ip-reputation:cache:v1";

    private final RedisNetworkBackend backend;
    private final Gson redisGson;
    private final Config config;

    @Inject
    public RedisIpReputationCache(RedisNetworkBackend backend, @Named("redis") Gson redisGson, Config config) {
        this.backend = backend;
        this.redisGson = redisGson;
        this.config = config;
    }

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

    private String cacheKey(String normalizedIp) {
        return KEY_PREFIX + ":" + config.server + ":" + normalizedIp;
    }

    private String normalizeIp(String ip) {
        return ip.trim();
    }
}
