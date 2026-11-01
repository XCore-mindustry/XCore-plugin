package org.xcore.plugin.rating.ladder;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * The effect of one rated match on one player's standing: always one more match played.
 *
 * @param ratingDelta signed rating change; the store clamps the result to the ladder minimum
 * @param stats       additive counter increments, keyed by {@code [a-z][a-z0-9_]{0,31}}
 */
public record StandingMutation(String uuid, int ratingDelta, boolean win, Map<String, Integer> stats) {
    private static final Pattern STAT = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    public StandingMutation {
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid must not be blank");
        stats = stats == null ? Map.of() : Map.copyOf(stats);
        for (var entry : stats.entrySet()) {
            if (!STAT.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("Invalid stat name: " + entry.getKey());
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Stat increments must not be negative: " + entry.getKey());
            }
        }
    }

    public StandingMutation(String uuid, int ratingDelta, boolean win) {
        this(uuid, ratingDelta, win, Map.of());
    }
}
