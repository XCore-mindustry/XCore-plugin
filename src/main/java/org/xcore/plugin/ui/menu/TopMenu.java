package org.xcore.plugin.ui.menu;

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

import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Deque;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class TopMenu extends Menu {

    private static final int PLAYERS_PER_PAGE = 10;

    private final TopMenuService topMenuService;
    private final PlayerMenu playerMenu;

    @Inject
    public TopMenu(Config config,
                   GlobalConfig globalConfig,
                   SessionService sessionService,
                   TopMenuService topMenuService,
                   PlayerMenu playerMenu) {
        super(config, globalConfig, sessionService);
        this.topMenuService = topMenuService;
        this.playerMenu = playerMenu;
    }

    public void top(String uuid) {
        top(uuid, null, null, 1, true);
    }

    public void top(String uuid, TopCategory category, int page) {
        top(uuid, category, null, page, true);
    }

    private void top(String uuid,
                     TopCategory category,
                     LeaderboardCursor cursor,
                     int page,
                     boolean resetState) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        TopMenuState state = session.getDraft(TopMenuState.class);
        if (state == null) {
            session.locale().send("error-internal", args());
            return;
        }

        TopCategory resolvedCategory = category == null ? topMenuService.resolveDefaultCategory() : category;
        if (resetState || state.category != resolvedCategory) {
            state.category = resolvedCategory;
            state.currentPage = 1;
            state.currentCursor = null;
            state.nextCursor = null;
            state.backStack.clear();
        }

        var topPage = topMenuService.loadCursorPage(resolvedCategory, cursor, page, PLAYERS_PER_PAGE, session.data);
        state.category = resolvedCategory;
        state.currentPage = topPage.currentPage();
        state.currentCursor = topPage.currentCursor();
        state.nextCursor = topPage.nextCursor();

        renderCursorPage(uuid, session, topPage, state);
    }

    private void renderCursorPage(String uuid,
                                  Session session,
                                  TopMenuService.TopCursorPage topPage,
                                  TopMenuState state) {
        TopCategory resolvedCategory = topPage.category();
        Localization local = session.locale();
        String categoryName = local.t(resolvedCategory.bundleKey());

        var builder = session.builder()
                .title("top-menu-title", args("category", categoryName));

        if (topPage.totalEntries() <= 0) {
            builder.content("top-menu-empty", args("category", categoryName));
        } else {
            builder.content("top-menu-content", args(
                    "page", topPage.currentPage(),
                    "totalPages", topPage.totalPages(),
                    "totalEntries", topPage.totalEntries(),
                    "category", categoryName,
                    "selfRankLine", selfRankLine(local, topPage.selfRank())
            ));

            addCursorPlayerRows(builder, session, topPage, resolvedCategory);
        }

        builder.start()
                .ifAddLocal(!state.backStack.isEmpty(), "previous", () -> {
                    LeaderboardCursor previous = state.backStack.pollLast();
                    top(uuid, resolvedCategory, previous, Math.max(1, state.currentPage - 1), false);
                })
                .add(local.t("top-menu-category-button", args("category", categoryName)), () -> {
                    TopCategory savedCategory = resolvedCategory;
                    LeaderboardCursor savedCursor = state.currentCursor;
                    int savedPage = state.currentPage;
                    session.pushHistory(() -> top(uuid, savedCategory, savedCursor, savedPage, false));
                    categories(uuid, resolvedCategory);
                })
                .ifAddLocal(topPage.hasNext() && state.nextCursor != null, "next", () -> {
                    state.backStack.addLast(state.currentCursor);
                    top(uuid, resolvedCategory, state.nextCursor, state.currentPage + 1, false);
                })
                .end()
                .addNavigationRow()
                .show();
    }

    public void categories(String uuid, TopCategory currentCategory) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        TopCategory resolvedCategory = currentCategory == null ? topMenuService.resolveDefaultCategory() : currentCategory;

        session.builder()
                .title("top-menu-categories-title")
                .content("top-menu-categories-content", args(
                        "category", session.locale().t(resolvedCategory.bundleKey())
                ))
                .addRow(
                        categoryButton(session, TopCategory.MINI_PVP, resolvedCategory),
                        () -> top(uuid, TopCategory.MINI_PVP, null, 1, true),
                        categoryButton(session, TopCategory.PLAYTIME, resolvedCategory),
                        () -> top(uuid, TopCategory.PLAYTIME, null, 1, true)
                )
                .addRow(categoryButton(session, TopCategory.HEXED, resolvedCategory), () -> top(uuid, TopCategory.HEXED, null, 1, true))
                .addNavigationRow()
                .show();
    }

    private void addCursorPlayerRows(org.xcore.plugin.ui.MenuBuilder builder,
                                     Session session,
                                     TopMenuService.TopCursorPage topPage,
                                     TopCategory category) {
        for (int i = 0; i < topPage.players().size(); i++) {
            PlayerData player = topPage.players().get(i);
            int indexOnPage = i;

            builder.addRow(
                    cursorPlayerButton(session, topPage, category, player, indexOnPage),
                    () -> openCursorPlayerProfile(session, topPage, category, player)
            );
        }
    }

    private void openCursorPlayerProfile(Session session,
                                         TopMenuService.TopCursorPage topPage,
                                         TopCategory category,
                                         PlayerData playerData) {
        session.pushHistory(() -> top(session.data.uuid, category, topPage.currentCursor(), topPage.currentPage(), false));
        playerMenu.player(session.data.uuid, playerData);
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
}
