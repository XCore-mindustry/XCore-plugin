package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.ui.menu.SeasonMenu;

@Singleton
public class SeasonController implements CloudClientController {

    private final SeasonMenu seasonMenu;

    @Inject
    public SeasonController(SeasonMenu seasonMenu) {
        this.seasonMenu = seasonMenu;
    }

    @Command("season|seasons")
    public void season(XCoreSender sender) {
        seasonMenu.season(sender.player().uuid());
    }
}
