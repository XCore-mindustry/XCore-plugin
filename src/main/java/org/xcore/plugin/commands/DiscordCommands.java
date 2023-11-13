package org.xcore.plugin.commands;

import arc.util.*;
import discord4j.common.util.TimestampFormat;
import discord4j.core.event.domain.interaction.*;
import discord4j.core.object.component.*;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.listeners.SocketEvents.*;
import org.xcore.plugin.modules.discord.*;
import org.xcore.plugin.utils.*;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.discord.Bot.*;

public class DiscordCommands {
    public static final CommandHandler discordCommands = new CommandHandler(globalConfig.discordCommandPrefix);

    public static void init() {
        register("help", "List of all commands.", (args, context) -> {
            var builder = new StringBuilder();
            discordCommands.getCommandList().each(command -> builder.append(discordCommands.prefix).append("**").append(command.text).append("**").append(!command.paramText.isEmpty() ? " " + command.paramText : "").append(" - ").append(command.description).append("\n"));
            context.info("All available commands:", builder.toString()).subscribe();
        });

        register("stats", "<player-id>", "Show player stats.", (args, context) -> {
            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            context.success((embed) -> embed.title(Strings.stripColors(data.nickname) + " Stats")
                            .addField("ID", String.valueOf(data.pid), false)
                            .addField("Total playtime", data.totalPlayTime + " minutes", false)
                            .addField("Hexed Rank", data.hexedRank().name(), false)
                            .addField("MiniPvP rating", String.valueOf(data.pvpRating), true))
                    .subscribe();
        });
        register("search", "<player-name...>", "Search players.",
                globalConfig.discordAdminRoleId,
                (args, context) -> PaginationUtil.paginate(context, (embed, page) -> {
                    embed.title("Searching players '" + args[0] + "'");

                    var search = database.getPlayerDatas().search(args[0], 6, page);

                    if (search != null) {
                        embed.color(Color.GREEN);
                        for (PlayerData data : search.results()) {
                            embed.addField(data.nickname, Strings.format("""
                                    ID: @
                                    Total playtime: @ minutes
                                    """, data.pid, data.totalPlayTime), false);
                        }

                        embed.footer("Page " + page + "/" + search.pages() + ", " + search.total() + " players", null);
                    } else {
                        embed.color(Color.RED);
                        embed.description("Players not found.");
                    }

                    return search == null ? 0 : search.pages();
                }));

        register(
                "upload-map",
                "",
                "Upload map to the servers.",
                globalConfig.discordMapReviewerRoleId,
                (args, context) -> {
            var attachments = context.message()
                    .getAttachments()
                    .stream()
                    .filter(attachment -> attachment.getFilename().endsWith(".msav"))
                    .map(a -> a.getUrl().substring(0, a.getUrl().indexOf("?")))
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
                            .timeout(Duration.ofMinutes(10))
                            .onErrorResume(TimeoutException.class, exception -> Mono.empty())
                            .subscribe(event -> {
                                NetSock.post(new SocketEvents.LoadMaps(attachments.toArray(new String[0]), event.getValues().get(0)));
                                message.delete().subscribe();
                                context.success("Success", "Successfully uploaded maps to " + event.getValues().get(0)).subscribe();
                            }))
                    .subscribe();
        });

        register("maps", "<server>", "List of maps", (args, context) -> {
            var server = Utils.findServer(args[0]);
            if (DiscordHelper.notFound(context, server)) return;

            NetSock.request(new MapsListRequest(server),
                    response -> context.success(server + " Map List", Strings.join("\n", response.maps)).subscribe(),
                    () -> DiscordHelper.noResponse(context)
            );
        });

        register("remove-map", "<server> <map...>", "Remove map", globalConfig.discordMapReviewerRoleId, (args, context) -> {
            var server = Utils.findServer(args[0]);
            if (DiscordHelper.notFound(context, server)) return;

            NetSock.request(new MapRemoveRequest(args[1], server),
                    response -> context.info("Result", response.result).subscribe(),
                    () -> DiscordHelper.noResponse(context)
            );
        });

