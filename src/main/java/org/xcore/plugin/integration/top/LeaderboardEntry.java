package org.xcore.plugin.integration.top;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a single ranked player entry in a leaderboard.
 *
 * @param playerUuid   UUID of the player (required for profile clicks: profile:uuid)
 * @param rank         1-based position on the leaderboard
 * @param displayName  player's nickname or display name
 * @param primaryValue primary score/stat as formatted string (e.g. "1,450", "12h 30m")
 * @param attributes   additional provider-specific metadata (e.g. league, tier, wins)
 * @param displayText  pre-formatted button label text (used as default in formatEntry)
 */
public record LeaderboardEntry(
        String playerUuid,
        int rank,
        String displayName,
        String primaryValue,
        Map<String, String> attributes,
        String displayText
) {
    public LeaderboardEntry {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (playerUuid.isBlank()) {
            throw new IllegalArgumentException("playerUuid must not be blank");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1");
        }
        displayName = displayName == null ? "" : displayName;
        primaryValue = primaryValue == null ? "" : primaryValue;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        displayText = displayText == null ? "" : displayText;
    }

    public static LeaderboardEntry of(String playerUuid, int rank, String displayName, String primaryValue, String displayText) {
        return new LeaderboardEntry(playerUuid, rank, displayName, primaryValue, Map.of(), displayText);
    }
}
