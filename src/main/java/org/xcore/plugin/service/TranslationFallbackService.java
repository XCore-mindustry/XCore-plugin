package org.xcore.plugin.service;

import arc.func.Cons;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationProviderPipeline;
import org.xcore.plugin.localization.TranslationResult;

import java.util.List;
import java.util.stream.Collectors;

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
    public void translate(Request request, Cons<TranslationResult> callback) {
        if (translationProviders.isEmpty()) {
            translationMetricsService.incrementGlobal("unavailable_total");
            Log.debug("[Translation] No providers configured for target '@'", request.targetLanguage());
            callback.get(TranslationResult.failure(
                    TranslationFailure.unavailable(name(), "translation is unavailable: no providers configured")));
            return;
        }

        Log.debug("[Translation] Starting fallback pipeline for '@' -> '@' using @ provider(s)",
                request.sourceLanguage(), request.targetLanguage(), translationProviders.size());
        translate(request, 0, callback);
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
                .map(translationProvider -> translationProvider.name() + ":" + translationProvider.type())
                .collect(Collectors.joining(","));
    }

    private void translate(Request request, int providerIndex, Cons<TranslationResult> callback) {
        if (providerIndex >= translationProviders.size()) {
            translationMetricsService.incrementGlobal("all_failed_total");
            Log.debug("[Translation] All providers failed for '@' -> '@'", request.sourceLanguage(), request.targetLanguage());
            callback.get(TranslationResult.failure(
                    TranslationFailure.unavailable(name(), "all translation providers failed")));
            return;
        }

        TranslationProvider translationProvider = translationProviders.get(providerIndex);
        if (!translationProvider.supports(request.targetLanguage())) {
            Log.debug("[Translation] Skipping provider '@' for unsupported target '@'",
                    translationProvider.name(), request.targetLanguage());
            translate(request, providerIndex + 1, callback);
            return;
        }

        Log.debug("[Translation] Trying provider '@' (type='@') for '@' -> '@'",
                translationProvider.name(), translationProvider.type(), request.sourceLanguage(), request.targetLanguage());
        translationMetricsService.incrementProvider(translationProvider.name(), "attempts_total");
        long startNanos = System.nanoTime();
        translationProvider.translate(request, result -> {
            if (result instanceof TranslationResult.Success) {
                long latencyMs = elapsedMillis(startNanos);
                translationMetricsService.incrementProvider(translationProvider.name(), "success_total");
                translationMetricsService.incrementGlobal("provider_success_total");
                translationMetricsService.recordProviderLatency(translationProvider.name(), latencyMs);
                translationMetricsService.markProviderSuccess(translationProvider.name());
                Log.debug("[Translation] Provider '@' succeeded in @ ms for target '@'",
                        translationProvider.name(), latencyMs, request.targetLanguage());
                callback.get(result);
                return;
            }

            long latencyMs = elapsedMillis(startNanos);
            translationMetricsService.incrementProvider(translationProvider.name(), "failure_total");
            translationMetricsService.incrementGlobal("provider_failure_total");
            translationMetricsService.recordProviderLatency(translationProvider.name(), latencyMs);
            if (result instanceof TranslationResult.Failure(var failure)) {
                translationMetricsService.markProviderFailure(translationProvider.name(), failure.reason());
                Log.debug("[Translation] Provider '@' failed in @ ms: @",
                        translationProvider.name(), latencyMs, failure.reason());
            } else {
                translationMetricsService.markProviderFailure(translationProvider.name(), "unknown failure");
                Log.debug("[Translation] Provider '@' failed in @ ms with unknown failure",
                        translationProvider.name(), latencyMs);
            }

            Log.debug("[Translation] Falling back from provider '@' to next provider for target '@'",
                    translationProvider.name(), request.targetLanguage());
            translate(request, providerIndex + 1, callback);
        });
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
