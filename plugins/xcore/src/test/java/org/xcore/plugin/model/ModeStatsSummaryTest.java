package org.xcore.plugin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModeStatsSummaryTest {

    @Test
    @DisplayName("winRatePercent returns zero for empty stats")
    void winRatePercentReturnsZeroForEmptyStats() {
        assertThat(ModeStatsSummary.EMPTY.winRatePercent()).isZero();
        assertThat(ModeStatsSummary.EMPTY.hasData()).isFalse();
    }

    @Test
    @DisplayName("winRatePercent rounds based on played and won games")
    void winRatePercentRoundsBasedOnPlayedAndWonGames() {
        var stats = new ModeStatsSummary(7, 3, 120, 75, 1, 4);

        assertThat(stats.winRatePercent()).isEqualTo(43);
        assertThat(stats.hasData()).isTrue();
    }
}
