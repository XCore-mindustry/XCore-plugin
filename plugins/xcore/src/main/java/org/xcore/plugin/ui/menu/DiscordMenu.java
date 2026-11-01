package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
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
    public DiscordMenu(TomlSecretsConfig secretsConfig,
                       SessionService sessionService,
                       DiscordLinkService discordLinkService,
                       MenuService menuService) {
        super(secretsConfig, sessionService);
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

        discordLinkService.requestCode(session, regenerate, result -> {
            if (!result.success()) {
                session.locale().send(result.isError("already-linked")
                        ? "commands-discord-link-already-linked" : "commands-discord-link-error", args());
                main(uuid);
                return;
            }
            session.setDraft(DiscordFlows.LinkingState.class, new DiscordFlows.LinkingState(result));
            session.menuService.renderRoute(session, MenuRoute.of(DiscordFlows.ROUTE_LINKING));
        });
    }
}
