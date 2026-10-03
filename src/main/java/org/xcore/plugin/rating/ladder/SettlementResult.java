package org.xcore.plugin.rating.ladder;

import java.util.Map;

/**
 * Outcome of {@link Ladder#settle(MatchSettlement)}.
 *
 * @param claimed   {@code true} for the one caller that owns this match; history and
 *                  notifications belong to that caller only
 * @param applied   whether standings changed
 * @param reason    {@code "applied"}, the skip reason, or the ledger status of a duplicate
 * @param standings standings after the match, keyed by player UUID
 */
public record SettlementResult(
        String operationId,
        boolean claimed,
        boolean applied,
        String reason,
        Map<String, LadderStanding> standings
) {
    public SettlementResult {
        standings = Map.copyOf(standings);
    }

    static SettlementResult duplicate(String operationId, String ledgerStatus) {
        return new SettlementResult(operationId, false, false, ledgerStatus, Map.of());
    }

    static SettlementResult skipped(String operationId, String reason) {
        return new SettlementResult(operationId, true, false, reason, Map.of());
    }

    static SettlementResult applied(String operationId, Map<String, LadderStanding> standings) {
        return new SettlementResult(operationId, true, true, "applied", standings);
    }
}
