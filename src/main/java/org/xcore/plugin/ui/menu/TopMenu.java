package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;
import org.xcore.plugin.service.top.BuiltInTopCategoryProvider;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class TopMenu extends Menu {

    private static final int PLAYERS_PER_PAGE = 10;
    private static final LeaderboardCursor FIRST_PAGE_MARKER = new LeaderboardCursor(0, 0, Integer.MIN_VALUE);
    private static final String FIRST_PAGE_TOKEN = "__first__";

    private static final String ROUTE_TOP_LIST = "top.list";
    private static final String ROUTE_TOP_CATEGORIES = "top.categories";

    private static final String ACTION_PROFILE_PREFIX = "profile:";
    private static final String ACTION_CATEGORY_PREFIX = "category:";

    private final TopMenuService topMenuService;
    private final PlayerMenu playerMenu;
    private final MenuService menuService;
    private final TopCategoryRegistry categoryRegistry;

    @Inject
    public TopMenu(TomlSecretsConfig secretsConfig,
                   SessionService sessionService,
                   MenuService menuService,
                   TopMenuService topMenuService,
                   PlayerMenu playerMenu,
                   TopCategoryRegistry categoryRegistry) {
        super(secretsConfig, sessionService);
        this.menuService = menuService;
        this.topMenuService = topMenuService;
        this.playerMenu = playerMenu;
        this.categoryRegistry = initRegistry(categoryRegistry, topMenuService);
    }

    public TopMenu(TomlSecretsConfig secretsConfig,
                   SessionService sessionService,
                   MenuService menuService,
                   TopMenuService topMenuService,
                   PlayerMenu playerMenu) {
        this(secretsConfig, sessionService, menuService, topMenuService, playerMenu, null);
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
        menuService.registerRoute(new TopListFlow());
        menuService.registerRoute(new CategoriesFlow());
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

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
                .withParam("category", resolvedCategory != null ? resolvedCategory.name() : "")
                .withParam("page", String.valueOf(page)));
    }

    public void topById(String uuid, String categoryId, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        TopCategory enumCategory = parseCategory(categoryId);
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

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
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

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_CATEGORIES)
                .withParam("category", resolvedId));
    }

    private TopCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TopCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private final class TopListFlow extends BaseMenuFlow<TopMenuState> {
        TopListFlow() {
            super(ROUTE_TOP_LIST, TopMenuState.class);
            action("previous", ctx -> {
                TopMenuState state = ctx.state();
                if (state.category != null) {
                    LeaderboardCursor previous = state.backStack.pollLast();
                    LeaderboardCursor previousCursor = previous == FIRST_PAGE_MARKER ? null : previous;
                    state.currentCursor = previousCursor;
                    state.currentPage = Math.max(1, state.currentPage - 1);
                } else {
                    String previousToken = state.tokenBackStack.pollLast();
                    String previousCursor = FIRST_PAGE_TOKEN.equals(previousToken) ? null : previousToken;
                    state.currentCursorToken = previousCursor;
                    state.currentPage = Math.max(1, state.currentPage - 1);
                }
                ctx.render();
            });
            action("next", ctx -> {
                TopMenuState state = ctx.state();
                if (state.category != null) {
                    state.backStack.addLast(state.currentCursor == null ? FIRST_PAGE_MARKER : state.currentCursor);
                    state.currentCursor = state.nextCursor;
                    state.currentPage = state.currentPage + 1;
                } else {
                    state.tokenBackStack.addLast(state.currentCursorToken == null ? FIRST_PAGE_TOKEN : state.currentCursorToken);
                    state.currentCursorToken = state.nextCursorToken;
                    state.currentPage = state.currentPage + 1;
                }
                ctx.render();
            });
            action("category", ctx -> {
                TopMenuState state = ctx.state();
                Session session = ctx.session();
                TopCategory savedCategory = state.category;
                String savedCategoryId = state.categoryId;
                LeaderboardCursor savedCursor = state.currentCursor;
                int savedPage = state.currentPage;
                Deque<LeaderboardCursor> savedBackStack = new ArrayDeque<>(state.backStack);
                LeaderboardCursor savedNextCursor = state.nextCursor;
                String savedCurrentToken = state.currentCursorToken;
                String savedNextToken = state.nextCursorToken;
                Deque<String> savedTokenBackStack = new ArrayDeque<>(state.tokenBackStack);

                session.pushHistory(() -> {
                    session.clear();
                    TopMenuState histState = session.getDraft(TopMenuState.class);
                    histState.category = savedCategory;
                    histState.categoryId = savedCategoryId;
                    histState.currentCursor = savedCursor;
                    histState.currentPage = savedPage;
                    histState.backStack = savedBackStack;
                    histState.nextCursor = savedNextCursor;
                    histState.currentCursorToken = savedCurrentToken;
                    histState.nextCursorToken = savedNextToken;
                    histState.tokenBackStack = savedTokenBackStack;
                    session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
                            .withParam("category", savedCategoryId != null ? savedCategoryId : (savedCategory != null ? savedCategory.name() : ""))
                            .withParam("page", String.valueOf(savedPage)));
                });
                session.menuService.hideFollowUp(session);
                session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_CATEGORIES)
                        .withParam("category", state.categoryId != null ? state.categoryId : (state.category != null ? state.category.name() : "")));
            });
            actionPrefix("profile:", (ctx, targetUuid) -> {
                PlayerData target = ctx.session().playerDataRepository.findByUuid(targetUuid);
                if (target != null) {
                    if (ctx.route() != null) {
                        ctx.session().pushRouteHistory(ctx.route());
                    }
                    ctx.session().menuService.hideFollowUp(ctx.session());
                    playerMenu.player(ctx.session().data.uuid, target);
                }
            });
        }

        @Override
        public TopMenuState createState(Session session, MenuRoute route, TopMenuState currentState) {
            if (currentState == null) {
                currentState = new TopMenuState();
            }
            String routeCatParam = route.param("category");
            TopCategory routeCategory = parseCategory(routeCatParam);
            TopCategory resolvedCategory;
            String resolvedCategoryId;

            if (routeCategory != null) {
                resolvedCategory = routeCategory;
                resolvedCategoryId = routeCategory.name();
            } else if (routeCatParam != null && !routeCatParam.isBlank()) {
                resolvedCategory = null;
                resolvedCategoryId = routeCatParam;
            } else {
                resolvedCategory = topMenuService.resolveDefaultCategory();
                resolvedCategoryId = resolvedCategory != null ? resolvedCategory.name() : null;
            }

            int routePage = route.intParam("page", 1);
            boolean changed = (currentState.category != resolvedCategory)
                    || !Objects.equals(currentState.categoryId, resolvedCategoryId);

            if (changed) {
                currentState.category = resolvedCategory;
                currentState.categoryId = resolvedCategoryId;
                currentState.currentPage = routePage;
                currentState.currentCursor = null;
                currentState.nextCursor = null;
                currentState.backStack.clear();
                currentState.currentCursorToken = null;
                currentState.nextCursorToken = null;
                currentState.tokenBackStack.clear();
            }

            return currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<TopMenuState> context) {
            Session session = context.session();
            TopMenuState state = context.state();
            Localization local = context.locale();

            // 1. LEGACY PATH: built-in category
            if (state.category != null) {
                TopCategory resolvedCategory = state.category;
                var topPage = topMenuService.loadCursorPage(resolvedCategory, state.currentCursor, state.currentPage, PLAYERS_PER_PAGE, session.data);
                state.category = resolvedCategory;
                state.categoryId = resolvedCategory.name();
                state.currentPage = topPage.currentPage();
                state.currentCursor = topPage.currentCursor();
                state.nextCursor = topPage.nextCursor();

                String categoryName = local.t(resolvedCategory.bundleKey());
                var grid = new MenuGrid();

                if (topPage.totalEntries() > 0) {
                    for (int i = 0; i < topPage.players().size(); i++) {
                        PlayerData player = topPage.players().get(i);
                        String buttonText = cursorPlayerButton(session, topPage, resolvedCategory, player, i);
                        grid.row(MenuButton.of(buttonText, ACTION_PROFILE_PREFIX + player.uuid));
                    }
                }

                List<MenuButton> navRow = new ArrayList<>();
                if (!state.backStack.isEmpty()) {
                    navRow.add(MenuButton.of(local.t("previous"), "previous"));
                }
                navRow.add(MenuButton.of(local.t("top-menu-category-button", args("category", categoryName)), "category"));
                if (topPage.hasNext() && state.nextCursor != null) {
                    navRow.add(MenuButton.of(local.t("next"), "next"));
                }
                if (!navRow.isEmpty()) {
                    grid.row(navRow.toArray(new MenuButton[0]));
                }

                grid.defaultNavigation(session, local);

                return MenuScreen.followUp(
                        local.t("top-menu-title", args("category", categoryName)),
                        topPage.totalEntries() <= 0
                                ? local.t("top-menu-empty", args("category", categoryName))
                                : local.t("top-menu-content", args(
                                        "page", topPage.currentPage(),
                                        "totalPages", topPage.totalPages(),
                                        "totalEntries", topPage.totalEntries(),
                                        "category", categoryName,
                                        "selfRankLine", selfRankLine(local, topPage.selfRank())
                                )),
                        grid.build()
                );
            }

            // 2. GENERIC SPI PATH: custom plugin category
            String categoryId = state.categoryId != null ? state.categoryId : "";
            TopCategoryProvider provider = categoryRegistry.resolve(categoryId).orElse(null);

            if (provider == null) {
                return MenuScreen.followUp(
                        local.t("top-menu-title", args("category", categoryId)),
                        local.t("top-menu-empty", args("category", categoryId)),
                        new MenuGrid().defaultNavigation(session, local).build()
                );
            }

            LeaderboardPageRequest request = new LeaderboardPageRequest(
                    categoryId,
                    state.currentPage,
                    PLAYERS_PER_PAGE,
                    state.currentCursorToken,
                    session.data
            );

            LeaderboardPage page;
            try {
                page = provider.loadPage(request);
            } catch (Exception e) {
                page = LeaderboardPage.empty(state.currentPage);
            }

            state.currentPage = page.currentPage();
            state.currentCursorToken = request.cursor();
            state.nextCursorToken = page.nextCursor();

            String categoryName = safeDisplayName(provider, local);
            var grid = new MenuGrid();

            if (!page.entries().isEmpty()) {
                for (LeaderboardEntry entry : page.entries()) {
                    String buttonText = safeFormatEntry(provider, entry, local);
                    grid.row(MenuButton.of(buttonText, ACTION_PROFILE_PREFIX + entry.playerUuid()));
                }
            }

            List<MenuButton> navRow = new ArrayList<>();
            if (!state.tokenBackStack.isEmpty()) {
                navRow.add(MenuButton.of(local.t("previous"), "previous"));
            }
            navRow.add(MenuButton.of(local.t("top-menu-category-button", args("category", categoryName)), "category"));
            if (page.hasNext() && state.nextCursorToken != null) {
                navRow.add(MenuButton.of(local.t("next"), "next"));
            }
            if (!navRow.isEmpty()) {
                grid.row(navRow.toArray(new MenuButton[0]));
            }

            grid.defaultNavigation(session, local);

            long totalEntries = page.totalEntries() != null ? page.totalEntries() : page.entries().size();
            int totalPages = page.totalEntries() != null
                    ? Math.max(1, (int) Math.ceil((double) page.totalEntries() / PLAYERS_PER_PAGE))
                    : (page.hasNext() ? page.currentPage() + 1 : page.currentPage());

            return MenuScreen.followUp(
                    local.t("top-menu-title", args("category", categoryName)),
                    page.entries().isEmpty()
                            ? local.t("top-menu-empty", args("category", categoryName))
                            : local.t("top-menu-content", args(
                                    "page", page.currentPage(),
                                    "totalPages", totalPages,
                                    "totalEntries", totalEntries,
                                    "category", categoryName,
                                    "selfRankLine", selfRankLine(local, page.selfRank())
                            )),
                    grid.build()
            );
        }
    }

    private final class CategoriesFlow extends BaseMenuFlow<TopCategoriesState> {
        CategoriesFlow() {
            super(ROUTE_TOP_CATEGORIES, TopCategoriesState.class);
            actionPrefix(ACTION_CATEGORY_PREFIX, (ctx, categoryName) -> {
                Session session = ctx.session();
                session.clear();
                session.clearDraft(TopMenuState.class);
                session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
                        .withParam("category", categoryName)
                        .withParam("page", "1"));
            });
        }

        @Override
        public TopCategoriesState createState(Session session, MenuRoute route, TopCategoriesState currentState) {
            return currentState == null ? new TopCategoriesState() : currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<TopCategoriesState> context) {
            Session session = context.session();
            Localization local = context.locale();

            String routeCatParam = context.route().param("category");
            TopCategory currentEnum = parseCategory(routeCatParam);
            String currentCategoryId = currentEnum != null
                    ? currentEnum.name()
                    : (routeCatParam != null && !routeCatParam.isBlank() ? routeCatParam : null);

            if (currentCategoryId == null) {
                TopCategory defEnum = topMenuService.resolveDefaultCategory();
                currentCategoryId = defEnum != null ? defEnum.name() : "";
            }

            String currentCategoryDisplayName = resolveDisplayName(currentCategoryId, local);

            var grid = new MenuGrid();
            List<TopCategoryProvider> allProviders = categoryRegistry.all();

            List<MenuButton> row = new ArrayList<>();
            for (TopCategoryProvider provider : allProviders) {
                boolean isSelected = provider.id().equalsIgnoreCase(currentCategoryId);
                String label = safeDisplayName(provider, local);
                String buttonText = isSelected ? "[accent]●[] " + label : label;
                row.add(MenuButton.of(buttonText, ACTION_CATEGORY_PREFIX + provider.id()));
                if (row.size() == 2) {
                    grid.row(row.toArray(new MenuButton[0]));
                    row.clear();
                }
            }
            if (!row.isEmpty()) {
                grid.row(row.toArray(new MenuButton[0]));
            }

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("top-menu-categories-title"),
                    local.t("top-menu-categories-content", args("category", currentCategoryDisplayName)),
                    grid.build()
            );
        }
    }

    private String safeDisplayName(TopCategoryProvider provider, Localization local) {
        try {
            String name = provider.displayName(local);
            if (name != null && !name.isBlank()) return name;
        } catch (Exception ignored) {
        }
        return provider.id();
    }

    private String safeFormatEntry(TopCategoryProvider provider, LeaderboardEntry entry, Localization local) {
        try {
            String formatted = provider.formatEntry(entry, local);
            if (formatted != null && !formatted.isBlank()) return formatted;
        } catch (Exception ignored) {
        }
        if (!entry.displayText().isBlank()) return entry.displayText();
        return rankLabel(entry.rank()) + " [accent]" + entry.displayName() + "[] [gray]—[] [white]" + entry.primaryValue() + "[]";
    }

    private String resolveDisplayName(String categoryId, Localization local) {
        TopCategory enumCat = parseCategory(categoryId);
        if (enumCat != null) {
            return local.t(enumCat.bundleKey());
        }
        return categoryRegistry.resolve(categoryId)
                .map(p -> safeDisplayName(p, local))
                .orElse(categoryId);
    }

    private String selfRankLine(Localization local, Integer selfRank) {
        if (selfRank == null) {
            return local.t("top-menu-self-rank-unknown");
        }
        return local.t("top-menu-self-rank-known", args("rank", selfRank));
    }

    private String cursorPlayerButton(Session session,
                                      TopMenuService.TopCursorPage topPage,
                                      TopCategory category,
                                      PlayerData playerData,
                                      int zeroBasedIndexOnPage) {
        Localization local = session.locale();
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(local.getLocale());
        int displayRank = topPage.displayRank(zeroBasedIndexOnPage);
        String rankLabel = rankLabel(displayRank);

        return switch (category) {
            case MINI_PVP -> local.t("top-menu-entry-mini-pvp", args(
                    "rankLabel", rankLabel,
                    "nickname", playerData.nickname,
                    "value", numberFormat.format(playerData.pvpRating)
            ));
            case PLAYTIME -> local.t("top-menu-entry-playtime", args(
                    "rankLabel", rankLabel,
                    "nickname", playerData.nickname,
                    "value", formatPlayTime(playerData.totalPlayTime, local)
            ));
            case HEXED -> local.t("top-menu-entry-hexed", args(
                    "rankLabel", rankLabel,
                    "nickname", playerData.nickname,
                    "rankName", local.t("hexed-ranks-" + playerData.hexedRank().name()),
                    "value", numberFormat.format(playerData.hexedPoints)
            ));
        };
    }

    private static String rankLabel(int displayRank) {
        return switch (displayRank) {
            case 1 -> "[gold]1.[]";
            case 2 -> "[lightgray]2.[]";
            case 3 -> "[orange]3.[]";
            default -> "[lightgray]" + displayRank + ".[]";
        };
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
