package org.xcore.protocol.generated.messages.sentinel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.shared.ActorRefV1;

public final class SentinelMessages {
    private SentinelMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.sentinel.SentinelMessages instances");
    }

    public record SentinelSubnetRulesCheckRequestV1(
            String request,
            String targetServer,
            String ip
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-rules.check.request";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetRulesCheckRequestV1 {
            Objects.requireNonNull(request, "request must not be null");
            if (request.length() < 1) {
                throw new IllegalArgumentException("request must be at least 1 characters");
            }
            Objects.requireNonNull(targetServer, "targetServer must not be null");
            if (targetServer.length() < 1) {
                throw new IllegalArgumentException("targetServer must be at least 1 characters");
            }
            Objects.requireNonNull(ip, "ip must not be null");
            if (ip.length() < 1) {
                throw new IllegalArgumentException("ip must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("request", request);
            payload.put("targetServer", targetServer);
            payload.put("ip", ip);
            return payload;
        }
    }

    public record SentinelSubnetRulesCheckResponseV1(
            String request,
            String targetServer,
            boolean allowed,
            List<String> matchedRules
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-rules.check.response";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetRulesCheckResponseV1 {
            Objects.requireNonNull(request, "request must not be null");
            if (request.length() < 1) {
                throw new IllegalArgumentException("request must be at least 1 characters");
            }
            Objects.requireNonNull(targetServer, "targetServer must not be null");
            if (targetServer.length() < 1) {
                throw new IllegalArgumentException("targetServer must be at least 1 characters");
            }
            matchedRules = Objects.requireNonNull(matchedRules, "matchedRules must not be null");
            matchedRules = List.copyOf(matchedRules);
            for (String item : matchedRules) {
                Objects.requireNonNull(item, "matchedRules[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("request", request);
            payload.put("targetServer", targetServer);
            payload.put("allowed", allowed);
            payload.put("matchedRules", List.copyOf(matchedRules));
            return payload;
        }
    }

    public record SentinelSubnetRulesCommandV1(
            String request,
            String idempotency,
            ActorRefV1 actor,
            SentinelSubnetRulesCommandV1Operation operation,
            String targetServer,
            List<String> rules,
            String source,
            String reason,
            Integer expiresAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-rules.command";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetRulesCommandV1 {
            Objects.requireNonNull(request, "request must not be null");
            if (request.length() < 1) {
                throw new IllegalArgumentException("request must be at least 1 characters");
            }
            Objects.requireNonNull(idempotency, "idempotency must not be null");
            if (idempotency.length() < 1) {
                throw new IllegalArgumentException("idempotency must be at least 1 characters");
            }
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(operation, "operation must not be null");
            if (targetServer != null) {
                Objects.requireNonNull(targetServer, "targetServer must not be null");
                if (targetServer.length() < 1) {
                    throw new IllegalArgumentException("targetServer must be at least 1 characters");
                }
            }
            rules = Objects.requireNonNull(rules, "rules must not be null");
            rules = List.copyOf(rules);
            for (String item : rules) {
                Objects.requireNonNull(item, "rules[] must not be null");
            }
            if (source != null) {
                Objects.requireNonNull(source, "source must not be null");
                if (source.length() < 1) {
                    throw new IllegalArgumentException("source must be at least 1 characters");
                }
            }
            if (reason != null) {
                Objects.requireNonNull(reason, "reason must not be null");
                if (reason.length() < 1) {
                    throw new IllegalArgumentException("reason must be at least 1 characters");
                }
            }
            if (expiresAt != null) {
                if (expiresAt < 0) {
                    throw new IllegalArgumentException("expiresAt must be >= 0");
                }
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("request", request);
            payload.put("idempotency", idempotency);
            payload.put("actor", actor.toPayload());
            if (operation != null) {
                payload.put("operation", operation.toString());
            }
            if (targetServer != null) {
                payload.put("targetServer", targetServer);
            }
            payload.put("rules", List.copyOf(rules));
            if (source != null) {
                payload.put("source", source);
            }
            if (reason != null) {
                payload.put("reason", reason);
            }
            if (expiresAt != null) {
                payload.put("expiresAt", expiresAt);
            }
            return payload;
        }
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

    public record SentinelSubnetRulesListRequestV1(
            String request,
            String targetServer
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-rules.list.request";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetRulesListRequestV1 {
            Objects.requireNonNull(request, "request must not be null");
            if (request.length() < 1) {
                throw new IllegalArgumentException("request must be at least 1 characters");
            }
            Objects.requireNonNull(targetServer, "targetServer must not be null");
            if (targetServer.length() < 1) {
                throw new IllegalArgumentException("targetServer must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("request", request);
            payload.put("targetServer", targetServer);
            return payload;
        }
    }

    public record SentinelSubnetRulesListResponseV1(
            String request,
            String targetServer,
            List<String> rules
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-rules.list.response";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetRulesListResponseV1 {
            Objects.requireNonNull(request, "request must not be null");
            if (request.length() < 1) {
                throw new IllegalArgumentException("request must be at least 1 characters");
            }
            Objects.requireNonNull(targetServer, "targetServer must not be null");
            if (targetServer.length() < 1) {
                throw new IllegalArgumentException("targetServer must be at least 1 characters");
            }
            rules = Objects.requireNonNull(rules, "rules must not be null");
            rules = List.copyOf(rules);
            for (String item : rules) {
                Objects.requireNonNull(item, "rules[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("request", request);
            payload.put("targetServer", targetServer);
            payload.put("rules", List.copyOf(rules));
            return payload;
        }
    }

    public record SentinelSubnetRulesResponseV1(
            String request,
            boolean success,
            String targetServer,
            String error,
            List<String> rules
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "sentinel.subnet-rules.response";
    public static final int MESSAGE_VERSION = 1;

        public SentinelSubnetRulesResponseV1 {
            Objects.requireNonNull(request, "request must not be null");
            if (request.length() < 1) {
                throw new IllegalArgumentException("request must be at least 1 characters");
            }
            if (targetServer != null) {
                Objects.requireNonNull(targetServer, "targetServer must not be null");
                if (targetServer.length() < 1) {
                    throw new IllegalArgumentException("targetServer must be at least 1 characters");
                }
            }
            if (error != null) {
                Objects.requireNonNull(error, "error must not be null");
                if (error.length() < 1) {
                    throw new IllegalArgumentException("error must be at least 1 characters");
                }
            }
            if (rules != null) {
                rules = Objects.requireNonNull(rules, "rules must not be null");
                rules = List.copyOf(rules);
                for (String item : rules) {
                    Objects.requireNonNull(item, "rules[] must not be null");
                }
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("request", request);
            payload.put("success", success);
            if (targetServer != null) {
                payload.put("targetServer", targetServer);
            }
            if (error != null) {
                payload.put("error", error);
            }
            if (rules != null) {
                payload.put("rules", List.copyOf(rules));
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
