package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.service.ServerRegistryService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.MenuService;

/**
 * Menu service entrypoint for opening the server browser dialog.
 */
@Singleton
public class ServerMenu {

    private final ServerRegistryService registryService;
    private final MenuService menuService;

    @Inject
    public ServerMenu(ServerRegistryService registryService, MenuService menuService) {
        this.registryService = registryService;
        this.menuService = menuService;
    }

    public void open(Session session) {
        open(session, null);
    }

    public void open(Session session, String categoryName) {
        if (session == null || session.player == null) return;
        session.clear();

        ServerRegistryService.Category category = ServerRegistryService.Category.ALL;
        if (categoryName != null && !categoryName.isBlank()) {
            for (ServerRegistryService.Category cat : ServerRegistryService.Category.values()) {
                if (cat.name().equalsIgnoreCase(categoryName.trim())
                        || cat.fallbackName().toLowerCase().contains(categoryName.trim().toLowerCase())) {
                    category = cat;
                    break;
                }
            }
        }

        var controller = new ServerSelectorUiController(registryService, session);
        var initialModel = ServerSelectorUiController.createModel(registryService, category);
        menuService.openUi(session, controller, initialModel);
    }
}
