package org.xcore.plugin.service;

import io.lettuce.core.api.async.RedisAsyncCommands;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Singleton
public class TranslationMetricsService {

    private static final DateTimeFormatter MINUTE_BUCKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);

    private final RedisNetworkBackend backend;
    private final TomlXcoreConfig config;

    @Inject
    public TranslationMetricsService(RedisNetworkBackend backend, TomlXcoreConfig config) {
        this.backend = backend;
        this.config = config;
    }

    public void incrementGlobal(String field) {
        if (!isEnabled() || field == null || field.isBlank()) {
            return;
        }

        backend.withAsyncCommands(commands -> incrementTotalsAndMinuteBucket(
                commands, globalTotalsKey(), globalMinuteKey(), field), false);
    }

    public void incrementProvider(String providerId, String field) {
        if (!isEnabled() || providerId == null || providerId.isBlank() || field == null || field.isBlank()) {
            return;
        }

        backend.withAsyncCommands(commands -> incrementTotalsAndMinuteBucket(
                commands, providerTotalsKey(providerId), providerMinuteKey(providerId), field), false);
    }

    public void recordProviderLatency(String providerId, long latencyMs) {
        if (!isEnabled() || providerId == null || providerId.isBlank() || latencyMs < 0) {
            return;
        }

        backend.withAsyncCommands(commands -> {
            String totalsKey = providerTotalsKey(providerId);
            commands.hincrby(totalsKey, "latency_sum_ms", latencyMs);
            commands.hincrby(totalsKey, "latency_count", 1);
            return commands.hset(totalsKey, "last_latency_ms", Long.toString(latencyMs))
                    .toCompletableFuture()
                    .orTimeout(500, TimeUnit.MILLISECONDS)
                    .thenApply(ignored -> true);
        }, false);
    }

    public void markProviderSuccess(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return;
        }

        backend.withAsyncCommands(commands -> commands
                .hset(providerTotalsKey(providerId), "last_success_at", Long.toString(System.currentTimeMillis()))
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply(ignored -> true), false);
    }

    public void markProviderFailure(String providerId, String reason) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return;
        }

        backend.withAsyncCommands(commands -> {
            String totalsKey = providerTotalsKey(providerId);
            commands.hset(totalsKey, "last_failure_at", Long.toString(System.currentTimeMillis()));
            return commands.hset(totalsKey, "last_failure_reason", (reason == null || reason.isBlank()) ? "" : reason)
                    .toCompletableFuture()
                    .orTimeout(500, TimeUnit.MILLISECONDS)
                    .thenApply(ignored -> true);
        }, false);
    }

    public Map<String, String> readGlobalTotals() {
        return readGlobalTotalsAsync().toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readGlobalTotalsAsync() {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        return backend.withAsyncCommands(commands ->
                commands.hgetall(globalTotalsKey())
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .thenApply(map -> (Map<String, String>) new LinkedHashMap<>(map))
                        .exceptionally(err -> Map.of()),
                Map.of()
        );
    }

    public Map<String, String> readProviderTotals(String providerId) {
        return readProviderTotalsAsync(providerId).toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readProviderTotalsAsync(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        return backend.withAsyncCommands(commands ->
                commands.hgetall(providerTotalsKey(providerId))
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .thenApply(map -> (Map<String, String>) new LinkedHashMap<>(map))
                        .exceptionally(err -> Map.of()),
                Map.of()
        );
    }

    public Map<String, String> readCurrentMinuteGlobal() {
        return readCurrentMinuteGlobalAsync().toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readCurrentMinuteGlobalAsync() {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        return backend.withAsyncCommands(commands ->
                commands.hgetall(globalMinuteKey())
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .thenApply(map -> (Map<String, String>) new LinkedHashMap<>(map))
                        .exceptionally(err -> Map.of()),
                Map.of()
        );
    }

    public Map<String, String> readCurrentMinuteProvider(String providerId) {
        return readCurrentMinuteProviderAsync(providerId).toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readCurrentMinuteProviderAsync(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return CompletableFuture.completedFuture(Map.of());
        }

        return backend.withAsyncCommands(commands ->
                commands.hgetall(providerMinuteKey(providerId))
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .thenApply(map -> (Map<String, String>) new LinkedHashMap<>(map))
                        .exceptionally(err -> Map.of()),
                Map.of()
        );
    }

    private boolean isEnabled() {
        return config.translation.enabled && config.translation.metrics.enabled;
    }

    private CompletionStage<Boolean> incrementTotalsAndMinuteBucket(RedisAsyncCommands<String, String> commands,
                                                                   String totalsKey,
                                                                   String minuteKey,
                                                                   String field) {
        var totals = commands.hincrby(totalsKey, field, 1).toCompletableFuture();
        if (!config.translation.metrics.minuteBucketsEnabled) {
            return totals.orTimeout(500, TimeUnit.MILLISECONDS).thenApply(ignored -> true);
        }

        commands.hincrby(minuteKey, field, 1);
        return commands.expire(minuteKey, config.translation.metrics.minuteBucketTtlSeconds)
                .toCompletableFuture()
                .orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply(ignored -> true);
    }

    private String globalTotalsKey() {
        return "xcore:translation:metrics:" + config.server.name + ":totals";
    }

    private String providerTotalsKey(String providerId) {
        return "xcore:translation:metrics:" + config.server.name + ":provider:" + sanitize(providerId) + ":totals";
    }

    private String globalMinuteKey() {
        return "xcore:translation:metrics:" + config.server.name + ":minute:" + currentMinuteBucket();
    }

    private String providerMinuteKey(String providerId) {
        return "xcore:translation:metrics:" + config.server.name + ":provider:" + sanitize(providerId) + ":minute:" + currentMinuteBucket();
    }

    private String currentMinuteBucket() {
        return MINUTE_BUCKET_FORMAT.format(Instant.now());
    }

    private String sanitize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
    }
}
