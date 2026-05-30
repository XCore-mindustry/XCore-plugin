package org.xcore.plugin.localization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
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

        TranslationProviderPipeline pipeline = new TranslationProviderFactory(config, new TomlSecretsConfig())
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

        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.translation.providers = new LinkedHashMap<>();
        secretsConfig.translation.providers.put("openai-main", openAiProviderConfig(true));
        secretsConfig.translation.providers.put("google", googleProviderConfig(true));
        secretsConfig.translation.providers.put("openai-disabled", openAiProviderConfig(false));

        TranslationProviderPipeline pipeline = new TranslationProviderFactory(config, secretsConfig)
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

    private TomlSecretsConfig.TranslationSection.ProviderConfig openAiProviderConfig(boolean enabled) {
        TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        providerConfig.type = "openai";
        providerConfig.enabled = enabled;
        providerConfig.apiKey = "test-key";
        providerConfig.model = "gpt-test";
        providerConfig.normalize();
        return providerConfig;
    }

    private TomlSecretsConfig.TranslationSection.ProviderConfig googleProviderConfig(boolean enabled) {
        TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        providerConfig.type = "google";
        providerConfig.enabled = enabled;
        providerConfig.normalize();
        return providerConfig;
    }
}
