package org.xcore.plugin.integration.idempotency;

import java.util.Optional;

/** Persistent operation ledger for integrations that must safely retry work. */
public interface PluginIdempotencyLedger {
    LedgerClaim claim(String operationId, String operationType, String resultHash);
    Optional<LedgerEntry> find(String operationId);
    boolean markCompleted(String operationId, String resultHash);
    boolean markSkipped(String operationId, String reason);
}
