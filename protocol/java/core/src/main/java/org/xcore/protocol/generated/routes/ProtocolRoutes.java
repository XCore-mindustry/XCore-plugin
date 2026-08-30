package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.messages.chat.ChatMessages;
import org.xcore.protocol.generated.messages.discord.DiscordMessages;
import org.xcore.protocol.generated.messages.maps.MapsMessages;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages;
import org.xcore.protocol.generated.messages.sentinel.SentinelMessages;

public final class ProtocolRoutes {
    private ProtocolRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.ProtocolRoutes instances");
    }

    public record MessageKey(String messageType, int messageVersion) {}

    public record RouteResponseDescriptor(
            String messageType,
            int messageVersion,
            Class<? extends ProtocolPayload> payloadType,
            String stream,
            Map<String, String> bindings
    ) {}

    public record RouteDescriptor(
            String family,
            String methodName,
            String messageType,
            int messageVersion,
            Class<? extends ProtocolPayload> payloadType,
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

    public static final RouteDescriptor MAPS_LIST_REQUEST_V1 = new RouteDescriptor(
            "maps",
            "mapsListRequestV1Route",
            "maps.list.request",
            1,
            MapsMessages.MapsListRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            10000,
            false,
            false,
            "maps",
            new RouteResponseDescriptor(
                    "maps.list.response",
                    1,
                    MapsMessages.MapsListResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final RouteDescriptor MAPS_REMOVE_REQUEST_V1 = new RouteDescriptor(
            "maps",
            "mapsRemoveRequestV1Route",
            "maps.remove.request",
            1,
            MapsMessages.MapsRemoveRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            10000,
            false,
            true,
            "maps",
            new RouteResponseDescriptor(
                    "maps.remove.response",
                    1,
                    MapsMessages.MapsRemoveResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final RouteDescriptor MAPS_LOAD_COMMAND_V1 = new RouteDescriptor(
            "maps",
            "mapsLoadCommandV1Route",
            "maps.load.command",
            1,
            MapsMessages.MapsLoadCommandV1.class,
            "command",
            "xcore:cmd:maps-load:{server}",
            Map.of("server", "payload.server"),
            "server",
            300000,
            false,
            true,
            "maps",
            null
    );

    public static final RouteDescriptor CHAT_MESSAGE_V1 = new RouteDescriptor(
            "chat",
            "chatMessageV1Route",
            "chat.message",
            1,
            ChatMessages.ChatMessageV1.class,
            "event",
            "xcore:evt:chat:message",
            Map.of(),
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
            Map.of(),
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
            Map.of("server", "payload.server"),
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
            Map.of(),
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
            Map.of(),
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
            Map.of(),
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
            Map.of("server", "payload.server"),
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
            Map.of("server", "payload.server"),
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
            Map.of("server", "payload.server"),
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
            Map.of("server", "payload.server"),
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
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor PLAYER_DATA_CACHE_RELOAD_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "playerDataCacheReloadCommandV1Route",
            "player-data-cache.reload.command",
            1,
            ChatMessages.PlayerDataCacheReloadCommandV1.class,
            "command",
            "xcore:cmd:reload-cache:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor SERVER_COMMAND_EXECUTE_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "serverCommandExecuteCommandV1Route",
            "server-command.execute.command",
            1,
            ChatMessages.ServerCommandExecuteCommandV1.class,
            "command",
            "xcore:cmd:execute-command:broadcast",
            Map.of(),
            "broadcast",
            120000,
            false,
            false,
            "server-runtime",
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
            Map.of(),
            "broadcast",
            60000,
            true,
            false,
            "server-runtime",
            null
    );

    public static final RouteDescriptor DISCORD_LINK_CODE_CREATED_V1 = new RouteDescriptor(
            "discord",
            "discordLinkCodeCreatedV1Route",
            "discord.link-code-created",
            1,
            DiscordMessages.DiscordLinkCodeCreatedV1.class,
            "event",
            "xcore:evt:discord:link-code",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_LINK_CONFIRM_COMMAND_V1 = new RouteDescriptor(
            "discord",
            "discordLinkConfirmCommandV1Route",
            "discord.link.confirm.command",
            1,
            DiscordMessages.DiscordLinkConfirmCommandV1.class,
            "command",
            "xcore:cmd:discord-link-confirm:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_UNLINK_COMMAND_V1 = new RouteDescriptor(
            "discord",
            "discordUnlinkCommandV1Route",
            "discord.unlink.command",
            1,
            DiscordMessages.DiscordUnlinkCommandV1.class,
            "command",
            "xcore:cmd:discord-unlink:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_LINK_STATUS_CHANGED_V1 = new RouteDescriptor(
            "discord",
            "discordLinkStatusChangedV1Route",
            "discord.link.status-changed",
            1,
            DiscordMessages.DiscordLinkStatusChangedV1.class,
            "event",
            "xcore:evt:discord:link-status",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1 = new RouteDescriptor(
            "discord",
            "discordAdminAccessChangedCommandV1Route",
            "discord.admin-access.changed.command",
            1,
            DiscordMessages.DiscordAdminAccessChangedCommandV1.class,
            "command",
            "xcore:cmd:discord-admin-access:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "discord-admin-access",
            null
    );

    public static final RouteDescriptor MODERATION_BAN_CREATED_V1 = new RouteDescriptor(
            "moderation",
            "moderationBanCreatedV1Route",
            "moderation.ban.created",
            1,
            ModerationMessages.ModerationBanCreatedV1.class,
            "event",
            "xcore:evt:moderation:ban",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_MUTE_CREATED_V1 = new RouteDescriptor(
            "moderation",
            "moderationMuteCreatedV1Route",
            "moderation.mute.created",
            1,
            ModerationMessages.ModerationMuteCreatedV1.class,
            "event",
            "xcore:evt:moderation:mute",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_VOTE_KICK_CREATED_V1 = new RouteDescriptor(
            "moderation",
            "moderationVoteKickCreatedV1Route",
            "moderation.vote-kick.created",
            1,
            ModerationMessages.ModerationVoteKickCreatedV1.class,
            "event",
            "xcore:evt:moderation:votekick",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_KICK_BANNED_COMMAND_V1 = new RouteDescriptor(
            "moderation",
            "moderationKickBannedCommandV1Route",
            "moderation.kick-banned.command",
            1,
            ModerationMessages.ModerationKickBannedCommandV1.class,
            "command",
            "xcore:cmd:kick-banned:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_PARDON_COMMAND_V1 = new RouteDescriptor(
            "moderation",
            "moderationPardonCommandV1Route",
            "moderation.pardon.command",
            1,
            ModerationMessages.ModerationPardonCommandV1.class,
            "command",
            "xcore:cmd:pardon-player:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_AUDIT_APPENDED_V1 = new RouteDescriptor(
            "moderation",
            "moderationAuditAppendedV1Route",
            "moderation.audit.appended",
            1,
            ModerationMessages.ModerationAuditAppendedV1.class,
            "event",
            "xcore:evt:moderation:audit",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor SENTINEL_SUBNET_RULES_INVALIDATED_V1 = new RouteDescriptor(
            "sentinel",
            "sentinelSubnetRulesInvalidatedV1Route",
            "sentinel.subnet-rules.invalidated",
            1,
            SentinelMessages.SentinelSubnetRulesInvalidatedV1.class,
            "event",
            "xcore:evt:sentinel:subnet:invalidated",
            Map.of(),
            "broadcast",
            60000,
            true,
            true,
            "sentinel",
            null
    );

    public static final RouteDescriptor SENTINEL_SUBNET_SWEEP_COMMAND_V1 = new RouteDescriptor(
            "sentinel",
            "sentinelSubnetSweepCommandV1Route",
            "sentinel.subnet-sweep.command",
            1,
            SentinelMessages.SentinelSubnetSweepCommandV1.class,
            "command",
            "xcore:cmd:sentinel:subnet:sweep:broadcast",
            Map.of(),
            "broadcast",
            60000,
            false,
            true,
            "sentinel",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("maps.list.request", 1), MAPS_LIST_REQUEST_V1),
            entry(key("maps.remove.request", 1), MAPS_REMOVE_REQUEST_V1),
            entry(key("maps.load.command", 1), MAPS_LOAD_COMMAND_V1),
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
            entry(key("player-data-cache.reload.command", 1), PLAYER_DATA_CACHE_RELOAD_COMMAND_V1),
            entry(key("server-command.execute.command", 1), SERVER_COMMAND_EXECUTE_COMMAND_V1),
            entry(key("server.heartbeat", 1), SERVER_HEARTBEAT_V1),
            entry(key("discord.link-code-created", 1), DISCORD_LINK_CODE_CREATED_V1),
            entry(key("discord.link.confirm.command", 1), DISCORD_LINK_CONFIRM_COMMAND_V1),
            entry(key("discord.unlink.command", 1), DISCORD_UNLINK_COMMAND_V1),
            entry(key("discord.link.status-changed", 1), DISCORD_LINK_STATUS_CHANGED_V1),
            entry(key("discord.admin-access.changed.command", 1), DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1),
            entry(key("moderation.ban.created", 1), MODERATION_BAN_CREATED_V1),
            entry(key("moderation.mute.created", 1), MODERATION_MUTE_CREATED_V1),
            entry(key("moderation.vote-kick.created", 1), MODERATION_VOTE_KICK_CREATED_V1),
            entry(key("moderation.kick-banned.command", 1), MODERATION_KICK_BANNED_COMMAND_V1),
            entry(key("moderation.pardon.command", 1), MODERATION_PARDON_COMMAND_V1),
            entry(key("moderation.audit.appended", 1), MODERATION_AUDIT_APPENDED_V1),
            entry(key("sentinel.subnet-rules.invalidated", 1), SENTINEL_SUBNET_RULES_INVALIDATED_V1),
            entry(key("sentinel.subnet-sweep.command", 1), SENTINEL_SUBNET_SWEEP_COMMAND_V1)
    );

    @SuppressWarnings("unchecked")
    public static final Map<Class<? extends ProtocolPayload>, RouteDescriptor> ROUTES_BY_PAYLOAD_TYPE = Map.ofEntries(
            entry((Class<? extends ProtocolPayload>) MapsMessages.MapsListRequestV1.class, MAPS_LIST_REQUEST_V1),
            entry((Class<? extends ProtocolPayload>) MapsMessages.MapsRemoveRequestV1.class, MAPS_REMOVE_REQUEST_V1),
            entry((Class<? extends ProtocolPayload>) MapsMessages.MapsLoadCommandV1.class, MAPS_LOAD_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.ChatMessageV1.class, CHAT_MESSAGE_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.ChatGlobalV1.class, CHAT_GLOBAL_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.ChatDiscordIngressCommandV1.class, CHAT_DISCORD_INGRESS_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.ChatPrivateV1.class, CHAT_PRIVATE_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.ServerActionV1.class, SERVER_ACTION_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.PlayerJoinLeaveV1.class, PLAYER_JOIN_LEAVE_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.PlayerCustomNicknameChangedCommandV1.class, PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.PlayerActiveBadgeChangedCommandV1.class, PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.PlayerBadgeInventoryChangedCommandV1.class, PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.PlayerBadgeSymbolColorModeChangedCommandV1.class, PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.PlayerPasswordResetCommandV1.class, PLAYER_PASSWORD_RESET_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.PlayerDataCacheReloadCommandV1.class, PLAYER_DATA_CACHE_RELOAD_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.ServerCommandExecuteCommandV1.class, SERVER_COMMAND_EXECUTE_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ChatMessages.ServerHeartbeatV1.class, SERVER_HEARTBEAT_V1),
            entry((Class<? extends ProtocolPayload>) DiscordMessages.DiscordLinkCodeCreatedV1.class, DISCORD_LINK_CODE_CREATED_V1),
            entry((Class<? extends ProtocolPayload>) DiscordMessages.DiscordLinkConfirmCommandV1.class, DISCORD_LINK_CONFIRM_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) DiscordMessages.DiscordUnlinkCommandV1.class, DISCORD_UNLINK_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) DiscordMessages.DiscordLinkStatusChangedV1.class, DISCORD_LINK_STATUS_CHANGED_V1),
            entry((Class<? extends ProtocolPayload>) DiscordMessages.DiscordAdminAccessChangedCommandV1.class, DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ModerationMessages.ModerationBanCreatedV1.class, MODERATION_BAN_CREATED_V1),
            entry((Class<? extends ProtocolPayload>) ModerationMessages.ModerationMuteCreatedV1.class, MODERATION_MUTE_CREATED_V1),
            entry((Class<? extends ProtocolPayload>) ModerationMessages.ModerationVoteKickCreatedV1.class, MODERATION_VOTE_KICK_CREATED_V1),
            entry((Class<? extends ProtocolPayload>) ModerationMessages.ModerationKickBannedCommandV1.class, MODERATION_KICK_BANNED_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ModerationMessages.ModerationPardonCommandV1.class, MODERATION_PARDON_COMMAND_V1),
            entry((Class<? extends ProtocolPayload>) ModerationMessages.ModerationAuditAppendedV1.class, MODERATION_AUDIT_APPENDED_V1),
            entry((Class<? extends ProtocolPayload>) SentinelMessages.SentinelSubnetRulesInvalidatedV1.class, SENTINEL_SUBNET_RULES_INVALIDATED_V1),
            entry((Class<? extends ProtocolPayload>) SentinelMessages.SentinelSubnetSweepCommandV1.class, SENTINEL_SUBNET_SWEEP_COMMAND_V1)
    );

    public static RouteDescriptor routeFor(String messageType, int messageVersion) {
        return ROUTES_BY_MESSAGE.get(new MessageKey(messageType, messageVersion));
    }

    @SuppressWarnings("unchecked")
    public static RouteDescriptor routeFor(ProtocolPayload payload) {
        return ROUTES_BY_PAYLOAD_TYPE.get((Class<? extends ProtocolPayload>) payload.getClass());
    }

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
