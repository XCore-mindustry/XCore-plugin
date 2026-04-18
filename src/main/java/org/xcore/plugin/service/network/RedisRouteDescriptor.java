package org.xcore.plugin.service.network;

import org.xcore.plugin.event.TransportEvents;

public record RedisRouteDescriptor(
        Class<?> payloadType,
        String streamPattern,
        String eventType,
        long ttlMillis,
        RedisRouteKind kind,
        RedisServerResolver serverResolver,
        Class<? extends TransportEvents.Response> responseType
) {
    public boolean isReadOnly() {
        return kind == RedisRouteKind.READ_ONLY;
    }

    public boolean isMutating() {
        return kind == RedisRouteKind.MUTATING;
    }

    public boolean isRpcRequest() {
        return kind == RedisRouteKind.RPC_REQUEST;
    }
}
