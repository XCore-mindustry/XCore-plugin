package org.xcore.plugin.event.socket;

import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ChatSocketHandler {

    private final NetworkService network;
    private final SessionService sessionService;
    private final PrivateMessageService privateMessageService;
    private final Config config;

    @Inject
    public ChatSocketHandler(NetworkService network,
                             SessionService sessionService,
                             PrivateMessageService privateMessageService,
                             Config config) {
        this.network = network;
        this.sessionService = sessionService;
        this.privateMessageService = privateMessageService;
        this.config = config;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.GlobalChatEvent.class, e -> {
            sessionService.broadcastFiltered("global-chat-format", args(
                    "server", e.server(),
                    "author", e.authorName(),
                    "message", e.message()
            ), session -> !Boolean.FALSE.equals(session.data.globalChatVisible));
            Log.infoTag("GLOBAL-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });

        network.subscribe(SocketEvents.DiscordMessageEvent.class, e -> {
            if (!config.server.equals(e.server())) {
                return;
            }

            sessionService.broadcastFiltered("discord-chat-format", args(
                    "author", e.authorName(),
                    "message", e.message()
            ), session -> !Boolean.FALSE.equals(session.data.discordRelayVisible));
            Log.infoTag("DISCORD-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });

        network.subscribe(SocketEvents.PrivateMessageEvent.class, e -> {
            if (config.server.equals(e.server())) {
                return;
            }

            Session recipientSession = sessionService.get(e.toUuid());
            if (recipientSession == null || recipientSession.player == null) {
                return;
            }

            PrivateMessage message = PrivateMessage.builder()
                    .fromUuid(e.fromUuid())
                    .fromPid(e.fromPid())
                    .fromName(e.fromName())
                    .toUuid(e.toUuid())
                    .toPid(e.toPid())
                    .message(e.message())
                    .deliveredAt(System.currentTimeMillis())
                    .build();

            privateMessageService.deliverIncoming(message, recipientSession);
            recipientSession.lastPrivateTargetPid = e.fromPid();
        });
    }
}
