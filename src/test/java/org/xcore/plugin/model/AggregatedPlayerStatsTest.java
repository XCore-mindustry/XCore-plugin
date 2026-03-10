package org.xcore.plugin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AggregatedPlayerStatsTest {

    @Test
    @DisplayName("winRatePercent returns zero when there are no games")
    void winRatePercentReturnsZeroWhenThereAreNoGames() {
        var stats = new AggregatedPlayerStats(0, 0, 0, 0, 0, 0, 0);

        assertThat(stats.winRatePercent()).isZero();
    }

    @Test
    @DisplayName("winRatePercent rounds to nearest whole percent")
    void winRatePercentRoundsToNearestWholePercent() {
        var stats = new AggregatedPlayerStats(3, 2, 0, 0, 0, 0, 0);

        assertThat(stats.winRatePercent()).isEqualTo(67);
    }
}
