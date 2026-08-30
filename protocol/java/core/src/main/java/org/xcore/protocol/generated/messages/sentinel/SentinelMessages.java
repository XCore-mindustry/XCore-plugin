package org.xcore.protocol.generated.messages.sentinel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public final class SentinelMessages {
    private SentinelMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.sentinel.SentinelMessages instances");
    }

    public record SentinelSubnetRulesInvalidatedV1(
            String reason,
            String sourceServer,
            String occurredAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-rules.invalidated";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetRulesInvalidatedV1 {
            if (reason != null) {
                Objects.requireNonNull(reason, "reason must not be null");
                if (reason.length() < 1) {
                    throw new IllegalArgumentException("reason must be at least 1 characters");
                }
            }
            if (sourceServer != null) {
                Objects.requireNonNull(sourceServer, "sourceServer must not be null");
                if (sourceServer.length() < 1) {
                    throw new IllegalArgumentException("sourceServer must be at least 1 characters");
                }
            }
            if (occurredAt != null) {
                Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            if (reason != null) {
                payload.put("reason", reason);
            }
            if (sourceServer != null) {
                payload.put("sourceServer", sourceServer);
            }
            if (occurredAt != null) {
                payload.put("occurredAt", occurredAt);
            }
            return payload;
        }
    }

    public record SentinelSubnetSweepCommandV1(
            String actor,
            String reason,
            String occurredAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-sweep.command";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetSweepCommandV1 {
            if (actor != null) {
                Objects.requireNonNull(actor, "actor must not be null");
                if (actor.length() < 1) {
                    throw new IllegalArgumentException("actor must be at least 1 characters");
                }
            }
            if (reason != null) {
                Objects.requireNonNull(reason, "reason must not be null");
                if (reason.length() < 1) {
                    throw new IllegalArgumentException("reason must be at least 1 characters");
                }
            }
            if (occurredAt != null) {
                Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            if (actor != null) {
                payload.put("actor", actor);
            }
            if (reason != null) {
                payload.put("reason", reason);
            }
            if (occurredAt != null) {
                payload.put("occurredAt", occurredAt);
            }
            return payload;
        }
    }
}
