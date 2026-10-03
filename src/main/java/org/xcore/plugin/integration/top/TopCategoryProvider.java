package org.xcore.plugin.integration.top;

import org.xcore.plugin.localization.Localization;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

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
     * Loads a page of leaderboard entries according to the given request. May block on
     * storage: the menu calls it off the game thread.
     *
     * @param request pagination and viewer context
     * @return a page containing entries, next cursor, total count, and self rank
     */
    LeaderboardPage loadPage(LeaderboardPageRequest request);

    /**
     * The leaderboards this category consists of, in the order the viewer steps through
     * them (newest first). A category with a single leaderboard returns nothing and gets no
     * switcher. May block on storage, like {@link #loadPage}.
     */
    default List<TopScope> scopes() {
        return List.of();
    }

    /** The switcher label of one of this category's {@link #scopes()}. */
    default String formatScope(TopScope scope, Localization local) {
        return scope != null ? scope.id() : "";
    }

    /**
     * Formats the menu button text for a single player entry in this category.
     *
     * @param entry the leaderboard entry
     * @param local viewer's localization context
     * @return formatted button label shown on the menu screen
     */
    default String formatEntry(LeaderboardEntry entry, Localization local) {
        return entry != null ? entry.displayText() : "";
    }

    /**
     * Formats the value display for a single player entry in this category.
     *
     * @param entry the leaderboard entry
     * @param local viewer's localization context
     * @return formatted value label shown on the right side of the card
     */
    default String formatValue(LeaderboardEntry entry, Localization local) {
        return entry != null ? formatValue(entry.primaryValue(), local) : "-";
    }

    /**
     * Formats a raw {@link LeaderboardEntry#primaryValue()} or
     * {@link LeaderboardPage#selfPrimaryValue()} of this category. Whole numbers are
     * grouped for the viewer's locale unless the category knows better.
     */
    default String formatValue(String primaryValue, Localization local) {
        if (primaryValue == null || primaryValue.isBlank()) {
            return "-";
        }
        try {
            long value = Long.parseLong(primaryValue);
            return NumberFormat.getIntegerInstance(local != null ? local.getLocale() : Locale.ROOT).format(value);
        } catch (NumberFormatException e) {
            return primaryValue;
        }
    }
}
