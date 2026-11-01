package org.xcore.protocol.generated.messages.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public final class IdentityMessages {
    private IdentityMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.identity.IdentityMessages instances");
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
}