        register("bans", "[name]", "List/search bans.", globalConfig.discordAdminRoleId, (args, context) -> {
            PaginationUtil.paginate(context, (embed, page) -> {
                embed.title("Bans");

                var bans = args.length > 0
                        ? database.getBanDatas().search(args[0], 6, page)
                        : database.getBanDatas().pagedData(6, page);

                if (bans != null) {
                    embed.color(Color.GREEN);
                    for (BanData ban : bans.results()) {
                        embed.addField(ban.name, Strings.format("""
                                        Admin: @
                                        Reason: @
                                        Unban Date: @
                                        """,
                                ban.adminName, ban.reason, TimestampFormat.LONG_DATE.format(ban.unbanDate.toInstant())), false);
                    }

                    embed.footer("Page " + page + "/" + bans.pages() + ", " + bans.total() + " bans", null);
                } else {
                    embed.color(Color.RED);
                    embed.description("Bans not found.");
                }

                return bans == null ? 0 : bans.pages();
            });
        });

        register("ban", "<player-id> <period> [reason...]", "Ban the player", globalConfig.discordAdminRoleId, (args, context) -> {
            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            Instant date = Utils.parsePeriod(args[1], TimeUnit.DAYS);
            if (DiscordHelper.checkPeriod(context, date)) return;

            Date unbanDate = new Date(Time.millis() + date.toEpochMilli());

            context.channel().createMessage(MessageCreateSpec.builder()
                    .content("Are you sure you want to ban a player named '" + data.nickname + "'?")
                    .addComponent(ActionRow.of(Button.primary("yes", "Yes"), Button.danger("no", "No")))
                    .build()).subscribe(m -> gateway.on(ButtonInteractionEvent.class)
                    .filter(event -> DiscordHelper.buttonFilter(event, context, m))
                    .subscribe(event -> {
                        if (event.getCustomId().equals("yes")) {
                            NetSock.post(new SocketEvents.KickBannedPlayer(data.uuid, data.ip));

                            BanData ban = BanData.builder()
                                    .name(data.nickname)
                                    .uuid(data.uuid)
                                    .ip(data.ip)
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

        register("unban", "<player-id>", "Unban player", globalConfig.discordAdminRoleId, (args, context) -> {
            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            var info = netServer.admins.getInfoOptional(data.uuid);
            String ip = info != null ? info.lastIP : null;

            database.getBanDatas().deleteBan(data.uuid, ip);
            context.success("Success", "'@' unbanned", data.nickname).subscribe();
        });

        register("pardon", "<player-id>", "Pardon player.", globalConfig.discordAdminRoleId, (args, context) -> {
            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            NetSock.post(new PardonPlayer(data.uuid));
            context.success("Success", "'@' pardoned.", data.nickname).subscribe();
        });

        register("remove-admin", "<player-id>", "Remove the player from admin panel", globalConfig.discordGeneralAdminRoleId, (args, context) -> {
            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            NetSock.post(new SocketEvents.RemoveAdmin(data.uuid));
            context.success("Success", "'@' removed from admin panel.", data.nickname).subscribe();
        });

        register("reset-password", "<player-id>", "Resets the admin password.", globalConfig.discordGeneralAdminRoleId, (args, context) -> {
            int id = Strings.parseInt(args[0]);
            if (DiscordHelper.checkId(context, id)) return;

            var data = database.getPlayerDatas().getPlayerDataById(id);
            if (DiscordHelper.notFound(context, data)) return;

            data.adminPassword = "";
            data.save();
            context.success("Success", "Password reset for '@'.", data.nickname).subscribe();
        });
    }

    private static void register(String text, String description, CommandHandler.CommandRunner<MessageContext> runner) {
        register(text, "", description, -1, runner);
    }

    private static void register(String text, String paramText, String description, CommandHandler.CommandRunner<MessageContext> runner) {
        register(text, paramText, description, -1, runner);
    }

    private static void register(String text, String paramText, String description, long role, CommandHandler.CommandRunner<MessageContext> runner) {
        discordCommands.<MessageContext>register(text, paramText, description, (args, context) -> {
            if (role != -1 && DiscordHelper.noRole(context, role)) return;

            runner.accept(args, context);
        });
    }
}