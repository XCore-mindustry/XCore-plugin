package org.xcore.plugin.event.socket;

import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ChatSocketHandler {

    private final NetworkService network;
    private final SessionService sessionService;
    private final Config config;

    @Inject
    public ChatSocketHandler(NetworkService network,
                             SessionService sessionService,
                             Config config) {
        this.network = network;
        this.sessionService = sessionService;
        this.config = config;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.GlobalChatEvent.class, e -> {
            sessionService.broadcast("global-chat-format", args(
                    "server", e.server(),
                    "author", e.authorName(),
                    "message", e.message()
            ));
            Log.infoTag("GLOBAL-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });

        network.subscribe(SocketEvents.DiscordMessageEvent.class, e -> {
            if (!config.server.equals(e.server())) {
                return;
            }

            sessionService.broadcast("discord-chat-format", args(
                    "author", e.authorName(),
                    "message", e.message()
            ));
            Log.infoTag("DISCORD-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });
    }
}
