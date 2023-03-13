package org.xcore.plugin.modules.discord;

import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import discord4j.common.util.Snowflake;
import discord4j.common.util.TimestampFormat;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.Embed;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.*;
import discord4j.discordjson.possible.Possible;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.util.Color;
import org.reactivestreams.Publisher;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.Database;
import org.xcore.plugin.utils.JavelinCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.xcore.plugin.PluginVars.*;

public class Bot {
    public static Mono<GuildMessageChannel> bansChannel;
    public static Mono<Role> adminRole;

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
            adminRole = gateway.getRoleById(
                    Snowflake.of("1058023472661549176"),
                    Snowflake.of(globalConfig.discordAdminRoleId));

            onEvent(ButtonInteractionEvent.class, event -> {
                if (event.getCustomId().equals("edit-ban")) {
                    return event.presentModal(InteractionPresentModalSpec.builder()
                            .title("Edit reason and ban duration")
                            .customId("editban")
                            .addComponent(ActionRow.of(TextInput.small("reason", "Reason", 3, 100).required()))
                            .addComponent(ActionRow.of(TextInput.small("duration", "Duration", 1, 6).required()))
                            .build());
                }

                if (event.getCustomId().endsWith("unban")) {
                    var author = event.getInteraction().getMember().orElse(null);

                    if (author == null) return Mono.empty();
                    if (!author.getRoleIds().contains(Snowflake.of(globalConfig.discordAdminRoleId)))
                        return Mono.empty();

                    long bid = Long.parseLong(event.getCustomId().split("-")[0]);

                    BanData ban = Database.getBanById(bid);
                    ban.unban = true;

                    if (!ban.server.equals(config.server)) {
                        JavelinCommunicator.sendEvent(ban);
                    } else {
                        Utils.handleBanData(ban);
                    }

                    var message = event.getMessage().orElseThrow();
                    message.edit(MessageEditSpec.builder()
                            .addEmbed(toEmbedCreateSpecBuilder(message.getEmbeds().get(0))
                                    .footer("Unbanned by " + author.getDisplayName(), author.getEffectiveAvatarUrl())
                                    .build()
                            )
                            .components(List.of())
                            .build()).subscribe();

                    return event.reply("Successfully").withEphemeral(true);
                }
                return Mono.empty();
            });

            onEvent(ModalSubmitInteractionEvent.class, event -> {
                if (event.getCustomId().equals("editban")) {
                    var author = event.getInteraction().getMember().orElse(null);

                    if (author == null) return Mono.empty();
                    if (!author.getRoleIds().contains(Snowflake.of(globalConfig.discordAdminRoleId)))
                        return Mono.empty();

                    var components = event.getComponents(TextInput.class);

                    String reason = null;
                    String duration = null;
                    for (TextInput component : components) {
                        if (component.getCustomId().equals("reason")) reason = component.getValue().orElse(null);
                        else duration = component.getValue().orElse(null);
                    }

                    if (reason == null || duration == null || !Strings.canParseInt(duration)) return Mono.empty();

                    var message = event.getMessage().orElseThrow();

                    BanData ban = activeBanData.get(message.getId().asLong());
                    ban.generateBid();
                    ban.reason = reason;
                    ban.unbanDate = Time.millis() + TimeUnit.DAYS.toMillis(Strings.parseInt(duration));
                    Database.setBan(ban);

                    var edit = message.edit(MessageEditSpec.builder()
                            .addEmbed(toEmbedCreateSpecBuilder(message.getEmbeds().get(0))
                                    .addField("Reason", reason, false)
                                    .addField("Unban date", TimestampFormat.LONG_DATE.format(Instant.ofEpochMilli(ban.unbanDate)), false)
                                    .build())
                            .components(List.of(ActionRow.of(Button.danger(ban.bid + "-unban", "Unban"))))
                            .build());
                    return Mono.zip(edit, event.reply("Successfully.").withEphemeral(true));
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
                    JavelinCommunicator.sendEvent(
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

    public static void sendMessageEvent(String playerName, String message) {
        sendMessageEvent(playerName, message, config.server);
    }

    public static void sendMessageEvent(String playerName, String message, String server) {
        if (!isConnected) return;
        getServerLogChannel(server).createMessage(
                Strings.format("`@: @`", playerName, message)
        ).subscribe();
    }

    public static void sendServerAction(String message) {
        sendServerAction(message, config.server);
    }

    public static void sendServerAction(String message, String server) {
        if (!isConnected) return;
        getServerLogChannel(server).createMessage(message).subscribe();
    }

    public static void sendJoinLeaveEventMessage(String playerName, Boolean join) {
        sendJoinLeaveEventMessage(playerName, config.server, join);
    }

    public static void sendJoinLeaveEventMessage(String playerName, String server, Boolean join) {
        if (!isConnected) return;
        getServerLogChannel(server).createMessage(
                Strings.format("`@` " + (join ? "joined" : "left"), playerName)
        ).subscribe();
    }

    public static void sendBanEvent(BanData ban) {
        if (!isConnected) return;
        bansChannel.flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder().title("Ban")
                        .color(Color.RED)
                        .addField("Violator", ban.name, false)
                        .addField("Admin", ban.adminName, false)
                        .addField("Server", ban.server, false)
                        .build())
                .addComponent(ActionRow.of(Button.danger("edit-ban", "Edit reason and ban duration.")))
                .build())).subscribe(data -> activeBanData.put(data.getId().asLong(), ban));
    }

    public static <E extends Event, T> void onEvent(Class<E> eventClass, Function<E, Publisher<T>> mapper) {
        gateway.on(eventClass, mapper).
                doOnError(Log::err).
                subscribe();
    }

    public static EmbedCreateSpec.Builder toEmbedCreateSpecBuilder(Embed embed) {
        return EmbedCreateSpec.builder()
                .title(embed.getData().title())
                .description(embed.getData().description())
                .url(embed.getData().url())
                .timestamp(embed.getTimestamp()
                        .map(Possible::of)
                        .orElse(Possible.absent()))
                .color(embed.getColor()
                        .map(Possible::of)
                        .orElse(Possible.absent()))
                .footer(embed.getFooter()
                        .map(d -> EmbedCreateFields.Footer.of(d.getText(), d.getIconUrl().orElse(null)))
                        .orElse(null))
                .image(embed.getImage()
                        .map(Embed.Image::getUrl)
                        .map(Possible::of)
                        .orElse(Possible.absent()))
                .author(embed.getAuthor()
                        .map(d -> EmbedCreateFields.Author.of(d.getName().orElse(null),
                                d.getUrl().orElse(null), d.getIconUrl().orElse(null)))
                        .orElse(null))
                .thumbnail(embed.getThumbnail()
                        .map(Embed.Thumbnail::getUrl)
                        .map(Possible::of)
                        .orElse(Possible.absent()))
                .fields(embed.getFields().stream()
                        .map(d -> EmbedCreateFields.Field.of(d.getName(), d.getValue(), d.isInline()))
                        .collect(Collectors.toList()));
    }
}
