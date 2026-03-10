package org.xcore.plugin.model;

public record PlayerStatsOverview(
        AggregatedPlayerStats overall,
        ModeStatsSummary pvp,
        ModeStatsSummary survival,
        ModeStatsSummary hexed
) {
}
