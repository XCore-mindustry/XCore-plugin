package org.xcore.plugin.config;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

import java.time.Clock;

@Factory
public final class ClockFactory {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
