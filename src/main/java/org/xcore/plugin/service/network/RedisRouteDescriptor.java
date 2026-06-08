package org.xcore.plugin.service.network;

import java.util.Map;

public record RedisRouteDescriptor(
        Class<?> payloadType,
        String streamPattern,
        Map<String, String> streamBindings,
        String messageType,
        long ttlMillis,
        RedisDeliveryMode deliveryMode,
        boolean idempotentConsumerRecommended,
        RedisServerResolver serverResolver,
        Class<?> responseType,
        String responseStreamPattern,
        Map<String, String> responseStreamBindings
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
