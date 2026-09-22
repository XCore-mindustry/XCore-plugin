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

    private final EventMenu menu;

    @Inject
    public EventController(EventMenu menu) {
        this.menu = menu;
    }

    public EventController(Provider<EventMenu> menuProvider) {
        this(menuProvider.get());
    }

    @Command("event")
    public void event(XCoreSender sender) {
        menu.main(menu.getUuid(sender));
    }

    @Command("events [page]")
    public void events(XCoreSender sender, @Argument("page") @Default("1") int page) {
        menu.events(menu.getUuid(sender), page);
    }
}
