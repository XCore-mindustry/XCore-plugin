package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPGuideService;
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
    private final TomlXcoreConfig config;

    private final MapMenu map;
    private final EventMenu event;
    private final HelpMenu help;
    private final PlayerMenu player;
    private final MiniPvPGuideService pvpGuide;

    @Inject
    public InformationMenu(TomlXcoreConfig config, TomlSecretsConfig secretsConfig, SessionService sessionService,
                           BuildInfo buildInfo, MenuService menuService, MapMenu map, EventMenu event,
                           HelpMenu help, PlayerMenu player, MiniPvPGuideService pvpGuide) {
        super(secretsConfig, sessionService);
        this.config = config;
        this.buildInfo = buildInfo;
        this.menuService = menuService;
        this.map = map;
        this.event = event;
        this.help = help;
        this.player = player;
        this.pvpGuide = pvpGuide;
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
            action("help", ctx -> openRoutedFromMain(ctx, () -> help.help(getUuid(ctx.session()), 1)));
            action("maps", ctx -> openRoutedFromMain(ctx, () -> map.maps(getUuid(ctx.session()), 1)));
            action("players", ctx -> openRoutedFromMain(ctx, () -> player.players(getUuid(ctx.session()), 1)));
            action("event-main", ctx -> openRoutedFromMain(ctx, () -> event.main(getUuid(ctx.session()))));
            action("event-list", ctx -> openRoutedFromMain(ctx, () -> event.events(getUuid(ctx.session()), 1)));
            action("pvp-guide", ctx -> openRoutedFromMain(ctx, () -> pvpGuide.open(ctx.session())));
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
            if ("event".equals(config.server.name)) {
                grid.row(
                        MenuButton.of(local.t("event-menu-main"), "event-main"),
                        MenuButton.of(local.t("event-events"), "event-list")
                );
            }
            if (pvpGuide.hostsMode()) {
                grid.row(MenuButton.of(local.t("pvp-guide-menu-button"), "pvp-guide"));
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
            action("site", ctx -> openUrl(ctx.session(), secretsConfig.externalLinks.siteUrl));
            action("discord", ctx -> openUrl(ctx.session(), secretsConfig.externalLinks.discordUrl));
            action("github", ctx -> openUrl(ctx.session(), secretsConfig.externalLinks.githubUrl));
            action("donatello", ctx -> openUrl(ctx.session(), secretsConfig.externalLinks.donatelloUrl));
            action("weblate", ctx -> openUrl(ctx.session(), secretsConfig.externalLinks.weblateUrl));
            action("discord-red-vs-blue", ctx -> openUrl(ctx.session(), secretsConfig.externalLinks.discordRedVSBlueUrl));
        }

        @Override
        public MenuScreen render(MenuRenderContext<NoState> context) {
            var local = context.locale();
            var grid = new MenuGrid()
                    .row(MenuButton.of(local.t("site"), "site"))
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
                    local.t("commands-info-title", args("server-name", config.server.name)),
                    local.t("commands-info-text", args("version", buildInfo.getVersion())),
                    grid.build()
            );
        }

        private void openUrl(Session session, String url) {
            session.menuService.openUri(session, url);
        }
    }
}
