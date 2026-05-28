package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class DiscordMenu extends Menu {

    private final DiscordLinkService discordLinkService;
    private final MenuService menuService;

    @Inject
    public DiscordMenu(GlobalConfig globalConfig,
                       SessionService sessionService,
                       DiscordLinkService discordLinkService,
                       MenuService menuService) {
        super(globalConfig, sessionService);
        this.discordLinkService = discordLinkService;
        this.menuService = menuService;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new DiscordFlows.MainFlow(this, discordLinkService));
        menuService.registerRoute(new DiscordFlows.LinkingFlow(this, discordLinkService));
    }

    public void main(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(DiscordFlows.ROUTE_MAIN));
    }

    public void linking(String uuid, boolean regenerate) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        var result = regenerate
                ? discordLinkService.createCode(session)
                : discordLinkService.getOrCreateActiveCode(session);

        if (!result.success()) {
            var local = session.locale();
            if (result.isError("already-linked")) {
                local.send("commands-discord-link-already-linked", args());
            } else {
                local.send("commands-discord-link-error", args());
            }
            session.menuService.close(session);
            main(uuid);
            return;
        }

        session.setDraft(DiscordFlows.LinkingState.class, new DiscordFlows.LinkingState(result));
        session.menuService.renderRoute(session, MenuRoute.of(DiscordFlows.ROUTE_LINKING).withParam("regenerate", String.valueOf(regenerate)));
    }
}
