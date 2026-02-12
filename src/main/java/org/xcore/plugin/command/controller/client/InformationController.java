package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MenuSession;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationController implements CloudClientController {

    private final Config config;
    private final GlobalConfig globalConfig;
    private final BuildInfo buildInfo;

    private final Provider<MapController> mapController;
    private final Provider<EventController> eventController;
    private final Provider<HelpController> helpController;
    private final Provider<PlayerController> playerController;

    private final MenuService menuService;
    private final BundleService bundle;

    @Inject
    public InformationController(
            Config config,
            GlobalConfig globalConfig,
            BuildInfo buildInfo,
            Provider<MapController> mapController,
            Provider<EventController> eventController,
            Provider<HelpController> helpController, Provider<PlayerController> playerController,
            MenuService menuService,
            BundleService bundle
    ) {
        this.config = config;
        this.globalConfig = globalConfig;
        this.buildInfo = buildInfo;
        this.mapController = mapController;
        this.eventController = eventController;
        this.helpController = helpController;
        this.playerController = playerController;
        this.menuService = menuService;
        this.bundle = bundle;
    }

    @Command("main|xcore|m")
    public void mainXCore(XCoreSender sender) {
        handleMain(sender.player(), sender);
    }


    @Command("information|info")
    public void information(XCoreSender sender) {
        handleInformation(sender.player());
    }

    private void handleMain(Player player, XCoreSender sender) {
        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();

        Runnable lambda = () -> {session.pushHistory(() -> {handleMain(player, sender); }); };
        session.builder().title("menu-main-title").content("menu-main-content")
                .add("commands-info", () -> {lambda.run(); handleInformation(player); })
                .add("help-menu", () -> {lambda.run(); helpController.get().showIndex(sender, 1); })
                .end()
                .add("map-maps", () -> {lambda.run(); mapController.get().handleMaps(player, 1); })
                .add("player-menu-players", () -> {lambda.run(); playerController.get().handlePlayers(player, 1); })
                .end()
                .ifAdd(config.isEvent(),"event-menu-main", () -> {lambda.run(); eventController.get().handleMain(player); })
                .ifAdd(config.isEvent(),"event-events", () -> {lambda.run(); eventController.get().handleEvents(player, 1); })
                .end()
                .addNavigationRow()
                .show();
    }

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

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }
}
