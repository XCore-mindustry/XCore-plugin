package org.xcore.plugin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigTranslationTest {

    @Test
    @DisplayName("config normalize applies translation defaults when fields are missing")
    void normalize_appliesTranslationDefaults_whenFieldsAreMissing() {
        Config config = new Config();
        config.translation = null;
        config.disabledCommands = null;
        config.disabledFeatures = null;

        config.normalize();

        assertThat(config.disabledCommands).isNotNull().isEmpty();
        assertThat(config.disabledFeatures).isNotNull().isEmpty();
        assertThat(config.translation).isNotNull();
        assertThat(config.translation.enabled).isTrue();
        assertThat(config.translation.pipeline).containsExactly("google");
        assertThat(config.translation.cache.enabled).isTrue();
        assertThat(config.translation.cache.ttlSeconds).isEqualTo(1800);
        assertThat(config.translation.cache.maxTextLength).isEqualTo(500);
        assertThat(config.translation.metrics.enabled).isTrue();
        assertThat(config.translation.metrics.minuteBucketsEnabled).isTrue();
        assertThat(config.translation.metrics.minuteBucketTtlSeconds).isEqualTo(21600);
        assertThat(config.translation.llm.preserveFormattingTokens).isTrue();
        assertThat(config.translation.llm.structuredOutputRequired).isTrue();
        assertThat(config.translation.llm.maxInputChars).isEqualTo(500);
        assertThat(config.translation.llm.maxOutputChars).isEqualTo(1200);
    }

    @Test
    @DisplayName("global config normalize creates default google translation provider")
    void normalize_createsDefaultGoogleTranslationProvider() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.translationProviders = null;

        globalConfig.normalize();

        assertThat(globalConfig.translationProviders)
                .containsOnlyKeys("google");

        GlobalConfig.TranslationProviderConfig providerConfig = globalConfig.translationProviders.get("google");
        assertThat(providerConfig).isNotNull();
        assertThat(providerConfig.type).isEqualTo("google");
        assertThat(providerConfig.enabled).isTrue();
        assertThat(providerConfig.baseUrl).isEqualTo("https://api.openai.com/v1");
        assertThat(providerConfig.model).isEqualTo("gpt-5.4");
        assertThat(providerConfig.timeoutSeconds).isEqualTo(15);
        assertThat(providerConfig.maxRetries).isEqualTo(1);
    }

    @Test
    @DisplayName("global config normalize repairs invalid provider fields")
    void normalize_repairsInvalidProviderFields() {
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.TranslationProviderConfig providerConfig = new GlobalConfig.TranslationProviderConfig();
        providerConfig.type = "";
        providerConfig.baseUrl = "";
        providerConfig.model = "";
        providerConfig.timeoutSeconds = 0;
        providerConfig.maxRetries = -1;
        providerConfig.supportedLanguages = null;
        globalConfig.translationProviders = new LinkedHashMap<>(Map.of("llm", providerConfig));

        globalConfig.normalize();

        GlobalConfig.TranslationProviderConfig normalized = globalConfig.translationProviders.get("llm");
        assertThat(normalized.type).isEqualTo("google");
        assertThat(normalized.baseUrl).isEqualTo("https://api.openai.com/v1");
        assertThat(normalized.model).isEqualTo("gpt-5.4");
        assertThat(normalized.timeoutSeconds).isEqualTo(15);
        assertThat(normalized.maxRetries).isEqualTo(1);
        assertThat(normalized.supportedLanguages).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("translation config normalize restores default pipeline when empty")
    void normalize_restoresDefaultPipeline_whenEmpty() {
        Config config = new Config();
        config.translation.pipeline = List.of();
        config.translation.cache.ttlSeconds = 0;
        config.translation.cache.maxTextLength = -10;
        config.translation.metrics.minuteBucketTtlSeconds = 0;
        config.translation.llm.maxInputChars = -5;
        config.translation.llm.maxOutputChars = 0;

        config.normalize();

        assertThat(config.translation.pipeline).containsExactly("google");
        assertThat(config.translation.cache.ttlSeconds).isEqualTo(1800);
        assertThat(config.translation.cache.maxTextLength).isEqualTo(500);
        assertThat(config.translation.metrics.minuteBucketTtlSeconds).isEqualTo(21600);
        assertThat(config.translation.llm.maxInputChars).isEqualTo(500);
        assertThat(config.translation.llm.maxOutputChars).isEqualTo(1200);
    }
}
