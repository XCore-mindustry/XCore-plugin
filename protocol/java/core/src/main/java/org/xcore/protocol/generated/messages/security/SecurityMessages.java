package org.xcore.protocol.generated.messages.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public final class SecurityMessages {
    private SecurityMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.security.SecurityMessages instances");
    }

    public record PlayerPasswordResetCommandV1(
            String playerUuid,
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "player.password-reset.command";
    public static final int MESSAGE_VERSION = 1;

        public PlayerPasswordResetCommandV1 {
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("playerUuid", playerUuid);
            payload.put("server", server);
            return payload;
        }
    }

    public record SecurityPermissionsChangedV1(
            String playerUuid,
            int revision,
            String sourceServer,
            String occurredAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "security.permissions.changed";
    public static final int MESSAGE_VERSION = 1;

        public SecurityPermissionsChangedV1 {
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            if (revision < 0) {
                throw new IllegalArgumentException("revision must be >= 0");
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
            payload.put("playerUuid", playerUuid);
            payload.put("revision", revision);
            if (sourceServer != null) {
                payload.put("sourceServer", sourceServer);
            }
            if (occurredAt != null) {
                payload.put("occurredAt", occurredAt);
            }
            return payload;
        }
    }

    public record SecurityStaffResetPasswordRequestV1(
            String server,
            String operationId,
            String playerUuid
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "security.staff.reset-password.request";
    public static final int MESSAGE_VERSION = 1;

        public SecurityStaffResetPasswordRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(operationId, "operationId must not be null");
            if (operationId.length() < 1) {
                throw new IllegalArgumentException("operationId must be at least 1 characters");
            }
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("operationId", operationId);
            payload.put("playerUuid", playerUuid);
            return payload;
        }
    }

    public record SecurityStaffResetPasswordResponseV1(
            String server,
            String operationId,
            boolean changed
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "security.staff.reset-password.response";
    public static final int MESSAGE_VERSION = 1;

        public SecurityStaffResetPasswordResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(operationId, "operationId must not be null");
            if (operationId.length() < 1) {
                throw new IllegalArgumentException("operationId must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("operationId", operationId);
            payload.put("changed", changed);
            return payload;
        }
    }

    public record SecurityStaffSyncRequestV1(
            String server,
            String operationId,
            String playerUuid,
            String discordId,
            List<String> roleIds,
            boolean complete
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "security.staff.sync.request";
    public static final int MESSAGE_VERSION = 1;

        public SecurityStaffSyncRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(operationId, "operationId must not be null");
            if (operationId.length() < 1) {
                throw new IllegalArgumentException("operationId must be at least 1 characters");
            }
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            Objects.requireNonNull(discordId, "discordId must not be null");
            if (discordId.length() < 1) {
                throw new IllegalArgumentException("discordId must be at least 1 characters");
            }
            roleIds = Objects.requireNonNull(roleIds, "roleIds must not be null");
            roleIds = List.copyOf(roleIds);
            for (String item : roleIds) {
                Objects.requireNonNull(item, "roleIds[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("operationId", operationId);
            payload.put("playerUuid", playerUuid);
            payload.put("discordId", discordId);
            payload.put("roleIds", List.copyOf(roleIds));
            payload.put("complete", complete);
            return payload;
        }
    }

    public record SecurityStaffSyncResponseV1(
            String server,
            String operationId,
            int revision,
            boolean changed
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "security.staff.sync.response";
    public static final int MESSAGE_VERSION = 1;

        public SecurityStaffSyncResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(operationId, "operationId must not be null");
            if (operationId.length() < 1) {
                throw new IllegalArgumentException("operationId must be at least 1 characters");
            }
            if (revision < 0) {
                throw new IllegalArgumentException("revision must be >= 0");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("operationId", operationId);
            payload.put("revision", revision);
            payload.put("changed", changed);
            return payload;
        }
    }
}
