package org.xcore.plugin.command.controller.client;

import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.ui.Menus;
import org.xcore.plugin.command.core.ClientController;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.common.CustomGatherers;

import java.util.stream.IntStream;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationController implements ClientController {

    private CommandHandler handler;
    private int infoMenuId;

    private final Config config;
    private final GlobalConfig globalConfig;
    private final BuildInfo buildInfo;

    private final MapController mapController;
    private final EventController eventController;

    @Inject
    public InformationController(Config config, GlobalConfig globalConfig, BuildInfo buildInfo, MapController mapController, EventController eventController) {
        this.config = config;
        this.globalConfig = globalConfig;
        this.buildInfo = buildInfo;
        this.mapController = mapController;
        this.eventController = eventController;
    }

    @Override
    public void setup(CommandHandler handler) {
        this.handler = handler;
        initMenu();
    }

    @Override
    public int priority() {
        return 100;
    }

    public void initMenu() {
        this.infoMenuId = Menus.registerMenu((player, option) -> {
            if (config.isEvent()) {
                switch (option) {
                    case 0 -> Call.openURI(player.con, globalConfig.discordUrl);
                    case 1 -> Call.openURI(player.con, globalConfig.githubUrl);
                    case 2 -> Call.openURI(player.con, globalConfig.donatelloUrl);
                    case 3 -> Call.openURI(player.con, globalConfig.weblateUrl);
                    case 4 -> Call.openURI(player.con, globalConfig.discordRedVSBlueUrl);
                    case 5 -> mapController.handleMaps(player, 1);
                    case 6 -> eventController.handleEvents(player, 1);
                }
            } else {
                switch (option) {
                    case 0 -> Call.openURI(player.con, globalConfig.discordUrl);
                    case 1 -> Call.openURI(player.con, globalConfig.githubUrl);
                    case 2 -> Call.openURI(player.con, globalConfig.donatelloUrl);
                    case 3 -> Call.openURI(player.con, globalConfig.weblateUrl);
                    case 4 -> Call.openURI(player.con, globalConfig.discordRedVSBlueUrl);
                    case 5 -> mapController.handleMaps(player, 1);
                }
            }
        });
    }

    @Command(name = "help", params = "[page]")
    public void help(ClientContext ctx) {
        var commands = handler.getCommandList();
        var pagination = CustomGatherers.calculatePagination(commands.size, globalConfig.commandsPerPage);

        int requestedPage = ctx.argInt(0, 1);

        if (!pagination.isValidPage(requestedPage)) {
            ctx.send("error-page-between", args("totalPages", pagination.totalPages()));
            return;
        }

        var result = new StringBuilder();
        result.append(ctx.format("commands-help-start-content", args(
                "page", requestedPage,
                "totalPages", pagination.totalPages()
        ))).append("\n\n");

        IntStream.range(0, commands.size)
                .mapToObj(commands::get)
                .filter(command -> !command.text.equals("login"))
                .gather(CustomGatherers.indexedPage(globalConfig.commandsPerPage, requestedPage))
                .forEach(indexed -> {
                    var command = indexed.value();
                    result.append(ctx.format("commands-help-content", args(
                            "commandName", command.text,
                            "commandParams", ctx.format("commands-" + command.text + "-params", args()),
                            "commandDescription", ctx.format("commands-" + command.text + "-description", args())
                    ))).append("\n");
                });

        ctx.player().sendMessage(result.toString());
    }

    @Command(name = "information", aliases = {"info"})
    public void info(ClientContext ctx) {
        if (config.isEvent()) {
            Call.menu(ctx.player().con, infoMenuId,
                    ctx.format("commands-info-title", args("xcorServerName", config.server)),
                    ctx.format("commands-info-text", args("xcoreVersion", buildInfo.getVersion())),
                    new String[][]{
                            {"Discord", "GitHub",},
                            {"Donatello", "Weblate"},
                            {"RedVSBlue"},
                            {ctx.format("map-maps", args()), ctx.format("event-events", args())},
                            {ctx.format("close", args())}
                    }
            );
        } else {
            Call.menu(ctx.player().con, infoMenuId,
                    ctx.format("commands-info-title", args("xcorServerName", config.server)),
                    ctx.format("commands-info-text", args("xcoreVersion", buildInfo.getVersion())),
                    new String[][]{
                            {"Discord", "GitHub",},
                            {"Donatello", "Weblate"},
                            {"RedVSBlue"},
                            {ctx.format("map-maps", args())},
                            {ctx.format("close", args())}
                    }
            );
        }
    }
}
