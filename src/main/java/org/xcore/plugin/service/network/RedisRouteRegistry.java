package org.xcore.plugin.service.network;

import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RedisRouteRegistry {
    private static final RedisServerResolver PAYLOAD_SERVER_RESOLVER = (payload, defaultServer) -> {
        if (payload instanceof TransportEvents.ServerScopedEvent serverScopedEvent) {
            String server = serverScopedEvent.server();
            if (server != null && !server.isBlank()) {
                return server;
            }
        }
        return defaultServer;
    };

    private final Map<Class<?>, RedisRouteDescriptor> descriptorsByType;

    public RedisRouteRegistry() {
        this.descriptorsByType = new LinkedHashMap<>();
        registerDefaults();
    }

    public RedisRouteDescriptor routeDescriptorFor(Object payload) {
        RedisRouteDescriptor descriptor = descriptorsByType.get(payload.getClass());
        if (descriptor != null) {
            return descriptor;
        }

        String eventType = "event." + payload.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return new RedisRouteDescriptor(
                payload.getClass(),
                "xcore:evt:raw",
                eventType,
                60_000L,
                RedisRouteKind.READ_ONLY,
                RedisServerResolver.broadcast(),
                null
        );
    }

    public RedisRouteDescriptor routeDescriptorFor(Class<?> type) {
        return descriptorsByType.get(type);
    }

    public List<String> subscribeStreamsFor(Class<?> type, String defaultServer) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        if (descriptor == null) {
            return List.of();
        }
        return List.of(resolveStreamKey(descriptor, defaultServer, defaultServer));
    }

    public boolean isReadOnlyType(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        return descriptor != null && descriptor.isReadOnly();
    }

    public boolean isMutatingType(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        return descriptor != null && descriptor.isMutating();
    }

    public boolean isRpcRequestType(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        return descriptor != null && descriptor.isRpcRequest();
    }

    public Class<? extends TransportEvents.Response> responseTypeForRequest(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        return descriptor == null ? null : descriptor.responseType();
    }

    public String rpcTypeForRequestClass(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        if (descriptor == null || !descriptor.isRpcRequest()) {
            return null;
        }
        return descriptor.eventType();
    }

    public String resolveStreamKey(RedisRouteDescriptor descriptor, Object payload, String defaultServer) {
        String resolvedServer = descriptor.serverResolver().resolveServer(payload, defaultServer);
        return resolveStreamKey(descriptor, resolvedServer, defaultServer);
    }

    private String resolveStreamKey(RedisRouteDescriptor descriptor, String resolvedServer, String defaultServer) {
        String server = (resolvedServer == null || resolvedServer.isBlank()) ? defaultServer : resolvedServer;
        if (descriptor.streamPattern().contains("{server}")) {
            return descriptor.streamPattern().replace("{server}", server);
        }
        return descriptor.streamPattern();
    }

    public List<RedisRouteDescriptor> descriptors() {
        return new ArrayList<>(descriptorsByType.values());
    }

    private void registerDefaults() {
        register(readOnly(TransportEvents.MessageEvent.class, "xcore:evt:chat:message", "chat.message", 60_000L, RedisServerResolver.broadcast()));
        register(readOnly(TransportEvents.ServerActionEvent.class, "xcore:evt:server:action", "server.action", 60_000L, RedisServerResolver.broadcast()));
        register(readOnly(TransportEvents.PlayerJoinLeaveEvent.class, "xcore:evt:player:joinleave", "player.join_leave", 60_000L, RedisServerResolver.broadcast()));
        register(readOnly(TransportEvents.GlobalChatEvent.class, "xcore:evt:chat:global", "chat.global", 60_000L, RedisServerResolver.broadcast()));
        register(readOnly(TransportEvents.DiscordMessageEvent.class, "xcore:cmd:discord-message:{server}", "chat.discord_ingress", 60_000L, PAYLOAD_SERVER_RESOLVER));
        register(readOnly(TransportEvents.PrivateMessageEvent.class, "xcore:evt:chat:private", "chat.private", 60_000L, RedisServerResolver.broadcast()));
        register(readOnly(BanData.class, "xcore:evt:moderation:ban", "moderation.ban", 120_000L, RedisServerResolver.broadcast()));
        register(readOnly(MuteData.class, "xcore:evt:moderation:mute", "moderation.mute", 120_000L, RedisServerResolver.broadcast()));
        register(readOnly(TransportEvents.VoteKickEvent.class, "xcore:evt:moderation:votekick", "moderation.votekick", 120_000L, RedisServerResolver.broadcast()));
        register(readOnly(TransportEvents.ModerationAuditAppendedEvent.class, "xcore:evt:moderation:audit", "moderation.audit", 120_000L, RedisServerResolver.broadcast()));
        register(mutating(TransportEvents.KickBannedPlayer.class, "xcore:cmd:kick-banned:{server}", "moderation.kick_banned", 120_000L, RedisServerResolver.defaultServer()));
        register(mutating(TransportEvents.PlayerCustomNicknameChanged.class, "xcore:cmd:player-custom-nickname:{server}", "player.custom_nickname", 120_000L, RedisServerResolver.defaultServer()));
        register(mutating(TransportEvents.PlayerActiveBadgeChanged.class, "xcore:cmd:player-active-badge:{server}", "player.active_badge", 120_000L, RedisServerResolver.defaultServer()));
        register(mutating(TransportEvents.PlayerBadgeSymbolColorModeChanged.class, "xcore:cmd:player-badge-symbol-color-mode:{server}", "player.badge_symbol_color_mode", 120_000L, RedisServerResolver.defaultServer()));
        register(mutating(TransportEvents.PlayerBadgeInventoryChanged.class, "xcore:cmd:player-badge-inventory:{server}", "player.badge_inventory", 120_000L, RedisServerResolver.defaultServer()));
        register(mutating(TransportEvents.PlayerPasswordReset.class, "xcore:cmd:player-password-reset:{server}", "player.password_reset", 120_000L, RedisServerResolver.defaultServer()));
        register(readOnly(TransportEvents.DiscordLinkCodeCreatedEvent.class, "xcore:evt:discord:link-code", "discord.link_code_created", 120_000L, RedisServerResolver.broadcast()));
        register(mutating(TransportEvents.DiscordLinkConfirmEvent.class, "xcore:cmd:discord-link-confirm:{server}", "discord.link_confirm", 120_000L, PAYLOAD_SERVER_RESOLVER));
        register(mutating(TransportEvents.DiscordUnlinkEvent.class, "xcore:cmd:discord-unlink:{server}", "discord.unlink", 120_000L, PAYLOAD_SERVER_RESOLVER));
        register(readOnly(TransportEvents.DiscordLinkStatusChangedEvent.class, "xcore:evt:discord:link-status", "discord.link_status_changed", 120_000L, RedisServerResolver.broadcast()));
        register(mutating(TransportEvents.DiscordAdminAccessChanged.class, "xcore:cmd:discord-admin-access:{server}", "discord.admin_access_changed", 120_000L, PAYLOAD_SERVER_RESOLVER));
        register(mutating(TransportEvents.ReloadPlayerDataCache.class, "xcore:cmd:reload-cache:{server}", "cache.reload_player_data", 120_000L, RedisServerResolver.defaultServer()));
        register(mutating(TransportEvents.LoadMapsV2.class, "xcore:cmd:maps-load:{server}", "maps.load", 300_000L, PAYLOAD_SERVER_RESOLVER));
        register(mutating(TransportEvents.ExecuteCommand.class, "xcore:cmd:execute-command:broadcast", "server.execute_command", 120_000L, RedisServerResolver.broadcast()));
        register(mutating(TransportEvents.PardonPlayer.class, "xcore:cmd:pardon-player:{server}", "moderation.pardon", 120_000L, RedisServerResolver.defaultServer()));
        register(rpc(TransportEvents.MapsListRequest.class, "xcore:rpc:req:{server}", "maps.list", 10_000L, PAYLOAD_SERVER_RESOLVER, TransportEvents.MapsListResponse.class));
        register(rpc(TransportEvents.MapRemoveRequest.class, "xcore:rpc:req:{server}", "maps.remove", 10_000L, PAYLOAD_SERVER_RESOLVER, TransportEvents.MapRemoveResponse.class));
    }

    private void register(RedisRouteDescriptor descriptor) {
        descriptorsByType.put(descriptor.payloadType(), descriptor);
    }

    private static RedisRouteDescriptor readOnly(Class<?> payloadType,
                                                 String streamPattern,
                                                 String eventType,
                                                 long ttlMillis,
                                                 RedisServerResolver serverResolver) {
        return new RedisRouteDescriptor(payloadType, streamPattern, eventType, ttlMillis, RedisRouteKind.READ_ONLY, serverResolver, null);
    }

    private static RedisRouteDescriptor mutating(Class<?> payloadType,
                                                 String streamPattern,
                                                 String eventType,
                                                 long ttlMillis,
                                                 RedisServerResolver serverResolver) {
        return new RedisRouteDescriptor(payloadType, streamPattern, eventType, ttlMillis, RedisRouteKind.MUTATING, serverResolver, null);
    }

    private static RedisRouteDescriptor rpc(Class<?> payloadType,
                                            String streamPattern,
                                            String eventType,
                                            long ttlMillis,
                                            RedisServerResolver serverResolver,
                                            Class<? extends TransportEvents.Response> responseType) {
        return new RedisRouteDescriptor(payloadType, streamPattern, eventType, ttlMillis, RedisRouteKind.RPC_REQUEST, serverResolver, responseType);
    }
}
