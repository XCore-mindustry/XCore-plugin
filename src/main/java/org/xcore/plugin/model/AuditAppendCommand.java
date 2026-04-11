package org.xcore.plugin.model;

import lombok.Builder;

public record AuditAppendCommand(
        AuditAction action,
        AuditTarget target,
        AuditActor actor,
        AuditOrigin origin,
        String reason,
        AuditDetails details,
        String relatedAuditId,
        String supersedesAuditId,
        String requestId
) {
    @Builder public AuditAppendCommand {}
}
