package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;
import org.xcore.plugin.service.top.BuiltInTopCategoryProvider;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.ArrayDeque;
import java.util.Deque;

@Singleton
public class TopMenu extends Menu {

    private final TopMenuService topMenuService;
    private final PlayerMenu playerMenu;
    private final MenuService menuService;
    private final TopCategoryRegistry categoryRegistry;
    private final Async async;

    @Inject
    public TopMenu(TomlSecretsConfig secretsConfig,
                   SessionService sessionService,
                   MenuService menuService,
                   TopMenuService topMenuService,
                   PlayerMenu playerMenu,
                   TopCategoryRegistry categoryRegistry,
                   Async async) {
        super(secretsConfig, sessionService);
        this.menuService = menuService;
        this.topMenuService = topMenuService;
        this.playerMenu = playerMenu;
        this.async = async;
        this.categoryRegistry = initRegistry(categoryRegistry, topMenuService);
    }

    public TopMenu(TomlSecretsConfig secretsConfig,
                   SessionService sessionService,
                   MenuService menuService,
                   TopMenuService topMenuService,
                   PlayerMenu playerMenu,
                   TopCategoryRegistry categoryRegistry) {
        this(secretsConfig, sessionService, menuService, topMenuService, playerMenu, categoryRegistry, null);
    }

    public TopMenu(TomlSecretsConfig secretsConfig,
                   SessionService sessionService,
                   MenuService menuService,
                   TopMenuService topMenuService,
                   PlayerMenu playerMenu) {
        this(secretsConfig, sessionService, menuService, topMenuService, playerMenu, null, null);
    }

    private static TopCategoryRegistry initRegistry(TopCategoryRegistry registry, TopMenuService topMenuService) {
        TopCategoryRegistry effective = registry;
        if (effective == null && topMenuService != null) {
            effective = topMenuService.categoryRegistry();
        }
        if (effective == null) {
            effective = new TopCategoryRegistry();
        }
        effective.registerIfAbsent(new BuiltInTopCategoryProvider(TopCategory.PLAYTIME, 10, topMenuService));
        return effective;
    }

    public TopCategoryRegistry registry() {
        return categoryRegistry;
    }

    public String formatPlayTime(long totalPlayTime, Localization local) {
        return super.formatPlayTime((int) totalPlayTime, local);
    }

    @PostConstruct
    public void init() {
    }

    public void top(String uuid) {
        topById(uuid, null, 1);
    }

    public void top(String uuid, TopCategory category, int page) {
        topById(uuid, category != null ? category.name() : null, page);
    }

    public void topById(String uuid, String categoryId, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        String resolvedId = categoryId;
        if (resolvedId == null || resolvedId.isBlank()) {
            resolvedId = categoryRegistry.resolveDefault(TopCategory.PLAYTIME.name())
                    .map(TopCategoryProvider::id)
                    .orElse(TopCategory.PLAYTIME.name());
        }

        openTopUi(session, resolvedId, page, null, null);
    }

    public void categories(String uuid, TopCategory currentCategory) {
        categoriesById(uuid, currentCategory != null ? currentCategory.name() : null);
    }

    public void categoriesById(String uuid, String currentCategoryId) {
        topById(uuid, currentCategoryId, 1);
    }

    public void openTopUi(Session session, String categoryId) {
        openTopUi(session, categoryId, 1, null, null);
    }

    public void openTopUi(Session session, String categoryId, int page, String cursor, Deque<String> backStack) {
        if (session == null || session.player == null) return;
        session.clear();

        var controller = new TopUiController(
                this,
                categoryRegistry,
                playerMenu,
                sessionService,
                async,
                session
        );
        var initialModel = controller.createInitialModel(categoryId, page, cursor, backStack);
        menuService.openUi(session, controller, initialModel);
    }
}
