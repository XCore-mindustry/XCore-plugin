package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Singleton
public class TranslationMetricsService {

    private static final DateTimeFormatter MINUTE_BUCKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
            .withZone(ZoneOffset.UTC);

    private final RedisNetworkBackend backend;
    private final Config config;

    @Inject
    public TranslationMetricsService(RedisNetworkBackend backend, Config config) {
        this.backend = backend;
        this.config = config;
    }

    public void incrementGlobal(String field) {
        if (!isEnabled() || field == null || field.isBlank()) {
            return;
        }

        backend.withCommands(commands -> {
            commands.hincrby(globalTotalsKey(), field, 1);
            incrementMinuteBucket(commands, globalMinuteKey(), field);
            return true;
        }, false);
    }

    public void incrementProvider(String providerId, String field) {
        if (!isEnabled() || providerId == null || providerId.isBlank() || field == null || field.isBlank()) {
            return;
        }

        backend.withCommands(commands -> {
            commands.hincrby(providerTotalsKey(providerId), field, 1);
            incrementMinuteBucket(commands, providerMinuteKey(providerId), field);
            return true;
        }, false);
    }

    public void recordProviderLatency(String providerId, long latencyMs) {
        if (!isEnabled() || providerId == null || providerId.isBlank() || latencyMs < 0) {
            return;
        }

        backend.withCommands(commands -> {
            String totalsKey = providerTotalsKey(providerId);
            commands.hincrby(totalsKey, "latency_sum_ms", latencyMs);
            commands.hincrby(totalsKey, "latency_count", 1);
            commands.hset(totalsKey, "last_latency_ms", Long.toString(latencyMs));
            return true;
        }, false);
    }

    public void markProviderSuccess(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return;
        }

        backend.withCommands(commands -> {
            commands.hset(providerTotalsKey(providerId), "last_success_at", Long.toString(System.currentTimeMillis()));
            return true;
        }, false);
    }

    public void markProviderFailure(String providerId, String reason) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return;
        }

        backend.withCommands(commands -> {
            String totalsKey = providerTotalsKey(providerId);
            commands.hset(totalsKey, "last_failure_at", Long.toString(System.currentTimeMillis()));
            if (reason != null && !reason.isBlank()) {
                commands.hset(totalsKey, "last_failure_reason", reason);
            }
            return true;
        }, false);
    }

    public Map<String, String> readGlobalTotals() {
        if (!isEnabled()) {
            return Map.of();
        }

        return backend.withCommands(commands -> new LinkedHashMap<>(commands.hgetall(globalTotalsKey())), Map.of());
    }

    public Map<String, String> readProviderTotals(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return Map.of();
        }

        return backend.withCommands(commands -> new LinkedHashMap<>(commands.hgetall(providerTotalsKey(providerId))), Map.of());
    }

    public Map<String, String> readCurrentMinuteGlobal() {
        if (!isEnabled()) {
            return Map.of();
        }

        return backend.withCommands(commands -> new LinkedHashMap<>(commands.hgetall(globalMinuteKey())), Map.of());
    }

    public Map<String, String> readCurrentMinuteProvider(String providerId) {
        if (!isEnabled() || providerId == null || providerId.isBlank()) {
            return Map.of();
        }

        return backend.withCommands(commands -> new LinkedHashMap<>(commands.hgetall(providerMinuteKey(providerId))), Map.of());
    }

    private boolean isEnabled() {
        return config.translation.enabled && config.translation.metrics.enabled;
    }

    private void incrementMinuteBucket(io.lettuce.core.api.sync.RedisCommands<String, String> commands,
                                       String key,
                                       String field) {
        if (!config.translation.metrics.minuteBucketsEnabled) {
            return;
        }

        commands.hincrby(key, field, 1);
        commands.expire(key, config.translation.metrics.minuteBucketTtlSeconds);
    }

    private String globalTotalsKey() {
        return "xcore:translation:metrics:" + config.server + ":totals";
    }

    private String providerTotalsKey(String providerId) {
        return "xcore:translation:metrics:" + config.server + ":provider:" + sanitize(providerId) + ":totals";
    }

    private String globalMinuteKey() {
        return "xcore:translation:metrics:" + config.server + ":minute:" + currentMinuteBucket();
    }

    private String providerMinuteKey(String providerId) {
        return "xcore:translation:metrics:" + config.server + ":provider:" + sanitize(providerId) + ":minute:" + currentMinuteBucket();
    }

    private String currentMinuteBucket() {
        return MINUTE_BUCKET_FORMAT.format(Instant.now());
    }

    private String sanitize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
    }
}
