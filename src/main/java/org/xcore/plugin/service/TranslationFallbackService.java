package org.xcore.plugin.service;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationOutcome;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationProviderPipeline;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Runs the configured providers in order. Fallback is per target: only the targets the
 * previous provider could not translate (or does not support) move on to the next one.
 */
@Singleton
public class TranslationFallbackService implements TranslationProvider {

    private final List<TranslationProvider> translationProviders;
    private final TranslationMetricsService translationMetricsService;

    @Inject
    public TranslationFallbackService(TranslationProviderPipeline translationProviderPipeline,
                                      TranslationMetricsService translationMetricsService) {
        this.translationProviders = translationProviderPipeline.providers();
        this.translationMetricsService = translationMetricsService;

        if (this.translationProviders.isEmpty()) {
            PLog.err("Translation fallback initialized without configured providers");
        }
    }

    @Override
    public String name() {
        return "fallback";
    }

    @Override
    public String signature() {
        return pipelineSignature();
    }

    @Override
    public CompletionStage<TranslationOutcome> translate(Request request) {
        if (translationProviders.isEmpty()) {
            translationMetricsService.incrementGlobal("unavailable_total");
            Log.debug("[Translation] No providers configured for targets @", request.targetLanguages());
            return CompletableFuture.completedFuture(TranslationOutcome.failed(request.targetLanguages(),
                    TranslationFailure.unavailable(name(), "translation is unavailable: no providers configured")));
        }

        Log.debug("[Translation] Starting fallback pipeline for '@' -> @ using @ provider(s)",
                request.sourceLanguage(), request.targetLanguages(), translationProviders.size());
        return translate(request, 0, new TranslationOutcome(null, Map.of(), Map.of()));
    }

    @Override
    public boolean supports(String languageCode) {
        for (TranslationProvider translationProvider : translationProviders) {
            if (translationProvider.supports(languageCode)) {
                return true;
            }
        }

        return false;
    }

    public String pipelineSignature() {
        if (translationProviders.isEmpty()) {
            return "none";
        }

        return translationProviders.stream()
                .map(TranslationProvider::signature)
                .collect(Collectors.joining(","));
    }

    private CompletionStage<TranslationOutcome> translate(Request request, int providerIndex, TranslationOutcome accumulated) {
        List<String> remaining = request.targetLanguages().stream()
                .filter(target -> !accumulated.translations().containsKey(target))
                .toList();
        if (remaining.isEmpty()) {
            return CompletableFuture.completedFuture(accumulated);
        }

        if (providerIndex >= translationProviders.size()) {
            translationMetricsService.incrementGlobal("all_failed_total");
            Log.debug("[Translation] All providers failed for @", remaining);
            TranslationOutcome failed = TranslationOutcome.failed(remaining,
                    TranslationFailure.unavailable(name(), "all translation providers failed"));
            return CompletableFuture.completedFuture(accumulated.merge(failed));
        }

        TranslationProvider translationProvider = translationProviders.get(providerIndex);
        List<String> supported = remaining.stream().filter(translationProvider::supports).toList();
        if (supported.isEmpty()) {
            Log.debug("[Translation] Skipping provider '@' for unsupported targets @", translationProvider.name(), remaining);
            return translate(request, providerIndex + 1, accumulated);
        }

        Log.debug("[Translation] Trying provider '@' (type='@') for @", translationProvider.name(), translationProvider.type(), supported);
        translationMetricsService.incrementProvider(translationProvider.name(), "attempts_total");
        long startNanos = System.nanoTime();
        Request providerRequest = new Request(request.text(), request.sourceLanguage(), supported);

        return invoke(translationProvider, providerRequest)
                .thenCompose(outcome -> {
                    record(translationProvider, outcome, elapsedMillis(startNanos));
                    return translate(request, providerIndex + 1, accumulated.merge(outcome));
                });
    }

    /** A provider that throws or completes exceptionally fails its targets instead of the pipeline. */
    private CompletionStage<TranslationOutcome> invoke(TranslationProvider translationProvider, Request request) {
        CompletionStage<TranslationOutcome> stage;
        try {
            stage = translationProvider.translate(request);
        } catch (RuntimeException exception) {
            stage = CompletableFuture.failedFuture(exception);
        }

        return stage.exceptionally(error -> TranslationOutcome.failed(request.targetLanguages(),
                TranslationFailure.failed(translationProvider.name(), unwrap(error))));
    }

    private void record(TranslationProvider translationProvider, TranslationOutcome outcome, long latencyMs) {
        String providerName = translationProvider.name();
        translationMetricsService.recordProviderLatency(providerName, latencyMs);
        if (outcome.allSucceeded()) {
            translationMetricsService.incrementProvider(providerName, "success_total");
            translationMetricsService.incrementGlobal("provider_success_total");
            translationMetricsService.markProviderSuccess(providerName);
            Log.debug("[Translation] Provider '@' succeeded in @ ms", providerName, latencyMs);
            return;
        }

        String reason = outcome.failures().values().iterator().next().reason();
        translationMetricsService.incrementProvider(providerName, "failure_total");
        translationMetricsService.incrementGlobal("provider_failure_total");
        translationMetricsService.markProviderFailure(providerName, reason);
        Log.debug("[Translation] Provider '@' failed for @ in @ ms: @", providerName, outcome.failures().keySet(), latencyMs, reason);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
