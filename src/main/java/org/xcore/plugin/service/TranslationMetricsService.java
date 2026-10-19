package org.xcore.plugin.service;

import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Translation counters, kept in memory and flushed to the Redis hashes that {@code trstats}
 * reads every {@link #FLUSH_INTERVAL_SECONDS} seconds. Recording is a map update under a lock,
 * so the chat path never waits on Redis; reads add the deltas that are not flushed yet.
 */
@Singleton
public class TranslationMetricsService {

    static final float FLUSH_INTERVAL_SECONDS = 15f;

    private static final DateTimeFormatter MINUTE_BUCKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);

    private final RedisNetworkBackend backend;
    private final TomlXcoreConfig config;
    private final ScheduledTasks scheduledTasks = new ScheduledTasks(TranslationMetricsService.class);

    private Map<String, Map<String, Long>> pendingIncrements = new HashMap<>();
    private Map<String, Map<String, String>> pendingValues = new HashMap<>();
    private Set<String> pendingExpirations = new HashSet<>();

    @Inject
    public TranslationMetricsService(RedisNetworkBackend backend, TomlXcoreConfig config) {
        this.backend = backend;
        this.config = config;
    }

    @PostConstruct
    void start() {
        scheduledTasks.every(FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, this::flush);
    }

    @PreDestroy
    void shutdown() {
        scheduledTasks.close();
        flush();
    }

    public void incrementGlobal(String field) {
        if (!isEnabled() || field == null || field.isBlank()) {
            return;
        }

        increment(globalTotalsKey(), globalMinuteKey(), field, 1);
    }

    public void incrementProvider(String providerId, String field) {
        if (!isEnabled() || providerId == null || providerId.isBlank() || field == null || field.isBlank()) {
            return;
        }

        increment(providerTotalsKey(providerId), providerMinuteKey(providerId), field, 1);
    }

    public void recordProviderLatency(String providerId, long latencyMs) {
        if (!isEnabled() || providerId == null || providerId.isBlank() || latencyMs < 0) {
            return;
        }

        String totalsKey = providerTotalsKey(providerId);
        synchronized (this) {
            addIncrement(totalsKey, "latency_sum_ms", latencyMs);
            addIncrement(totalsKey, "latency_count", 1);
            setValue(totalsKey, "last_latency_ms", Long.toString(latencyMs));
        }
    }

    public void markProviderSuccess(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return;
        }

        synchronized (this) {
            setValue(providerTotalsKey(providerId), "last_success_at", Long.toString(System.currentTimeMillis()));
        }
    }

    public void markProviderFailure(String providerId, String reason) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return;
        }

        String totalsKey = providerTotalsKey(providerId);
        synchronized (this) {
            setValue(totalsKey, "last_failure_at", Long.toString(System.currentTimeMillis()));
            setValue(totalsKey, "last_failure_reason", (reason == null || reason.isBlank()) ? "" : reason);
        }
    }

    /**
     * Sends everything recorded since the last flush in one batch of async commands. A Redis
     * failure drops that batch: these are statistics, and retrying would only pile them up.
     */
    public void flush() {
        Map<String, Map<String, Long>> increments;
        Map<String, Map<String, String>> values;
        Set<String> expirations;
        synchronized (this) {
            if (pendingIncrements.isEmpty() && pendingValues.isEmpty()) {
                return;
            }
            increments = pendingIncrements;
            values = pendingValues;
            expirations = pendingExpirations;
            pendingIncrements = new HashMap<>();
            pendingValues = new HashMap<>();
            pendingExpirations = new HashSet<>();
        }

        backend.withAsyncCommands(commands -> {
            CompletableFuture<?> last = CompletableFuture.completedFuture(null);
            for (var hash : increments.entrySet()) {
                for (var field : hash.getValue().entrySet()) {
                    last = commands.hincrby(hash.getKey(), field.getKey(), field.getValue()).toCompletableFuture();
                }
            }
            for (var hash : values.entrySet()) {
                for (var field : hash.getValue().entrySet()) {
                    last = commands.hset(hash.getKey(), field.getKey(), field.getValue()).toCompletableFuture();
                }
            }
            for (String key : expirations) {
                last = commands.expire(key, config.translation.metrics.minuteBucketTtlSeconds).toCompletableFuture();
            }
            return last.orTimeout(2, TimeUnit.SECONDS).thenApply(ignored -> true);
        }, false);
    }

    public Map<String, String> readGlobalTotals() {
        return readGlobalTotalsAsync().toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readGlobalTotalsAsync() {
        return isEnabled() ? read(globalTotalsKey()) : CompletableFuture.completedFuture(Map.of());
    }

    public Map<String, String> readProviderTotals(String providerId) {
        return readProviderTotalsAsync(providerId).toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readProviderTotalsAsync(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return read(providerTotalsKey(providerId));
    }

    public Map<String, String> readCurrentMinuteGlobal() {
        return readCurrentMinuteGlobalAsync().toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readCurrentMinuteGlobalAsync() {
        return isEnabled() ? read(globalMinuteKey()) : CompletableFuture.completedFuture(Map.of());
    }

    public Map<String, String> readCurrentMinuteProvider(String providerId) {
        return readCurrentMinuteProviderAsync(providerId).toCompletableFuture().join();
    }

    public CompletionStage<Map<String, String>> readCurrentMinuteProviderAsync(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return read(providerMinuteKey(providerId));
    }

    private CompletionStage<Map<String, String>> read(String key) {
        return backend.withAsyncCommands(commands ->
                        commands.hgetall(key)
                                .toCompletableFuture()
                                .orTimeout(500, TimeUnit.MILLISECONDS)
                                .thenApply(map -> (Map<String, String>) new LinkedHashMap<>(map))
                                .exceptionally(err -> Map.of()),
                        Map.<String, String>of())
                .thenApply(stored -> withPending(key, stored));
    }

    private synchronized Map<String, String> withPending(String key, Map<String, String> stored) {
        Map<String, Long> increments = pendingIncrements.getOrDefault(key, Map.of());
        Map<String, String> values = pendingValues.getOrDefault(key, Map.of());
        if (increments.isEmpty() && values.isEmpty()) {
            return stored;
        }

        Map<String, String> merged = new LinkedHashMap<>(stored);
        merged.putAll(values);
        increments.forEach((field, delta) -> merged.merge(field, Long.toString(delta),
                (current, added) -> Long.toString(parseLong(current) + delta)));
        return merged;
    }

    private void increment(String totalsKey, String minuteKey, String field, long delta) {
        synchronized (this) {
            addIncrement(totalsKey, field, delta);
            if (config.translation.metrics.minuteBucketsEnabled) {
                addIncrement(minuteKey, field, delta);
                pendingExpirations.add(minuteKey);
            }
        }
    }

    private void addIncrement(String key, String field, long delta) {
        pendingIncrements.computeIfAbsent(key, ignored -> new HashMap<>()).merge(field, delta, Long::sum);
    }

    private void setValue(String key, String field, String value) {
        pendingValues.computeIfAbsent(key, ignored -> new HashMap<>()).put(field, value);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private boolean isEnabled() {
        return config.translation.enabled && config.translation.metrics.enabled;
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
