package org.xcore.plugin.model;

import java.time.Instant;

public record AuditRecordSummary(
        String auditId,
        AuditAction action,
        String targetName,
        String actorName,
        String reason,
        Long durationMs,
        Instant expiresAt,
        long createdAtEpochMs
) {
}
