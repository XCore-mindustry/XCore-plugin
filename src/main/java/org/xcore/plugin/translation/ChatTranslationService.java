package org.xcore.plugin.translation;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationOutcome;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.service.TranslationCacheService;
import org.xcore.plugin.service.TranslationFallbackService;
import org.xcore.plugin.service.TranslationMetricsService;
import org.xcore.plugin.translation.ChatTranslation.Reason;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Translates chat text into the languages its recipients read. It knows nothing about
 * players: callers pass an author key (for the per-author budget), the raw text and the
 * target languages, and get one future per target that always completes, never
 * exceptionally, and is never slower than {@code translation.limits.request_timeout_seconds}.
 *
 * <p>Order of work for each target: prefilter, detected-language skip, in-memory cache,
 * author budget, in-flight sharing, Redis cache, then the provider pipeline with every
 * remaining target in one request.
 */
@Singleton
public class ChatTranslationService {

    private static final String SOURCE_LANGUAGE = "auto";

    private final TomlXcoreConfig config;
    private final TranslationFallbackService pipeline;
    private final TranslationCacheService redisCache;
    private final TranslationMemoryCache memoryCache;
    private final TranslationPrefilter prefilter;
    private final TranslationAdmission admission;
    private final TranslationMetricsService metrics;
    private final Map<String, CompletableFuture<ChatTranslation>> inFlight = new ConcurrentHashMap<>();

    @Inject
    public ChatTranslationService(TomlXcoreConfig config,
                                  TranslationFallbackService pipeline,
                                  TranslationCacheService redisCache,
                                  TranslationMemoryCache memoryCache,
                                  TranslationPrefilter prefilter,
                                  TranslationAdmission admission,
                                  TranslationMetricsService metrics) {
        this.config = config;
        this.pipeline = pipeline;
        this.redisCache = redisCache;
        this.memoryCache = memoryCache;
        this.prefilter = prefilter;
        this.admission = admission;
        this.metrics = metrics;
    }

    /** Whether recipients reading {@code languageCode} can get translations at all. */
    public boolean supports(String languageCode) {
        return config.translation.enabled && languageCode != null && pipeline.supports(languageCode);
    }

    public Map<String, CompletableFuture<ChatTranslation>> translate(String authorKey,
                                                                     String text,
                                                                     Collection<String> targetLanguages) {
        Map<String, CompletableFuture<ChatTranslation>> results = new LinkedHashMap<>();
        List<String> targets = targetLanguages.stream().distinct().toList();
        if (targets.isEmpty()) {
            return results;
        }

        if (!config.translation.enabled) {
            return completeAll(results, targets, Reason.DISABLED);
        }

        String prepared = prefilter.prepare(text);
        if (prepared == null) {
            metrics.incrementGlobal("prefilter_skipped_total");
            return completeAll(results, targets, Reason.NOT_NEEDED);
        }

        String signature = pipeline.pipelineSignature();
        String detected = memoryCache.detectedLanguage(prepared);
        List<String> pending = new ArrayList<>();
        for (String target : targets) {
            metrics.incrementGlobal("requests_total");
            if (!pipeline.supports(target)) {
                metrics.incrementGlobal("unsupported_language_total");
                results.put(target, completed(Reason.UNSUPPORTED_LANGUAGE));
            } else if (detected != null && sameLanguage(detected, target)) {
                metrics.incrementGlobal("same_language_skipped_total");
                results.put(target, completed(Reason.SAME_LANGUAGE));
            } else {
                String cached = memoryCache.translation(signature, target, prepared);
                if (cached != null) {
                    metrics.incrementGlobal("cache_hits_total");
                    metrics.incrementGlobal("memory_cache_hits_total");
                    results.put(target, CompletableFuture.completedFuture(toChatTranslation(prepared, cached)));
                } else {
                    pending.add(target);
                }
            }
        }

        if (pending.isEmpty()) {
            return results;
        }

        // Charged once per message that needs more than the memory cache, before joining
        // anyone else's request, so a rejected author never hands RATE_LIMITED to others.
        if (!admission.tryAcquireAuthor(authorKey)) {
            metrics.incrementGlobal("rate_limited_total");
            Log.debug("[Translation] Author '@' is over the translation budget", authorKey);
            return completeAll(results, pending, Reason.RATE_LIMITED);
        }

        Map<String, CompletableFuture<ChatTranslation>> owned = new LinkedHashMap<>();
        for (String target : pending) {
            String key = signature + '\u0000' + target + '\u0000' + prepared;
            CompletableFuture<ChatTranslation> mine = new CompletableFuture<>();
            CompletableFuture<ChatTranslation> existing = inFlight.putIfAbsent(key, mine);
            if (existing != null) {
                metrics.incrementGlobal("inflight_shared_total");
                results.put(target, existing);
                continue;
            }

            mine.whenComplete((ignored, error) -> inFlight.remove(key, mine));
            owned.put(target, mine);
            results.put(target, mine);
        }

        if (!owned.isEmpty()) {
            resolve(prepared, signature, owned);
        }
        return results;
    }

    /** Number of translations currently being fetched. Visible for tests and stats. */
    public int inFlightCount() {
        return inFlight.size();
    }

