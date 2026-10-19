package org.xcore.plugin.rating.view;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.ClockFactory;
import org.xcore.plugin.config.ClockFactory$DI;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.prize.PrizeGrantRepository;
import org.xcore.plugin.rating.season.SeasonResolver;
import org.xcore.plugin.rating.season.SeasonSchedule;
import org.xcore.plugin.rating.season.SeasonStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LadderViewsAvajeTest {
    @Test
    void generatedWiringUsesTheClockOverriddenInTheScope() {
        var now = Instant.parse("2050-01-01T00:00:00Z");
        var fixed = Clock.fixed(now, ZoneOffset.UTC);
        var local = mock(Localization.class);
        when(local.t(eq("player-menu-time-hours"), anyMap())).thenReturn("1h");
        try (var scope = BeanScope.builder()
                .bean(Clock.class, fixed)
                .bean(SeasonSchedule.class, SeasonSchedule.from(new TomlSecretsConfig().rating.seasons))
                .modules(new AvajeModule() {
                    @Override
                    public Class<?>[] classes() { return new Class<?>[]{ClockFactory.class, Clock.class, LadderViews.class}; }

                    @Override
                    public void build(Builder builder) {
                        ClockFactory$DI.build(builder);
                        ClockFactory$DI.build_clock(builder);
                        LadderViews$DI.build(builder);
                    }
                }).forTesting()
                .mock(SeasonStore.class)
                .mock(SeasonResolver.class)
                .mock(PrizeGrantRepository.class)
                .build()) {
            assertThat(scope.get(Clock.class)).isSameAs(fixed);
            assertThat(scope.get(LadderViews.class).text().remaining(now.plusSeconds(3600), local)).isEqualTo("1h");
        }
    }
}
