package org.xcore.plugin.model;

import java.util.List;

public record LeaderboardSlice<T>(List<T> items, boolean hasNext, LeaderboardCursor nextCursor) {
}
