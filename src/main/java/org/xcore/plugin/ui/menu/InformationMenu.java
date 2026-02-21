package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class InformationMenu extends Menu {
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
        Session session = sessionService.get(uuid).clear();

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
        Session session = sessionService.get(uuid).clear();

        session.builder().title("commands-info-title", args("server-name", config.server))
                .content("commands-info-text", args("version", buildInfo.getVersion()))
                .addLocal("discord", () -> Call.openURI(session.player.con, globalConfig.discordUrl))
                .addLocal("github", () -> Call.openURI(session.player.con, globalConfig.githubUrl))
                .end()
                .addLocal("donatello", () -> Call.openURI(session.player.con, globalConfig.donatelloUrl))
                .addLocal("weblate", () -> Call.openURI(session.player.con, globalConfig.weblateUrl))
                .end()
                .addLocalRow("discord-red-vs-blue", () -> Call.openURI(session.player.con, globalConfig.discordRedVSBlueUrl))
                .addNavigationRow()
                .show();
    }
}
