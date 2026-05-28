package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationResult;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisIpReputationCacheTest {

    @Test
    @DisplayName("get returns null for missing key")
    void get_returnsNullForMissingKey() {
        RedisCommands<String, String> commands = redisCommands(new HashMap<>());
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationCache cache = new RedisIpReputationCache(backend, new Gson(), config("mini-pvp"));

        assertThat(cache.get("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("put and get round trip stores result with ttl")
    void putAndGet_roundTripStoresResult() {
        RedisCommands<String, String> commands = redisCommands(new HashMap<>());
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationCache cache = new RedisIpReputationCache(backend, new Gson(), config("mini-pvp"));

        IpReputationResult result = new IpReputationResult("1.2.3.4", true, false, false);

        assertThat(cache.put("1.2.3.4", result)).isTrue();

        IpReputationResult cached = cache.get("1.2.3.4");
        assertThat(cached).isNotNull();
        assertThat(cached.ip()).isEqualTo("1.2.3.4");
        assertThat(cached.proxy()).isTrue();
        assertThat(cached.hosting()).isFalse();
        assertThat(cached.mobile()).isFalse();
    }

    @Test
    @DisplayName("get returns null for blank ip")
    void get_returnsNullForBlankIp() {
        RedisCommands<String, String> commands = redisCommands(new HashMap<>());
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationCache cache = new RedisIpReputationCache(backend, new Gson(), config("mini-pvp"));

        assertThat(cache.get("")).isNull();
        assertThat(cache.get(null)).isNull();
    }

    @Test
    @DisplayName("put returns false for blank ip or null result")
    void put_returnsFalseForInvalidInput() {
        RedisCommands<String, String> commands = redisCommands(new HashMap<>());
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationCache cache = new RedisIpReputationCache(backend, new Gson(), config("mini-pvp"));

        assertThat(cache.put("", new IpReputationResult("1.2.3.4", true, false, false))).isFalse();
        assertThat(cache.put("1.2.3.4", null)).isFalse();
        assertThat(cache.put(null, new IpReputationResult("1.2.3.4", true, false, false))).isFalse();
    }

    @Test
    @DisplayName("key includes server name and version prefix")
    void key_includesServerNameAndVersionPrefix() {
        Map<String, String> store = new HashMap<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationCache cache = new RedisIpReputationCache(backend, new Gson(), config("alpha"));

        cache.put("1.2.3.4", new IpReputationResult("1.2.3.4", false, false, false));

        assertThat(store).containsKey("xcore:ip-reputation:cache:v1:alpha:1.2.3.4");
    }

    @Test
    @DisplayName("backend failure returns fallback")
    void backendFailure_returnsFallback() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withCommands(any(Function.class), any())).thenAnswer(invocation -> invocation.getArgument(1));

        RedisIpReputationCache cache = new RedisIpReputationCache(backend, new Gson(), config("mini-pvp"));

        assertThat(cache.get("1.2.3.4")).isNull();
        assertThat(cache.put("1.2.3.4", new IpReputationResult("1.2.3.4", true, false, false))).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static RedisNetworkBackend backend(RedisCommands<String, String> commands) {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withCommands(any(Function.class), any())).thenAnswer(invocation -> {
            Function<RedisCommands<String, String>, Object> operation = invocation.getArgument(0);
            return operation.apply(commands);
        });
        return backend;
    }

    @SuppressWarnings("unchecked")
    private static RedisCommands<String, String> redisCommands(Map<String, String> store) {
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(commands.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            store.put(key, value);
            return "OK";
        }).when(commands).set(anyString(), anyString(), any(SetArgs.class));
        return commands;
    }

    private static TomlXcoreConfig config(String server) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = server;
        return config;
    }
}
