package org.xcore.plugin.integration.blockhistory;

import java.util.List;

/** Immutable current-map snapshot. Providers should read at most 4096 entries. */
public record BlockHistorySummary(int windowSeconds, int built, int destroyed, int configured,
                                  boolean truncated, List<Entry> topBlocks, Area area,
                                  Integer lastActionSecondsAgo) {
    public BlockHistorySummary {
        if (windowSeconds < 1 || built < 0 || destroyed < 0 || configured < 0
                || (lastActionSecondsAgo != null && lastActionSecondsAgo < 0)) {
            throw new IllegalArgumentException("Invalid block history counts/window");
        }
        topBlocks = List.copyOf(topBlocks);
        if (topBlocks.size() > 8) throw new IllegalArgumentException("At most eight top blocks");
    }

    public record Entry(String block, int built, int destroyed) {
        public Entry {
            if (block == null || block.isBlank() || block.length() > 64 || built < 0 || destroyed < 0) {
                throw new IllegalArgumentException("Invalid block entry");
            }
        }
    }

    public record Area(int x1, int y1, int x2, int y2) {}
}
