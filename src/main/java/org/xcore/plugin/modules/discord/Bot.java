package org.xcore.plugin.modules.discord;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.util.Color;
import org.reactivestreams.Publisher;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.globalConfig;

public class Bot {
    public static Mono<GuildMessageChannel> bansChannel, privateChannel;

    public static DiscordClient client;
    public static GatewayDiscordClient gateway;

    public static boolean isConnected = false;

    public static void connect() {
        try {
            client = DiscordClient.create(globalConfig.discordBotToken);
            gateway = client.gateway().
                    setEnabledIntents(IntentSet.of(Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES))
                    .login().block();

            bansChannel = gateway.getChannelById(Snowflake.of(globalConfig.discordBansChannelId)).ofType(GuildMessageChannel.class);
            privateChannel = gateway.getChannelById(Snowflake.of(globalConfig.discordPrivateChannelId)).ofType(GuildMessageChannel.class);

            onEvent(ButtonInteractionEvent.class, event -> {
                var author = event.getInteraction().getMember().orElse(null);
                var message = event.getMessage().orElse(null);

                if (author == null || message == null) return Mono.empty();
                if (!author.getRoleIds().contains(Snowflake.of(globalConfig.discordAdminRoleId)))
                    return event.reply("Access denied").withEphemeral(true);

                if (event.getCustomId().endsWith("admreq")) {
                    String[] args = event.getCustomId().split("_");

                    String server = args[0];
                    String uuid = args[1];

                    SockCommunicator.sendEvent(new SocketEvents.AdminRequestConfirmEvent(uuid, server));

                    var info = Find.playerInfo(uuid);

                    message.delete().subscribe();

                    return event.reply(author.getDisplayName() + " confirmed adminship to player "
                            + (info != null ? info.lastName : "<unknown>") + " on server " + server);
                }

                if (event.getCustomId().equals("decline")) {
                    return message.delete(author.getDisplayName());
                }

                return Mono.empty();
            });

            onEvent(MessageCreateEvent.class, event -> {
                var author = event.getMember().orElse(null);
                if (author == null || author.isBot() || event.getMessage().getContent().isBlank())
                    return Mono.empty();

                if (!globalConfig.servers.containsValue(event.getMessage().getChannelId().asLong(), false) && !event.getMessage().getContent().startsWith("/"))
                    return Mono.empty();

                String server = globalConfig.servers.findKey(event.getMessage().getChannelId().asLong(), false);

                if (server == null) return Mono.empty();

                if (server.equals(config.server)) {
                    XcorePlugin.sendMessageFromDiscord(author.getDisplayName(), event.getMessage().getContent());
                } else {
                    SockCommunicator.sendEvent(
                            new SocketEvents.DiscordMessageEvent(author.getDisplayName(), event.getMessage().getContent(), server)
                    );
                }
                return Mono.empty();
            });

            isConnected = true;
        } catch (Exception e) {
            XcorePlugin.err("Error while connecting to discord: ");
            e.printStackTrace();
        }
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

    public static void sendAdminRequestEvent(String uuid, String name, String server) {
        privateChannel.flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder().title("Admin Request")
                        .color(Color.RED)
                        .addField("Name", name, false)
                        .addField("Server", server, false)
                        .build())
                .addComponent(ActionRow.of(Button.danger(server + "_" + uuid + "_admreq", "Confirm"),
                        Button.success("decline", "Decline")))
                .build())).subscribe();
    }

    public static void sendUnban(BanData data) {
        bansChannel.flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                        .addEmbed(EmbedCreateSpec.builder().title("Player Ban Expired")
                                .color(Color.GREEN)
                                .addField("Player", data.name, false)
                                .addField("Reason", data.reason, false)
                                .addField("Admin", data.adminName, false)
                                .build())
                        .build()))
                .subscribe();
    }

    public static <E extends Event, T> void onEvent(Class<E> eventClass, Function<E, Publisher<T>> mapper) {
        gateway.on(eventClass, mapper)
                .doOnError(Log::err)
                .subscribe();
    }
}
