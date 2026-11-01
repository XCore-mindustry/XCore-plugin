package org.xcore.plugin.service.network;

import org.xcore.protocol.generated.routes.ProtocolRoutes;

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
    private final Map<Class<?>, RedisRouteDescriptor> descriptorsByType;

    public RedisProtocolRouteAdapter() {
        this.descriptorsByType = new LinkedHashMap<>();
        for (var entry : ProtocolRoutes.ROUTES_BY_PAYLOAD_TYPE.entrySet()) {
            Class<?> payloadType = entry.getKey();
            ProtocolRoutes.RouteDescriptor route = entry.getValue();
            RedisDeliveryMode deliveryMode = mapDeliveryMode(route);
            Class<?> responseType = route.response() != null ? route.response().payloadType() : null;
            RedisServerResolver resolver = resolveServerResolver(route.bindings());
            RedisRouteDescriptor descriptor = new RedisRouteDescriptor(
                    payloadType,
                    route.stream(),
                    Map.copyOf(route.bindings()),
                    route.messageType(),
                    route.ttlMs(),
                    deliveryMode,
                    route.idempotentConsumerRecommended(),
                    resolver,
                    responseType,
                    route.response() != null ? route.response().stream() : null,
                    route.response() != null ? Map.copyOf(route.response().bindings()) : Map.of()
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

    public String responseStreamKeyForRequest(Object payload, String defaultServer, String requester) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(payload);
        if (descriptor == null || descriptor.responseStreamPattern() == null) {
            return null;
        }
        return resolvePattern(descriptor.responseStreamPattern(), descriptor.responseStreamBindings(), payload, defaultServer, requester);
    }

    public String resolveStreamKey(RedisRouteDescriptor descriptor, Object payload, String defaultServer) {
        return resolvePattern(descriptor.streamPattern(), descriptor.streamBindings(), payload, defaultServer, defaultServer);
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

    private static RedisServerResolver resolveServerResolver(Map<String, String> bindings) {
        String serverBinding = bindings.get("server");
        if (serverBinding == null || serverBinding.isBlank()) {
            return RedisServerResolver.broadcast();
        }
        if (serverBinding.startsWith("payload.")) {
            return RedisServerResolver.payloadField(serverBinding.substring("payload.".length()));
        }
        return RedisServerResolver.defaultServer();
    }

    private String resolvePattern(String pattern,
                                  Map<String, String> bindings,
                                  Object payload,
                                  String defaultServer,
                                  String requester) {
        String resolved = pattern;
        for (var entry : bindings.entrySet()) {
            String replacement = resolveBinding(entry.getValue(), payload, defaultServer, requester);
            resolved = resolved.replace("{" + entry.getKey() + "}", replacement);
        }
        if (resolved.contains("{server}")) {
            String fallbackServer = routeServer(payload, defaultServer);
            resolved = resolved.replace("{server}", fallbackServer);
        }
        if (resolved.contains("{requester}")) {
            resolved = resolved.replace("{requester}", requester == null || requester.isBlank() ? defaultServer : requester);
        }
        return resolved;
    }

    private String resolveBinding(String binding,
                                  Object payload,
                                  String defaultServer,
                                  String requester) {
        if (binding == null || binding.isBlank()) {
            return defaultServer;
        }
        if (binding.startsWith("payload.")) {
            return RedisServerResolver.payloadField(binding.substring("payload.".length()))
                    .resolveServer(payload, defaultServer);
        }
        if ("rpc.requester".equals(binding)) {
            return requester == null || requester.isBlank() ? defaultServer : requester;
        }
        return defaultServer;
    }

    private String routeServer(Object payload, String defaultServer) {
        RedisRouteDescriptor descriptor = routeDescriptorFor(payload);
        if (descriptor == null) {
            return defaultServer;
        }
        String resolved = descriptor.serverResolver().resolveServer(payload, defaultServer);
        return resolved == null || resolved.isBlank() ? defaultServer : resolved;
    }
}
