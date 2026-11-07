package org.xcore.plugin.model;

public record AggregatedPlayerStats(
        long gamesPlayed,
        long gamesWon,
        long blocksBuilt,
        long blocksDeconstructed,
        long blocksDestroyed,
        long unitsProduced,
        long unitsDestroyed
) {

    public static final AggregatedPlayerStats EMPTY = new AggregatedPlayerStats(0, 0, 0, 0, 0, 0, 0);

    public int winRatePercent() {
        if (gamesPlayed <= 0) {
            return 0;
        }
        return (int)Math.round((gamesWon * 100d) / gamesPlayed);
    }
}
