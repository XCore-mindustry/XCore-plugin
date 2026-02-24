package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TimeServiceTest {

    private final TimeService timeService = new TimeService();

    @Test
    @DisplayName("parsePeriod parses single unit token")
    void parseSingleToken() {
        var result = timeService.parsePeriod("10m", TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.toEpochMilli()).isEqualTo(Duration.ofMinutes(10).toMillis());
    }

    @Test
    @DisplayName("parsePeriod parses combined tokens")
    void parseCombinedTokens() {
        var result = timeService.parsePeriod("1h30m", TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.toEpochMilli()).isEqualTo(Duration.ofHours(1).plusMinutes(30).toMillis());
    }

    @Test
    @DisplayName("parsePeriod rejects negative values by default")
    void rejectNegativeByDefault() {
        var result = timeService.parsePeriod("-10m", TimeUnit.SECONDS);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("parsePeriod accepts negative values when explicitly allowed")
    void allowNegativeWhenEnabled() {
        var result = timeService.parsePeriod("-10m", TimeUnit.SECONDS, true);

        assertThat(result).isNotNull();
        assertThat(result.toEpochMilli()).isEqualTo(-Duration.ofMinutes(10).toMillis());
    }

    @Test
    @DisplayName("parsePeriod returns null for invalid format")
    void invalidFormatReturnsNull() {
        var result = timeService.parsePeriod("not-a-period", TimeUnit.SECONDS);

        assertThat(result).isNull();
    }
}
