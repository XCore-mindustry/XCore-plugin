package org.xcore.plugin.rating.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonTest {
    private static final Instant STARTS = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    @DisplayName("a season cannot end before it starts")
    void rejectsReversedInterval() {
        assertThatThrownBy(() -> Season.opening("minipvp", 1, STARTS, STARTS.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endsAt");
    }

    @Test
    @DisplayName("a season ended the moment it started is still a season")
    void acceptsEmptyInterval() {
        assertThat(Season.opening("minipvp", 1, STARTS, STARTS).endsAt()).isEqualTo(STARTS);
    }
}
