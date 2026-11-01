package org.xcore.plugin.integration.blockhistory;

import java.util.Optional;

/** Game-thread-only, bounded native history read. No database/network I/O or retained game objects. */
@FunctionalInterface
public interface BlockHistoryProvider {
    Optional<BlockHistorySummary> summarize(String playerUuid, int windowSeconds);
}
