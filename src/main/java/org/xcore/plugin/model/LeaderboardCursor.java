package org.xcore.plugin.model;

public record LeaderboardCursor(int primaryValue, int secondaryValue, int pid) {
    public boolean isValid() {
        return pid >= 0;
    }
}
