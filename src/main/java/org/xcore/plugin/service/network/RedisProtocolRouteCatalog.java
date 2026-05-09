package org.xcore.plugin.service.network;

import org.xcore.protocol.generated.routes.ProtocolRoutes;

import java.util.List;
import java.util.Map;

/**
 * Explicit topology model for the current Redis transport routes.
 *
 * <p>Derived from {@link ProtocolRoutes} as the canonical source of truth for stream patterns,
 * message types, TTL, delivery kind, idempotency recommendation, and response mapping.</p>
 */
public final class RedisProtocolRouteCatalog {
    public enum ServerScope {
        BROADCAST,
        DEFAULT_SERVER,
        PAYLOAD_SERVER
    }

    public record RouteSpec(
            Class<?> payloadType,
            String streamPattern,
            String messageType,
            long ttlMillis,
            RedisDeliveryMode deliveryMode,
            ServerScope serverScope,
            boolean readOnly,
            boolean rpcRequest,
            boolean idempotentConsumerRecommended,
            Class<?> responseType
    ) {
    }

    public static final List<RouteSpec> ROUTES = ProtocolRoutes.ROUTES_BY_PAYLOAD_TYPE.values().stream()
            .map(RedisProtocolRouteCatalog::toRouteSpec)
            .toList();

    public static final Map<Class<?>, RouteSpec> ROUTES_BY_TYPE = ROUTES.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(RouteSpec::payloadType, spec -> spec));

    private RedisProtocolRouteCatalog() {
    }

    public static RouteSpec routeFor(Class<?> payloadType) {
        return ROUTES_BY_TYPE.get(payloadType);
    }

    public static boolean isCompatibleStreamPattern(String streamKey) {
        return RedisTransportContracts.isStableExternalStream(streamKey);
    }

    private static RouteSpec toRouteSpec(ProtocolRoutes.RouteDescriptor route) {
        RedisDeliveryMode deliveryMode = mapDeliveryMode(route.kind());
        ServerScope serverScope = mapServerScope(route.stream(), route.targetScope());
        boolean readOnly = deliveryMode == RedisDeliveryMode.EVENT;
        boolean rpcRequest = deliveryMode == RedisDeliveryMode.RPC_REQUEST;
        Class<?> responseType = route.response() != null ? route.response().payloadType() : null;
        return new RouteSpec(
                route.payloadType(),
                route.stream(),
                route.messageType(),
                route.ttlMs(),
                deliveryMode,
                serverScope,
                readOnly,
                rpcRequest,
                route.idempotentConsumerRecommended(),
                responseType
        );
    }

    private static RedisDeliveryMode mapDeliveryMode(String kind) {
        return switch (kind.toLowerCase()) {
            case "event" -> RedisDeliveryMode.EVENT;
            case "command" -> RedisDeliveryMode.COMMAND;
            case "rpc-request", "rpc_request" -> RedisDeliveryMode.RPC_REQUEST;
            default -> throw new IllegalArgumentException("Unknown delivery mode: " + kind);
        };
    }

    private static ServerScope mapServerScope(String stream, String targetScope) {
        if (!stream.contains("{server}")) {
            return ServerScope.BROADCAST;
        }
        if ("server".equalsIgnoreCase(targetScope) || "PAYLOAD_SERVER".equalsIgnoreCase(targetScope)) {
            return ServerScope.PAYLOAD_SERVER;
        }
        return ServerScope.DEFAULT_SERVER;
    }
}
