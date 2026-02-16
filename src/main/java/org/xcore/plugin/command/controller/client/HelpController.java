package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.ui.menu.HelpMenu;

@Singleton
public class HelpController implements CloudClientController {

    private final HelpMenu menu;

    @Inject
    public HelpController(HelpMenu menu) {
        this.menu = menu;
    }

    @Command("help [page]")
    public void help(XCoreSender sender, @Argument("page") @Default("1") int page) {
        menu.sender(sender);
        menu.help(menu.getUuid(sender), page);
    }
}
