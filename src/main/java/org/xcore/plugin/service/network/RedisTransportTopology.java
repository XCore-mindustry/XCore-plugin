package org.xcore.plugin.service.network;

import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;

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
            Class<? extends TransportEvents.Response> responseType
    ) {
    }

    public static final List<RouteSpec> ROUTES = List.of(
            route(TransportEvents.MessageEvent.class, "xcore:evt:chat:message", "chat.message", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.ServerActionEvent.class, "xcore:evt:server:action", "server.action", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.PlayerJoinLeaveEvent.class, "xcore:evt:player:joinleave", "player.join_leave", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.GlobalChatEvent.class, "xcore:evt:chat:global", "chat.global", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.DiscordMessageEvent.class, "xcore:cmd:discord-message:{server}", "chat.discord_ingress", 60_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, true),
            route(TransportEvents.PrivateMessageEvent.class, "xcore:evt:chat:private", "chat.private", 60_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(BanData.class, "xcore:evt:moderation:ban", "moderation.ban", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(MuteData.class, "xcore:evt:moderation:mute", "moderation.mute", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.VoteKickEvent.class, "xcore:evt:moderation:votekick", "moderation.votekick", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.ModerationAuditAppendedEvent.class, "xcore:evt:moderation:audit", "moderation.audit", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.KickBannedPlayer.class, "xcore:cmd:kick-banned:{server}", "moderation.kick_banned", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerCustomNicknameChanged.class, "xcore:cmd:player-custom-nickname:{server}", "player.custom_nickname", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerActiveBadgeChanged.class, "xcore:cmd:player-active-badge:{server}", "player.active_badge", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerBadgeSymbolColorModeChanged.class, "xcore:cmd:player-badge-symbol-color-mode:{server}", "player.badge_symbol_color_mode", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerBadgeInventoryChanged.class, "xcore:cmd:player-badge-inventory:{server}", "player.badge_inventory", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.PlayerPasswordReset.class, "xcore:cmd:player-password-reset:{server}", "player.password_reset", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.DiscordLinkCodeCreatedEvent.class, "xcore:evt:discord:link-code", "discord.link_code_created", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.DiscordLinkConfirmEvent.class, "xcore:cmd:discord-link-confirm:{server}", "discord.link_confirm", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(TransportEvents.DiscordUnlinkEvent.class, "xcore:cmd:discord-unlink:{server}", "discord.unlink", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(TransportEvents.DiscordLinkStatusChangedEvent.class, "xcore:evt:discord:link-status", "discord.link_status_changed", 120_000L, DeliveryMode.EVENT, ServerScope.BROADCAST, true),
            route(TransportEvents.DiscordAdminAccessChanged.class, "xcore:cmd:discord-admin-access:{server}", "discord.admin_access_changed", 120_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(TransportEvents.ReloadPlayerDataCache.class, "xcore:cmd:reload-cache:{server}", "cache.reload_player_data", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            route(TransportEvents.LoadMapsV2.class, "xcore:cmd:maps-load:{server}", "maps.load", 300_000L, DeliveryMode.COMMAND, ServerScope.PAYLOAD_SERVER, false),
            route(TransportEvents.ExecuteCommand.class, "xcore:cmd:execute-command:broadcast", "server.execute_command", 120_000L, DeliveryMode.COMMAND, ServerScope.BROADCAST, false),
            route(TransportEvents.PardonPlayer.class, "xcore:cmd:pardon-player:{server}", "moderation.pardon", 120_000L, DeliveryMode.COMMAND, ServerScope.DEFAULT_SERVER, false),
            rpcRoute(TransportEvents.MapsListRequest.class, "xcore:rpc:req:{server}", "maps.list", 10_000L, ServerScope.PAYLOAD_SERVER, TransportEvents.MapsListResponse.class),
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
                                      Class<? extends TransportEvents.Response> responseType) {
        return new RouteSpec(payloadType, streamPattern, eventType, ttlMillis, DeliveryMode.RPC_REQUEST, serverScope, false, true, responseType);
    }
}
