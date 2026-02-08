package org.xcore.plugin.command.controller.client;

import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.plugin.command.core.ClientController;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.MenuService;
import org.xcore.plugin.ui.MenuSession;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationController implements ClientController {

    private CommandHandler handler;

    private final Config config;
    private final GlobalConfig globalConfig;
    private final BuildInfo buildInfo;

    private final Provider<MapController> mapController;
    private final Provider<EventController> eventController;

    private final MenuService menuService;
    private final BundleService bundle;

    @Inject
    public InformationController(Config config, GlobalConfig globalConfig, BuildInfo buildInfo,
                                 Provider<MapController> mapController, Provider<EventController> eventController,
                                 MenuService menuService, BundleService bundle) {
        this.config = config;
        this.globalConfig = globalConfig;
        this.buildInfo = buildInfo;
        this.mapController = mapController;
        this.eventController = eventController;
        this.menuService = menuService;
        this.bundle = bundle;
    }

    @Override
    public void setup(CommandHandler handler) {
        this.handler = handler;
    }

    @Override
    public int priority() {
        return 100;
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
    public void information(ClientContext ctx) {
        handleInformation(ctx.player());
    }

    public void handleInformation(Player player) {
        String menuTitle = bundle.format(bundle.locale(player),"commands-info-title", args("xcorServerName", config.server));
        String menuContent = bundle.format(bundle.locale(player), "commands-info-text", args("xcoreVersion", buildInfo.getVersion()));

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(bundle.format(bundle.locale(player), "discord", args()), () -> {
            Call.openURI(player.con, globalConfig.discordUrl);
        }));
        row1.add(session.add(bundle.format(bundle.locale(player), "github", args()), () -> {
            Call.openURI(player.con, globalConfig.githubUrl);
        }));
        rows.add(row1);

        List<String> row2 = new ArrayList<>();
        row2.add(session.add(bundle.format(bundle.locale(player), "donatello", args()), () -> {
            Call.openURI(player.con, globalConfig.donatelloUrl);
        }));
        row2.add(session.add(bundle.format(bundle.locale(player), "weblate", args()), () -> {
            Call.openURI(player.con, globalConfig.weblateUrl);
        }));
        rows.add(row2);

        List<String> row3 = new ArrayList<>();
        row3.add(session.add(bundle.format(bundle.locale(player), "discord-red-vs-blue", args()), () -> {
            Call.openURI(player.con, globalConfig.discordRedVSBlueUrl);
        }));
        rows.add(row3);

        List<String> row4 = new ArrayList<>();
        row4.add(session.add(bundle.format(bundle.locale(player), "map-maps", args()), () -> {
            session.pushHistory(() -> handleInformation(player));
            mapController.get().handleMaps(player, 1);
        }));
        if (config.isEvent()) {
            row4.add(session.add(bundle.format(bundle.locale(player), "event-events", args()), () -> {
                session.pushHistory(() -> handleInformation(player));
                eventController.get().handleEvents(player, 1);
            }));
        }
        rows.add(row4);

        menuService.addNavigationRow(player, session, rows);

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }
}
