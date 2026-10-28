package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.discord.DiscordMessages;

public final class DiscordRoutes {
    private DiscordRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.DiscordRoutes instances");
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

    public static final RouteDescriptor DISCORD_LINK_CODE_CREATED_V1 = new RouteDescriptor(
            "discord",
            "discordLinkCodeCreatedV1Route",
            "discord.link-code-created",
            1,
            DiscordMessages.DiscordLinkCodeCreatedV1.class,
            "event",
            "xcore:evt:discord:link-code",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_LINK_CONFIRM_COMMAND_V1 = new RouteDescriptor(
            "discord",
            "discordLinkConfirmCommandV1Route",
            "discord.link.confirm.command",
            1,
            DiscordMessages.DiscordLinkConfirmCommandV1.class,
            "command",
            "xcore:cmd:discord-link-confirm:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_UNLINK_COMMAND_V1 = new RouteDescriptor(
            "discord",
            "discordUnlinkCommandV1Route",
            "discord.unlink.command",
            1,
            DiscordMessages.DiscordUnlinkCommandV1.class,
            "command",
            "xcore:cmd:discord-unlink:{server}",
            Map.of("server", "payload.server"),
            "server",
            120000,
            false,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_LINK_STATUS_CHANGED_V1 = new RouteDescriptor(
            "discord",
            "discordLinkStatusChangedV1Route",
            "discord.link.status-changed",
            1,
            DiscordMessages.DiscordLinkStatusChangedV1.class,
            "event",
            "xcore:evt:discord:link-status",
            Map.of(),
            "broadcast",
            120000,
            true,
            true,
            "discord-linking",
            null
    );

    public static final RouteDescriptor DISCORD_ACCOUNT_OPERATION_REQUEST_V1 = new RouteDescriptor(
            "discord",
            "discordAccountOperationRequestV1Route",
            "discord.account.operation.request",
            1,
            DiscordMessages.DiscordAccountOperationRequestV1.class,
            "rpc-request",
            "xcore:rpc:req:{server}",
            Map.of("server", "payload.server"),
            "server",
            10000,
            false,
            true,
            "discord-linking",
            new RouteResponseDescriptor(
                    "discord.account.operation.response",
                    1,
                    DiscordMessages.DiscordAccountOperationResponseV1.class,
                    "xcore:rpc:resp:{requester}",
                    Map.of("requester", "rpc.requester")
            )
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("discord.link-code-created", 1), DISCORD_LINK_CODE_CREATED_V1),
            entry(key("discord.link.confirm.command", 1), DISCORD_LINK_CONFIRM_COMMAND_V1),
            entry(key("discord.unlink.command", 1), DISCORD_UNLINK_COMMAND_V1),
            entry(key("discord.link.status-changed", 1), DISCORD_LINK_STATUS_CHANGED_V1),
            entry(key("discord.account.operation.request", 1), DISCORD_ACCOUNT_OPERATION_REQUEST_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
