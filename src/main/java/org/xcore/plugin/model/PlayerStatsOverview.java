package org.xcore.plugin.model;

public record PlayerStatsOverview(
        AggregatedPlayerStats overall,
        ModeStatsSummary pvp,
        ModeStatsSummary survival,
        ModeStatsSummary hexed
) {
    public static final PlayerStatsOverview EMPTY = new PlayerStatsOverview(
            AggregatedPlayerStats.EMPTY,
            ModeStatsSummary.EMPTY,
            ModeStatsSummary.EMPTY,
            ModeStatsSummary.EMPTY
    );
}
