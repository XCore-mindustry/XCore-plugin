package org.xcore.plugin.service.network;

import org.xcore.plugin.event.TransportEvents;

import java.util.List;
import java.util.Locale;

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
        if (descriptor != null) {
            return new Route(
                    registry.resolveStreamKey(descriptor, event, defaultServer),
                    descriptor.eventType(),
                    descriptor.ttlMillis()
            );
        }

        var eventType = "event." + event.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return new Route("xcore:evt:raw", eventType, 60000L);
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

    public Class<? extends TransportEvents.Response> responseTypeForRequest(Class<?> type) {
        return registry.responseTypeForRequest(type);
    }

    public String rpcTypeForRequestClass(Class<?> type) {
        return registry.rpcTypeForRequestClass(type);
    }
}
