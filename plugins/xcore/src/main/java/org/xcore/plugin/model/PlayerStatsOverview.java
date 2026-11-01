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

    /** Games outside PvP, survival and Hexed (events, mostly): what is left of the total once the modes are taken out. */
    public int otherGamesPlayed() {
        return Math.max(0, overall.gamesPlayed() - pvp.gamesPlayed() - survival.gamesPlayed() - hexed.gamesPlayed());
    }

    /** Wins in the games counted by {@link #otherGamesPlayed()}. */
    public int otherGamesWon() {
        return Math.max(0, overall.gamesWon() - pvp.gamesWon() - survival.gamesWon() - hexed.gamesWon());
    }
}
