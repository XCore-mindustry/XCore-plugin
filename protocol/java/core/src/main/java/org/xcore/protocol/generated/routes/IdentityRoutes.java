package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.identity.IdentityMessages;

public final class IdentityRoutes {
    private IdentityRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.IdentityRoutes instances");
    }

    public record MessageKey(String messageType, int messageVersion) {}

    public record RouteResponseDescriptor(
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String stream,
            Map<String, String> bindings
    ) {}

    public record RouteDescriptor(
            String family,
            String methodName,
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String kind,
            String stream,
            Map<String, String> bindings,
            String targetScope,
            int ttlMs,
            boolean replayable,
            boolean idempotentConsumerRecommended,
            String owner,
            RouteResponseDescriptor response
    ) {}

    public static final RouteDescriptor PLAYER_JOIN_LEAVE_V1 = new RouteDescriptor(
            "identity",
            "playerJoinLeaveV1Route",
            "player.join-leave",
            1,
            IdentityMessages.PlayerJoinLeaveV1.class,
            "event",
            "xcore:evt:player:joinleave",
            Map.of(),
            "broadcast",
            60000,
            true,
            false,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "identity",
            "playerCustomNicknameChangedCommandV1Route",
            "player.custom-nickname.changed.command",
            1,
            IdentityMessages.PlayerCustomNicknameChangedCommandV1.class,
            "command",
            "xcore:cmd:player-custom-nickname:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "identity",
            "playerActiveBadgeChangedCommandV1Route",
            "player.active-badge.changed.command",
            1,
            IdentityMessages.PlayerActiveBadgeChangedCommandV1.class,
            "command",
            "xcore:cmd:player-active-badge:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "identity",
            "playerBadgeInventoryChangedCommandV1Route",
            "player.badge-inventory.changed.command",
            1,
            IdentityMessages.PlayerBadgeInventoryChangedCommandV1.class,
            "command",
            "xcore:cmd:player-badge-inventory:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "identity",
            "playerBadgeSymbolColorModeChangedCommandV1Route",
            "player.badge-symbol-color-mode.changed.command",
            1,
            IdentityMessages.PlayerBadgeSymbolColorModeChangedCommandV1.class,
            "command",
            "xcore:cmd:player-badge-symbol-color-mode:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("player.join-leave", 1), PLAYER_JOIN_LEAVE_V1),
            entry(key("player.custom-nickname.changed.command", 1), PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1),
            entry(key("player.active-badge.changed.command", 1), PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1),
            entry(key("player.badge-inventory.changed.command", 1), PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1),
            entry(key("player.badge-symbol-color-mode.changed.command", 1), PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
