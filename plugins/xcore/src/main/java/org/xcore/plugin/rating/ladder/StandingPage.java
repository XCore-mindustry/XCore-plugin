package org.xcore.plugin.rating.ladder;

import java.util.List;

/**
 * One keyset page of a ladder leaderboard, best rating first.
 *
 * @param nextCursor opaque cursor for the following page, {@code null} on the last page
 */
public record StandingPage(List<LadderStanding> standings, String nextCursor, boolean hasNext) {
    public StandingPage {
        standings = List.copyOf(standings);
        if (!hasNext) {
            nextCursor = null;
        } else if (nextCursor == null || nextCursor.isBlank()) {
            throw new IllegalArgumentException("nextCursor is required when hasNext is true");
        }
    }
}
