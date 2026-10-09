package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.ui.menu.MatchHistoryMenu;

@Singleton
public class MatchController implements CloudClientController {

    private final MatchHistoryMenu matchHistoryMenu;

    @Inject
    public MatchController(MatchHistoryMenu matchHistoryMenu) {
        this.matchHistoryMenu = matchHistoryMenu;
    }

    @Command("matches")
    public void matches(XCoreSender sender) {
        matchHistoryMenu.matches(sender.player().uuid());
    }
}
