package org.xcore.plugin.discord;

import arc.util.Log;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.NetworkService;

@Singleton
public class DiscordService {

    private final GlobalConfig globalConfig;
    private final NetworkService network;
    private final DiscordCommandRegistry commandRegistry;
    private final DiscordInteractionHandler interactionHandler;
    private final DiscordMessageHandler messageHandler;
    private final DiscordLogBridge logBridge;

    private DiscordClient client;
    @Getter
    private GatewayDiscordClient gateway;
    private boolean isConnected = false;

    @Inject
    public DiscordService(
            GlobalConfig globalConfig,
            NetworkService network,
            DiscordCommandRegistry commandRegistry,
            DiscordInteractionHandler interactionHandler,
            DiscordMessageHandler messageHandler,
            DiscordLogBridge logBridge
    ) {
        this.globalConfig = globalConfig;
        this.network = network;
        this.commandRegistry = commandRegistry;
        this.interactionHandler = interactionHandler;
        this.messageHandler = messageHandler;
        this.logBridge = logBridge;
    }

    @PostConstruct
    public void connect() {
        if (!network.isSocketServer()) return;

        try {
            if (globalConfig.discordBotToken.isEmpty()) {
                Log.warn("Discord bot token is not set. Discord integration disabled.");
                return;
            }

            client = DiscordClientBuilder.create(globalConfig.discordBotToken)
                    .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                    .build();

            try {
                gateway = client.gateway()
                        .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT))
                        .login()
                        .blockOptional()
                        .orElseThrow();
            } catch (Exception e) {
                Log.err("Error while connecting to discord gateway: ", e);
                return;
            }

            isConnected = true;

            logBridge.initialize(gateway, isConnected);
            interactionHandler.register(gateway);
            messageHandler.register(gateway);
            commandRegistry.registerCommands(this);

            Log.info("Discord integration initialized successfully.");
        } catch (Exception e) {
            Log.err("Error inside discord module logic: ", e);
        }
    }
}
