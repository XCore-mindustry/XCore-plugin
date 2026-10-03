package org.xcore.plugin.integration.top;

import org.xcore.plugin.model.PlayerData;

import java.util.HashMap;
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

    /**
     * Attributes every player card understands: nickname override, active badge, PID and
     * the admin mark. Providers add their own on top.
     */
    public static Map<String, String> profileAttributes(PlayerData player) {
        Map<String, String> attributes = new HashMap<>();
        if (player == null) {
            return attributes;
        }
        if (player.customNickname != null && !player.customNickname.isBlank()) {
            attributes.put("customNickname", player.customNickname);
        }
        if (player.activeBadge != null && !player.activeBadge.isBlank()) {
            attributes.put("activeBadge", player.activeBadge);
            attributes.put("badgeColorMode", player.badgeSymbolColorMode != null ? player.badgeSymbolColorMode : "default");
        }
        if (player.pid > 0) {
            attributes.put("pid", String.valueOf(player.pid));
        }
        attributes.put("admin", String.valueOf(player.admin));
        return attributes;
    }

    public static LeaderboardEntry of(String playerUuid, int rank, String displayName, String primaryValue, String displayText) {
        return new LeaderboardEntry(playerUuid, rank, displayName, primaryValue, Map.of(), displayText);
    }
}
