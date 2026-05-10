package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationMenu extends Menu {
    private static final String ROUTE_MAIN = "information.main";
    private static final String ROUTE_INFORMATION = "information.info";
    private static final String ACTION_INFORMATION = "information";
    private static final String ACTION_HELP = "help";
    private static final String ACTION_MAPS = "maps";
    private static final String ACTION_PLAYERS = "players";
    private static final String ACTION_EVENT_MAIN = "event-main";
    private static final String ACTION_EVENT_LIST = "event-list";
    private static final String ACTION_DISCORD = "discord";
    private static final String ACTION_GITHUB = "github";
    private static final String ACTION_DONATELLO = "donatello";
    private static final String ACTION_WEBLATE = "weblate";
    private static final String ACTION_DISCORD_RED_VS_BLUE = "discord-red-vs-blue";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    private final BuildInfo buildInfo;
    private final MenuService menuService;

    private final Provider<MapMenu> map;
    private final Provider<EventMenu> event;
    private final Provider<HelpMenu> help;
    private final Provider<PlayerMenu> player;


    @Inject
    public InformationMenu(Config config, GlobalConfig globalConfig, SessionService sessionService,
                           BuildInfo buildInfo, MenuService menuService, Provider<MapMenu> map, Provider<EventMenu> event,
                           Provider<HelpMenu> help, Provider<PlayerMenu> player) {
        super(config, globalConfig, sessionService);
        this.buildInfo = buildInfo;
        this.menuService = menuService;
        this.map = map;
        this.event = event;
        this.help = help;
        this.player = player;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new MainFlow());
        menuService.registerRoute(new InformationFlow());
    }

    public void main(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_MAIN));
    }

    public void information(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_INFORMATION));
    }

    private final class MainFlow implements RoutedMenuFlow<MainState> {
        @Override
        public String routeId() {
            return ROUTE_MAIN;
        }

        @Override
        public MainState createState(Session session, MenuRoute route, MainState currentState) {
            return currentState == null ? new MainState() : currentState;
        }

        @Override
        public Class<MainState> stateType() {
            return MainState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<MainState> context) {
            var local = context.locale();
            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(local.t("commands-info"), ACTION_INFORMATION),
                    MenuButton.of(local.t("help-menu"), ACTION_HELP)
            ));
            rows.add(List.of(
                    MenuButton.of(local.t("map-maps"), ACTION_MAPS),
                    MenuButton.of(local.t("player-menu-players"), ACTION_PLAYERS)
            ));

            if (config.isEvent()) {
                rows.add(List.of(
                        MenuButton.of(local.t("event-menu-main"), ACTION_EVENT_MAIN),
                        MenuButton.of(local.t("event-events"), ACTION_EVENT_LIST)
                ));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (context.session().canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("menu-main-title"),
                    local.t("menu-main-content"),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<MainState> context, String actionId) {
            Session session = context.session();
            String uuid = getUuid(session);
            switch (actionId) {
                case ACTION_INFORMATION -> context.openRoute(MenuRoute.of(ROUTE_INFORMATION));
                case ACTION_HELP -> openRoutedFromMain(context, () -> help.get().help(uuid, 1));
                case ACTION_MAPS -> openRoutedFromMain(context, () -> map.get().maps(uuid, 1));
                case ACTION_PLAYERS -> openRoutedFromMain(context, () -> player.get().players(uuid, 1));
                case ACTION_EVENT_MAIN -> openRoutedFromMain(context, () -> event.get().main(uuid));
                case ACTION_EVENT_LIST -> openRoutedFromMain(context, () -> event.get().events(uuid, 1));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }

        private void openRoutedFromMain(MenuRenderContext<MainState> context, Runnable action) {
            Session session = context.session();
            if (context.route() != null) {
                session.pushRouteHistory(context.route());
            }
            action.run();
        }
    }

    private final class InformationFlow implements RoutedMenuFlow<InformationState> {
        @Override
        public String routeId() {
            return ROUTE_INFORMATION;
        }

        @Override
        public InformationState createState(Session session, MenuRoute route, InformationState currentState) {
            return currentState == null ? new InformationState() : currentState;
        }

        @Override
        public Class<InformationState> stateType() {
            return InformationState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<InformationState> context) {
            Session session = context.session();
            var local = context.locale();
            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(local.t("discord"), ACTION_DISCORD),
                    MenuButton.of(local.t("github"), ACTION_GITHUB)
            ));
            rows.add(List.of(
                    MenuButton.of(local.t("donatello"), ACTION_DONATELLO),
                    MenuButton.of(local.t("weblate"), ACTION_WEBLATE)
            ));
            rows.add(List.of(MenuButton.of(local.t("discord-red-vs-blue"), ACTION_DISCORD_RED_VS_BLUE)));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.followUp(
                    local.t("commands-info-title", args("server-name", config.server)),
                    local.t("commands-info-text", args("version", buildInfo.getVersion())),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<InformationState> context, String actionId) {
            Session session = context.session();
            switch (actionId) {
                case ACTION_DISCORD -> openUrl(session, globalConfig.discordUrl);
                case ACTION_GITHUB -> openUrl(session, globalConfig.githubUrl);
                case ACTION_DONATELLO -> openUrl(session, globalConfig.donatelloUrl);
                case ACTION_WEBLATE -> openUrl(session, globalConfig.weblateUrl);
                case ACTION_DISCORD_RED_VS_BLUE -> openUrl(session, globalConfig.discordRedVSBlueUrl);
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }

        private void openUrl(Session session, String url) {
            session.menuService.openUri(session, url);
        }
    }

    public static final class InformationState {
    }

    public static final class MainState {
    }
}
