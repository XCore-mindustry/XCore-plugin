package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.node.CommandNode;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.MenuService;
import org.xcore.plugin.ui.MenuSession;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationController implements CloudClientController {

    private final Config config;
    private final GlobalConfig globalConfig;
    private final BuildInfo buildInfo;

    private final Provider<MapController> mapController;
    private final Provider<EventController> eventController;

    private final MenuService menuService;
    private final BundleService bundle;
    private final CommandManager<XCoreSender> commandManager;

    @Inject
    public InformationController(
            Config config,
            GlobalConfig globalConfig,
            BuildInfo buildInfo,
            Provider<MapController> mapController,
            Provider<EventController> eventController,
            MenuService menuService,
            BundleService bundle,
            CommandManager<XCoreSender> commandManager
    ) {
        this.config = config;
        this.globalConfig = globalConfig;
        this.buildInfo = buildInfo;
        this.mapController = mapController;
        this.eventController = eventController;
        this.menuService = menuService;
        this.bundle = bundle;
        this.commandManager = commandManager;
    }

    @Command("help [page]")
    public void help(XCoreSender sender, @Default("1") int page) {
        // Получаем список команд из Cloud
        List<CommandNode<XCoreSender>> commands = commandManager.commandTree().getRootNodes().stream()
                .filter(node -> !node.componentName().equals("login"))
                .toList();

        var pagination = CustomGatherers.calculatePagination(commands.size(), globalConfig.commandsPerPage);

        if (!pagination.isValidPage(page)) {
            sender.send("error-page-between", args("totalPages", pagination.totalPages()));
            return;
        }

        var result = new StringBuilder();
        result.append(sender.format("commands-help-start-content", args(
                "page", page,
                "totalPages", pagination.totalPages()
        ))).append("\n\n");

        IntStream.range(0, commands.size())
                .mapToObj(commands::get)
                .gather(CustomGatherers.indexedPage(globalConfig.commandsPerPage, page))
                .forEach(indexed -> {
                    var command = indexed.value();
                    String name = command.componentName();
                    result.append(sender.format("commands-help-content", args(
                            "commandName", name,
                            "commandParams", sender.format("commands-" + name + "-params", args()),
                            "commandDescription", sender.format("commands-" + name + "-description", args())
                    ))).append("\n");
                });

        sender.player().sendMessage(result.toString());
    }

    @Command("information|info")
    public void information(XCoreSender sender) {
        handleInformation(sender.player());
    }

    /**
     * Полностью перенесённая логика меню из старого кода
     */
    private void handleInformation(Player player) {
        String menuTitle = bundle.format(bundle.locale(player),
                "commands-info-title", args("xcorServerName", config.server));
        String menuContent = bundle.format(bundle.locale(player),
                "commands-info-text", args("xcoreVersion", buildInfo.getVersion()));

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();

        List<List<String>> rows = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(bundle.format(bundle.locale(player), "discord", args()),
                () -> Call.openURI(player.con, globalConfig.discordUrl)));
        row1.add(session.add(bundle.format(bundle.locale(player), "github", args()),
                () -> Call.openURI(player.con, globalConfig.githubUrl)));
        rows.add(row1);

        List<String> row2 = new ArrayList<>();
        row2.add(session.add(bundle.format(bundle.locale(player), "donatello", args()),
                () -> Call.openURI(player.con, globalConfig.donatelloUrl)));
        row2.add(session.add(bundle.format(bundle.locale(player), "weblate", args()),
                () -> Call.openURI(player.con, globalConfig.weblateUrl)));
        rows.add(row2);

        List<String> row3 = new ArrayList<>();
        row3.add(session.add(bundle.format(bundle.locale(player), "discord-red-vs-blue", args()),
                () -> Call.openURI(player.con, globalConfig.discordRedVSBlueUrl)));
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

        Call.menu(player.con,
                menuService.getMenuId(),
                menuTitle,
                menuContent,
                menuService.convertListToArray(rows));
    }
}
