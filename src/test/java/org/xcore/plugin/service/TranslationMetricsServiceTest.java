package org.xcore.plugin.service;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TranslationMetricsServiceTest {

    @Test
    @DisplayName("incrementGlobal updates totals and minute bucket with configured ttl")
    void incrementGlobal_updatesTotalsAndMinuteBucketWithConfiguredTtl() {
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        Map<String, Long> expirations = new HashMap<>();
        RedisCommands<String, String> commands = redisCommands(hashes, expirations);
        RedisAsyncCommands<String, String> asyncCommands = redisAsyncCommands(hashes, expirations);
        RedisNetworkBackend backend = backend(commands, asyncCommands);

        TomlXcoreConfig config = config("mini-pvp");
        config.translation.metrics.minuteBucketTtlSeconds = 42;
        TranslationMetricsService service = new TranslationMetricsService(backend, config);

        service.incrementGlobal("requests_total");

        assertThat(hashes.get("xcore:translation:metrics:mini-pvp:totals"))
                .containsEntry("requests_total", "1");
        String minuteKey = hashes.keySet().stream()
                .filter(key -> key.startsWith("xcore:translation:metrics:mini-pvp:minute:"))
                .findFirst()
                .orElseThrow();
        assertThat(hashes.get(minuteKey)).containsEntry("requests_total", "1");
        assertThat(expirations).containsEntry(minuteKey, 42L);
    }

    @Test
    @DisplayName("incrementProvider uses sanitized provider key and records totals")
    void incrementProvider_usesSanitizedProviderKeyAndRecordsTotals() {
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        Map<String, Long> expirations = new HashMap<>();
        RedisCommands<String, String> commands = redisCommands(hashes, expirations);
        RedisAsyncCommands<String, String> asyncCommands = redisAsyncCommands(hashes, expirations);
        RedisNetworkBackend backend = backend(commands, asyncCommands);
        TranslationMetricsService service = new TranslationMetricsService(backend, config("alpha"));

        service.incrementProvider(" OpenAI / Model ", "attempts_total");

        assertThat(hashes)
                .containsKey("xcore:translation:metrics:alpha:provider:openai___model:totals");
        assertThat(hashes.get("xcore:translation:metrics:alpha:provider:openai___model:totals"))
                .containsEntry("attempts_total", "1");
        assertThat(hashes.keySet())
                .anyMatch(key -> key.startsWith("xcore:translation:metrics:alpha:provider:openai___model:minute:"));
    }

    @Test
    @DisplayName("recordProviderLatency and status markers preserve provider totals fields")
    void recordProviderLatencyAndStatusMarkers_preserveProviderTotalsFields() {
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        Map<String, Long> expirations = new HashMap<>();
        RedisCommands<String, String> commands = redisCommands(hashes, expirations);
        RedisAsyncCommands<String, String> asyncCommands = redisAsyncCommands(hashes, expirations);
        RedisNetworkBackend backend = backend(commands, asyncCommands);
        TranslationMetricsService service = new TranslationMetricsService(backend, config("beta"));

        service.recordProviderLatency("google", 123);
        service.markProviderSuccess("google");
        service.markProviderFailure("google", "timeout");

        assertThat(hashes.get("xcore:translation:metrics:beta:provider:google:totals"))
                .containsEntry("latency_sum_ms", "123")
                .containsEntry("latency_count", "1")
                .containsEntry("last_latency_ms", "123")
                .containsEntry("last_failure_reason", "timeout")
                .containsKeys("last_success_at", "last_failure_at");
    }

    @Test
    @DisplayName("disabled metrics skip backend access and reads return empty maps")
    void disabledMetrics_skipBackendAccessAndReadsReturnEmptyMaps() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        TomlXcoreConfig config = config("mini-pvp");
        config.translation.metrics.enabled = false;
        TranslationMetricsService service = new TranslationMetricsService(backend, config);

        service.incrementGlobal("requests_total");
        service.incrementProvider("google", "attempts_total");
        service.recordProviderLatency("google", 10);
        service.markProviderSuccess("google");
        service.markProviderFailure("google", "timeout");

        assertThat(service.readGlobalTotals()).isEmpty();
        assertThat(service.readProviderTotals("google")).isEmpty();
        assertThat(service.readCurrentMinuteGlobal()).isEmpty();
        assertThat(service.readCurrentMinuteProvider("google")).isEmpty();
        verifyNoInteractions(backend);
    }

    @Test
    @DisplayName("backend failure returns read fallbacks")
    void backendFailure_returnsReadFallbacks() {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withCommands(any(Function.class), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(backend.withAsyncCommands(any(Function.class), any())).thenAnswer(invocation ->
                java.util.concurrent.CompletableFuture.completedFuture(invocation.getArgument(1))
        );
        TranslationMetricsService service = new TranslationMetricsService(backend, config("mini-pvp"));

        assertThat(service.readGlobalTotals()).isEmpty();
        assertThat(service.readProviderTotals("google")).isEmpty();
        assertThat(service.readCurrentMinuteGlobal()).isEmpty();
        assertThat(service.readCurrentMinuteProvider("google")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static RedisNetworkBackend backend(RedisCommands<String, String> commands,
                                                RedisAsyncCommands<String, String> asyncCommands) {
        RedisNetworkBackend backend = mock(RedisNetworkBackend.class);
        when(backend.withCommands(any(Function.class), any())).thenAnswer(invocation -> {
            Function<RedisCommands<String, String>, Object> operation = invocation.getArgument(0);
            return operation.apply(commands);
        });
        when(backend.withAsyncCommands(any(Function.class), any())).thenAnswer(invocation -> {
            Function<RedisAsyncCommands<String, String>, CompletionStage<Object>> operation = invocation.getArgument(0);
            return operation.apply(asyncCommands);
        });
        return backend;
    }

    @SuppressWarnings("unchecked")
    private static RedisNetworkBackend backend(RedisCommands<String, String> commands) {
        return backend(commands, mock(RedisAsyncCommands.class));
    }

    @SuppressWarnings("unchecked")
    private static RedisCommands<String, String> redisCommands(Map<String, Map<String, String>> hashes,
                                                                Map<String, Long> expirations) {
        RedisCommands<String, String> commands = mock(RedisCommands.class);
        when(commands.hgetall(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return new LinkedHashMap<>(hashes.getOrDefault(key, Map.of()));
        });
        when(commands.hincrby(anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String field = invocation.getArgument(1);
            long delta = invocation.getArgument(2);
            Map<String, String> hash = hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            long next = Long.parseLong(hash.getOrDefault(field, "0")) + delta;
            hash.put(field, Long.toString(next));
            return next;
        });
        when(commands.hset(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String field = invocation.getArgument(1);
            String value = invocation.getArgument(2);
            hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(field, value);
            return true;
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long ttl = invocation.getArgument(1);
            expirations.put(key, ttl);
            return true;
        }).when(commands).expire(anyString(), anyLong());
        return commands;
    }

    @SuppressWarnings("unchecked")
    private static RedisAsyncCommands<String, String> redisAsyncCommands(Map<String, Map<String, String>> hashes,
                                                                         Map<String, Long> expirations) {
        RedisAsyncCommands<String, String> commands = mock(RedisAsyncCommands.class);
        when(commands.hincrby(anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String field = invocation.getArgument(1);
            long delta = invocation.getArgument(2);
            Map<String, String> hash = hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            long next = Long.parseLong(hash.getOrDefault(field, "0")) + delta;
            hash.put(field, Long.toString(next));
            return redisFuture(next);
        });
        when(commands.hset(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String field = invocation.getArgument(1);
            String value = invocation.getArgument(2);
            hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(field, value);
            return redisFuture(true);
        });
        when(commands.expire(anyString(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long ttl = invocation.getArgument(1);
            expirations.put(key, ttl);
            return redisFuture(true);
        });
        return commands;
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisFuture<T> redisFuture(T value) {
        RedisFuture<T> future = mock(RedisFuture.class);
        when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(value));
        return future;
    }

    private static TomlXcoreConfig config(String server) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = server;
        return config;
    }
}
