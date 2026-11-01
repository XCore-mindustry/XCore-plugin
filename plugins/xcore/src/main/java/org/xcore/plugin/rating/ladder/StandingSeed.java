package org.xcore.plugin.rating.ladder;

/**
 * The rating a player enters a season with.
 *
 * @param rating     starting rating in the new season
 * @param fromSeason season the rating was carried over from, {@code 0} for a player new to the ladder
 * @param fromRating rating the player finished {@code fromSeason} with
 */
public record StandingSeed(int rating, int fromSeason, int fromRating) {
    public StandingSeed {
        if (rating < 0 || fromSeason < 0 || fromRating < 0) {
            throw new IllegalArgumentException("seed values must not be negative");
        }
    }

    /** A player with no earlier standing on the ladder. */
    public static StandingSeed fresh(int rating) {
        return new StandingSeed(rating, 0, rating);
    }

    public static StandingSeed carried(int rating, LadderStanding previous) {
        return new StandingSeed(rating, previous.season(), previous.rating());
    }

    public boolean carried() {
        return fromSeason > 0;
    }
}
