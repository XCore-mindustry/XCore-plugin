package org.xcore.plugin.service.network;

public record RedisRouteDescriptor(
        Class<?> payloadType,
        String streamPattern,
        String messageType,
        long ttlMillis,
        RedisDeliveryMode deliveryMode,
        boolean idempotentConsumerRecommended,
        RedisServerResolver serverResolver,
        Class<?> responseType
) {
    public boolean isReadOnly() {
        return deliveryMode == RedisDeliveryMode.EVENT;
    }

    public boolean isMutating() {
        return deliveryMode == RedisDeliveryMode.COMMAND;
    }

    public boolean isRpcRequest() {
        return deliveryMode == RedisDeliveryMode.RPC_REQUEST;
    }

    public boolean shouldClaimIdempotency() {
        return idempotentConsumerRecommended;
    }
}
