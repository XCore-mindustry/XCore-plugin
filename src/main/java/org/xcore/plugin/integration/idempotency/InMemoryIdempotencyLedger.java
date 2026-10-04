package org.xcore.plugin.integration.idempotency;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Non-durable {@link PluginIdempotencyLedger} with the same semantics as the MongoDB one.
 * Meant for tests of integrations built on the ledger, in this plugin and in dependants.
 */
public final class InMemoryIdempotencyLedger implements PluginIdempotencyLedger {
    private final Map<String, LedgerEntry> entries = new HashMap<>();
    private boolean leasesExpired;

    @Override
    public synchronized LedgerClaim claim(String operationId, String operationType, String resultHash) {
        long now = System.currentTimeMillis();
        LedgerEntry existing = entries.get(operationId);
        if (existing == null) {
            LedgerEntry entry = new LedgerEntry(operationId, operationType, "CLAIMED", resultHash, now, now, null);
            entries.put(operationId, entry);
            return new LedgerClaim(true, entry);
        }
        if (!resultHash.equals(existing.resultHash())) {
            throw new IllegalStateException("Operation already exists with a different result hash: " + operationId);
        }
        if (!"CLAIMED".equals(existing.status()) || !leasesExpired) {
            return new LedgerClaim(false, existing);
        }
        LedgerEntry taken = with(existing, "CLAIMED", existing.reason());
        entries.put(operationId, taken);
        return new LedgerClaim(true, taken);
    }

    @Override
    public synchronized Optional<LedgerEntry> find(String operationId) {
        return Optional.ofNullable(entries.get(operationId));
    }

    @Override
    public synchronized boolean markCompleted(String operationId, String resultHash) {
        LedgerEntry entry = entries.get(operationId);
        if (entry == null || !"CLAIMED".equals(entry.status()) || !resultHash.equals(entry.resultHash())) {
            return false;
        }
        entries.put(operationId, with(entry, "COMPLETED", entry.reason()));
        return true;
    }

    @Override
    public synchronized boolean markSkipped(String operationId, String reason) {
        LedgerEntry entry = entries.get(operationId);
        if (entry == null || !"CLAIMED".equals(entry.status())) {
            return false;
        }
        entries.put(operationId, with(entry, "SKIPPED", reason == null ? "" : reason));
        return true;
    }

    /** Lets the next {@link #claim} take over operations that were claimed but never finished. */
    public synchronized void expireLeases() {
        leasesExpired = true;
    }

    private static LedgerEntry with(LedgerEntry entry, String status, String reason) {
        return new LedgerEntry(entry.operationId(), entry.operationType(), status, entry.resultHash(),
                entry.createdAt(), System.currentTimeMillis(), reason);
    }
}
