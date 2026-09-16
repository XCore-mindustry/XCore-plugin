package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.chat.ChatMessages;

public final class ChatRoutes {
    private ChatRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.ChatRoutes instances");
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

    public static final RouteDescriptor CHAT_MESSAGE_V1 = new RouteDescriptor(
            "chat",
            "chatMessageV1Route",
            "chat.message",
            1,
            ChatMessages.ChatMessageV1.class,
            "event",
            "xcore:evt:chat:message",
            Map.of(),
            "broadcast",
            60000,
            true,
            false,
            "chat",
            null
    );

    public static final RouteDescriptor CHAT_GLOBAL_V1 = new RouteDescriptor(
            "chat",
            "chatGlobalV1Route",
            "chat.global",
            1,
            ChatMessages.ChatGlobalV1.class,
            "event",
            "xcore:evt:chat:global",
            Map.of(),
            "broadcast",
            60000,
            true,
            false,
            "chat",
            null
    );

    public static final RouteDescriptor CHAT_DISCORD_INGRESS_COMMAND_V1 = new RouteDescriptor(
            "chat",
            "chatDiscordIngressCommandV1Route",
            "chat.discord-ingress.command",
            1,
            ChatMessages.ChatDiscordIngressCommandV1.class,
            "command",
            "xcore:cmd:discord-message:{server}",
            Map.of("server", "payload.server"),
            "server",
            60000,
            false,
            true,
            "chat",
            null
    );

    public static final RouteDescriptor CHAT_PRIVATE_V1 = new RouteDescriptor(
            "chat",
            "chatPrivateV1Route",
            "chat.private",
            1,
            ChatMessages.ChatPrivateV1.class,
            "event",
            "xcore:evt:chat:private",
            Map.of(),
            "broadcast",
            60000,
            true,
            false,
            "chat",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("chat.message", 1), CHAT_MESSAGE_V1),
            entry(key("chat.global", 1), CHAT_GLOBAL_V1),
            entry(key("chat.discord-ingress.command", 1), CHAT_DISCORD_INGRESS_COMMAND_V1),
            entry(key("chat.private", 1), CHAT_PRIVATE_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
