package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.TopMenuCacheService;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAsyncWriteTest {
    @Test
    void observerPutUsesNativeAsyncSet() {
        RedisAsyncCommands<String, String> commands = mock(RedisAsyncCommands.class);
        RedisFuture<String> redisFuture = redisFuture("OK");
        when(commands.set(anyString(), anyString(), any(SetArgs.class))).thenReturn(redisFuture);

        RedisNetworkBackend backend = backend(commands);
        RedisObserverStateStore store = new RedisObserverStateStore(backend, new Gson(), config("mini-pvp"));

        assertThat(store.putAsync("uuid-1", mindustry.game.Team.sharded).toCompletableFuture().join()).isTrue();
        verify(commands).set(anyString(), anyString(), any(SetArgs.class));
        verify(backend, never()).withCommands(any(Function.class), any());
    }

    @Test
    void observerDeleteUsesNativeAsyncDelete() {
        RedisAsyncCommands<String, String> commands = mock(RedisAsyncCommands.class);
        RedisFuture<Long> redisFuture = redisFuture(1L);
        when(commands.del(anyString())).thenReturn(redisFuture);

        RedisNetworkBackend backend = backend(commands);
        RedisObserverStateStore store = new RedisObserverStateStore(backend, new Gson(), config("mini-pvp"));

        assertThat(store.deleteAsync("uuid-1").toCompletableFuture().join()).isTrue();
        verify(commands).del(anyString());
        verify(backend, never()).withCommands(any(Function.class), any());
    }

    @Test
    void topCacheWritesUseNativeAsyncCommands() {
        RedisAsyncCommands<String, String> commands = mock(RedisAsyncCommands.class);
        RedisFuture<Long> incrFuture = redisFuture(3L);
        RedisFuture<String> setFuture = redisFuture("OK");
        when(commands.incr(anyString())).thenReturn(incrFuture);
        when(commands.set(anyString(), anyString(), any(SetArgs.class))).thenReturn(setFuture);

        RedisNetworkBackend backend = backend(commands);
        TopMenuCacheService cache = new TopMenuCacheService(backend, new Gson(), config("mini-pvp"));

        assertThat(cache.invalidateAllAsync().toCompletableFuture().join()).isTrue();
        assertThat(cache.putTotalEntriesAsync(3L, 42L).toCompletableFuture().join()).isTrue();
        verify(commands).incr(anyString());
        verify(commands).set(anyString(), anyString(), any(SetArgs.class));
        verify(backend, never()).withCommands(any(Function.class), any());
    }

    @Test
    void asyncWritesFailFastWhenRedisHangs() {
        // Chaos guard: a Redis connection that accepts commands but never answers
        // must not hang the caller; the 500ms orTimeout bounds the wait.
        RedisAsyncCommands<String, String> commands = mock(RedisAsyncCommands.class);
        RedisFuture<Long> hanging = hangingFuture();
        when(commands.incr(anyString())).thenReturn(hanging);

        RedisNetworkBackend backend = backend(commands);
        TopMenuCacheService cache = new TopMenuCacheService(backend, new Gson(), config("mini-pvp"));

        long startedAt = System.nanoTime();
        CompletionStage<Boolean> stage = cache.invalidateAllAsync();

        assertThat(stage.toCompletableFuture()).failsWithin(2, TimeUnit.SECONDS);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        assertThat(elapsedMillis).isLessThan(2_000);
    }

    @SuppressWarnings("unchecked")
    private static RedisNetworkBackend backend(RedisAsyncCommands<String, String> commands) {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withAsyncCommands(any(Function.class), any())).thenAnswer(invocation -> {
            Function<RedisAsyncCommands<String, String>, CompletionStage<Object>> operation = invocation.getArgument(0);
            return operation.apply(commands);
        });
        return backend;
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisFuture<T> redisFuture(T value) {
        RedisFuture<T> future = mock(RedisFuture.class);
        when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(value));
        return future;
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisFuture<T> hangingFuture() {
        RedisFuture<T> future = mock(RedisFuture.class);
        when(future.toCompletableFuture()).thenReturn(new CompletableFuture<>());
        return future;
    }

    private static TomlXcoreConfig config(String server) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = server;
        return config;
    }
}
