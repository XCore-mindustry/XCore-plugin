package org.xcore.plugin.rating;

/**
 * Immutable configuration rules for rating calculation engines.
 */
public record RatingPolicy(
        int defaultRating,
        int minimumRating,
        int kFactor,
        int minimumPlayers,
        int minimumPlayTimeSeconds
) {
    public RatingPolicy {
        if (minimumRating < 0) {
            throw new IllegalArgumentException("minimumRating must not be negative");
        }
        if (defaultRating < minimumRating) {
            throw new IllegalArgumentException("defaultRating must not be below minimumRating");
        }
        if (kFactor <= 0) {
            throw new IllegalArgumentException("kFactor must be positive");
        }
        if (minimumPlayers < 2) {
            throw new IllegalArgumentException("minimumPlayers must be at least 2");
        }
        if (minimumPlayTimeSeconds < 0) {
            throw new IllegalArgumentException("minimumPlayTimeSeconds must not be negative");
        }
    }

    public RatingPolicy(int defaultRating, int minimumRating, int kFactor, int minimumPlayers) {
        this(defaultRating, minimumRating, kFactor, minimumPlayers, 0);
    }

    public static RatingPolicy placementEloV1() {
        return new RatingPolicy(1000, 100, 32, 2, 0);
    }

    public static RatingPolicy teamEloV1() {
        return new RatingPolicy(1000, 100, 32, 2, 15);
    }
}