    private void resolve(String text, String signature, Map<String, CompletableFuture<ChatTranslation>> owned) {
        List<String> targets = List.copyOf(owned.keySet());
        CompletionStage<Void> work;
        try {
            work = lookupRedis(text, signature, targets).thenCompose(hits -> {
                hits.forEach((target, translated) -> {
                    metrics.incrementGlobal("cache_hits_total");
                    memoryCache.putTranslation(signature, target, text, translated);
                    owned.get(target).complete(toChatTranslation(text, translated));
                });

                List<String> misses = targets.stream().filter(target -> !hits.containsKey(target)).toList();
                misses.forEach(ignored -> metrics.incrementGlobal("cache_misses_total"));
                return misses.isEmpty()
                        ? CompletableFuture.<Void>completedFuture(null)
                        : callPipeline(text, signature, misses, owned);
            });
        } catch (RuntimeException exception) {
            work = CompletableFuture.failedFuture(exception);
        }

        // Every owned future must finish, or its recipients wait for a deadline that delivers
        // the original anyway and every later identical message joins a request that never ends.
        work.whenComplete((ignored, error) -> {
            if (error != null) {
                PLog.err("Translation for @ failed unexpectedly: @", targets, unwrap(error).toString());
            }
            owned.values().forEach(future -> future.complete(ChatTranslation.untranslated(Reason.FAILED)));
        });
    }

    private CompletionStage<Map<String, String>> lookupRedis(String text, String signature, List<String> targets) {
        Map<String, CompletableFuture<TranslationCacheService.CachedTranslation>> lookups = new LinkedHashMap<>();
        for (String target : targets) {
            lookups.put(target, redisCache.getAsync(SOURCE_LANGUAGE, target, text, signature)
                    .exceptionally(error -> {
                        Log.debug("[Translation] Cache lookup failed: @", unwrap(error).getMessage());
                        return null;
                    })
                    .toCompletableFuture());
        }

        return CompletableFuture.allOf(lookups.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Map<String, String> hits = new LinkedHashMap<>();
                    lookups.forEach((target, lookup) -> {
                        var cached = lookup.join();
                        if (cached != null && cached.translatedText() != null && !cached.translatedText().isBlank()) {
                            hits.put(target, cached.translatedText());
                        }
                    });
                    return hits;
                });
    }

    private CompletionStage<Void> callPipeline(String text,
                                               String signature,
                                               List<String> targets,
                                               Map<String, CompletableFuture<ChatTranslation>> owned) {
        if (!admission.tryAcquireSlot()) {
            metrics.incrementGlobal("overloaded_total");
            targets.forEach(target -> owned.get(target).complete(ChatTranslation.untranslated(Reason.OVERLOADED)));
            return CompletableFuture.completedFuture(null);
        }

        CompletionStage<TranslationOutcome> stage;
        try {
            stage = pipeline.translate(new TranslationProvider.Request(text, SOURCE_LANGUAGE, targets));
        } catch (RuntimeException exception) {
            stage = CompletableFuture.failedFuture(exception);
        }

        return stage.toCompletableFuture()
                .orTimeout(config.translation.limits.requestTimeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> admission.releaseSlot())
                .thenAccept(outcome -> {
                    if (outcome.detectedSourceLanguage() != null && !outcome.detectedSourceLanguage().isBlank()) {
                        memoryCache.putDetectedLanguage(text, primaryLanguage(outcome.detectedSourceLanguage()));
                    }

                    for (String target : targets) {
                        String translated = outcome.translations().get(target);
                        if (translated != null) {
                            memoryCache.putTranslation(signature, target, text, translated);
                            redisCache.putAsync(SOURCE_LANGUAGE, target, text, signature, translated, "pipeline");
                            owned.get(target).complete(toChatTranslation(text, translated));
                            continue;
                        }

                        TranslationFailure failure = outcome.failures().get(target);
                        metrics.incrementGlobal("original_message_fallback_total");
                        PLog.err("Translation pipeline failed for '@': @", target,
                                failure == null ? "unknown reason" : failure.reason());
                        owned.get(target).complete(ChatTranslation.untranslated(Reason.FAILED));
                    }
                });
    }

    private static ChatTranslation toChatTranslation(String original, String translated) {
        String normalizedTranslation = normalizeForComparison(translated);
        if (normalizedTranslation.isEmpty() || normalizedTranslation.equalsIgnoreCase(normalizeForComparison(original))) {
            return ChatTranslation.untranslated(Reason.SAME_LANGUAGE);
        }
        return new ChatTranslation.Translated(translated.strip());
    }

    private static String normalizeForComparison(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    static boolean sameLanguage(String detected, String target) {
        return primaryLanguage(detected).equals(primaryLanguage(target));
    }

    /** {@code zh-CN} and {@code zh} are the same language for skipping purposes. */
    static String primaryLanguage(String code) {
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('-') >= 0 ? normalized.indexOf('-') : normalized.indexOf('_');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private static Map<String, CompletableFuture<ChatTranslation>> completeAll(Map<String, CompletableFuture<ChatTranslation>> results,
                                                                               List<String> targets,
                                                                               Reason reason) {
        targets.forEach(target -> results.put(target, completed(reason)));
        return results;
    }

    private static CompletableFuture<ChatTranslation> completed(Reason reason) {
        return CompletableFuture.completedFuture(ChatTranslation.untranslated(reason));
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
