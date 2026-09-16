package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.security.SecurityMessages;

public final class SecurityRoutes {
    private SecurityRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.SecurityRoutes instances");
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

    public static final RouteDescriptor PLAYER_PASSWORD_RESET_COMMAND_V1 = new RouteDescriptor(
            "security",
            "playerPasswordResetCommandV1Route",
            "player.password-reset.command",
            1,
            SecurityMessages.PlayerPasswordResetCommandV1.class,
            "command",
            "xcore:cmd:player-password-reset:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "player-session",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("player.password-reset.command", 1), PLAYER_PASSWORD_RESET_COMMAND_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
