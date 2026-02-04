package org.xcore.plugin.discord;

import arc.util.CommandHandler;
import arc.util.Strings;
import discord4j.common.util.TimestampFormat;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.*;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.moderation.ModerationService;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class DiscordCommandRegistry {

    private final GlobalConfig globalConfig;
    private final PlayerDataRepository playerDataRepository;
    private final BanDataRepository banDataRepository;
    private final AdminDataRepository adminDataRepository;
    private final ModerationService moderationService;
    private final DiscordLogBridge discordLogBridge;
    private final org.xcore.plugin.service.NetworkService network;

    private final CommandHandler discordCommands;

    @Inject
    public DiscordCommandRegistry(
            GlobalConfig globalConfig,
            PlayerDataRepository playerDataRepository,
            BanDataRepository banDataRepository,
            AdminDataRepository adminDataRepository,
            ModerationService moderationService,
            DiscordLogBridge discordLogBridge,
            org.xcore.plugin.service.NetworkService network
    ) {
        this.globalConfig = globalConfig;
        this.playerDataRepository = playerDataRepository;
        this.banDataRepository = banDataRepository;
        this.adminDataRepository = adminDataRepository;
        this.moderationService = moderationService;
        this.discordLogBridge = discordLogBridge;
        this.network = network;
        this.discordCommands = new CommandHandler(globalConfig.discordCommandPrefix);
    }

    public CommandHandler getHandler() {
        return discordCommands;
    }

    public void registerCommands(DiscordService discordService) {
        register("help", "List of all commands.", (args, ctx) -> {
            var builder = new StringBuilder();
            discordCommands.getCommandList().each(command ->
                    builder.append(discordCommands.prefix).append("**").append(command.text).append("**")
                            .append(!command.paramText.isEmpty() ? " " + command.paramText : "")
                            .append(" - ").append(command.description).append("\n"));
            ctx.info("All available commands:", builder.toString()).subscribe();
        });

        register("stats", "<player-id>", "Show player stats.", (args, ctx) -> {
            int id = Strings.parseInt(args[0]);
            if (ctx.checkId(id)) return;

            var data = playerDataRepository.findByPid(id);
            if (ctx.playerNotFound(data)) return;

            ctx.success((embed) -> embed.title(Strings.stripColors(data.nickname) + " Stats")
                            .addField("ID", String.valueOf(data.pid), false)
                            .addField("Total playtime", data.totalPlayTime + " minutes", false)
                            .addField("Hexed Rank", data.hexedRank().name(), false)
                            .addField("MiniPvP rating", String.valueOf(data.pvpRating), true))
                    .subscribe();
        });

        register("search", "<player-name...>", "Search players.",
                globalConfig.discordAdminRoleId,
                (args, ctx) -> ctx.paginate((embed, page) -> {
                    embed.title("Searching players '" + args[0] + "'");

                    var search = playerDataRepository.search(args[0], 6, page);

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

        register("upload-map", "", "Upload map to the servers.", globalConfig.discordMapReviewerRoleId, (args, ctx) -> {
            SocketEvents.FileURL[] files = ctx.message()
                    .getAttachments()
                    .stream()
                    .filter(attachment -> attachment.getFilename().endsWith(".msav"))
                    .map(a -> new SocketEvents.FileURL(a.getUrl(), a.getFilename()))
                    .toArray(SocketEvents.FileURL[]::new);

            if (files.length == 0) {
                ctx.error("Attach a file", "You need to attach a file with the extension 'msav'").subscribe();
                return;
            }

            List<SelectMenu.Option> servers = new ArrayList<>();
            for (var key : globalConfig.servers.keys()) {
                servers.add(SelectMenu.Option.of(key, key));
            }

            ctx.channel().createMessage(MessageCreateSpec.builder()
                            .content("Choose server:")
                            .addComponent(ActionRow.of(SelectMenu.of("choose-server", servers).withMaxValues(1).withPlaceholder("Choose server")))
                            .build())
                    .doOnNext(message -> discordService.getGateway().on(SelectMenuInteractionEvent.class)
                            .filter(event -> event.getCustomId().equals("choose-server")
                                    && event.getMessageId().equals(message.getId())
                                    && event.getInteraction().getMember().get().equals(ctx.member()))
                            .timeout(Duration.ofMinutes(10))
                            .onErrorResume(TimeoutException.class, exception -> Mono.empty())
                            .subscribe(event -> {
                                network.post(new SocketEvents.LoadMapsV2(files, event.getValues().get(0)));
                                message.delete().subscribe();
                                ctx.success("Success", "Successfully uploaded maps to " + event.getValues().get(0)).subscribe();
                            }))
                    .subscribe();
        });

        register("maps", "<server>", "List of maps", (args, ctx) -> {
            var server = network.findServer(args[0]);
            if (ctx.serverNotFound(server)) return;

            network.request(new SocketEvents.MapsListRequest(server),
                    response -> ctx.success(server + " Map List", Strings.join("\n", response.maps)).subscribe(),
                    () -> ctx.noResponse()
            );
        });

        register("remove-map", "<server> <map...>", "Remove map", globalConfig.discordMapReviewerRoleId, (args, ctx) -> {
            var server = network.findServer(args[0]);
            if (ctx.serverNotFound(server)) return;

            network.request(new SocketEvents.MapRemoveRequest(server, args[1]),
                    response -> ctx.info("Result", response.result).subscribe(),
                    ctx::noResponse
            );
        });

        register("bans", "[name]", "List/search bans.", globalConfig.discordAdminRoleId, (args, ctx) -> {
            ctx.paginate((embed, page) -> {
                embed.title("Bans");
                var bans = args.length > 0
                        ? banDataRepository.search(args[0], 6, page)
                        : banDataRepository.findAllPaged(6, page);

                if (bans != null) {
                    embed.color(Color.GREEN);
                    for (BanData ban : bans.results()) {
                        embed.addField(ban.name, Strings.format("""
                                        Admin: @
                                        Reason: @
                                        Unban Date: @
                                        """,
                                ban.adminName, ban.reason, TimestampFormat.LONG_DATE.format(ban.expireDate)), false);
                    }
                    embed.footer("Page " + page + "/" + bans.pages() + ", " + bans.total() + " bans", null);
                } else {
                    embed.color(Color.RED);
                    embed.description("Bans not found.");
                }
                return bans == null ? 0 : bans.pages();
            });
        });

        register("ban", "<player-id> <period> [reason...]", "Ban the player", globalConfig.discordAdminRoleId, (args, ctx) -> {
            int id = Strings.parseInt(args[0]);
            if (ctx.checkId(id)) return;

            var data = playerDataRepository.findByPid(id);
            if (ctx.playerNotFound(data)) return;

            var period = moderationService.parsePeriod(args[1], TimeUnit.DAYS);
            if (ctx.checkPeriod(period)) return;

            ctx.channel().createMessage(MessageCreateSpec.builder()
                    .content("Are you sure you want to ban a player named '" + data.nickname + "'?")
                    .addComponent(ActionRow.of(Button.primary("yes", "Yes"), Button.danger("no", "No")))
                    .build()).subscribe(m -> discordService.getGateway().on(ButtonInteractionEvent.class)
                    .filter(event -> ctx.buttonFilter(event, m))
                    .subscribe(event -> {
                        if (event.getCustomId().equals("yes")) {
                            String reason = args.length > 2 ? args[2] : null;
                            var result = moderationService.banById(id, ctx.member().getDisplayName(), reason, period, true);

                            if (result.isSuccess()) {
                                discordLogBridge.sendBan(result.getData().get());
                                ctx.success("Success", "Successfully banned player '" + data.nickname + "'").subscribe();
                            } else {
                                ctx.error("Error", result.getMessage().orElse("Failed to ban player")).subscribe();
                            }
                        }
                        event.getInteraction().getMessage().ifPresent(message -> message.delete().subscribe());
                    }));
        });

        register("unban", "<player-id>", "Unban player", globalConfig.discordAdminRoleId, (args, ctx) -> {
            int id = Strings.parseInt(args[0]);
            if (ctx.checkId(id)) return;

            var result = moderationService.unbanById(id);

            if (result.isSuccess()) {
                ctx.success("Success", "'" + result.getData().get().nickname + "' unbanned").subscribe();
            } else {
                ctx.error("Error", result.getMessage().orElse("Player not found")).subscribe();
            }
        });

        register("mute", "<player-id> <period> [reason...]", "Mute the player", globalConfig.discordAdminRoleId, (args, ctx) -> {
            int id = Strings.parseInt(args[0]);
            if (ctx.checkId(id)) return;

            var data = playerDataRepository.findByPid(id);
            if (ctx.playerNotFound(data)) return;

            var period = moderationService.parsePeriod(args[1], TimeUnit.DAYS);
            if (ctx.checkPeriod(period)) return;

            String reason = args.length > 2 ? args[2] : null;
            var result = moderationService.muteById(id, ctx.member().getDisplayName(), reason, period);

            if (result.isSuccess()) {
                ctx.success("Success", "Successfully muted player '" + data.nickname + "'").subscribe();
            } else {
                ctx.error("Error", result.getMessage().orElse("Failed to mute player")).subscribe();
            }
        });

        register("unmute", "<player-id>", "Unmute player", globalConfig.discordAdminRoleId, (args, ctx) -> {
            int id = Strings.parseInt(args[0]);
            if (ctx.checkId(id)) return;

            var result = moderationService.unmuteById(id);

            if (result.isSuccess()) {
                ctx.success("Success", "'" + result.getData().get().nickname + "' unmuted").subscribe();
            } else {
                ctx.error("Error", result.getMessage().orElse("Player not found")).subscribe();
            }
        });

        register("remove-admin", "<player-id>", "Remove the player from admin panel", globalConfig.discordGeneralAdminRoleId, (args, ctx) -> {
            int id = Strings.parseInt(args[0]);
            if (ctx.checkId(id)) return;

            var data = playerDataRepository.findByPid(id);
            if (ctx.playerNotFound(data)) return;

            adminDataRepository.delete(data.uuid);
            network.post(new SocketEvents.RemoveAdmin(data.uuid));
            ctx.success("Success", "'@' removed from admin panel.", data.nickname).subscribe();
        });

        register("reset-password", "<player-id>", "Resets the admin password.", globalConfig.discordGeneralAdminRoleId, (args, ctx) -> {
            int id = Strings.parseInt(args[0]);
            if (ctx.checkId(id)) return;

            var data = playerDataRepository.findByPid(id);
            if (ctx.playerNotFound(data)) return;

            var adminData = adminDataRepository.findByUuid(data.uuid);
            adminData.password = "";
            adminDataRepository.save(adminData);
            ctx.success("Success", "Password reset for '@'.", data.nickname).subscribe();
        });
    }

    private void register(String text, String description, CommandHandler.CommandRunner<MessageContext> runner) {
        register(text, "", description, -1, runner);
    }

    private void register(String text, String paramText, String description, CommandHandler.CommandRunner<MessageContext> runner) {
        register(text, paramText, description, -1, runner);
    }

    private void register(String text, String paramText, String description, long role, CommandHandler.CommandRunner<MessageContext> runner) {
        discordCommands.<MessageContext>register(text, paramText, description, (args, context) -> {
            context.member().getBasePermissions().subscribe(x -> {
                if (role == -1 || context.hasRole(role) || x.contains(Permission.ADMINISTRATOR))
                    runner.accept(args, context);
                else
                    context.error("Missing permissions", "You must be at least @ to use this command.", "<@&" + role + ">").subscribe();
            });
        });
    }
}
