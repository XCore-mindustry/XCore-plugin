package org.xcore.plugin.discord;

import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;
import reactor.core.publisher.Mono;


@Singleton
public class DiscordInteractionHandler {

    private final GlobalConfig globalConfig;
    private final SessionService sessionService;
    private final AdminDataRepository adminDataRepository;
    private final NetworkService network;

    @Inject
    public DiscordInteractionHandler(
            GlobalConfig globalConfig,
            SessionService sessionService,
            AdminDataRepository adminDataRepository,
            NetworkService network
    ) {
        this.globalConfig = globalConfig;
        this.sessionService = sessionService;
        this.adminDataRepository = adminDataRepository;
        this.network = network;
    }

    public void register(GatewayDiscordClient gateway) {
        gateway.on(ButtonInteractionEvent.class, this::handleButtonInteraction).subscribe();
    }

    /**
     * Handle button interaction events (e.g., admin request confirmation/decline)
     */
    private Mono<Void> handleButtonInteraction(ButtonInteractionEvent event) {
        var author = event.getInteraction().getMember().orElse(null);
        var message = event.getMessage().orElse(null);

        if (author == null || message == null) return Mono.empty();

        if (!author.getRoleIds().contains(Snowflake.of(globalConfig.discordAdminRoleId))) {
            return event.reply("Access denied").withEphemeral(true);
        }

        if (event.getCustomId().endsWith("admreq")) {
            return handleAdminRequestConfirm(event, author, message);
        }

        if (event.getCustomId().equals("decline")) {
            return message.delete(author.getDisplayName());
        }

        return Mono.empty();
    }

    /**
     * Handle admin request confirmation button
     */
    private Mono<Void> handleAdminRequestConfirm(ButtonInteractionEvent event, discord4j.core.object.entity.Member author, discord4j.core.object.entity.Message message) {
        String[] args = event.getCustomId().split("_");

        String server = args[0];
        PlayerData data = sessionService.getOrLoadFromDb(Strings.parseInt(args[1]));
        var adminData = adminDataRepository.findByUuid(data.uuid);

        network.post(new SocketEvents.AdminRequestConfirmEvent(data.uuid, server));

        adminData.adminConfirmed = true;
        adminDataRepository.save(adminData);

        return message.getRestChannel().createMessage(
                author.getDisplayName() + " confirmed adminship to player " +
                        data.nickname + " on server " + server
        ).then(message.delete());
    }
}
