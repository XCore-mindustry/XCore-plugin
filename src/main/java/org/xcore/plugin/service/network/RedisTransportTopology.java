package org.xcore.plugin.service.network;

import org.xcore.plugin.event.TransportEvents;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkCodeCreatedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkConfirmCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationAuditAppendedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationPardonCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationVoteKickCreatedV1;

import java.util.List;
import java.util.Map;

/**
 * Explicit topology model for the current Redis transport routes.
 *
 * <p>The goal is not to replace the router yet. This model documents the compatibility surface and gives
 * follow-up refactor steps a shared, typed source of truth for route metadata.</p>
 */
public final class RedisTransportTopology {
    public enum DeliveryMode {
        EVENT,
        COMMAND,
        RPC_REQUEST
    }

    public enum ServerScope {
        BROADCAST,
        DEFAULT_SERVER,
        PAYLOAD_SERVER
    }

    public record RouteSpec(
            Class<?> payloadType,
            String streamPattern,
            String eventType,
            long ttlMillis,
            DeliveryMode deliveryMode,
            ServerScope serverScope,
            boolean readOnly,
            boolean rpcRequest,
            Class<?> responseType
    ) {
    }

    public static final List<RouteSpec> ROUTES = List.of(
            route(TransportEvents.MessageEvent.class, "xcore:evt:chat:message", "chat.message", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.ServerActionEvent.class, "xcore:evt:server:action", "server.action", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.PlayerJoinLeaveEvent.class, "xcore:evt:player:joinleave", "player.join_leave", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.GlobalChatEvent.class, "xcore:evt:chat:global", "chat.global", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.DiscordMessageEvent.class, "xcore:cmd:discord-message:{server}", "chat.discord_ingress", 60_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, true),
            route(TransportEvents.PrivateMessageEvent.class, "xcore:evt:chat:private", "chat.private", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(ModerationBanCreatedV1.class, "xcore:evt:moderation:ban", "moderation.ban.created", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(ModerationMuteCreatedV1.class, "xcore:evt:moderation:mute", "moderation.mute.created", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(ModerationVoteKickCreatedV1.class, "xcore:evt:moderation:votekick", "moderation.vote-kick.created", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(ModerationAuditAppendedV1.class, "xcore:evt:moderation:audit", "moderation.audit.appended", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(ModerationKickBannedCommandV1.class, "xcore:cmd:kick-banned:{server}", "moderation.kick-banned.command", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(TransportEvents.PlayerCustomNicknameChanged.class, "xcore:cmd:player-custom-nickname:{server}", "player.custom_nickname", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerActiveBadgeChanged.class, "xcore:cmd:player-active-badge:{server}", "player.active_badge", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerBadgeSymbolColorModeChanged.class, "xcore:cmd:player-badge-symbol-color-mode:{server}", "player.badge_symbol_color_mode", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerBadgeInventoryChanged.class, "xcore:cmd:player-badge-inventory:{server}", "player.badge_inventory", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerPasswordReset.class, "xcore:cmd:player-password-reset:{server}", "player.password_reset", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(DiscordLinkCodeCreatedV1.class, "xcore:evt:discord:link-code", "discord.link-code-created", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(DiscordLinkConfirmCommandV1.class, "xcore:cmd:discord-link-confirm:{server}", "discord.link.confirm.command", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(DiscordUnlinkCommandV1.class, "xcore:cmd:discord-unlink:{server}", "discord.unlink.command", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(DiscordLinkStatusChangedV1.class, "xcore:evt:discord:link-status", "discord.link.status-changed", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(DiscordAdminAccessChangedCommandV1.class, "xcore:cmd:discord-admin-access:{server}", "discord.admin-access.changed.command", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(TransportEvents.ReloadPlayerDataCache.class, "xcore:cmd:reload-cache:{server}", "cache.reload_player_data", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.LoadMapsV2.class, "xcore:cmd:maps-load:{server}", "maps.load", 300_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(TransportEvents.ExecuteCommand.class, "xcore:cmd:execute-command:broadcast", "server.execute_command", 120_000L, DeliveryMode.COMMAND, ServerScope.BROADCAST, false),
            route(ModerationPardonCommandV1.class, "xcore:cmd:pardon-player:{server}", "moderation.pardon.command", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            rpcRoute(MapsListRequestV1.class, "xcore:rpc:req:{server}", "maps.list.request", 10_000L, ServerScope.PAYLOAD_SERVER, MapsListResponseV1.class),
            rpcRoute(TransportEvents.MapRemoveRequest.class, "xcore:rpc:req:{server}", "maps.remove", 10_000L, ServerScope.PAYLOAD_SERVER, TransportEvents.MapRemoveResponse.class)
    );

    public static final Map<Class<?>, RouteSpec> ROUTES_BY_TYPE = ROUTES.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(RouteSpec::payloadType, spec -> spec));

    private RedisTransportTopology() {
    }

    public static RouteSpec routeFor(Class<?> payloadType) {
        return ROUTES_BY_TYPE.get(payloadType);
    }

    public static boolean isCompatibleStreamPattern(String streamKey) {
        return RedisTransportContracts.isStableExternalStream(streamKey);
    }

    private static RouteSpec route(Class<?> payloadType,
                                   String streamPattern,
                                   String eventType,
                                   long ttlMillis,
                                   DeliveryMode deliveryMode,
                                   ServerScope serverScope,
                                   boolean readOnly) {
        return new RouteSpec(payloadType, streamPattern, eventType, ttlMillis, deliveryMode, serverScope, readOnly, false, null);
    }

    private static RouteSpec rpcRoute(Class<?> payloadType,
                                      String streamPattern,
                                      String eventType,
                                      long ttlMillis,
                                      ServerScope serverScope,
                                      Class<?> responseType) {
        return new RouteSpec(payloadType, streamPattern, eventType, ttlMillis, DeliveryMode.RPC_REQUEST, serverScope, false, true, responseType);
    }
}
