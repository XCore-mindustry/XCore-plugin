package org.xcore.plugin.integration.top;

import org.xcore.plugin.localization.Localization;

/**
 * Service Provider Interface (SPI) for registering custom leaderboard categories in the {@code /top} menu.
 */
public interface TopCategoryProvider {

    /**
     * Unique identifier for this category (e.g. "MINI_PVP", "PLAYTIME", "HEXED", "hexed-elo").
     * Must be non-null and non-blank.
     */
    String id();

    /**
     * Localized display name shown in menu titles, headers, and category picker buttons.
     *
     * @param local viewer's localization context
     * @return human-readable category name
     */
    String displayName(Localization local);

    /**
     * Priority order for sorting in the categories selection screen.
     * Higher numbers appear first. Ties are broken by registration order.
     */
    default int priority() {
        return 0;
    }

    /**
     * Loads a page of leaderboard entries according to the given request.
     *
     * @param request pagination and viewer context
     * @return a page containing entries, next cursor, total count, and self rank
     */
    LeaderboardPage loadPage(LeaderboardPageRequest request);

    /**
     * Formats the menu button text for a single player entry in this category.
     *
     * @param entry the leaderboard entry
     * @param local viewer's localization context
     * @return formatted button label shown on the menu screen
     */
    default String formatEntry(LeaderboardEntry entry, Localization local) {
        return entry.displayText();
    }
}
