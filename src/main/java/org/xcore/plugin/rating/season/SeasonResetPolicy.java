package org.xcore.plugin.rating.season;

import org.xcore.plugin.rating.RatingPolicy;

/**
 * How much of a rating survives into the next season.
 *
 * @param carry share of the distance from the default rating that is kept by a soft reset
 */
public record SeasonResetPolicy(Mode mode, double carry) {

    public enum Mode {
        /** Pull every rating towards the default by {@code 1 - carry}. */
        SOFT,
        /** Everyone starts again from the default rating. */
        HARD,
        /** Ratings carry over unchanged; only the match counters start again. */
        NONE
    }

    public SeasonResetPolicy {
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        if (!(carry >= 0.0 && carry <= 1.0)) throw new IllegalArgumentException("carry must be within 0..1");
    }

    /** @throws IllegalArgumentException when {@code mode} is not soft, hard or none */
    public static SeasonResetPolicy of(String mode, double carry) {
        try {
            return new SeasonResetPolicy(Mode.valueOf(mode == null ? "" : mode.strip().toUpperCase()), carry);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Season reset must be soft, hard or none with a carry within 0..1, got '"
                    + mode + "' and " + carry);
        }
    }

    /** The rating a player starts the next season with after finishing on {@code previousRating}. */
    public int seed(int previousRating, RatingPolicy policy) {
        int seeded = switch (mode) {
            case SOFT -> policy.defaultRating()
                    + (int) Math.round((previousRating - policy.defaultRating()) * carry);
            case HARD -> policy.defaultRating();
            case NONE -> previousRating;
        };
        return Math.max(policy.minimumRating(), seeded);
    }
}
