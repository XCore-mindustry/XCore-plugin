package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;
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

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class TopMenu extends Menu {

    private static final int PLAYERS_PER_PAGE = 10;
    private static final LeaderboardCursor FIRST_PAGE_MARKER = new LeaderboardCursor(0, 0, -2);

    private static final String ROUTE_TOP_LIST = "top.list";
    private static final String ROUTE_TOP_CATEGORIES = "top.categories";

    private static final String ACTION_PROFILE_PREFIX = "profile:";

    private final TopMenuService topMenuService;
    private final PlayerMenu playerMenu;
    private final MenuService menuService;

    @Inject
    public TopMenu(Config config,
                   GlobalConfig globalConfig,
                   SessionService sessionService,
                   MenuService menuService,
                   TopMenuService topMenuService,
                   PlayerMenu playerMenu) {
        super(config, globalConfig, sessionService);
        this.menuService = menuService;
        this.topMenuService = topMenuService;
        this.playerMenu = playerMenu;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new TopListFlow());
        menuService.registerRoute(new CategoriesFlow());
    }

    public void top(String uuid) {
        top(uuid, null, 1);
    }

    public void top(String uuid, TopCategory category, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        TopCategory resolvedCategory = category == null ? topMenuService.resolveDefaultCategory() : category;
        TopMenuState state = session.getDraft(TopMenuState.class);
        state.category = resolvedCategory;
        state.currentPage = page;
        state.currentCursor = null;
        state.nextCursor = null;
        state.backStack.clear();

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
                .withParam("category", resolvedCategory.name())
                .withParam("page", String.valueOf(page)));
    }

    public void categories(String uuid, TopCategory currentCategory) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        TopCategory resolvedCategory = currentCategory == null ? topMenuService.resolveDefaultCategory() : currentCategory;

        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_CATEGORIES)
                .withParam("category", resolvedCategory.name()));
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
                LeaderboardCursor previous = state.backStack.pollLast();
                LeaderboardCursor previousCursor = previous == FIRST_PAGE_MARKER ? null : previous;
                state.currentCursor = previousCursor;
                state.currentPage = Math.max(1, state.currentPage - 1);
                ctx.render();
            });
            action("next", ctx -> {
                TopMenuState state = ctx.state();
                state.backStack.addLast(state.currentCursor == null ? FIRST_PAGE_MARKER : state.currentCursor);
                state.currentCursor = state.nextCursor;
                state.currentPage = state.currentPage + 1;
                ctx.render();
            });
            action("category", ctx -> {
                TopMenuState state = ctx.state();
                Session session = ctx.session();
                TopCategory savedCategory = state.category;
                LeaderboardCursor savedCursor = state.currentCursor;
                int savedPage = state.currentPage;
                Deque<LeaderboardCursor> savedBackStack = new ArrayDeque<>(state.backStack);
                LeaderboardCursor savedNextCursor = state.nextCursor;
                session.pushHistory(() -> {
                    session.clear();
                    TopMenuState histState = session.getDraft(TopMenuState.class);
                    histState.category = savedCategory;
                    histState.currentCursor = savedCursor;
                    histState.currentPage = savedPage;
                    histState.backStack = savedBackStack;
                    histState.nextCursor = savedNextCursor;
                    session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
                            .withParam("category", savedCategory.name()));
                });
                session.menuService.hideFollowUp(session);
                session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_CATEGORIES)
                        .withParam("category", state.category.name()));
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
            TopCategory routeCategory = parseCategory(route.param("category"));
            TopCategory resolvedCategory = routeCategory == null ? topMenuService.resolveDefaultCategory() : routeCategory;
            int routePage = route.intParam("page", 1);

            if (currentState.category != resolvedCategory) {
                currentState.category = resolvedCategory;
                currentState.currentPage = routePage;
                currentState.currentCursor = null;
                currentState.nextCursor = null;
                currentState.backStack.clear();
            }

            return currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<TopMenuState> context) {
            Session session = context.session();
            TopMenuState state = context.state();
            Localization local = context.locale();

            TopCategory resolvedCategory = state.category == null ? topMenuService.resolveDefaultCategory() : state.category;
            var topPage = topMenuService.loadCursorPage(resolvedCategory, state.currentCursor, state.currentPage, PLAYERS_PER_PAGE, session.data);
            state.category = resolvedCategory;
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
    }

    private final class CategoriesFlow extends BaseMenuFlow<TopCategoriesState> {
        CategoriesFlow() {
            super(ROUTE_TOP_CATEGORIES, TopCategoriesState.class);
            defaultAction((ctx, actionId) -> {
                try {
                    TopCategory category = TopCategory.valueOf(actionId);
                    Session session = ctx.session();
                    session.clear();
                    session.clearDraft(TopMenuState.class);
                    session.menuService.renderRoute(session, MenuRoute.of(ROUTE_TOP_LIST)
                            .withParam("category", category.name())
                            .withParam("page", "1"));
                } catch (IllegalArgumentException ignored) {
                }
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

            TopCategory currentCategory = parseCategory(context.route().param("category"));
            if (currentCategory == null) {
                currentCategory = topMenuService.resolveDefaultCategory();
            }

            String categoryName = local.t(currentCategory.bundleKey());

            var grid = new MenuGrid();
            grid.row(
                    MenuButton.of(categoryButton(session, TopCategory.MINI_PVP, currentCategory), TopCategory.MINI_PVP.name()),
                    MenuButton.of(categoryButton(session, TopCategory.PLAYTIME, currentCategory), TopCategory.PLAYTIME.name())
            );
            grid.row(MenuButton.of(categoryButton(session, TopCategory.HEXED, currentCategory), TopCategory.HEXED.name()));
            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("top-menu-categories-title"),
                    local.t("top-menu-categories-content", args("category", categoryName)),
                    grid.build()
            );
        }
    }

    private String categoryButton(Session session, TopCategory category, TopCategory currentCategory) {
        String label = session.locale().t(category.bundleKey());
        return category == currentCategory ? "[accent]●[] " + label : label;
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

    private String rankLabel(int displayRank) {
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
    }

    public static final class TopCategoriesState {
    }
}
