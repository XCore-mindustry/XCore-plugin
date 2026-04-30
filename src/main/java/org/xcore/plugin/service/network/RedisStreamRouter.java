package org.xcore.plugin.service.network;

import java.util.List;

public final class RedisStreamRouter {
    private final RedisRouteRegistry registry;

    public RedisStreamRouter() {
        this(new RedisRouteRegistry());
    }

    public RedisStreamRouter(RedisRouteRegistry registry) {
        this.registry = registry;
    }

    public record Route(String streamKey, String eventType, long ttlMillis) {
    }

    public Route route(Object event, String defaultServer) {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(event);
        if (descriptor == null) {
            throw new UnsupportedOperationException("Redis route does not support payload type: " + event.getClass().getName());
        }

        return new Route(
                registry.resolveStreamKey(descriptor, event, defaultServer),
                descriptor.eventType(),
                descriptor.ttlMillis()
        );
    }

    public List<String> subscribeStreamsFor(Class<?> type, String defaultServer) {
        return registry.subscribeStreamsFor(type, defaultServer);
    }

    public boolean isReadOnlyType(Class<?> type) {
        return registry.isReadOnlyType(type);
    }

    public boolean isMutatingType(Class<?> type) {
        return registry.isMutatingType(type);
    }

    public boolean isRpcRequestType(Class<?> type) {
        return registry.isRpcRequestType(type);
    }

    public Class<?> responseTypeForRequest(Class<?> type) {
        return registry.responseTypeForRequest(type);
    }

    public String rpcTypeForRequestClass(Class<?> type) {
        return registry.rpcTypeForRequestClass(type);
    }
}
