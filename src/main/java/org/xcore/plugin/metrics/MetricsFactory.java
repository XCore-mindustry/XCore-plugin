package org.xcore.plugin.metrics;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

@Factory
public final class MetricsFactory {
    @Bean
    public MetricsService metricsService(DefaultMetricsService metricsService) {
        return metricsService;
    }
}
