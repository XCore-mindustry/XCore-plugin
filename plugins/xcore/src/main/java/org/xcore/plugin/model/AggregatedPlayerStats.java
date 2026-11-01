package org.xcore.plugin.model;

public record AggregatedPlayerStats(
        int gamesPlayed,
        int gamesWon,
        int blocksBuilt,
        int blocksDeconstructed,
        int blocksDestroyed,
        int unitsProduced,
        int unitsDestroyed
) {

    public static final AggregatedPlayerStats EMPTY = new AggregatedPlayerStats(0, 0, 0, 0, 0, 0, 0);

    public int winRatePercent() {
        if (gamesPlayed <= 0) {
            return 0;
        }
        return Math.round((gamesWon * 100f) / gamesPlayed);
    }
}
