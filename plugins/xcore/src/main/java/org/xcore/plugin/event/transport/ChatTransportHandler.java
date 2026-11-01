package org.xcore.plugin.event.transport;

import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatDiscordIngressCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatGlobalV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatPrivateV1;
import org.xcore.plugin.chat.ChatDeliveryService;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Map;
import java.util.function.Predicate;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ChatTransportHandler {

    private final NetworkService network;
    private final SessionService sessionService;
    private final PrivateMessageService privateMessageService;
    private final TomlXcoreConfig config;
    private final Async async;
    private final ChatDeliveryService chatDeliveryService;

    @Inject
    public ChatTransportHandler(NetworkService network,
                                SessionService sessionService,
                                PrivateMessageService privateMessageService,
                                TomlXcoreConfig config,
                                Async async,
                                ChatDeliveryService chatDeliveryService) {
        this.network = network;
        this.sessionService = sessionService;
        this.privateMessageService = privateMessageService;
        this.config = config;
        this.async = async;
        this.chatDeliveryService = chatDeliveryService;
    }

    /**
     * Every listener here runs on a Redis subscriber thread and every one of them writes
     * chat packets to player connections, so all three bodies are marshalled. They are
     * otherwise pure — a cached session lookup and a message send — so nothing waits.
     */
    public void registerListeners() {
        network.subscribe(ChatGlobalV1.class, e -> async.main(() -> {
            relay(config.translation.channels.remoteGlobal,
                    "remote:" + e.server() + ":" + Strings.stripColors(e.authorName()),
                    e.authorName(), e.message(), "global-chat-format", args(
                            "server", e.server(),
                            "author", e.authorName(),
                            "message", e.message()
                    ), session -> !Boolean.FALSE.equals(session.data.globalChatVisible));
            Log.infoTag("GLOBAL-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        }));

        network.subscribe(ChatDiscordIngressCommandV1.class, e -> async.main(() -> {
            if (!config.server.name.equals(e.server())) {
                return;
            }

            relay(config.translation.channels.discord,
                    "discord:" + Strings.stripColors(e.authorName()),
                    e.authorName(), e.message(), "discord-chat-format", args(
                            "author", e.authorName(),
                            "message", e.message()
                    ), session -> !Boolean.FALSE.equals(session.data.discordRelayVisible));
            Log.infoTag("DISCORD-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        }));

        network.subscribe(ChatPrivateV1.class, e -> async.main(() -> {
            if (config.server.name.equals(e.server())) {
                return;
            }

            Session recipientSession = sessionService.get(e.toUuid());
            if (recipientSession == null || recipientSession.player == null) {
                return;
            }

            PrivateMessage message = new PrivateMessage();
            message.fromUuid = e.fromUuid();
            message.fromPid = e.fromPid();
            message.fromName = e.fromName();
            message.toUuid = e.toUuid();
            message.toPid = e.toPid();
            message.message = e.message();
            message.deliveredAt = System.currentTimeMillis();

            privateMessageService.deliverIncoming(message, recipientSession);
            recipientSession.lastPrivateTargetPid = e.fromPid();
        }));
    }

    /** Relayed chat goes through translation only when its channel is enabled for it. */
    private void relay(boolean translate,
                       String authorKey,
                       String authorName,
                       String message,
                       String bundleKey,
                       Map<String, Object> args,
                       Predicate<Session> filter) {
        if (translate) {
            chatDeliveryService.deliverRemote(authorKey, authorName, message, bundleKey, args, filter);
        } else {
            sessionService.broadcastFiltered(bundleKey, args, filter);
        }
    }
}
