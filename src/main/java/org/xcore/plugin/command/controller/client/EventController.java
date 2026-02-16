package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.ui.menu.EventMenu;

@Singleton
public class EventController implements CloudClientController {

    private final Provider<EventMenu> menu;

    @Inject
    public EventController(Provider<EventMenu> menu) {
        this.menu = menu;
    }

    @Command("event")
    public void event(XCoreSender sender) {
        menu.get().main(menu.get().getUuid(sender));
    }

    @Command("events [page]")
    public void events(XCoreSender sender, @Argument("page") @Default("1") int page) {
        menu.get().events(menu.get().getUuid(sender), page);
    }
}
