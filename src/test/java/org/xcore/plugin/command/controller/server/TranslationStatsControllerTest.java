package org.xcore.plugin.command.controller.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.TranslationMetricsService;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranslationStatsControllerTest {

    @Test
    @DisplayName("trstats queries global and per-provider translation metrics")
    void translationStats_queriesGlobalAndPerProviderMetrics() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        config.translation.pipeline = java.util.List.of("nvidia-mistral-small", "google");

        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.translation.providers = new LinkedHashMap<>();
        TomlSecretsConfig.TranslationSection.ProviderConfig nvidia = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        nvidia.type = "openai";
        secretsConfig.translation.providers.put("nvidia-mistral-small", nvidia);
        TomlSecretsConfig.TranslationSection.ProviderConfig google = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        google.type = "google";
        secretsConfig.translation.providers.put("google", google);

        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        when(translationMetricsService.readGlobalTotals()).thenReturn(Map.of("requests_total", "5"));
        when(translationMetricsService.readCurrentMinuteGlobal()).thenReturn(Map.of("requests_total", "2"));
        when(translationMetricsService.readProviderTotals("nvidia-mistral-small")).thenReturn(Map.of("attempts_total", "3"));
        when(translationMetricsService.readCurrentMinuteProvider("nvidia-mistral-small")).thenReturn(Map.of("attempts_total", "1"));
        when(translationMetricsService.readProviderTotals("google")).thenReturn(Map.of("attempts_total", "2"));
        when(translationMetricsService.readCurrentMinuteProvider("google")).thenReturn(Map.of("attempts_total", "1"));

        TranslationStatsController controller = new TranslationStatsController(config, secretsConfig, translationMetricsService);

        assertThatNoException().isThrownBy(() -> controller.translationStats(mock(XCoreSender.class)));

        verify(translationMetricsService).readGlobalTotals();
        verify(translationMetricsService).readCurrentMinuteGlobal();
        verify(translationMetricsService).readProviderTotals("nvidia-mistral-small");
        verify(translationMetricsService).readCurrentMinuteProvider("nvidia-mistral-small");
        verify(translationMetricsService).readProviderTotals("google");
        verify(translationMetricsService).readCurrentMinuteProvider("google");
    }
}
