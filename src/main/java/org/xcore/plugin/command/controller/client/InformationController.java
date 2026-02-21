package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.ui.menu.InformationMenu;

@Singleton
public class InformationController implements CloudClientController {

    private final InformationMenu menu;

    @Inject
    public InformationController(InformationMenu menu){
        this.menu = menu;
    }

    @Command("main|xcore|m")
    public void mainXCore(XCoreSender sender) {
        menu.sender(sender);
        menu.main(menu.getUuid(sender));
    }


    @Command("information|info")
    public void information(XCoreSender sender) {
        menu.information(menu.getUuid(sender));
    }
}
