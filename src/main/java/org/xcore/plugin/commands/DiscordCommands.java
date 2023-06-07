package org.xcore.plugin.commands;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Http;
import arc.util.Strings;
import arc.util.Time;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionComponent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.MessageCreateSpec;
import io.netty.handler.timeout.TimeoutException;
import mindustry.io.MapIO;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.discord.DiscordHelper;
import org.xcore.plugin.modules.discord.MessageContext;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.dataDirectory;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.database;
import static org.xcore.plugin.PluginVars.globalConfig;
import static org.xcore.plugin.modules.discord.Bot.gateway;
import static org.xcore.plugin.modules.discord.Bot.sendBan;

public class DiscordCommands {
    public static final CommandHandler discordCommands = new CommandHandler(globalConfig.discordCommandPrefix);

    public static void init() {
        discordCommands.<MessageContext>register("help", "List of all commands.", (args, context) -> {
            var builder = new StringBuilder();
            discordCommands.getCommandList().each(command -> builder.append(discordCommands.prefix).append("**").append(command.text).append("**").append(!command.paramText.isEmpty() ? " " + command.paramText : "").append(" - ").append(command.description).append("\n"));
            context.info("All available commands:", builder.toString()).subscribe();
        });
        discordCommands.<MessageContext>register("upload-map", "Upload map to the servers", (args, context) -> {
            if (DiscordHelper.noRole(context, globalConfig.discordMapReviewerRoleId)) return;

            if (context.message().getAttachments().isEmpty()) {
                context.error("Attach a file", "You need to attach a file with the extension \"msav\"").subscribe();
                return;
            }

            Seq<String> maps = new Seq<>();
            context.message().getAttachments().forEach(attachment -> Http.get(attachment.getUrl())
                    .error(err -> context.error("Invalid file", attachment.getFilename() + " is not valid map file!").subscribe())
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

            context.channel().createMessage(MessageCreateSpec.builder()
                            .content("Choose server:")
                            .addComponent(ActionRow.of(servers))
                            .build())
                    .doOnNext(m -> gateway.on(ButtonInteractionEvent.class)
                            .filter(event -> context.member().getId().asLong() == event.getInteraction().getMember().orElseThrow().getId().asLong())
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

        discordCommands.<MessageContext>register("ban", "<player-id> <period> [reason...]", "Ban the player", (args, context) -> {
            if (DiscordHelper.noRole(context, globalConfig.discordAdminRoleId)) return;

            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDataExecutor().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            Instant date = Utils.parsePeriod(args[1], TimeUnit.DAYS);
            if (DiscordHelper.checkPeriod(context, date)) return;

            Date unbanDate = new Date(Time.millis() + date.toEpochMilli());
            var info = netServer.admins.getInfoOptional(data.uuid);
            String ip = info != null ? info.lastIP : null;

            context.channel().createMessage(MessageCreateSpec.builder()
                    .content("Are you sure you want to ban a player named '" + data.nickname + "'?")
                    .addComponent(ActionRow.of(Button.primary("yes", "Yes"), Button.danger("no", "No")))
                    .build()).subscribe(m -> gateway.on(ButtonInteractionEvent.class)
                    .filter(event -> context.member().getId().asLong() == event.getInteraction().getMember().orElseThrow().getId().asLong())
                    .subscribe(event -> {
                        if (event.getCustomId().equals("yes")) {
                            SockCommunicator.sendEvent(new SocketEvents.KickBannedPlayer(data.uuid, ip));

                            BanData ban = BanData.builder()
                                    .name(data.nickname)
                                    .uuid(data.uuid)
                                    .ip(ip)
                                    .adminName(context.member().getDisplayName())
                                    .reason(args.length > 2 ? args[2] : "Not Specified")
                                    .unbanDate(unbanDate)
                                    .build();

                            sendBan(ban);
                            ban.save();
                            context.success("Success", "Successfully banned player '" + data.nickname + "'").subscribe();
                        }

                        event.getInteraction().getMessage().ifPresent(message -> message.delete().subscribe());
                    }));
        });

        discordCommands.<MessageContext>register("unban", "<player-id>", "Unban player", (args, context) -> {
            if (DiscordHelper.noRole(context, globalConfig.discordAdminRoleId)) return;

            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDataExecutor().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            var info = netServer.admins.getInfoOptional(data.uuid);
            String ip = info != null ? info.lastIP : null;

            database.getBanDataExecutor().deleteBan(data.uuid, ip);
            context.success("Success", "'@' unbanned", data.nickname).subscribe();
        });
    }
}

