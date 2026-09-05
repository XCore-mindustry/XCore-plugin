package org.xcore.plugin.integration.top;

import java.util.List;

/**
 * Paged leaderboard result.
 *
 * @param currentPage  1-based page number
 * @param entries      list of entries on this page
 * @param hasNext      whether more results exist
 * @param nextCursor   opaque string cursor for requesting the next page (required if hasNext is true)
 * @param totalEntries total number of entries in the category (nullable if provider cannot count)
 * @param selfRank     viewer's 1-based rank in this category (nullable if unranked/unknown)
 */
public record LeaderboardPage(
        int currentPage,
        List<LeaderboardEntry> entries,
        boolean hasNext,
        String nextCursor,
        Long totalEntries,
        Integer selfRank
) {
    public LeaderboardPage {
        if (currentPage < 1) {
            throw new IllegalArgumentException("currentPage must be >= 1");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (hasNext && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor is required when hasNext is true");
        }
        if (!hasNext) {
            nextCursor = null;
        }
        if (totalEntries != null && totalEntries < 0) {
            throw new IllegalArgumentException("totalEntries must not be negative");
        }
        if (selfRank != null && selfRank < 1) {
            throw new IllegalArgumentException("selfRank must be >= 1");
        }
    }

    public static LeaderboardPage empty(int page) {
        return new LeaderboardPage(Math.max(1, page), List.of(), false, null, 0L, null);
    }
}
