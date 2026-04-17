package org.xcore.plugin.service;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.LeaderboardSlice;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopMenuCacheServiceTest {

    @Test
    @DisplayName("count round trip uses versioned redis key")
    void countRoundTrip_usesVersionedRedisKey() {
        RedisCommands<String, String> commands = redisCommands(new HashMap<>());
        RedisNetworkBackend backend = backend(commands);
        TopMenuCacheService service = new TopMenuCacheService(backend, new Gson(), config("mini-pvp"));

        assertThat(service.currentVersion()).isEqualTo(0L);
        assertThat(service.getTotalEntries(0L)).isNull();

        assertThat(service.putTotalEntries(0L, 42L)).isTrue();
        assertThat(service.getTotalEntries(0L)).isEqualTo(42L);
    }

    @Test
    @DisplayName("invalidateAll bumps cache version and makes old entries stale")
    void invalidateAll_bumpsCacheVersionAndMakesOldEntriesStale() {
        RedisCommands<String, String> commands = redisCommands(new HashMap<>());
        RedisNetworkBackend backend = backend(commands);
        TopMenuCacheService service = new TopMenuCacheService(backend, new Gson(), config("mini-pvp"));

        service.putTotalEntries(0L, 9L);
        assertThat(service.getTotalEntries(0L)).isEqualTo(9L);

        service.invalidateAll();

        assertThat(service.currentVersion()).isEqualTo(1L);
        assertThat(service.getTotalEntries(0L)).isEqualTo(9L);
        assertThat(service.getTotalEntries(1L)).isNull();
    }

    @Test
    @DisplayName("slice round trip stores and restores cursor page payload")
    void sliceRoundTrip_storesAndRestoresCursorPagePayload() {
        RedisCommands<String, String> commands = redisCommands(new HashMap<>());
        RedisNetworkBackend backend = backend(commands);
        TopMenuCacheService service = new TopMenuCacheService(backend, new Gson(), config("mini-pvp"));

        PlayerData player = new PlayerData("uuid-2", true);
        player.pid = 11;
        player.nickname = "PlayerTwo";

        LeaderboardCursor currentCursor = new LeaderboardCursor(1550, 0, 9);
        LeaderboardCursor nextCursor = new LeaderboardCursor(1499, 0, 11);
        LeaderboardSlice<PlayerData> slice = new LeaderboardSlice<>(List.of(player), true, nextCursor);

        assertThat(service.getTopSlice(1L, TopCategory.MINI_PVP, 10, currentCursor)).isNull();
        assertThat(service.putTopSlice(1L, TopCategory.MINI_PVP, 10, currentCursor, slice)).isTrue();

        LeaderboardSlice<PlayerData> restored = service.getTopSlice(1L, TopCategory.MINI_PVP, 10, currentCursor);
        assertThat(restored).isNotNull();
        assertThat(restored.items()).hasSize(1);
        assertThat(restored.items().getFirst().uuid).isEqualTo("uuid-2");
        assertThat(restored.hasNext()).isTrue();
        assertThat(restored.nextCursor()).isEqualTo(nextCursor);
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
        when(commands.incr(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, String.valueOf(next));
            return next;
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            store.put(key, value);
            return "OK";
        }).when(commands).set(anyString(), anyString(), any(SetArgs.class));
        return commands;
    }

    private static Config config(String server) {
        Config config = new Config();
        config.server = server;
        return config;
    }
}
