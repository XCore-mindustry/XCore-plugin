package org.xcore.plugin.integration.playerstorage;

import java.util.List;
import java.util.Objects;

public record PlayerPage(List<PlayerRecord> players, String nextCursor, boolean hasNext) {
    public PlayerPage {
        Objects.requireNonNull(players, "players");
        players = List.copyOf(players);
        if (hasNext && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("A page with more results requires a cursor");
        }
        if (!hasNext) nextCursor = null;
    }
}
