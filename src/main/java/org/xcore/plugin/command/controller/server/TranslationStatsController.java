package org.xcore.plugin.command.controller.server;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.TranslationMetricsService;

import java.util.Locale;
import java.util.Map;

@Singleton
public class TranslationStatsController implements CloudServerController {

    private final TomlXcoreConfig config;
    private final GlobalConfig globalConfig;
    private final TranslationMetricsService translationMetricsService;

    @Inject
    public TranslationStatsController(TomlXcoreConfig config,
                                      GlobalConfig globalConfig,
                                      TranslationMetricsService translationMetricsService) {
        this.config = config;
        this.globalConfig = globalConfig;
        this.translationMetricsService = translationMetricsService;
    }

    @Command("trstats")
    @CommandDescription("Shows translation pipeline metrics and per-provider statistics.")
    public void translationStats(XCoreSender sender) {
        Log.info("Translation stats for server '@':", config.server.name);
        Log.info(" Pipeline enabled: @", config.translation.enabled);
        Log.info(" Pipeline: @", String.join(" -> ", config.translation.pipeline));

        Map<String, String> totals = translationMetricsService.readGlobalTotals();
        Map<String, String> minute = translationMetricsService.readCurrentMinuteGlobal();

        long requestsTotal = longValue(totals, "requests_total");
        long cacheHitsTotal = longValue(totals, "cache_hits_total");
        long cacheMissesTotal = longValue(totals, "cache_misses_total");
        long unsupportedTotal = longValue(totals, "unsupported_language_total");
        long originalFallbackTotal = longValue(totals, "original_message_fallback_total");
        long allFailedTotal = longValue(totals, "all_failed_total");
        long unavailableTotal = longValue(totals, "unavailable_total");
        long currentMinuteRequests = longValue(minute, "requests_total");

        double cacheHitRatio = requestsTotal <= 0L
                ? 0.0d
                : (cacheHitsTotal * 100.0d) / requestsTotal;

        Log.info(" Totals:");
        Log.info("  requests_total=@ current_minute=@", requestsTotal, currentMinuteRequests);
        Log.info("  cache_hits_total=@ cache_misses_total=@ cache_hit_ratio=@%", cacheHitsTotal, cacheMissesTotal, formatDouble(cacheHitRatio));
        Log.info("  unsupported_language_total=@ original_message_fallback_total=@", unsupportedTotal, originalFallbackTotal);
        Log.info("  all_failed_total=@ unavailable_total=@", allFailedTotal, unavailableTotal);

        Log.info(" Providers:");
        for (String providerId : config.translation.pipeline) {
            if (providerId == null || providerId.isBlank()) {
                continue;
            }

            GlobalConfig.TranslationProviderConfig providerConfig = globalConfig.translationProviders.get(providerId);
            if (providerConfig == null) {
                Log.info("  - @ (missing config)", providerId);
                continue;
            }

            Map<String, String> providerTotals = translationMetricsService.readProviderTotals(providerId);
            Map<String, String> providerMinute = translationMetricsService.readCurrentMinuteProvider(providerId);

            long attempts = longValue(providerTotals, "attempts_total");
            long success = longValue(providerTotals, "success_total");
            long failure = longValue(providerTotals, "failure_total");
            long latencySum = longValue(providerTotals, "latency_sum_ms");
            long latencyCount = longValue(providerTotals, "latency_count");
            long currentMinuteProvider = longValue(providerMinute, "attempts_total");
            double avgLatency = latencyCount <= 0L ? 0.0d : (double) latencySum / latencyCount;

            Log.info(
                    "  - @ [type=@ enabled=@] attempts=@ success=@ failure=@ current_minute=@ avg_latency=@ms",
                    providerId,
                    providerConfig.type,
                    providerConfig.enabled,
                    attempts,
                    success,
                    failure,
                    currentMinuteProvider,
                    formatDouble(avgLatency)
            );
        }
    }

    private long longValue(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return 0L;
        }

        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
