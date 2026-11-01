package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.ServerMenu;

/**
 * Cloud command controller exposing /servers, /hub, /play, /lobby.
 */
@Singleton
public class ServerNavigationController implements CloudClientController {

    private final SessionService sessionService;
    private final ServerMenu serverMenu;

    @Inject
    public ServerNavigationController(SessionService sessionService, ServerMenu serverMenu) {
        this.sessionService = sessionService;
        this.serverMenu = serverMenu;
    }

    @Command("servers|hub|play|lobby [category]")
    @CommandDescription("Opens the interactive XCore server network browser.")
    public void servers(XCoreSender sender, @Argument("category") @Nullable String category) {
        if (!sender.isPlayer()) return;
        Session session = resolveSession(sender, sessionService);
        if (session == null) return;

        serverMenu.open(session, category);
    }
}
