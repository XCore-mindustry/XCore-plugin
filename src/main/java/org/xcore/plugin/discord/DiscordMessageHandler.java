package org.xcore.plugin.discord;

import arc.util.Log;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;

import static arc.util.Strings.format;
import static com.ospx.flubundle.Bundle.args;

/**
 * Handles Discord message events (chat bridge and command dispatching)
 */
@Singleton
public class DiscordMessageHandler {

    private final GlobalConfig globalConfig;
    private final Config config;
    private final NetworkService network;
    private final DiscordCommandRegistry commandRegistry;
    private final SessionService sessionService;

    @Inject
    public DiscordMessageHandler(
            GlobalConfig globalConfig,
            Config config,
            NetworkService network,
            DiscordCommandRegistry commandRegistry,
            SessionService sessionService
    ) {
        this.globalConfig = globalConfig;
        this.config = config;
        this.network = network;
        this.commandRegistry = commandRegistry;
        this.sessionService = sessionService;
    }

    /**
     * Register message event listeners on the Discord gateway
     */
    public void register(GatewayDiscordClient gateway) {
        registerCommandDispatcher(gateway);
        registerChatBridge(gateway);
    }

    /**
     * Register command dispatcher for Discord commands
     */
    private void registerCommandDispatcher(GatewayDiscordClient gateway) {
        gateway.on(MessageCreateEvent.class)
                .filter(event -> event.getMessage().getAuthor().map(user -> !user.isBot()).orElse(false))
                .subscribe(event -> {
                    var member = event.getMember().orElse(null);
                    var message = event.getMessage();
                    if (member == null || member.isBot()) return;

                    message.getChannel()
                            .map(channel -> new MessageContext(event.getMessage(), member, channel, globalConfig))
                            .subscribe(context -> {
                                var response = commandRegistry.getHandler().handleMessage(message.getContent(), context);

                                switch (response.type) {
                                    case fewArguments ->
                                            context.error("Too Few Arguments", "Usage: @**@** @",
                                                    commandRegistry.getHandler().prefix, response.runCommand, response.command.paramText).subscribe();
                                    case manyArguments ->
                                            context.error("Too Many Arguments", "Usage: @**@** @",
                                                    commandRegistry.getHandler().prefix, response.runCommand, response.command.paramText).subscribe();
                                    case unknownCommand -> {
                                    }
                                }
                            });
                });
    }

    /**
     * Register chat bridge (Discord → Game)
     */
    private void registerChatBridge(GatewayDiscordClient gateway) {
        gateway.on(MessageCreateEvent.class)
                .filter(event -> event.getMessage().getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(event -> globalConfig.servers.containsValue(event.getMessage().getChannelId().asLong()))
                .filter(event -> !event.getMessage().getContent().startsWith("/"))
                .subscribe(event -> {
                    var author = event.getMember().orElse(null);
                    var message = event.getMessage();
                    var content = message.getContent();

                    String server = findServerByChannelId(event.getMessage().getChannelId().asLong());

                    if (server == null || author == null) return;

                    if (server.equals(config.server)) {
                        sendMessageToGameFromDiscord(author.getDisplayName(), content);
                    } else {
                        network.post(
                                new SocketEvents.DiscordMessageEvent(author.getDisplayName(), content, server)
                        );
                    }
                });
    }

    /**
     * Send a message from Discord to the game chat
     */
    public void sendMessageToGameFromDiscord(String authorName, String message) {
        Log.infoTag("Discord", format("@: @", authorName, message));
        sessionService.broadcast("discord-message-format", args(
                "author", authorName,
                "message", message
        ));
    }

    private String findServerByChannelId(long channelId) {
        return globalConfig.servers.getByValue(channelId);
    }
}
