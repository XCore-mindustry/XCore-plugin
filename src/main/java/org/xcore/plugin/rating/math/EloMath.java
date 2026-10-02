package org.xcore.plugin.rating.math;

/**
 * Pure mathematical utilities for Elo rating calculations.
 */
public final class EloMath {

    private EloMath() {}

    /**
     * Calculates the logistic win expectancy of player/team A against player/team B.
     * E_A = 1 / (1 + 10^((R_B - R_A) / 400.0))
     *
     * @param ratingA rating of subject A
     * @param ratingB rating of opponent B
     * @return expected score in range (0.0, 1.0)
     */
    public static double expectedScore(double ratingA, double ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /**
     * Calculates the Elo delta given K-factor, actual score, and expected score.
     * delta = round(k * (actual - expected))
     *
     * @param k        K-factor scaling weight
     * @param actual   actual outcome (1.0 for win, 0.5 for draw, 0.0 for loss)
     * @param expected expected outcome in range (0.0, 1.0)
     * @return rounded integer rating delta
     */
    public static int delta(double k, double actual, double expected) {
        return (int) Math.round(k * (actual - expected));
    }

    /**
     * Computes the effective team rating considering team size advantage in Mindustry.
     * A team with more players can mine, build, and fight faster.
     * R_eff = R_avg + 400 * log10(max(1, teamSize))
     * When two teams have equal size, the log10 adjustments cancel out identically in expectedScore.
     *
     * @param averageRating arithmetic mean of team member ratings
     * @param teamSize      number of active team members
     * @return effective team strength rating
     */
    public static double effectiveTeamRating(double averageRating, int teamSize) {
        if (teamSize <= 0) {
            return averageRating;
        }
        return averageRating + 400.0 * Math.log10(teamSize);
    }
}
