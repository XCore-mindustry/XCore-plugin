package org.xcore.plugin.event.transport;

import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ChatTransportHandler {

    private final NetworkService network;
    private final SessionService sessionService;
    private final PrivateMessageService privateMessageService;
    private final Config config;

    @Inject
    public ChatTransportHandler(NetworkService network,
                                SessionService sessionService,
                                PrivateMessageService privateMessageService,
                                Config config) {
        this.network = network;
        this.sessionService = sessionService;
        this.privateMessageService = privateMessageService;
        this.config = config;
    }

    public void registerListeners() {
        network.subscribe(TransportEvents.GlobalChatEvent.class, e -> {
            sessionService.broadcastFiltered("global-chat-format", args(
                    "server", e.server(),
                    "author", e.authorName(),
                    "message", e.message()
            ), session -> !Boolean.FALSE.equals(session.data.globalChatVisible));
            Log.infoTag("GLOBAL-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });

        network.subscribe(TransportEvents.DiscordMessageEvent.class, e -> {
            if (!config.server.equals(e.server())) {
                return;
            }

            sessionService.broadcastFiltered("discord-chat-format", args(
                    "author", e.authorName(),
                    "message", e.message()
            ), session -> !Boolean.FALSE.equals(session.data.discordRelayVisible));
            Log.infoTag("DISCORD-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });

        network.subscribe(TransportEvents.PrivateMessageEvent.class, e -> {
            if (config.server.equals(e.server())) {
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
        });
    }
}
