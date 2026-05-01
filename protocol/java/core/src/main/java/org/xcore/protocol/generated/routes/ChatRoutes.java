package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.chat.ChatMessages;

public final class ChatRoutes {
    private ChatRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.ChatRoutes instances");
    }

    public record MessageKey(String messageType, int messageVersion) {}

    public record RouteResponseDescriptor(
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String stream
    ) {}

    public record RouteDescriptor(
            String family,
            String methodName,
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String kind,
            String stream,
            String targetScope,
            int ttlMs,
            boolean replayable,
            boolean idempotentConsumerRecommended,
            String owner,
            RouteResponseDescriptor response
    ) {}

    public static final RouteDescriptor CHAT_MESSAGE_V1 = new RouteDescriptor(
            "chat",
            "chatMessageV1Route",
            "chat.message",
            1,
            ChatMessages.ChatMessageV1.class,
            "event",
            "xcore:evt:chat:message",
            "broadcast",
            60000,
            true,
            false,
            "chat",
            null
    );

    public static final RouteDescriptor CHAT_GLOBAL_V1 = new RouteDescriptor(
            "chat",
            "chatGlobalV1Route",
            "chat.global",
            1,
            ChatMessages.ChatGlobalV1.class,
            "event",
            "xcore:evt:chat:global",
            "broadcast",
            60000,
            true,
            false,
            "chat",
            null
    );

    public static final RouteDescriptor CHAT_DISCORD_INGRESS_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "chatDiscordIngressCommandV1Route",
            "chat.discord-ingress.command",
            1,
            ChatMessages.ChatDiscordIngressCommandV1.class,
            "command",
            "xcore:cmd:discord-message:{server}",
            "server",
            60000,
            false,
            true,
            "chat",
            null
    );

    public static final RouteDescriptor CHAT_PRIVATE_V1 = new RouteDescriptor(
            "chat",
            "chatPrivateV1Route",
            "chat.private",
            1,
            ChatMessages.ChatPrivateV1.class,
            "event",
            "xcore:evt:chat:private",
            "broadcast",
            60000,
            true,
            false,
            "chat",
            null
    );

    public static final RouteDescriptor SERVER_ACTION_V1 = new RouteDescriptor(
            "chat",
            "serverActionV1Route",
            "server.action",
            1,
            ChatMessages.ServerActionV1.class,
            "event",
            "xcore:evt:server:action",
            "broadcast",
            60000,
            true,
            false,
            "server-runtime",
            null
    );

    public static final RouteDescriptor PLAYER_JOIN_LEAVE_V1 = new RouteDescriptor(
            "chat",
            "playerJoinLeaveV1Route",
            "player.join-leave",
            1,
            ChatMessages.PlayerJoinLeaveV1.class,
            "event",
            "xcore:evt:player:joinleave",
            "broadcast",
            60000,
            true,
            false,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "playerCustomNicknameChangedCommandV1Route",
            "player.custom-nickname.changed.command",
            1,
            ChatMessages.PlayerCustomNicknameChangedCommandV1.class,
            "command",
            "xcore:cmd:player-custom-nickname:{server}",
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "playerActiveBadgeChangedCommandV1Route",
            "player.active-badge.changed.command",
            1,
            ChatMessages.PlayerActiveBadgeChangedCommandV1.class,
            "command",
            "xcore:cmd:player-active-badge:{server}",
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "playerBadgeInventoryChangedCommandV1Route",
            "player.badge-inventory.changed.command",
            1,
            ChatMessages.PlayerBadgeInventoryChangedCommandV1.class,
            "command",
            "xcore:cmd:player-badge-inventory:{server}",
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "playerBadgeSymbolColorModeChangedCommandV1Route",
            "player.badge-symbol-color-mode.changed.command",
            1,
            ChatMessages.PlayerBadgeSymbolColorModeChangedCommandV1.class,
            "command",
            "xcore:cmd:player-badge-symbol-color-mode:{server}",
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_PASSWORD_RESET_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "playerPasswordResetCommandV1Route",
            "player.password-reset.command",
            1,
            ChatMessages.PlayerPasswordResetCommandV1.class,
            "command",
            "xcore:cmd:player-password-reset:{server}",
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor SERVER_HEARTBEAT_V1 = new RouteDescriptor(
            "chat",
            "serverHeartbeatV1Route",
            "server.heartbeat",
            1,
            ChatMessages.ServerHeartbeatV1.class,
            "event",
            "xcore:evt:server:heartbeat",
            "broadcast",
            60000,
            true,
            false,
            "server-runtime",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("chat.message", 1), CHAT_MESSAGE_V1),
            entry(key("chat.global", 1), CHAT_GLOBAL_V1),
            entry(key("chat.discord-ingress.command", 1), CHAT_DISCORD_INGRESS_COMMAND_V1),
            entry(key("chat.private", 1), CHAT_PRIVATE_V1),
            entry(key("server.action", 1), SERVER_ACTION_V1),
            entry(key("player.join-leave", 1), PLAYER_JOIN_LEAVE_V1),
            entry(key("player.custom-nickname.changed.command", 1), PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1),
            entry(key("player.active-badge.changed.command", 1), PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1),
            entry(key("player.badge-inventory.changed.command", 1), PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1),
            entry(key("player.badge-symbol-color-mode.changed.command", 1), PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1),
            entry(key("player.password-reset.command", 1), PLAYER_PASSWORD_RESET_COMMAND_V1),
            entry(key("server.heartbeat", 1), SERVER_HEARTBEAT_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
