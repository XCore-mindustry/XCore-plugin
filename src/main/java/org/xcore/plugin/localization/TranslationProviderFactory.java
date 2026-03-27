package org.xcore.plugin.localization;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

import java.util.List;

@Factory
public class TranslationProviderFactory {

    @Bean
    public List<TranslationProvider> translationProviders(GoogleTranslationProvider googleTranslationProvider) {
        return List.of(googleTranslationProvider);
    }
}
