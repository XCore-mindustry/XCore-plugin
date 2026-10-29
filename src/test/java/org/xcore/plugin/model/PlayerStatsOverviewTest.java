package org.xcore.plugin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerStatsOverviewTest {

    @Test
    @DisplayName("games outside PvP, survival and Hexed make up the rest of the total")
    void otherGamesAreWhatTheModesLeaveOfTheTotal() {
        var overview = new PlayerStatsOverview(
                new AggregatedPlayerStats(1736, 777, 0, 0, 0, 0, 0),
                new ModeStatsSummary(1000, 600, 0, 0, 0, 0),
                new ModeStatsSummary(500, 20, 80, 30, 0, 0),
                new ModeStatsSummary(200, 150, 0, 0, 1, 180));

        assertThat(overview.otherGamesPlayed()).isEqualTo(36);
        assertThat(overview.otherGamesWon()).isEqualTo(7);
    }

    @Test
    @DisplayName("the rest never goes below zero")
    void otherGamesNeverGoNegative() {
        var overview = new PlayerStatsOverview(
                new AggregatedPlayerStats(10, 2, 0, 0, 0, 0, 0),
                new ModeStatsSummary(12, 5, 0, 0, 0, 0),
                ModeStatsSummary.EMPTY,
                ModeStatsSummary.EMPTY);

        assertThat(overview.otherGamesPlayed()).isZero();
        assertThat(overview.otherGamesWon()).isZero();
    }
}
