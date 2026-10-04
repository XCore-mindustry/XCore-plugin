package org.xcore.plugin.ui.menu;

import arc.util.Log;
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

        openTopUi(session, resolvedId, null, page, null, null);
    }

    public void categories(String uuid, TopCategory currentCategory) {
        categoriesById(uuid, currentCategory != null ? currentCategory.name() : null);
    }

    public void categoriesById(String uuid, String currentCategoryId) {
        topById(uuid, currentCategoryId, 1);
    }

    public void openTopUi(Session session, String categoryId) {
        openTopUi(session, categoryId, null, 1, null, null);
    }

    /**
     * Opens the leaderboard on the given page. The page is read off the game thread, so the
     * dialog appears a moment after the call.
     *
     * @param scopeId one of the category's scopes (a season), {@code null} for the current one
     */
    public void openTopUi(Session session, String categoryId, String scopeId, int page, String cursor,
                          Deque<String> backStack) {
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
        var query = controller.query(categoryId, scopeId, page, cursor, backStack);
        var viewer = session.data;
        long requested = session.nextUiVersion();
        async.supply(() -> controller.fetch(query, viewer)).thenMain((data, error) -> {
            if (session.uiVersion() != requested) {
                return; // The player opened something else while this was loading.
            }
            if (error != null) {
                Log.err("Failed to open top category " + query.categoryId(), error);
                return;
            }
            menuService.openUi(session, controller, controller.model(data), true);
        });
    }
}
