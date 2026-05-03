package org.xcore.plugin.service.network;

public record RedisRouteDescriptor(
        Class<?> payloadType,
        String streamPattern,
        String eventType,
        long ttlMillis,
        RedisRouteKind kind,
        RedisServerResolver serverResolver,
        Class<?> responseType
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
