package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuFlow;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationMenu extends Menu {
    private static final String ACTION_DISCORD = "discord";
    private static final String ACTION_GITHUB = "github";
    private static final String ACTION_DONATELLO = "donatello";
    private static final String ACTION_WEBLATE = "weblate";
    private static final String ACTION_DISCORD_RED_VS_BLUE = "discord-red-vs-blue";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    private final BuildInfo buildInfo;

    private final Provider<MapMenu> map;
    private final Provider<EventMenu> event;
    private final Provider<HelpMenu> help;
    private final Provider<PlayerMenu> player;


    @Inject
    public InformationMenu(Config config, GlobalConfig globalConfig, SessionService sessionService,
                           BuildInfo buildInfo, Provider<MapMenu> map, Provider<EventMenu> event,
                           Provider<HelpMenu> help, Provider<PlayerMenu> player) {
        super(config, globalConfig, sessionService);
        this.buildInfo = buildInfo;
        this.map = map;
        this.event = event;
        this.help = help;
        this.player = player;
    }

    public void main(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        Runnable lambda = () -> {session.pushHistory(() -> {main(uuid); }); };
        session.builder().title("menu-main-title").content("menu-main-content")
                .addLocal("commands-info", () -> {lambda.run(); information(uuid); })
                .addLocal("help-menu", () -> {lambda.run(); help.get().help(uuid, 1); })
                .end()
                .addLocal("map-maps", () -> {lambda.run(); map.get().maps(uuid, 1); })
                .addLocal("player-menu-players", () -> {lambda.run(); player.get().players(uuid, 1); })
                .end()
                .ifAddLocal(config.isEvent(),"event-menu-main", () -> {lambda.run(); event.get().main(uuid); })
                .ifAddLocal(config.isEvent(),"event-events", () -> {lambda.run(); event.get().events(uuid, 1); })
                .end()
                .addNavigationRow()
                .show();
    }

    public void information(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderFlow(session, new InformationFlow());
    }

    private final class InformationFlow implements MenuFlow<InformationState> {
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
            if (session.hasHistory()) {
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
                case ACTION_BACK -> {
                    Runnable previousMenu = session.popHistory();
                    if (previousMenu != null) {
                        session.menuService.close(session);
                        previousMenu.run();
                    }
                }
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
}
