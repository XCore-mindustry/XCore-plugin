package org.xcore.plugin.service.network;

import java.util.List;

public final class RedisStreamRouter {
    private final RedisProtocolRouteAdapter routeAdapter;

    public RedisStreamRouter() {
        this(new RedisProtocolRouteAdapter());
    }

    public RedisStreamRouter(RedisProtocolRouteAdapter routeAdapter) {
        this.routeAdapter = routeAdapter;
    }

    public record Route(String streamKey, String messageType, long ttlMillis) {
    }

    public Route route(Object event, String defaultServer) {
        RedisRouteDescriptor descriptor = routeAdapter.routeDescriptorFor(event);
        if (descriptor == null) {
            throw new UnsupportedOperationException("Redis route does not support payload type: " + event.getClass().getName());
        }

        return new Route(
                routeAdapter.resolveStreamKey(descriptor, event, defaultServer),
                descriptor.messageType(),
                descriptor.ttlMillis()
        );
    }

    public List<String> subscribeStreamsFor(Class<?> type, String defaultServer) {
        return routeAdapter.subscribeStreamsFor(type, defaultServer);
    }

    public boolean isReadOnlyType(Class<?> type) {
        return routeAdapter.isReadOnlyType(type);
    }

    public boolean isMutatingType(Class<?> type) {
        return routeAdapter.isMutatingType(type);
    }

    public boolean shouldClaimIdempotency(Class<?> type) {
        return routeAdapter.shouldClaimIdempotency(type);
    }

    public boolean isRpcRequestType(Class<?> type) {
        return routeAdapter.isRpcRequestType(type);
    }

    public Class<?> responseTypeForRequest(Class<?> type) {
        return routeAdapter.responseTypeForRequest(type);
    }

    public String rpcTypeForRequestClass(Class<?> type) {
        return routeAdapter.rpcTypeForRequestClass(type);
    }

    public String responseStreamKeyForRequest(Object request, String defaultServer, String requester) {
        return routeAdapter.responseStreamKeyForRequest(request, defaultServer, requester);
    }
}
