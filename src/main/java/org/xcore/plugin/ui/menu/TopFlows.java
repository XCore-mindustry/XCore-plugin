package org.xcore.plugin.ui.menu;

import org.xcore.plugin.concurrent.Async;
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
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
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

import static com.ospx.flubundle.Bundle.args;

final class TopFlows {

    static final String ROUTE_TOP_LIST = "top.list";
    static final String ROUTE_TOP_CATEGORIES = "top.categories";

    static final int PLAYERS_PER_PAGE = 10;
    static final LeaderboardCursor FIRST_PAGE_MARKER = new LeaderboardCursor(0, 0, Integer.MIN_VALUE);
    static final String FIRST_PAGE_TOKEN = "__first__";

    static final String ACTION_PROFILE_PREFIX = "profile:";
    static final String ACTION_CATEGORY_PREFIX = "category:";

    private TopFlows() {
    }

    static TopCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TopCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static final class TopListFlow extends BaseMenuFlow<TopMenu.TopMenuState> {
        private final TopMenu menu;
        private final TopMenuService topMenuService;
        private final TopCategoryRegistry categoryRegistry;
        private final PlayerMenu playerMenu;
        private final SessionService sessionService;
        private final Async async;

        TopListFlow(TopMenu menu,
                    TopMenuService topMenuService,
                    TopCategoryRegistry categoryRegistry,
                    PlayerMenu playerMenu,
                    SessionService sessionService,
                    Async async) {
            super(ROUTE_TOP_LIST, TopMenu.TopMenuState.class);
            this.menu = menu;
            this.topMenuService = topMenuService;
            this.categoryRegistry = categoryRegistry;
            this.playerMenu = playerMenu;
            this.sessionService = sessionService;
            this.async = async;

            action("previous", ctx -> {
                TopMenu.TopMenuState state = ctx.state();
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
                TopMenu.TopMenuState state = ctx.state();
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
                TopMenu.TopMenuState state = ctx.state();
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
                    TopMenu.TopMenuState histState = session.getDraft(TopMenu.TopMenuState.class);
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
                Session session = ctx.session();
                if (session == null || session.data == null || targetUuid == null || targetUuid.isBlank()) return;

                PlayerData target = resolveOnlineProfileTarget(session, targetUuid);
                if (target != null) {
                    openProfile(ctx, session, target);
                    return;
                }

                if (async == null || session.player == null) return;

                var sourceRoute = ctx.route();
                var sourceScreen = session.activeScreen();
                long sourceVersion = sourceScreen == null ? -1L : sourceScreen.version();
                async.onMainForPlayer(session.player,
                        sessionService.getOrLoadFromDbAsync(targetUuid),
                        (player, loaded) -> {
                            if (loaded != null && isCurrentScreen(session, sourceRoute, sourceVersion)) {
                                openProfile(ctx, session, loaded);
                            }
                        });
            });
        }

        private PlayerData resolveOnlineProfileTarget(Session session, String targetUuid) {
            if (targetUuid.equals(session.data.uuid)) {
                return session.data;
            }
            Session online = sessionService != null ? sessionService.get(targetUuid) : null;
            return online != null ? online.data : null;
        }

        private boolean isCurrentScreen(Session session, MenuRoute sourceRoute, long sourceVersion) {
            var current = session.activeScreen();
            return current != null
                    && current.version() == sourceVersion
                    && Objects.equals(current.route(), sourceRoute);
        }

        private void openProfile(MenuRenderContext<?> ctx, Session session, PlayerData target) {
            if (ctx.route() != null) {
                session.pushRouteHistory(ctx.route());
            }
            session.menuService.hideFollowUp(session);
            playerMenu.player(session.data.uuid, target);
        }

        @Override
        public TopMenu.TopMenuState createState(Session session, MenuRoute route, TopMenu.TopMenuState currentState) {
            if (currentState == null) {
                currentState = new TopMenu.TopMenuState();
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
        public MenuScreen render(MenuRenderContext<TopMenu.TopMenuState> context) {
            Session session = context.session();
            TopMenu.TopMenuState state = context.state();
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
                        "value", menu.formatPlayTime(playerData.totalPlayTime, local)
                ));
                case HEXED -> local.t("top-menu-entry-hexed", args(
                        "rankLabel", rankLabel,
                        "nickname", playerData.nickname,
                        "rankName", local.t("hexed-ranks-" + playerData.hexedRank().name()),
                        "value", numberFormat.format(playerData.hexedPoints)
                ));
            };
        }
    }

    static final class CategoriesFlow extends BaseMenuFlow<TopMenu.TopCategoriesState> {
        private final TopMenuService topMenuService;
        private final TopCategoryRegistry categoryRegistry;

        CategoriesFlow(TopMenuService topMenuService, TopCategoryRegistry categoryRegistry) {
            super(ROUTE_TOP_CATEGORIES, TopMenu.TopCategoriesState.class);
            this.topMenuService = topMenuService;
            this.categoryRegistry = categoryRegistry;

            actionPrefix(ACTION_CATEGORY_PREFIX, (ctx, categoryName) -> {
                Session session = ctx.session();
                session.clear();
                session.clearDraft(TopMenu.TopMenuState.class);
                session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
                        .withParam("category", categoryName)
                        .withParam("page", "1"));
            });
        }

        @Override
        public TopMenu.TopCategoriesState createState(Session session, MenuRoute route, TopMenu.TopCategoriesState currentState) {
            return currentState == null ? new TopMenu.TopCategoriesState() : currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<TopMenu.TopCategoriesState> context) {
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

        private String resolveDisplayName(String categoryId, Localization local) {
            TopCategory enumCat = parseCategory(categoryId);
            if (enumCat != null) {
                return local.t(enumCat.bundleKey());
            }
            return categoryRegistry.resolve(categoryId)
                    .map(p -> safeDisplayName(p, local))
                    .orElse(categoryId);
        }
    }

    static String safeDisplayName(TopCategoryProvider provider, Localization local) {
        try {
            String name = provider.displayName(local);
            if (name != null && !name.isBlank()) return name;
        } catch (Exception ignored) {
        }
        return provider.id();
    }

    static String safeFormatEntry(TopCategoryProvider provider, LeaderboardEntry entry, Localization local) {
        try {
            String formatted = provider.formatEntry(entry, local);
            if (formatted != null && !formatted.isBlank()) return formatted;
        } catch (Exception ignored) {
        }
        if (!entry.displayText().isBlank()) return entry.displayText();
        return rankLabel(entry.rank()) + " [accent]" + entry.displayName() + "[] [gray]—[] [white]" + entry.primaryValue() + "[]";
    }

    static String selfRankLine(Localization local, Integer selfRank) {
        if (selfRank == null) {
            return local.t("top-menu-self-rank-unknown");
        }
        return local.t("top-menu-self-rank-known", args("rank", selfRank));
    }

    static String rankLabel(int displayRank) {
        return switch (displayRank) {
            case 1 -> "[gold]1.[]";
            case 2 -> "[lightgray]2.[]";
            case 3 -> "[orange]3.[]";
            default -> "[lightgray]" + displayRank + ".[]";
        };
    }
}
