package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.sentinel.SentinelMessages;

public final class SentinelRoutes {
    private SentinelRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.SentinelRoutes instances");
    }

    public record MessageKey(String messageType, int messageVersion) {}

    public record RouteResponseDescriptor(
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String stream,
            Map<String, String> bindings
    ) {}

    public record RouteDescriptor(
            String family,
            String methodName,
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String kind,
            String stream,
            Map<String, String> bindings,
            String targetScope,
            int ttlMs,
            boolean replayable,
            boolean idempotentConsumerRecommended,
            String owner,
            RouteResponseDescriptor response
    ) {}

    public static final RouteDescriptor SENTINEL_SUBNET_RULES_INVALIDATED_V1 = new RouteDescriptor(
            "sentinel",
            "sentinelSubnetRulesInvalidatedV1Route",
            "sentinel.subnet-rules.invalidated",
            1,
            SentinelMessages.SentinelSubnetRulesInvalidatedV1.class,
            "event",
            "xcore:evt:sentinel:subnet:invalidated",
            Map.of(),
            "broadcast",
            60000,
            true,
            true,
            "sentinel",
            null
    );

    public static final RouteDescriptor SENTINEL_SUBNET_SWEEP_COMMAND_V1 = new RouteDescriptor(
            "sentinel",
            "sentinelSubnetSweepCommandV1Route",
            "sentinel.subnet-sweep.command",
            1,
            SentinelMessages.SentinelSubnetSweepCommandV1.class,
            "command",
            "xcore:cmd:sentinel:subnet:sweep:broadcast",
            Map.of(),
            "broadcast",
            60000,
            false,
            true,
            "sentinel",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("sentinel.subnet-rules.invalidated", 1), SENTINEL_SUBNET_RULES_INVALIDATED_V1),
            entry(key("sentinel.subnet-sweep.command", 1), SENTINEL_SUBNET_SWEEP_COMMAND_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
