package org.xcore.plugin.model;

import java.util.Optional;

public class AuditAppendResult {
    private final boolean success;
    private final String message;
    private final AuditRecord record;

    private AuditAppendResult(boolean success, String message, AuditRecord record) {
        this.success = success;
        this.message = message;
        this.record = record;
    }

    public static AuditAppendResult success(AuditRecord record) {
        return new AuditAppendResult(true, null, record);
    }

    public static AuditAppendResult failure(String message) {
        return new AuditAppendResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    public Optional<AuditRecord> getRecord() {
        return Optional.ofNullable(record);
    }
}
