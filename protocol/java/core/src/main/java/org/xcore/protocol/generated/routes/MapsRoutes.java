package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.maps.MapsMessages;

public final class MapsRoutes {
    private MapsRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.MapsRoutes instances");
    }

    public record MessageKey(String messageType, int messageVersion) {}

    public record RouteResponseDescriptor(
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String stream
    ) {}

    public record RouteDescriptor(
            String family,
            String methodName,
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String kind,
            String stream,
            String targetScope,
            int ttlMs,
            boolean replayable,
            boolean idempotentConsumerRecommended,
            String owner,
            RouteResponseDescriptor response
    ) {}

    public static final RouteDescriptor MAPS_LIST_REQUEST_V1 = new RouteDescriptor(
            "maps",
            "mapsListRequestV1Route",
            "maps.list.request",
            1,
            MapsMessages.MapsListRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            "server",
            10000,
            false,
            false,
            "maps",
            new RouteResponseDescriptor(
                    "maps.list.response",
                    1,
                    MapsMessages.MapsListResponseV1.class,
                    "xcore:rpc:resp:{requester}"
            )
    );

    public static final RouteDescriptor MAPS_REMOVE_REQUEST_V1 = new RouteDescriptor(
            "maps",
            "mapsRemoveRequestV1Route",
            "maps.remove.request",
            1,
            MapsMessages.MapsRemoveRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            "server",
            10000,
            false,
            true,
            "maps",
            new RouteResponseDescriptor(
                    "maps.remove.response",
                    1,
                    MapsMessages.MapsRemoveResponseV1.class,
                    "xcore:rpc:resp:{requester}"
            )
    );

    public static final RouteDescriptor MAPS_LOAD_COMMAND_V1 = new RouteDescriptor(
            "maps",
            "mapsLoadCommandV1Route",
            "maps.load.command",
            1,
            MapsMessages.MapsLoadCommandV1.class,
            "command",
            "xcore:cmd:maps-load:{server}",
            "server",
            300000,
            false,
            true,
            "maps",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("maps.list.request", 1), MAPS_LIST_REQUEST_V1),
            entry(key("maps.remove.request", 1), MAPS_REMOVE_REQUEST_V1),
            entry(key("maps.load.command", 1), MAPS_LOAD_COMMAND_V1)
    );

    public static final Map<MessageKey, RouteDescriptor> MAPS_ROUTES_BY_MESSAGE = ROUTES_BY_MESSAGE;

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
