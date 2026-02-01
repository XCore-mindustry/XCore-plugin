package org.xcore.plugin.discord;

import arc.util.Log;
import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.emoji.UnicodeEmoji;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import mindustry.gen.Call;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerData;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class DiscordService {

    private final GlobalConfig globalConfig;
    private final Config config;
    private final DatabaseService database;
    private final NetworkService network;
    private final DiscordCommandRegistry commandRegistry;
    private final BundleService bundleService;

    private MessageChannel bansChannel;
    private MessageChannel privateChannel;
    private DiscordClient client;
    @Getter
    private GatewayDiscordClient gateway;
    private boolean isConnected = false;

    @Inject
    public DiscordService(GlobalConfig globalConfig, Config config, DatabaseService database,
                          NetworkService network, DiscordCommandRegistry commandRegistry,
                          BundleService bundleService) {
        this.globalConfig = globalConfig;
        this.config = config;
        this.database = database;
        this.network = network;
        this.commandRegistry = commandRegistry;
        this.bundleService = bundleService;
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

            bansChannel = safeGetChannel(globalConfig.discordBansChannelId, "Bans");
            privateChannel = safeGetChannel(globalConfig.discordPrivateChannelId, "Private/Admin");

            registerListeners();
            commandRegistry.registerCommands(this); // Регистрируем команды

            isConnected = true;
        } catch (Exception e) {
            Log.err("Error inside discord module logic: ", e);
        }
    }

    private void registerListeners() {
        gateway.on(ButtonInteractionEvent.class, event -> {
            var author = event.getInteraction().getMember().orElse(null);
            var message = event.getMessage().orElse(null);

            if (author == null || message == null) return Mono.empty();
            if (!author.getRoleIds().contains(Snowflake.of(globalConfig.discordAdminRoleId)))
                return event.reply("Access denied").withEphemeral(true);

            if (event.getCustomId().endsWith("admreq")) {
                String[] args = event.getCustomId().split("_");

                String server = args[0];
                PlayerData data = database.getCachedOrDb(Strings.parseInt(args[1]));
                var adminData = database.getAdminDataRepository().findByUuid(data.uuid);

                network.post(new SocketEvents.AdminRequestConfirmEvent(data.uuid, server));

                adminData.adminConfirmed = true;
                database.getAdminDataRepository().save(adminData);

                return message.getRestChannel().createMessage(author.getDisplayName() + " confirmed adminship to player " +
                        data.nickname + " on server " + server).then(message.delete());
            }

            if (event.getCustomId().equals("decline")) {
                return message.delete(author.getDisplayName());
            }

            return Mono.empty();
        }).subscribe();

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
                                        // Ignore unknown commands or handle differently
                                    }
                                }
                            });
                });

        gateway.on(MessageCreateEvent.class)
                .filter(event -> event.getMessage().getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(event -> globalConfig.servers.containsValue(event.getMessage().getChannelId().asLong(), false)
                        && !event.getMessage().getContent().startsWith("/"))
                .subscribe(event -> {
                    var author = event.getMember().orElse(null);
                    var message = event.getMessage();
                    var content = message.getContent();

                    String server = globalConfig.servers.findKey(event.getMessage().getChannelId().asLong(), false);

                    if (server == null) return;

                    if (server.equals(config.server)) {
                        sendMessageToGameFromDiscord(author.getDisplayName(), content);
                    } else {
                        network.post(
                                new SocketEvents.DiscordMessageEvent(author.getDisplayName(), content, server)
                        );
                    }
                });
    }

    private MessageChannel safeGetChannel(long id, String name) {
        if (id == 0L) {
            Log.warn("Discord channel '@' ID is not configured (0). Feature disabled.", name);
            return null;
        }

        try {
            return gateway.getChannelById(Snowflake.of(id))
                    .ofType(MessageChannel.class)
                    .block();
        } catch (ClientException e) {
            if (e.getStatus().code() == 404) {
                Log.warn("Discord channel '@' (ID: @) not found (404). Check your config.", name, id);
            } else if (e.getStatus().code() == 403) {
                Log.warn("Discord bot has no access to channel '@' (ID: @).", name, id);
            } else {
                Log.warn("Could not fetch Discord channel '@' (ID: @): @", name, id, e.getMessage());
            }
            return null;
        } catch (Exception e) {
            Log.warn("Unexpected error fetching Discord channel '@': @", name, e.getMessage());
            return null;
        }
    }

    public Optional<MessageChannel> getServerLogChannel(String server) {
        var id = globalConfig.servers.get(server);
        if (id == null) {
            Log.err("@ server has no log channel id!", server);
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(gateway.getChannelById(Snowflake.of(id)).ofType(MessageChannel.class).block());
        } catch (Exception e) {
            Log.warn("Failed to get log channel for server '@' (ID: @): @", server, id, e.getMessage());
            return Optional.empty();
        }
    }

    public void sendMessageEvent(String playerName, String message, String server) {
        if (!isConnected) return;

        if (globalConfig.servers.get(server) == null) {
            Log.err("@ server has no log channel id!", server);
            return;
        }

        getServerLogChannel(server).ifPresent(c -> sendMessage(c, Strings.format("`@: @`", playerName, message)));
    }

    public void sendMessage(MessageChannel channel, String message) {
        if (!isConnected || channel == null) return;
        channel.createMessage(MessageCreateSpec.builder()
                .content(message)
                .allowedMentions(AllowedMentions.suppressAll())
                .build()).subscribe();
    }

    public void sendMessageToGameFromDiscord(String authorName, String message) {
        Log.infoTag("Discord", Strings.format("@: @", authorName, message));
        bundleService.send("discord-message-format", args(
                "author", authorName,
                "message", message
        ));
    }


    public void sendConnectionEvent(String playerName, String server, Boolean join) {
        if (!isConnected) return;
        getServerLogChannel(server).ifPresent(c -> sendMessage(c, Strings.format("`@` " + (join ? "joined" : "left"), playerName)));
    }

    public void sendBan(BanData ban) {
        if (!isConnected || bansChannel == null) return;

        PlayerData data = database.getPlayerDataRepository().findByUuid(ban.uuid);

        bansChannel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder()
                        .title("Ban")
                        .color(Color.RED)
                        .addField("ID", String.valueOf(data == null ? -1 : data.pid), false)
                        .addField("Violator", ban.name, false)
                        .addField("Admin", ban.adminName, false)
                        .addField("Reason", ban.reason, false)
                        .addField("Unban Date", discord4j.common.util.TimestampFormat.LONG_DATE.format(ban.expireDate), false)
                        .build()
                )
                .build()).subscribe();
    }

    public void sendAdminRequestEvent(int pid, String server) {
        if (!isConnected || privateChannel == null) return;

        PlayerData data = database.getPlayerDataRepository().findById(pid);

        privateChannel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder().title("Admin Request")
                        .color(Color.RED)
                        .addField("Name", data.nickname, false)
                        .addField("Server", server, false)
                        .build())
                .addComponent(ActionRow.of(
                        Button.success(server + "_" + pid + "_admreq", UnicodeEmoji.unicode("✅"), "Confirm"),
                        Button.danger("decline", UnicodeEmoji.unicode("❌"), "Decline")))
                .build()).subscribe();
    }
}
