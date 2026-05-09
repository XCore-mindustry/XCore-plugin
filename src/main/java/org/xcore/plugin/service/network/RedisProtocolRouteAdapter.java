package org.xcore.plugin.service.network;

import org.xcore.protocol.generated.routes.ProtocolRoutes;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatDiscordIngressCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerActiveBadgeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeInventoryChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeSymbolColorModeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerCustomNicknameChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerDataCacheReloadCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerPasswordResetCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkCodeCreatedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkConfirmCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsLoadCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationAuditAppendedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationPardonCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationVoteKickCreatedV1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of Redis route descriptors derived from the generated {@link ProtocolRoutes} catalog.
 *
 * <p>Plugin-local server resolution is preserved. Stream patterns, message types, TTL, delivery
 * kind, idempotency recommendation, and response mapping come from {@code ProtocolRoutes} as the
 * canonical source of truth.</p>
 */
public final class RedisProtocolRouteAdapter {
    private static final RedisServerResolver MODERATION_SERVER_RESOLVER = (payload, defaultServer) -> {
        String server = moderationServer(payload);
        return server == null || server.isBlank() ? defaultServer : server;
    };

    private static final RedisServerResolver PAYLOAD_SERVER_RESOLVER = (payload, defaultServer) -> {
        String moderationServer = moderationServer(payload);
        if (moderationServer != null && !moderationServer.isBlank()) {
            return moderationServer;
        }
        String discordServer = discordServer(payload);
        if (discordServer != null && !discordServer.isBlank()) {
            return discordServer;
        }
        String playerSessionServer = playerSessionServer(payload);
        if (playerSessionServer != null && !playerSessionServer.isBlank()) {
            return playerSessionServer;
        }
        String mapsServer = mapsServer(payload);
        if (mapsServer != null && !mapsServer.isBlank()) {
            return mapsServer;
        }
        return defaultServer;
    };

    private final Map<Class<?>, RedisRouteDescriptor> descriptorsByType;

    public RedisProtocolRouteAdapter() {
        this.descriptorsByType = new LinkedHashMap<>();
        for (var entry : ProtocolRoutes.ROUTES_BY_PAYLOAD_TYPE.entrySet()) {
            Class<?> payloadType = entry.getKey();
            ProtocolRoutes.RouteDescriptor route = entry.getValue();
            RedisDeliveryMode deliveryMode = mapDeliveryMode(route);
            Class<?> responseType = route.response() != null ? route.response().payloadType() : null;
            RedisServerResolver resolver = resolveServerResolver(route);
            RedisRouteDescriptor descriptor = new RedisRouteDescriptor(
                    payloadType,
                    route.stream(),
                    route.messageType(),
                    route.ttlMs(),
                    deliveryMode,
                    route.idempotentConsumerRecommended(),
                    resolver,
                    responseType
            );
            descriptorsByType.put(payloadType, descriptor);
        }
    }

    public RedisRouteDescriptor routeDescriptorFor(Object payload) {
        return descriptorsByType.get(payload.getClass());
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

    public boolean shouldClaimIdempotency(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        return descriptor != null && descriptor.shouldClaimIdempotency();
    }

    public boolean isRpcRequestType(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        return descriptor != null && descriptor.isRpcRequest();
    }

    public Class<?> responseTypeForRequest(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        return descriptor == null ? null : descriptor.responseType();
    }

    public String rpcTypeForRequestClass(Class<?> type) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(type);
        if (descriptor == null || !descriptor.isRpcRequest()) {
            return null;
        }
        return descriptor.messageType();
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

    private static RedisDeliveryMode mapDeliveryMode(ProtocolRoutes.RouteDescriptor route) {
        return switch (route.kind().toLowerCase()) {
            case "event" -> RedisDeliveryMode.EVENT;
            case "command" -> RedisDeliveryMode.COMMAND;
            case "rpc-request", "rpc_request" -> RedisDeliveryMode.RPC_REQUEST;
            default -> throw new IllegalArgumentException("Unknown route kind: " + route.kind());
        };
    }

    private static RedisServerResolver resolveServerResolver(ProtocolRoutes.RouteDescriptor route) {
        String stream = route.stream();
        if (stream.contains("{server}")) {
            if ("moderation".equalsIgnoreCase(route.family())) {
                return MODERATION_SERVER_RESOLVER;
            }
            return PAYLOAD_SERVER_RESOLVER;
        }
        return RedisServerResolver.broadcast();
    }

    private static String moderationServer(Object payload) {
        if (payload instanceof ModerationBanCreatedV1 event) {
            return event.server();
        }
        if (payload instanceof ModerationMuteCreatedV1 event) {
            return event.server();
        }
        if (payload instanceof ModerationVoteKickCreatedV1 event) {
            return event.server();
        }
        if (payload instanceof ModerationAuditAppendedV1 event) {
            return event.server();
        }
        if (payload instanceof ModerationKickBannedCommandV1 command) {
            return command.server();
        }
        if (payload instanceof ModerationPardonCommandV1 command) {
            return command.server();
        }
        return null;
    }

    private static String discordServer(Object payload) {
        if (payload instanceof DiscordLinkCodeCreatedV1 event) {
            return event.server();
        }
        if (payload instanceof DiscordLinkConfirmCommandV1 command) {
            return command.server();
        }
        if (payload instanceof DiscordLinkStatusChangedV1 event) {
            return event.server();
        }
        if (payload instanceof DiscordUnlinkCommandV1 command) {
            return command.server();
        }
        if (payload instanceof DiscordAdminAccessChangedCommandV1 command) {
            return command.server();
        }
        if (payload instanceof ChatDiscordIngressCommandV1 command) {
            return command.server();
        }
        return null;
    }

    private static String mapsServer(Object payload) {
        if (payload instanceof MapsListRequestV1 request) {
            return request.server();
        }
        if (payload instanceof MapsLoadCommandV1 command) {
            return command.server();
        }
        if (payload instanceof MapsRemoveRequestV1 request) {
            return request.server();
        }
        return null;
    }

    private static String playerSessionServer(Object payload) {
        if (payload instanceof PlayerCustomNicknameChangedCommandV1 command) {
            return command.server();
        }
        if (payload instanceof PlayerActiveBadgeChangedCommandV1 command) {
            return command.server();
        }
        if (payload instanceof PlayerBadgeSymbolColorModeChangedCommandV1 command) {
            return command.server();
        }
        if (payload instanceof PlayerBadgeInventoryChangedCommandV1 command) {
            return command.server();
        }
        if (payload instanceof PlayerPasswordResetCommandV1 command) {
            return command.server();
        }
        if (payload instanceof PlayerDataCacheReloadCommandV1 command) {
            return command.server();
        }
        return null;
    }
}
