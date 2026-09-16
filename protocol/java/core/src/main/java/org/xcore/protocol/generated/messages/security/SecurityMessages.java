package org.xcore.protocol.generated.messages.security;

import java.util.LinkedHashMap;
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
}
