package org.xcore.plugin.rating.season;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.xcore.plugin.config.TomlSecretsConfig;

@Factory
public class SeasonBeans {

    @Bean
    public SeasonSchedule seasonSchedule(TomlSecretsConfig config) {
        return SeasonSchedule.from(config.rating.seasons);
    }
}
