package org.xcore.protocol.generated.messages.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public final class ChatMessages {
    private ChatMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.chat.ChatMessages instances");
    }

    public record ChatDiscordIngressCommandV1(
            String authorName,
            String message,
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "chat.discord-ingress.command";
    public static final int MESSAGE_VERSION = 1;

        public ChatDiscordIngressCommandV1 {
            Objects.requireNonNull(authorName, "authorName must not be null");
            if (authorName.length() < 1) {
                throw new IllegalArgumentException("authorName must be at least 1 characters");
            }
            Objects.requireNonNull(message, "message must not be null");
            if (message.length() < 1) {
                throw new IllegalArgumentException("message must be at least 1 characters");
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
            payload.put("authorName", authorName);
            payload.put("message", message);
            payload.put("server", server);
            return payload;
        }
    }

    public record ChatGlobalV1(
            String authorName,
            String message,
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "chat.global";
    public static final int MESSAGE_VERSION = 1;

        public ChatGlobalV1 {
            Objects.requireNonNull(authorName, "authorName must not be null");
            if (authorName.length() < 1) {
                throw new IllegalArgumentException("authorName must be at least 1 characters");
            }
            Objects.requireNonNull(message, "message must not be null");
            if (message.length() < 1) {
                throw new IllegalArgumentException("message must be at least 1 characters");
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
            payload.put("authorName", authorName);
            payload.put("message", message);
            payload.put("server", server);
            return payload;
        }
    }

    public record ChatMessageV1(
            String authorName,
            String message,
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "chat.message";
    public static final int MESSAGE_VERSION = 1;

        public ChatMessageV1 {
            Objects.requireNonNull(authorName, "authorName must not be null");
            if (authorName.length() < 1) {
                throw new IllegalArgumentException("authorName must be at least 1 characters");
            }
            Objects.requireNonNull(message, "message must not be null");
            if (message.length() < 1) {
                throw new IllegalArgumentException("message must be at least 1 characters");
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
            payload.put("authorName", authorName);
            payload.put("message", message);
            payload.put("server", server);
            return payload;
        }
    }

    public record ChatPrivateV1(
            String fromUuid,
            int fromPid,
            String fromName,
            String toUuid,
            int toPid,
            String message,
            String server
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "chat.private";
    public static final int MESSAGE_VERSION = 1;

        public ChatPrivateV1 {
            Objects.requireNonNull(fromUuid, "fromUuid must not be null");
            if (fromUuid.length() < 1) {
                throw new IllegalArgumentException("fromUuid must be at least 1 characters");
            }
            if (fromPid < 0) {
                throw new IllegalArgumentException("fromPid must be >= 0");
            }
            Objects.requireNonNull(fromName, "fromName must not be null");
            if (fromName.length() < 1) {
                throw new IllegalArgumentException("fromName must be at least 1 characters");
            }
            Objects.requireNonNull(toUuid, "toUuid must not be null");
            if (toUuid.length() < 1) {
                throw new IllegalArgumentException("toUuid must be at least 1 characters");
            }
            if (toPid < 0) {
                throw new IllegalArgumentException("toPid must be >= 0");
            }
            Objects.requireNonNull(message, "message must not be null");
            if (message.length() < 1) {
                throw new IllegalArgumentException("message must be at least 1 characters");
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
            payload.put("fromUuid", fromUuid);
            payload.put("fromPid", fromPid);
            payload.put("fromName", fromName);
            payload.put("toUuid", toUuid);
            payload.put("toPid", toPid);
            payload.put("message", message);
            payload.put("server", server);
            return payload;
        }
    }
}
