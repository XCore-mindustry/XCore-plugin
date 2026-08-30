package org.xcore.plugin.integration.idempotency;

public record LedgerEntry(
        String operationId,
        String operationType,
        String status,
        String resultHash,
        long createdAt,
        long updatedAt,
        String reason
) {}
