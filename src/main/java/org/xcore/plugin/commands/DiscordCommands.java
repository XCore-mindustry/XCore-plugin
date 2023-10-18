package org.xcore.plugin.commands;

import arc.util.*;
import discord4j.core.event.domain.interaction.*;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.Attachment;
import discord4j.core.spec.MessageCreateSpec;
import io.netty.handler.timeout.TimeoutException;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.listeners.SocketEvents.*;
import org.xcore.plugin.modules.discord.*;
import org.xcore.plugin.utils.*;
import org.xcore.plugin.utils.models.BanData;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.discord.Bot.*;

public class DiscordCommands {
    public static final CommandHandler discordCommands = new CommandHandler(globalConfig.discordCommandPrefix);

    public static void init() {
        discordCommands.<MessageContext>register("help", "List of all commands.", (args, context) -> {
            var builder = new StringBuilder();
            discordCommands.getCommandList().each(command -> builder.append(discordCommands.prefix).append("**").append(command.text).append("**").append(!command.paramText.isEmpty() ? " " + command.paramText : "").append(" - ").append(command.description).append("\n"));
            context.info("All available commands:", builder.toString()).subscribe();
        });

        discordCommands.<MessageContext>register("stats", "<player-id>", "Show player stats.", (args, context) -> {
            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            context.success((embed) -> embed.title(Strings.stripColors(data.nickname) + " Stats")
                            .addField("ID", String.valueOf(data.pid), false)
                            .addField("Total playtime", String.valueOf(data.totalPlayTime), false)
                            .addField("Hexed Rank", data.hexedRank().name(), false)
                            .addField("MiniPvP rating", String.valueOf(data.pvpRating), true))
                    .subscribe();
        });

        discordCommands.<MessageContext>register("upload-map", "Upload map to the servers", (args, context) -> {
            if (DiscordHelper.noRole(context, globalConfig.discordMapReviewerRoleId)) return;

            var attachments = context.message()
                    .getAttachments()
                    .stream()
                    .filter(attachment -> attachment.getFilename().endsWith(".msav"))
                    .map(Attachment::getUrl)
                    .toList();

            if (attachments.isEmpty()) {
                context.error("Attach a file", "You need to attach a file with the extension 'msav'").subscribe();
                return;
            }

            List<SelectMenu.Option> servers = new ArrayList<>();
            for (var key : globalConfig.servers.keys()) {
                servers.add(SelectMenu.Option.of(key, key));
            }

            context.channel().createMessage(MessageCreateSpec.builder()
                            .content("Choose server:")
                            .addComponent(ActionRow.of(SelectMenu.of("choose-server", servers).withMaxValues(1).withPlaceholder("Choose server")))
                            .build())
                    .doOnNext(message -> gateway.on(SelectMenuInteractionEvent.class)
                            .filter(event -> event.getCustomId().equals("choose-server")
                                    && event.getMessageId().equals(message.getId())
                                    && event.getInteraction().getMember().get().equals(context.member()))
                            .onErrorResume(TimeoutException.class, exception -> Mono.empty())
                            .subscribe(event -> {
                                NetSock.post(new SocketEvents.LoadMaps(attachments.toArray(new String[0]), event.getValues().get(0)));
                                message.delete().subscribe();
                                context.success("Success", "Successfully uploaded maps to " + event.getValues().get(0)).subscribe();
                            }))
                    .subscribe();
        });

        discordCommands.<MessageContext>register("maps", "<server>", "List of maps", (args, context) -> {
            var server = Utils.findServer(args[0]);
            if (DiscordHelper.notFound(context, server)) return;

            NetSock.request(new MapsListRequest(server),
                    response -> context.success(server + " Map List", Strings.join("\n", response.maps)).subscribe(),
                    () -> DiscordHelper.noResponse(context)
            );
        });

        discordCommands.<MessageContext>register("remove-map", "<server> <map...>", "Remove map", (args, context) -> {
            if (DiscordHelper.noRole(context, globalConfig.discordMapReviewerRoleId)) return;

            var server = Utils.findServer(args[0]);
            if (DiscordHelper.notFound(context, server)) return;

            NetSock.request(new MapRemoveRequest(args[1], server),
                    response -> context.info("Result", response.result).subscribe(),
                    () -> DiscordHelper.noResponse(context)
            );
        });

        discordCommands.<MessageContext>register("ban", "<player-id> <period> [reason...]", "Ban the player", (args, context) -> {
            if (DiscordHelper.noRole(context, globalConfig.discordAdminRoleId)) return;

            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
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
                    .filter(event -> DiscordHelper.buttonFilter(event, context, m))
                    .subscribe(event -> {
                        if (event.getCustomId().equals("yes")) {
                            NetSock.post(new SocketEvents.KickBannedPlayer(data.uuid, ip));

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

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            var info = netServer.admins.getInfoOptional(data.uuid);
            String ip = info != null ? info.lastIP : null;

            database.getBanDatas().deleteBan(data.uuid, ip);
            context.success("Success", "'@' unbanned", data.nickname).subscribe();
        });

        discordCommands.<MessageContext>register("pardon", "<player-id>", "Pardon player.", (args, context) -> {
            if (DiscordHelper.noRole(context, globalConfig.discordAdminRoleId)) return;

            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            NetSock.post(new PardonPlayer(data.uuid));
            context.success("Success", "'@' pardoned.", data.nickname).subscribe();
        });
    }
}