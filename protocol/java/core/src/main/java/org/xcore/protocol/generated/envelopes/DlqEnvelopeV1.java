package org.xcore.protocol.generated.envelopes;

import java.util.Objects;

public record DlqEnvelopeV1(
        String message_type,
        int message_version,
        String message_id,
        String correlation_id,
        String causation_id,
        String producer,
        String target,
        String created_at,
        String expires_at,
        String schema_ref,
        String payload_json,
        String source_stream,
        String source_group,
        String source_id,
        String failed_at,
        String attempts,
        String failure_reason
) {
    public static final String SCHEMA_VERSION = "1";
    public static final String MESSAGE_KIND = "dlq";
    public static final String CONTENT_TYPE = "application/json";

    public DlqEnvelopeV1 {
        Objects.requireNonNull(message_type, "message_type must not be null");
        if (message_type.length() < 1) {
            throw new IllegalArgumentException("message_type must be at least 1 characters");
        }
        if (message_version < 1) {
            throw new IllegalArgumentException("message_version must be >= 1");
        }
        Objects.requireNonNull(message_id, "message_id must not be null");
        if (message_id.length() < 1) {
            throw new IllegalArgumentException("message_id must be at least 1 characters");
        }
        if (correlation_id != null) {
            Objects.requireNonNull(correlation_id, "correlation_id must not be null");
            if (correlation_id.length() < 1) {
                throw new IllegalArgumentException("correlation_id must be at least 1 characters");
            }
        }
        if (causation_id != null) {
            Objects.requireNonNull(causation_id, "causation_id must not be null");
            if (causation_id.length() < 1) {
                throw new IllegalArgumentException("causation_id must be at least 1 characters");
            }
        }
        Objects.requireNonNull(producer, "producer must not be null");
        if (producer.length() < 1) {
            throw new IllegalArgumentException("producer must be at least 1 characters");
        }
        if (target != null) {
            Objects.requireNonNull(target, "target must not be null");
            if (target.length() < 1) {
                throw new IllegalArgumentException("target must be at least 1 characters");
            }
        }
        Objects.requireNonNull(created_at, "created_at must not be null");
        if (expires_at != null) {
            Objects.requireNonNull(expires_at, "expires_at must not be null");
        }
        if (schema_ref != null) {
            Objects.requireNonNull(schema_ref, "schema_ref must not be null");
            if (schema_ref.length() < 1) {
                throw new IllegalArgumentException("schema_ref must be at least 1 characters");
            }
        }
        Objects.requireNonNull(payload_json, "payload_json must not be null");
        if (payload_json.length() < 2) {
            throw new IllegalArgumentException("payload_json must be at least 2 characters");
        }
        Objects.requireNonNull(source_stream, "source_stream must not be null");
        if (source_stream.length() < 1) {
            throw new IllegalArgumentException("source_stream must be at least 1 characters");
        }
        Objects.requireNonNull(source_group, "source_group must not be null");
        if (source_group.length() < 1) {
            throw new IllegalArgumentException("source_group must be at least 1 characters");
        }
        Objects.requireNonNull(source_id, "source_id must not be null");
        if (source_id.length() < 1) {
            throw new IllegalArgumentException("source_id must be at least 1 characters");
        }
        Objects.requireNonNull(failed_at, "failed_at must not be null");
        Objects.requireNonNull(attempts, "attempts must not be null");
        Objects.requireNonNull(failure_reason, "failure_reason must not be null");
        if (failure_reason.length() < 1) {
            throw new IllegalArgumentException("failure_reason must be at least 1 characters");
        }
    }
}
