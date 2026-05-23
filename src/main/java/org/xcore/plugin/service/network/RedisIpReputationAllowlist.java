package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationAllowlist;

import java.util.Collections;
import java.util.Set;

@Singleton
public class RedisIpReputationAllowlist implements IpReputationAllowlist {

    private static final String KEY_PREFIX = "xcore:ip-reputation:allowlist:v1";

    private final RedisNetworkBackend backend;
    private final Config config;

    @Inject
    public RedisIpReputationAllowlist(RedisNetworkBackend backend, @Named("redis") Gson redisGson, Config config) {
        this.backend = backend;
        this.config = config;
    }

    @Override
    public boolean contains(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        String normalized = normalizeIp(ip);
        return backend.withCommands(commands -> {
            Boolean member = commands.sismember(allowlistKey(), normalized);
            return Boolean.TRUE.equals(member);
        }, false);
    }

    @Override
    public boolean add(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        String normalized = normalizeIp(ip);
        return backend.withCommands(commands -> {
            commands.sadd(allowlistKey(), normalized);
            return true;
        }, false);
    }

    @Override
    public boolean remove(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        String normalized = normalizeIp(ip);
        return backend.withCommands(commands -> {
            commands.srem(allowlistKey(), normalized);
            return true;
        }, false);
    }

    @Override
    public Set<String> list() {
        return backend.withCommands(commands -> {
            Set<String> members = commands.smembers(allowlistKey());
            return members != null ? members : Collections.emptySet();
        }, Collections.emptySet());
    }

    private String allowlistKey() {
        return KEY_PREFIX + ":" + config.server;
    }

    private String normalizeIp(String ip) {
        return ip.trim();
    }
}
