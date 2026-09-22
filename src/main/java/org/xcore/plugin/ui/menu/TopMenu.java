package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
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
import java.util.Optional;

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
        effective.registerIfAbsent(new BuiltInTopCategoryProvider(TopCategory.MINI_PVP, 20, topMenuService));
        effective.registerIfAbsent(new BuiltInTopCategoryProvider(TopCategory.PLAYTIME, 10, topMenuService));
        effective.registerIfAbsent(new BuiltInTopCategoryProvider(TopCategory.HEXED, 5, topMenuService));
        return effective;
    }

    public TopCategoryRegistry registry() {
        return categoryRegistry;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new TopFlows.TopListFlow(this, topMenuService, categoryRegistry, playerMenu, sessionService, async));
        menuService.registerRoute(new TopFlows.CategoriesFlow(topMenuService, categoryRegistry));
    }

    public void top(String uuid) {
        Optional<String> customDefault = categoryRegistry.defaultCategoryId();
        if (customDefault.isPresent()) {
            topById(uuid, customDefault.get(), 1);
        } else {
            top(uuid, null, 1);
        }
    }

    public void top(String uuid, TopCategory category, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        TopCategory resolvedCategory = category == null ? topMenuService.resolveDefaultCategory() : category;
        TopMenuState state = session.getDraft(TopMenuState.class);
        state.category = resolvedCategory;
        state.categoryId = resolvedCategory != null ? resolvedCategory.name() : null;
        state.currentPage = page;
        state.currentCursor = null;
        state.nextCursor = null;
        state.backStack.clear();
        state.currentCursorToken = null;
        state.nextCursorToken = null;
        state.tokenBackStack.clear();

        session.menuService.renderRoute(session, MenuRoute.of(TopFlows.ROUTE_TOP_LIST)
                .withParam("category", resolvedCategory != null ? resolvedCategory.name() : "")
                .withParam("page", String.valueOf(page)));
    }

    public void topById(String uuid, String categoryId, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        TopCategory enumCategory = TopFlows.parseCategory(categoryId);
        if (enumCategory != null) {
            top(uuid, enumCategory, page);
            return;
        }

        String resolvedId = categoryId;
        if (resolvedId == null || resolvedId.isBlank()) {
            var defaultProvider = categoryRegistry.resolveDefault(null);
            resolvedId = defaultProvider.map(TopCategoryProvider::id).orElse("");
        }

        TopMenuState state = session.getDraft(TopMenuState.class);
        state.category = null;
        state.categoryId = resolvedId;
        state.currentPage = page;
        state.currentCursor = null;
        state.nextCursor = null;
        state.backStack.clear();
        state.currentCursorToken = null;
        state.nextCursorToken = null;
        state.tokenBackStack.clear();

        session.menuService.renderRoute(session, MenuRoute.of(TopFlows.ROUTE_TOP_LIST)
                .withParam("category", resolvedId)
                .withParam("page", String.valueOf(page)));
    }

    public void categories(String uuid, TopCategory currentCategory) {
        categoriesById(uuid, currentCategory != null ? currentCategory.name() : null);
    }

    public void categoriesById(String uuid, String currentCategoryId) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        String resolvedId = currentCategoryId;
        if (resolvedId == null || resolvedId.isBlank()) {
            TopCategory def = topMenuService.resolveDefaultCategory();
            resolvedId = def != null ? def.name() : "";
        }

        session.menuService.renderRoute(session, MenuRoute.of(TopFlows.ROUTE_TOP_CATEGORIES)
                .withParam("category", resolvedId));
    }

    public static final class TopMenuState {
        public TopCategory category;
        public int currentPage = 1;
        public Deque<LeaderboardCursor> backStack = new ArrayDeque<>();
        public LeaderboardCursor currentCursor;
        public LeaderboardCursor nextCursor;

        public String categoryId;
        public String currentCursorToken;
        public String nextCursorToken;
        public Deque<String> tokenBackStack = new ArrayDeque<>();
    }

    public static final class TopCategoriesState {
    }
}
