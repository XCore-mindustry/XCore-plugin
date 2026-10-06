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

    public static final RouteDescriptor SECURITY_PERMISSIONS_CHANGED_V1 = new RouteDescriptor(
            "security",
            "securityPermissionsChangedV1Route",
            "security.permissions.changed",
            1,
            SecurityMessages.SecurityPermissionsChangedV1.class,
            "event",
            "xcore:evt:security:permissions-changed",
            Map.of(),
            "broadcast",
            300000,
            true,
            true,
            "permissions",
            null
    );

    public static final RouteDescriptor SECURITY_STAFF_SYNC_REQUEST_V1 = new RouteDescriptor(
            "security",
            "securityStaffSyncRequestV1Route",
            "security.staff.sync.request",
            1,
            SecurityMessages.SecurityStaffSyncRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            10000,
            false,
            true,
            "permissions",
            new RouteResponseDescriptor(
                    "security.staff.sync.response",
                    1,
                    SecurityMessages.SecurityStaffSyncResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final RouteDescriptor SECURITY_STAFF_RESET_PASSWORD_REQUEST_V1 = new RouteDescriptor(
            "security",
            "securityStaffResetPasswordRequestV1Route",
            "security.staff.reset-password.request",
            1,
            SecurityMessages.SecurityStaffResetPasswordRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            10000,
            false,
            true,
            "permissions",
            new RouteResponseDescriptor(
                    "security.staff.reset-password.response",
                    1,
                    SecurityMessages.SecurityStaffResetPasswordResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("player.password-reset.command", 1), PLAYER_PASSWORD_RESET_COMMAND_V1),
            entry(key("security.permissions.changed", 1), SECURITY_PERMISSIONS_CHANGED_V1),
            entry(key("security.staff.sync.request", 1), SECURITY_STAFF_SYNC_REQUEST_V1),
            entry(key("security.staff.reset-password.request", 1), SECURITY_STAFF_RESET_PASSWORD_REQUEST_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
