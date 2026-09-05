package org.xcore.plugin.service.top;

import org.xcore.plugin.model.LeaderboardCursor;

public final class LeaderboardCursorCodec {
    private LeaderboardCursorCodec() {
    }

    public static String encode(LeaderboardCursor cursor) {
        if (cursor == null) return null;
        return cursor.primaryValue() + ":" + cursor.secondaryValue() + ":" + cursor.pid();
    }

    public static LeaderboardCursor decode(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split(":");
        if (parts.length != 3) return null;
        try {
            return new LeaderboardCursor(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
