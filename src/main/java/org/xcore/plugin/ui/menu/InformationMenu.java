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
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.flow.NoState;
import org.xcore.plugin.ui.route.MenuRoute;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationMenu extends Menu {
    private static final String ROUTE_MAIN = "information.main";
    private static final String ROUTE_INFORMATION = "information.info";

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

    private final class MainFlow extends BaseMenuFlow<NoState> {
        MainFlow() {
            super(ROUTE_MAIN, NoState.class);
            action("information", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_INFORMATION)));
            action("help", ctx -> openRoutedFromMain(ctx, () -> help.get().help(getUuid(ctx.session()), 1)));
            action("maps", ctx -> openRoutedFromMain(ctx, () -> map.get().maps(getUuid(ctx.session()), 1)));
            action("players", ctx -> openRoutedFromMain(ctx, () -> player.get().players(getUuid(ctx.session()), 1)));
            action("event-main", ctx -> openRoutedFromMain(ctx, () -> event.get().main(getUuid(ctx.session()))));
            action("event-list", ctx -> openRoutedFromMain(ctx, () -> event.get().events(getUuid(ctx.session()), 1)));
        }

        @Override
        public MenuScreen render(MenuRenderContext<NoState> context) {
            var local = context.locale();
            var grid = new MenuGrid()
                    .row(
                            MenuButton.of(local.t("commands-info"), "information"),
                            MenuButton.of(local.t("help-menu"), "help")
                    )
                    .row(
                            MenuButton.of(local.t("map-maps"), "maps"),
                            MenuButton.of(local.t("player-menu-players"), "players")
                    );
            if (config.isEvent()) {
                grid.row(
                        MenuButton.of(local.t("event-menu-main"), "event-main"),
                        MenuButton.of(local.t("event-events"), "event-list")
                );
            }
            grid.defaultNavigation(context.session(), local);
            return MenuScreen.normal(
                    local.t("menu-main-title"),
                    local.t("menu-main-content"),
                    grid.build()
            );
        }

        private void openRoutedFromMain(MenuRenderContext<NoState> context, Runnable action) {
            Session session = context.session();
            if (context.route() != null) {
                session.pushRouteHistory(context.route());
            }
            action.run();
        }
    }

    private final class InformationFlow extends BaseMenuFlow<NoState> {
        InformationFlow() {
            super(ROUTE_INFORMATION, NoState.class);
            action("discord", ctx -> openUrl(ctx.session(), globalConfig.discordUrl));
            action("github", ctx -> openUrl(ctx.session(), globalConfig.githubUrl));
            action("donatello", ctx -> openUrl(ctx.session(), globalConfig.donatelloUrl));
            action("weblate", ctx -> openUrl(ctx.session(), globalConfig.weblateUrl));
            action("discord-red-vs-blue", ctx -> openUrl(ctx.session(), globalConfig.discordRedVSBlueUrl));
        }

        @Override
        public MenuScreen render(MenuRenderContext<NoState> context) {
            var local = context.locale();
            var grid = new MenuGrid()
                    .row(
                            MenuButton.of(local.t("discord"), "discord"),
                            MenuButton.of(local.t("github"), "github")
                    )
                    .row(
                            MenuButton.of(local.t("donatello"), "donatello"),
                            MenuButton.of(local.t("weblate"), "weblate")
                    )
                    .row(MenuButton.of(local.t("discord-red-vs-blue"), "discord-red-vs-blue"))
                    .defaultNavigation(context.session(), local);
            return MenuScreen.followUp(
                    local.t("commands-info-title", args("server-name", config.server)),
                    local.t("commands-info-text", args("version", buildInfo.getVersion())),
                    grid.build()
            );
        }

        private void openUrl(Session session, String url) {
            session.menuService.openUri(session, url);
        }
    }
}
