package org.xcore.plugin.modules.discord;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.common.util.TimestampFormat;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;
import reactor.core.publisher.Mono;

import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.commands.DiscordCommands.discordCommands;

public class Bot {
    public static MessageChannel bansChannel;
    public static MessageChannel privateChannel;

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

            bansChannel = gateway.getChannelById(Snowflake.of(globalConfig.discordBansChannelId)).ofType(MessageChannel.class).block();
            privateChannel = gateway.getChannelById(Snowflake.of(globalConfig.discordPrivateChannelId)).ofType(MessageChannel.class).block();

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

                    NetSock.post(new SocketEvents.AdminRequestConfirmEvent(data.uuid, server));

                    message.delete().subscribe();

                    data.adminConfirmed = true;

                    NetSock.post(new SocketEvents.SyncPlayerData(data));
                    data.save();
                    return event.reply(author.getDisplayName() + " confirmed adminship to player " +
                            data.nickname + " on server " + server);
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
                                .map(channel -> new MessageContext(event.getMessage(), member, channel))
                                .subscribe(context -> {
                                    var response = discordCommands.handleMessage(message.getContent(), context);

                                    switch (response.type) {
                                        case fewArguments ->
                                                context.error("Too Few Arguments", "Usage: @**@** @", discordCommands.prefix, response.runCommand, response.command.paramText).subscribe();
                                        case manyArguments ->
                                                context.error("Too Many Arguments", "Usage: @**@** @", discordCommands.prefix, response.runCommand, response.command.paramText).subscribe();
                                        case unknownCommand ->
                                                context.error("Unknown Command", "To see a list of all available commands, use @**help**", discordCommands.prefix).subscribe();
                                    }
                                });
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
                            NetSock.post(
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

    public static RestChannel getServerLogChannel(String server) {
        return client.getChannelById(Snowflake.of(globalConfig.servers.get(server)));
    }

    public static void sendMessageEvent(String playerName, String message, String server) {
        if (!isConnected) return;

        if (globalConfig.servers.get(server) == null) {
            Log.err("@ server has no log channel id!", server);
            return;
        }

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

        privateChannel.createMessage(MessageCreateSpec.builder()
                .addEmbed(embed.build())
                .build()).subscribe();
    }

    public static void sendBan(BanData ban) {
        if (!isConnected) return;

        PlayerData data = database.getPlayerDatas().getPlayerData(ban.uuid);

        bansChannel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder()
                        .title("Ban")
                        .color(Color.RED)
                        .addField("ID", String.valueOf(data == null ? -1 : data.pid), false)
                        .addField("Violator", ban.name, false)
                        .addField("Admin", ban.adminName, false)
                        .addField("Reason", ban.reason, false)
                        .addField("Unban Date", TimestampFormat.LONG_DATE.format(ban.unbanDate.toInstant()), false)
                        .build()
                )
                .build()).subscribe();
    }

    public static void sendAdminRequestEvent(int pid, String server) {
        PlayerData data = database.getPlayerDatas().getPlayerDataById(pid);

        privateChannel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder().title("Admin Request")
                        .color(Color.RED)
                        .addField("Name", data.nickname, false)
                        .addField("Server", server, false)
                        .build())
                .addComponent(ActionRow.of(
                        Button.success(server + "_" + pid + "_admreq", ReactionEmoji.unicode("✅"), "Confirm"),
                        Button.danger("decline", ReactionEmoji.unicode("❌"), "Decline")))
                .build()).subscribe();
    }
}
