package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationAllowlist;

import java.util.Collections;
import java.util.Set;

@Singleton
public class RedisIpReputationAllowlist implements IpReputationAllowlist {

    private static final String KEY_PREFIX = "xcore:ip-reputation:allowlist:v1";

    private final RedisNetworkBackend backend;
    private final TomlXcoreConfig config;

    /**
     * Constructs a Redis-backed IP reputation allowlist scoped to the configured server.
     *
     * @param backend     RedisNetworkBackend used to execute Redis commands for allowlist operations
     * @param redisGson   Gson instance bound to "redis" (accepted for injection; not used directly)
     * @param config      Configuration whose server-local name is used to scope the Redis key
     */
    @Inject
    public RedisIpReputationAllowlist(RedisNetworkBackend backend, @Named("redis") Gson redisGson, TomlXcoreConfig config) {
        this.backend = backend;
        this.config = config;
    }

    /**
     * Checks whether the given IP address is present in the Redis-backed allowlist.
     *
     * @param ip the IP address to check; if {@code null} or blank the method returns {@code false}
     * @return {@code true} if the normalized IP is a member of the allowlist, {@code false} otherwise
     */
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

    /**
     * Adds the given IP to the allowlist after trimming surrounding whitespace.
     *
     * @param ip the IP address to add; may include surrounding whitespace
     * @return `true` if the add operation was initiated, `false` when `ip` is null or blank
     */
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

    /**
     * Remove the given IP from the configured Redis allowlist.
     *
     * The input is trimmed before use; `null` or blank inputs are rejected.
     *
     * @param ip the IP address to remove (will be trimmed)
     * @return `true` if the remove command was issued to Redis, `false` otherwise
     */
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

    /**
     * Retrieve the IP addresses currently stored in the allowlist for the configured server.
     *
     * @return the set of allowlisted IP addresses, or an empty set if no members are present or the backend returned null
     */
    @Override
    public Set<String> list() {
        return backend.withCommands(commands -> {
            Set<String> members = commands.smembers(allowlistKey());
            return members != null ? members : Collections.emptySet();
        }, Collections.emptySet());
    }

    /**
     * Builds the Redis key used to store the IP allowlist for the current server.
     *
     * @return the namespaced Redis key for the allowlist (prefix + ":" + server name)
     */
    private String allowlistKey() {
        return KEY_PREFIX + ":" + config.server.name;
    }

    /**
     * Trim leading and trailing whitespace from an IP string.
     *
     * @param ip the IP string to normalize (may contain surrounding whitespace)
     * @return the input string with leading and trailing whitespace removed
     */
    private String normalizeIp(String ip) {
        return ip.trim();
    }
}
