package org.xcore.plugin.localization;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Inject;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.util.ArrayList;
import java.util.List;

@Factory
public class TranslationProviderFactory {

    private final Config config;
    private final GlobalConfig globalConfig;

    @Inject
    public TranslationProviderFactory(Config config, GlobalConfig globalConfig) {
        this.config = config;
        this.globalConfig = globalConfig;
    }

    @Bean
    public TranslationProviderPipeline translationProviderPipeline(GoogleTranslationProvider googleTranslationProvider,
                                                                  TranslationSafetyService translationSafetyService,
                                                                  TranslationExecutor translationExecutor) {
        if (!config.translation.enabled) {
            PLog.info("Translation pipeline is disabled in config");
            return new TranslationProviderPipeline(List.of());
        }

        List<TranslationProvider> orderedProviders = new ArrayList<>();
        for (String providerId : config.translation.pipeline) {
            if (providerId == null || providerId.isBlank()) {
                continue;
            }

            GlobalConfig.TranslationProviderConfig providerConfig = globalConfig.translationProviders.get(providerId);
            if (providerConfig == null) {
                PLog.err("Translation provider '@' is referenced in pipeline but missing in global config", providerId);
                continue;
            }

            if (!providerConfig.enabled) {
                continue;
            }

            TranslationProvider translationProvider = createProvider(
                    providerId,
                    providerConfig,
                    googleTranslationProvider,
                    translationSafetyService,
                    translationExecutor
            );
            if (translationProvider == null) {
                PLog.err("Translation provider '@' has unsupported type '@'", providerId, providerConfig.type);
                continue;
            }

            orderedProviders.add(translationProvider);
        }

        if (orderedProviders.isEmpty()) {
            PLog.err("Translation pipeline resolved to zero providers after config filtering");
        }

        return new TranslationProviderPipeline(orderedProviders);
    }

    private TranslationProvider createProvider(String providerId,
                                               GlobalConfig.TranslationProviderConfig providerConfig,
                                               GoogleTranslationProvider googleTranslationProvider,
                                               TranslationSafetyService translationSafetyService,
                                               TranslationExecutor translationExecutor) {
        return switch (providerConfig.type) {
            case "google" -> googleTranslationProvider;
            case "openai" -> new OpenAITranslationProvider(providerId, providerConfig, translationSafetyService, translationExecutor);
            default -> null;
        };
    }
}
