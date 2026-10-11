package org.xcore.plugin.rating.match;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One keyset page of a player's matches, newest first. Every match holds the player's own entry only.
 *
 * @param nextCursor opaque cursor of the following page, {@code null} on the last one
 */
public record MatchPage(List<MatchRecord> matches, @Nullable String nextCursor) {
    public MatchPage {
        matches = List.copyOf(matches);
        if (nextCursor != null && nextCursor.isBlank()) {
            nextCursor = null;
        }
    }

    public static MatchPage empty() {
        return new MatchPage(List.of(), null);
    }

    public boolean hasNext() {
        return nextCursor != null;
    }
}
