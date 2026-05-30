package org.xcore.plugin.event.transport;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkConfirmCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class DiscordLinkTransportHandler {

    private final NetworkService network;
    private final DiscordLinkService discordLinkService;
    private final SessionService sessionService;

    @Inject
    public DiscordLinkTransportHandler(NetworkService network,
                                       DiscordLinkService discordLinkService,
                                       SessionService sessionService) {
        this.network = network;
        this.discordLinkService = discordLinkService;
        this.sessionService = sessionService;
    }

    public void registerListeners() {
        network.subscribe(DiscordLinkConfirmCommandV1.class, e -> {
            Integer playerPid = e.player().playerPid();
            if (playerPid == null) {
                return;
            }

            var result = discordLinkService.confirmLink(
                    e.code(),
                    e.player().playerUuid(),
                    playerPid,
                    e.discord().discordId(),
                    e.discord().discordUsername()
            );
            if (!result.success()) {
                return;
            }

            var session = sessionService.get(e.player().playerUuid());
            if (session != null) {
                session.locale().send("commands-discord-link-confirmed", args(
                        "discordUsername", e.discord().discordUsername()
                ));
            }
        });

        network.subscribe(DiscordUnlinkCommandV1.class, e -> {
            String playerUuid = e.player().playerUuid();
            var session = sessionService.get(playerUuid);
            if (discordLinkService.unlink(playerUuid) && session != null) {
                session.locale().send("commands-discord-unlink-success", args());
            }
        });
    }
}
