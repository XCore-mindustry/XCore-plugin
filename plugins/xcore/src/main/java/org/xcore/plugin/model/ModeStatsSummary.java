package org.xcore.plugin.model;

public record ModeStatsSummary(
        int gamesPlayed,
        int gamesWon,
        int bestWave,
        int averageWave,
        int bestPlacement,
        int top3Finishes
) {

    public static final ModeStatsSummary EMPTY = new ModeStatsSummary(0, 0, 0, 0, 0, 0);

    public int winRatePercent() {
        if (gamesPlayed <= 0) {
            return 0;
        }
        return Math.round((gamesWon * 100f) / gamesPlayed);
    }

    public boolean hasData() {
        return gamesPlayed > 0 || gamesWon > 0 || bestWave > 0 || averageWave > 0 || bestPlacement > 0 || top3Finishes > 0;
    }
}
