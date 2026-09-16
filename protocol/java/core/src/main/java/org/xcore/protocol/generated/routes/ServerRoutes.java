package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.server.ServerMessages;

public final class ServerRoutes {
    private ServerRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.ServerRoutes instances");
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

    public static final RouteDescriptor SERVER_ACTION_V1 = new RouteDescriptor(
            "server",
            "serverActionV1Route",
            "server.action",
            1,
            ServerMessages.ServerActionV1.class,
            "event",
            "xcore:evt:server:action",
            Map.of(),
            "broadcast",
            60000,
            true,
            false,
            "server-runtime",
            null
    );

    public static final RouteDescriptor SERVER_COMMAND_EXECUTE_COMMAND_V1 = new RouteDescriptor(
            "server",
            "serverCommandExecuteCommandV1Route",
            "server-command.execute.command",
            1,
            ServerMessages.ServerCommandExecuteCommandV1.class,
            "command",
            "xcore:cmd:execute-command:broadcast",
            Map.of(),
            "broadcast",
            120000,
            false,
            false,
            "server-runtime",
            null
    );

    public static final RouteDescriptor PLAYER_DATA_CACHE_RELOAD_COMMAND_V1 = new RouteDescriptor(
            "server",
            "playerDataCacheReloadCommandV1Route",
            "player-data-cache.reload.command",
            1,
            ServerMessages.PlayerDataCacheReloadCommandV1.class,
            "command",
            "xcore:cmd:reload-cache:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final RouteDescriptor SERVER_HEARTBEAT_V1 = new RouteDescriptor(
            "server",
            "serverHeartbeatV1Route",
            "server.heartbeat",
            1,
            ServerMessages.ServerHeartbeatV1.class,
            "event",
            "xcore:evt:server:heartbeat",
            Map.of(),
            "broadcast",
            60000,
            true,
            false,
            "server-runtime",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("server.action", 1), SERVER_ACTION_V1),
            entry(key("server-command.execute.command", 1), SERVER_COMMAND_EXECUTE_COMMAND_V1),
            entry(key("player-data-cache.reload.command", 1), PLAYER_DATA_CACHE_RELOAD_COMMAND_V1),
            entry(key("server.heartbeat", 1), SERVER_HEARTBEAT_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
