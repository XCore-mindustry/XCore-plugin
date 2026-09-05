package org.xcore.plugin.integration.top;

import org.xcore.plugin.model.PlayerData;

import java.util.Objects;

/**
 * Request payload for loading a page of leaderboard entries.
 *
 * @param categoryId the category identifier
 * @param page       1-based page number hint (useful for offset-based providers)
 * @param pageSize   number of entries requested per page
 * @param cursor     opaque string cursor token (null for first page)
 * @param viewerData viewing player's data (nullable, used to compute self rank)
 */
public record LeaderboardPageRequest(
        String categoryId,
        int page,
        int pageSize,
        String cursor,
        PlayerData viewerData
) {
    public LeaderboardPageRequest {
        Objects.requireNonNull(categoryId, "categoryId");
        if (categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId must not be blank");
        }
        page = Math.max(1, page);
        pageSize = Math.max(1, pageSize);
    }
}
