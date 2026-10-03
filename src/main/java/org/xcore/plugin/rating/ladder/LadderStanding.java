package org.xcore.plugin.rating.ladder;

import org.xcore.plugin.rating.RatingLeague;

import java.util.Map;

/**
 * One player's position on a ladder within a season.
 *
 * <p>A standing with zero matches is virtual: nothing is stored for the player yet and
 * {@code rating} is the value they would start the season from.</p>
 *
 * @param stats additive, mode-specific counters (for example {@code top3})
 */
public record LadderStanding(
        String ladderId,
        int season,
        String uuid,
        int rating,
        int peakRating,
        int matches,
        int wins,
        Map<String, Integer> stats
) {
    public LadderStanding {
        if (ladderId == null || ladderId.isBlank()) throw new IllegalArgumentException("ladderId must not be blank");
        if (season < 1) throw new IllegalArgumentException("season must be positive");
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid must not be blank");
        if (rating < 0 || peakRating < 0 || matches < 0 || wins < 0) {
            throw new IllegalArgumentException("standing values must not be negative");
        }
        stats = stats == null ? Map.of() : Map.copyOf(stats);
    }

    /** The standing of a player who has not played a rated match this season. */
    public static LadderStanding unplaced(String ladderId, int season, String uuid, int startingRating) {
        return new LadderStanding(ladderId, season, uuid, startingRating, startingRating, 0, 0, Map.of());
    }

    public boolean placed() {
        return matches > 0;
    }

    public int stat(String name) {
        return stats.getOrDefault(name, 0);
    }

    public RatingLeague league() {
        return RatingLeague.fromRating(rating);
    }
}
