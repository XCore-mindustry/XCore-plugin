package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerActiveBadgeChangedCommandV1;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class BadgeController implements CloudClientController {

    private final SessionService sessionService;
    private final PlayerMenu playerMenu;
    private final PlayerDisplayService playerDisplayService;
    private final NetworkService network;
    private final Config config;

    @Inject
    public BadgeController(Config config,
                           SessionService sessionService,
                           PlayerMenu playerMenu,
                           PlayerDisplayService playerDisplayService,
                           NetworkService network) {
        this.config = config;
        this.sessionService = sessionService;
        this.playerMenu = playerMenu;
        this.playerDisplayService = playerDisplayService;
        this.network = network;
    }

    @Command("badge")
    public void badge(XCoreSender sender) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        playerMenu.badges(sender.player().uuid(), session.data);
    }

    @Command("badge clear")
    public void clear(XCoreSender sender) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;

        sessionService.setActiveBadge(session, "");
        playerDisplayService.refresh(session);
        network.post(new PlayerActiveBadgeChangedCommandV1(session.data.uuid, session.data.activeBadge, config.server));
        session.locale().send("badge-clear-success", args());
    }

    @Command("badge set <id>")
    public void set(XCoreSender sender, @Argument("id") String id) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;

        Badge badge = Badge.byId(id);
        if (badge == null) {
            session.locale().send("error-badge-not-found", args("badge", id));
            return;
        }

        if (!badge.selectable()) {
            session.locale().send("error-badge-not-selectable", args("badge", session.locale().t(badge.nameKey())));
            return;
        }

        if (session.data.unlockedBadges == null || !session.data.unlockedBadges.contains(badge.id())) {
            session.locale().send("error-badge-not-unlocked", args("badge", session.locale().t(badge.nameKey())));
            return;
        }

        sessionService.setActiveBadge(session, badge.id());
        playerDisplayService.refresh(session);
        network.post(new PlayerActiveBadgeChangedCommandV1(session.data.uuid, session.data.activeBadge, config.server));
        session.locale().send("badge-set-success", args("badge", session.locale().t(badge.nameKey())));
    }
}
