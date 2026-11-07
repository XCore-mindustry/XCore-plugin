package org.xcore.plugin.model;

public record ModeStatsSummary(
        long gamesPlayed,
        long gamesWon,
        int bestWave,
        int averageWave,
        int bestPlacement,
        long top3Finishes,
        long left,
        long streak,
        long bestStreak,
        long playMs,
        boolean completedOnly
) {

    public ModeStatsSummary(long games, long wins, int bestWave, int averageWave, int bestPlacement, long top3) {
        this(games, wins, bestWave, averageWave, bestPlacement, top3, 0, 0, 0, 0, false);
    }
    public static final ModeStatsSummary EMPTY = new ModeStatsSummary(0, 0, 0, 0, 0, 0);

    public int winRatePercent() {
        long denominator = completedOnly ? gamesPlayed - left : gamesPlayed;
        if (denominator <= 0) {
            return 0;
        }
        return (int)Math.round((gamesWon * 100d) / denominator);
    }

    public boolean hasData() {
        return gamesPlayed > 0 || gamesWon > 0 || bestWave > 0 || averageWave > 0 || bestPlacement > 0 || top3Finishes > 0;
    }
}
