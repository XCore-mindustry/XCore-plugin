package org.xcore.plugin.localization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationProviderFactoryTest {

    private TranslationExecutor translationExecutor;

    @AfterEach
    void tearDown() {
        if (translationExecutor != null) {
            translationExecutor.shutdown();
        }
    }

    @Test
    @DisplayName("translationProviderPipeline returns empty pipeline when translation is disabled")
    void translationProviderPipeline_returnsEmptyPipeline_whenTranslationDisabled() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.translation.enabled = false;

        TranslationProviderPipeline pipeline = new TranslationProviderFactory(config, new GlobalConfig())
                .translationProviderPipeline(
                        googleTranslationProvider(),
                        translationSafetyService(),
                        translationExecutor()
                );

        assertThat(pipeline.providers()).isEmpty();
    }

    @Test
    @DisplayName("translationProviderPipeline preserves configured order and filters missing or disabled providers")
    void translationProviderPipeline_preservesOrder_andFiltersMissingOrDisabledProviders() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.translation.pipeline = List.of("openai-main", "missing", "google", "openai-disabled");

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.translationProviders = new LinkedHashMap<>();
        globalConfig.translationProviders.put("openai-main", openAiProviderConfig(true));
        globalConfig.translationProviders.put("google", googleProviderConfig(true));
        globalConfig.translationProviders.put("openai-disabled", openAiProviderConfig(false));

        TranslationProviderPipeline pipeline = new TranslationProviderFactory(config, globalConfig)
                .translationProviderPipeline(
                        googleTranslationProvider(),
                        translationSafetyService(),
                        translationExecutor()
                );

        assertThat(pipeline.providers())
                .hasSize(2)
                .extracting(provider -> provider.name() + ":" + provider.type())
                .containsExactly("openai-main:openai", "google:google");
    }

    private GoogleTranslationProvider googleTranslationProvider() {
        return new GoogleTranslationProvider(translationExecutor());
    }

    private TranslationSafetyService translationSafetyService() {
        return new TranslationSafetyService(new TomlXcoreConfig());
    }

    private TranslationExecutor translationExecutor() {
        if (translationExecutor == null) {
            translationExecutor = new TranslationExecutor();
        }
        return translationExecutor;
    }

    private GlobalConfig.TranslationProviderConfig openAiProviderConfig(boolean enabled) {
        GlobalConfig.TranslationProviderConfig providerConfig = new GlobalConfig.TranslationProviderConfig();
        providerConfig.type = "openai";
        providerConfig.enabled = enabled;
        providerConfig.apiKey = "test-key";
        providerConfig.model = "gpt-test";
        providerConfig.normalize();
        return providerConfig;
    }

    private GlobalConfig.TranslationProviderConfig googleProviderConfig(boolean enabled) {
        GlobalConfig.TranslationProviderConfig providerConfig = new GlobalConfig.TranslationProviderConfig();
        providerConfig.type = "google";
        providerConfig.enabled = enabled;
        providerConfig.normalize();
        return providerConfig;
    }
}
