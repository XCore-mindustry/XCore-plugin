package org.xcore.plugin.event.transport;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.DiscordAccountOperationService;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAccountOperationRequestV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
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
    private final Async async;
    private final DiscordAccountOperationService operations;
    private final TomlXcoreConfig config;

    @Inject
    public DiscordLinkTransportHandler(NetworkService network,
                                       DiscordLinkService discordLinkService,
                                       SessionService sessionService,
                                       Async async, DiscordAccountOperationService operations, TomlXcoreConfig config) {
        this.network = network;
        this.discordLinkService = discordLinkService;
        this.sessionService = sessionService;
        this.async = async;
        this.operations = operations;
        this.config = config;
    }

    public void registerListeners() {
        network.subscribe(DiscordAccountOperationRequestV1.class, request -> {
            try {
                if (!java.util.UUID.fromString(request.operationId()).toString().equalsIgnoreCase(request.operationId())) {
                    throw new IllegalArgumentException("Invalid operation ID");
                }
            } catch (RuntimeException invalid) {
                network.respondError(request, "REJECTED", "invalid-operation-id");
                return;
            }
            network.respond(request, operations.execute(request, network.requester(request)));
        });
        network.subscribe(DiscordLinkStatusChangedV1.class, event ->
                discordLinkService.refreshCachedBinding(event.player().playerUuid()));
        network.subscribe(DiscordLinkConfirmCommandV1.class, e -> {
            if (!config.server.name.equals(e.server())) return;
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

            async.main(() -> {
                var session = sessionService.get(e.player().playerUuid());
                if (session != null) session.locale().send("commands-discord-link-confirmed", args(
                        "discordUsername", e.discord().discordUsername()));
            });
        });

        network.subscribe(DiscordUnlinkCommandV1.class, e -> {
            if (!config.server.name.equals(e.server())) return;
            String playerUuid = e.player().playerUuid();
            long issuedAt;
            try { issuedAt = java.time.Instant.parse(e.requestedAt()).toEpochMilli(); }
            catch (RuntimeException invalid) { return; }
            if (discordLinkService.unlinkLegacy(playerUuid, e.discord().discordId(), issuedAt)) {
                async.main(() -> {
                    var session = sessionService.get(playerUuid);
                    if (session != null) session.locale().send("commands-discord-unlink-success", args());
                });
            }
        });
    }
}
