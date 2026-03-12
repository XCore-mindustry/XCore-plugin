package org.xcore.plugin.event.socket;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class DiscordLinkSocketHandler {

    private final NetworkService network;
    private final DiscordLinkService discordLinkService;
    private final SessionService sessionService;
    private final Config config;

    @Inject
    public DiscordLinkSocketHandler(NetworkService network,
                                    DiscordLinkService discordLinkService,
                                    SessionService sessionService,
                                    Config config) {
        this.network = network;
        this.discordLinkService = discordLinkService;
        this.sessionService = sessionService;
        this.config = config;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.DiscordLinkConfirmEvent.class, e -> {
            var result = discordLinkService.confirmLink(
                    e.code(),
                    e.playerUuid(),
                    e.playerPid(),
                    e.discordId(),
                    e.discordUsername()
            );
            if (!result.success()) {
                return;
            }

            var session = sessionService.get(e.playerUuid());
            if (session != null) {
                session.locale().send("commands-discord-link-confirmed", args(
                        "discordUsername", e.discordUsername()
                ));
            }
        });

        network.subscribe(SocketEvents.DiscordUnlinkEvent.class, e -> {
            var session = sessionService.get(e.playerUuid());
            if (discordLinkService.unlink(e.playerUuid()) && session != null) {
                session.locale().send("commands-discord-unlink-success", args());
            }
        });
    }
}
