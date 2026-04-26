package org.xcore.protocol.generated.messages.chat;

import java.util.Objects;

public final class ChatMessages {
    private ChatMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.chat.ChatMessages instances");
    }

    public record ChatDiscordIngressCommandV1(
            String authorName,
            String message,
            String server
    ) {
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
    }

    public record ChatGlobalV1(
            String authorName,
            String message,
            String server
    ) {
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
    }

    public record ChatMessageV1(
            String authorName,
            String message,
            String server
    ) {
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
    }

    public record PlayerJoinLeaveV1(
            String playerName,
            String server,
            boolean joined
    ) {
        public static final String MESSAGE_TYPE = "player.join-leave";
        public static final int MESSAGE_VERSION = 1;

        public PlayerJoinLeaveV1 {
            Objects.requireNonNull(playerName, "playerName must not be null");
            if (playerName.length() < 1) {
                throw new IllegalArgumentException("playerName must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
        }
    }

    public record ServerActionV1(
            String message,
            String server
    ) {
        public static final String MESSAGE_TYPE = "server.action";
        public static final int MESSAGE_VERSION = 1;

        public ServerActionV1 {
            Objects.requireNonNull(message, "message must not be null");
            if (message.length() < 1) {
                throw new IllegalArgumentException("message must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
        }
    }

    public record ServerHeartbeatV1(
            String serverName,
            int discordChannelId,
            int players,
            int maxPlayers,
            String version,
            String host,
            Integer port
    ) {
        public static final String MESSAGE_TYPE = "server.heartbeat";
        public static final int MESSAGE_VERSION = 1;

        public ServerHeartbeatV1 {
            Objects.requireNonNull(serverName, "serverName must not be null");
            if (serverName.length() < 1) {
                throw new IllegalArgumentException("serverName must be at least 1 characters");
            }
            if (discordChannelId < 0) {
                throw new IllegalArgumentException("discordChannelId must be >= 0");
            }
            if (players < 0) {
                throw new IllegalArgumentException("players must be >= 0");
            }
            if (maxPlayers < 0) {
                throw new IllegalArgumentException("maxPlayers must be >= 0");
            }
            Objects.requireNonNull(version, "version must not be null");
            if (version.length() < 1) {
                throw new IllegalArgumentException("version must be at least 1 characters");
            }
            if (host != null) {
                Objects.requireNonNull(host, "host must not be null");
                if (host.length() < 1) {
                    throw new IllegalArgumentException("host must be at least 1 characters");
                }
            }
            if (port != null) {
                if (port < 0) {
                    throw new IllegalArgumentException("port must be >= 0");
                }
            }
        }
    }
}
