package org.xcore.plugin.service;

import arc.func.Cons;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationResult;

import java.util.List;

@Singleton
public class TranslationFallbackService implements TranslationProvider {

    private final List<TranslationProvider> translationProviders;

    @Inject
    public TranslationFallbackService(List<TranslationProvider> translationProviders) {
        this.translationProviders = List.copyOf(translationProviders);
    }

    @Override
    public String name() {
        return "fallback";
    }

    @Override
    public void translate(Request request, Cons<TranslationResult> callback) {
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

    private void translate(Request request, int providerIndex, Cons<TranslationResult> callback) {
        if (providerIndex >= translationProviders.size()) {
            callback.get(TranslationResult.failure(
                    TranslationFailure.unavailable(name(), "all translation providers failed")));
            return;
        }

        TranslationProvider translationProvider = translationProviders.get(providerIndex);
        if (!translationProvider.supports(request.targetLanguage())) {
            translate(request, providerIndex + 1, callback);
            return;
        }

        translationProvider.translate(request, result -> {
            if (result instanceof TranslationResult.Success) {
                callback.get(result);
                return;
            }

            translate(request, providerIndex + 1, callback);
        });
    }
}
