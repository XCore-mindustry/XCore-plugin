package org.xcore.protocol.generated.messages.chat;

import java.util.LinkedHashMap;
import java.util.List;
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

    public record PlayerDataCacheReloadCommandV1(
            String server
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "player-data-cache.reload.command";
        public static final int MESSAGE_VERSION = 1;

        public PlayerDataCacheReloadCommandV1 {
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
            payload.put("server", server);
            return payload;
        }
    }

    public record PlayerActiveBadgeChangedCommandV1(
            String playerUuid,
            String activeBadge,
            String server
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "player.active-badge.changed.command";
        public static final int MESSAGE_VERSION = 1;

        public PlayerActiveBadgeChangedCommandV1 {
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            Objects.requireNonNull(activeBadge, "activeBadge must not be null");
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
            payload.put("activeBadge", activeBadge);
            payload.put("server", server);
            return payload;
        }
    }

    public record PlayerBadgeInventoryChangedCommandV1(
            String playerUuid,
            String activeBadge,
            List<String> unlockedBadges,
            String server
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "player.badge-inventory.changed.command";
        public static final int MESSAGE_VERSION = 1;

        public PlayerBadgeInventoryChangedCommandV1 {
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            Objects.requireNonNull(activeBadge, "activeBadge must not be null");
            unlockedBadges = Objects.requireNonNull(unlockedBadges, "unlockedBadges must not be null");
            unlockedBadges = List.copyOf(unlockedBadges);
            for (String item : unlockedBadges) {
                Objects.requireNonNull(item, "unlockedBadges[] must not be null");
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
            payload.put("activeBadge", activeBadge);
            payload.put("unlockedBadges", List.copyOf(unlockedBadges));
            payload.put("server", server);
            return payload;
        }
    }

    public record PlayerBadgeSymbolColorModeChangedCommandV1(
            String playerUuid,
            String badgeSymbolColorMode,
            String server
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "player.badge-symbol-color-mode.changed.command";
        public static final int MESSAGE_VERSION = 1;

        public PlayerBadgeSymbolColorModeChangedCommandV1 {
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            Objects.requireNonNull(badgeSymbolColorMode, "badgeSymbolColorMode must not be null");
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
            payload.put("badgeSymbolColorMode", badgeSymbolColorMode);
            payload.put("server", server);
            return payload;
        }
    }

    public record PlayerCustomNicknameChangedCommandV1(
            String playerUuid,
            String customNickname,
            String server
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "player.custom-nickname.changed.command";
        public static final int MESSAGE_VERSION = 1;

        public PlayerCustomNicknameChangedCommandV1 {
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            Objects.requireNonNull(customNickname, "customNickname must not be null");
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
            payload.put("customNickname", customNickname);
            payload.put("server", server);
            return payload;
        }
    }

    public record PlayerJoinLeaveV1(
            String playerName,
            String server,
            boolean joined
    ) implements ProtocolPayload {
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

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("playerName", playerName);
            payload.put("server", server);
            payload.put("joined", joined);
            return payload;
        }
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

    public record ServerCommandExecuteCommandV1(
            String command,
            List<String> targetServers,
            boolean exclusion
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "server-command.execute.command";
        public static final int MESSAGE_VERSION = 1;

        public ServerCommandExecuteCommandV1 {
            Objects.requireNonNull(command, "command must not be null");
            if (command.length() < 1) {
                throw new IllegalArgumentException("command must be at least 1 characters");
            }
            targetServers = Objects.requireNonNull(targetServers, "targetServers must not be null");
            targetServers = List.copyOf(targetServers);
            for (String item : targetServers) {
                Objects.requireNonNull(item, "targetServers[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("command", command);
            payload.put("targetServers", List.copyOf(targetServers));
            payload.put("exclusion", exclusion);
            return payload;
        }
    }

    public record ServerActionV1(
            String message,
            String server
    ) implements ProtocolPayload {
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

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("message", message);
            payload.put("server", server);
            return payload;
        }
    }

    public record ServerHeartbeatV1(
            String serverName,
            long discordChannelId,
            int players,
            int maxPlayers,
            String version,
            String host,
            Integer port
    ) implements ProtocolPayload {
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

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("serverName", serverName);
            payload.put("discordChannelId", discordChannelId);
            payload.put("players", players);
            payload.put("maxPlayers", maxPlayers);
            payload.put("version", version);
            if (host != null) {
                payload.put("host", host);
            }
            if (port != null) {
                payload.put("port", port);
            }
            return payload;
        }
    }
}
