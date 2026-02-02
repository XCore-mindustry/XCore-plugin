package org.xcore.plugin.event.socket;

import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.discord.DiscordService;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.NetworkService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ChatSocketHandler {

    private final NetworkService network;
    private final DiscordService discordService;
    private final Config config;
    private final BundleService bundleService;

    @Inject
    public ChatSocketHandler(NetworkService network,
                             DiscordService discordService,
                             Config config,
                             BundleService bundleService) {
        this.network = network;
        this.discordService = discordService;
        this.config = config;
        this.bundleService = bundleService;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.GlobalChatEvent.class, e -> {
            bundleService.send("global-chat-format", args(
                    "server", e.server(),
                    "author", e.authorName(),
                    "message", e.message()
            ));
            Log.infoTag("GLOBAL-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });

        if (!network.isSocketServer()) {
            network.subscribe(SocketEvents.DiscordMessageEvent.class, e -> {
                if (!e.server().equals(config.server)) return;
                discordService.sendMessageToGameFromDiscord(e.authorName(), e.message());
            });
        }

        if (network.isSocketServer()) {
            network.subscribe(SocketEvents.MessageEvent.class, e ->
                    discordService.sendMessageEvent(e.authorName(), e.message(), e.server()));
        }
    }
}
