package org.xcore.plugin.modules.discord;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Http;
import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.component.ActionComponent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import io.netty.handler.timeout.TimeoutException;
import mindustry.io.MapIO;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.models.PlayerData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static mindustry.Vars.dataDirectory;
import static org.xcore.plugin.PluginVars.*;

public class Bot {
    public static Mono<GuildMessageChannel> bansChannel, privateChannel;

    public static DiscordClient client;
    public static GatewayDiscordClient gateway;

    public static boolean isConnected = false;

    public static void connect() {
        try {
            client = DiscordClientBuilder.create(globalConfig.discordBotToken)
                    .setDefaultAllowedMentions(AllowedMentions.suppressAll())
                    .build();
            gateway = client.gateway()
                    .setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES))
                    .login()
                    .blockOptional()
                    .orElseThrow();

            bansChannel = gateway.getChannelById(Snowflake.of(globalConfig.discordBansChannelId)).ofType(GuildMessageChannel.class);
            privateChannel = gateway.getChannelById(Snowflake.of(globalConfig.discordPrivateChannelId)).ofType(GuildMessageChannel.class);

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

                    SockCommunicator.sendEvent(new SocketEvents.AdminRequestConfirmEvent(data.uuid, server));

                    message.delete().subscribe();

                    data.adminConfirmed = true;

                    SockCommunicator.sendEvent(new SocketEvents.SyncPlayerData(data));
                    database.getPlayerDataExecutor().setPlayerData(data);
                    return event.reply(author.getDisplayName() + " confirmed adminship to player " +
                            data.nickname + " on server " + server);
                }

                if (event.getCustomId().equals("decline")) {
                    return message.delete(author.getDisplayName());
                }

                return Mono.empty();
            }).subscribe();

            command("upload-map")
                    .filter(event -> DiscordHelper.hasRole(event.getMember(), globalConfig.discordMapReviewerRoleId))
                    .map(MessageCreateEvent::getMessage)
                    .filter(message -> !message.getAttachments().isEmpty())
                    .subscribe(message -> {
                        Seq<String> maps = new Seq<>();
                        message.getAttachments().forEach(attachment -> Http.get(attachment.getUrl())
                                .error(err -> message.getRestChannel().createMessage(attachment.getFilename() + " is not valid map file!").subscribe())
                                .block((response) -> {
                                    String filename = attachment.getFilename().endsWith(".msav") ? attachment.getFilename() : attachment.getFilename() + ".msav";
                                    var file = dataDirectory.child("tmp").child(filename);
                                    file.writeBytes(response.getResult());

                                    MapIO.createMap(new Fi(file.toString()), true);

                                    maps.add(file.absolutePath());
                                }));

                        if (maps.isEmpty()) return;

                        List<ActionComponent> servers = new ArrayList<>();
                        for (String key : globalConfig.servers.keys()) {
                            servers.add(Button.primary(key, key));
                        }

                        message.getChannel().flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                                        .content("Choose server:")
                                        .addComponent(ActionRow.of(servers))
                                        .build()))
                                .doOnNext(m -> gateway.on(ButtonInteractionEvent.class)
                                        .filter(event -> DiscordHelper.hasRole(event.getInteraction().getMember(), globalConfig.discordMapReviewerRoleId))
                                        .filter(event -> m.getId().asLong() == event.getMessageId().asLong())
                                        .timeout(Duration.ofMinutes(3))
                                        .onErrorResume(TimeoutException.class, ignore -> {
                                            maps.each(map -> new Fi(map).delete());
                                            return Mono.empty();
                                        })
                                        .subscribe(e -> {
                                            SockCommunicator.sendEvent(new SocketEvents.LoadMaps(maps.toArray(String.class), e.getCustomId()));
                                            m.delete().subscribe();
                                            e.reply("Successfully uploaded maps").subscribe();
                                        }))
                                .subscribe();
                    });
            gateway.on(MessageCreateEvent.class)
                    .filter(event -> event.getMessage().getAuthor().map(user -> !user.isBot()).orElse(false))
                    .filter(event -> globalConfig.servers.containsValue(event.getMessage().getChannelId().asLong(), false) && !event.getMessage().getContent().startsWith("/"))
                    .subscribe(event -> {
                        var author = event.getMember().orElse(null);
                        var message = event.getMessage();
                        var content = message.getContent();

                        String server = globalConfig.servers.findKey(event.getMessage().getChannelId().asLong(), false);

                        if (server == null) return;

                        if (server.equals(config.server)) {
                            XcorePlugin.sendMessageFromDiscord(author.getDisplayName(), content);
                        } else {
                            SockCommunicator.sendEvent(
                                    new SocketEvents.DiscordMessageEvent(author.getDisplayName(), content, server)
                            );
                        }
                    });

            isConnected = true;
        } catch (Exception e) {
            XcorePlugin.err("Error while connecting to discord: ");
            e.printStackTrace();
        }
    }

    public static Flux<MessageCreateEvent> command(String name) {
        return gateway.on(MessageCreateEvent.class)
                .filter(event -> event.getMessage().getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(event -> event.getMessage().getContent().startsWith(globalConfig.discordCommandPrefix + name));
    }

    public static RestChannel getServerLogChannel(String server) {
        return client.getChannelById(Snowflake.of(globalConfig.servers.get(server)));
    }

    public static void sendMessageEvent(String playerName, String message, String server) {
        if (!isConnected) return;
        getServerLogChannel(server).createMessage(
                Strings.format("`@: @`", playerName, message)
        ).subscribe();
    }

    public static void sendJoinLeaveEventMessage(String playerName, String server, Boolean join) {
        if (!isConnected) return;
        getServerLogChannel(server).createMessage(
                Strings.format("`@` " + (join ? "joined" : "left"), playerName)
        ).subscribe();
    }

    public static void sendAdminPlayTimeMessage(Seq<PlayerData> datas) {
        if (!isConnected) return;
        EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder().title("Admin Activity Today")
                .color(Color.RED);

        for (PlayerData data : datas) {
            embed.addField(data.nickname, Strings.format("@/@ minutes", data.playTime, data.totalPlayTime), false);
        }

        privateChannel.flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                .addEmbed(embed.build())
                .build())).subscribe();
    }

    public static void sendAdminRequestEvent(int pid, String server) {
        PlayerData data = database.getPlayerDataExecutor().getPlayerDataById(pid);

        privateChannel.flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder().title("Admin Request")
                        .color(Color.RED)
                        .addField("Name", data.nickname, false)
                        .addField("Server", server, false)
                        .build())
                .addComponent(ActionRow.of(Button.success(server + "_" + pid + "_admreq", "Confirm"),
                        Button.danger("decline", "Decline")))
                .build())).subscribe();
    }
}
