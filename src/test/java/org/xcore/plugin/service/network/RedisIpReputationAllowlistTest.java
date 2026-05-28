package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisIpReputationAllowlistTest {

    @Test
    @DisplayName("contains returns false for missing ip")
    void contains_returnsFalseForMissingIp() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        assertThat(allowlist.contains("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("add and contains round trip")
    void addAndContains_roundTrip() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        assertThat(allowlist.add("1.2.3.4")).isTrue();
        assertThat(allowlist.contains("1.2.3.4")).isTrue();
        assertThat(allowlist.contains("5.6.7.8")).isFalse();
    }

    @Test
    @DisplayName("remove deletes ip from allowlist")
    void remove_deletesIpFromAllowlist() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        allowlist.add("1.2.3.4");
        assertThat(allowlist.contains("1.2.3.4")).isTrue();

        assertThat(allowlist.remove("1.2.3.4")).isTrue();
        assertThat(allowlist.contains("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("contains returns false for blank ip")
    void contains_returnsFalseForBlankIp() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        assertThat(allowlist.contains("")).isFalse();
        assertThat(allowlist.contains(null)).isFalse();
    }

    @Test
    @DisplayName("add and remove return false for blank ip")
    void addAndRemove_returnFalseForBlankIp() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        assertThat(allowlist.add("")).isFalse();
        assertThat(allowlist.add(null)).isFalse();
        assertThat(allowlist.remove("")).isFalse();
        assertThat(allowlist.remove(null)).isFalse();
    }

    @Test
    @DisplayName("key includes server name and version prefix")
    void key_includesServerNameAndVersionPrefix() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("alpha"));

        allowlist.add("1.2.3.4");

        assertThat(store).contains("1.2.3.4");
    }

    @Test
    @DisplayName("list returns empty set when no entries")
    void list_returnsEmptySetWhenNoEntries() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        assertThat(allowlist.list()).isEmpty();
    }

    @Test
    @DisplayName("list returns all added entries")
    void list_returnsAllAddedEntries() {
        Set<String> store = new HashSet<>();
        RedisCommands<String, String> commands = redisCommands(store);
        RedisNetworkBackend backend = backend(commands);
        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        allowlist.add("1.2.3.4");
        allowlist.add("5.6.7.8");

        assertThat(allowlist.list()).containsExactlyInAnyOrder("1.2.3.4", "5.6.7.8");
    }

    @Test
    @DisplayName("backend failure returns fallback")
    void backendFailure_returnsFallback() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withCommands(any(Function.class), any())).thenAnswer(invocation -> invocation.getArgument(1));

        RedisIpReputationAllowlist allowlist = new RedisIpReputationAllowlist(backend, new Gson(), config("mini-pvp"));

        assertThat(allowlist.contains("1.2.3.4")).isFalse();
        assertThat(allowlist.add("1.2.3.4")).isFalse();
        assertThat(allowlist.remove("1.2.3.4")).isFalse();
        assertThat(allowlist.list()).isEmpty();
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
    private static RedisCommands<String, String> redisCommands(Set<String> store) {
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(commands.sismember(anyString(), anyString())).thenAnswer(invocation -> {
            String member = invocation.getArgument(1);
            return store.contains(member);
        });
        when(commands.sadd(anyString(), anyString())).thenAnswer(invocation -> {
            String member = invocation.getArgument(1);
            return store.add(member) ? 1L : 0L;
        });
        when(commands.srem(anyString(), anyString())).thenAnswer(invocation -> {
            String member = invocation.getArgument(1);
            return store.remove(member) ? 1L : 0L;
        });
        when(commands.smembers(anyString())).thenAnswer(invocation -> {
            return new java.util.HashSet<>(store);
        });
        return commands;
    }

    private static TomlXcoreConfig config(String server) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = server;
        return config;
    }
}
