package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.text.NumberFormat;
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
        top(uuid, null, 1);
    }

    public void top(String uuid, TopCategory category, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        var topPage = topMenuService.loadPage(category, page, PLAYERS_PER_PAGE, session.data);
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
                    "selfRankLine", selfRankLine(local, topPage)
            ));

            addPlayerRows(builder, session, topPage, resolvedCategory);
        }

        builder.start()
                .ifAddLocal(topPage.hasPrevious(), "previous", () -> top(uuid, resolvedCategory, topPage.currentPage() - 1))
                .add(local.t("top-menu-category-button", args("category", categoryName)), () -> {
                    session.pushHistory(() -> top(uuid, resolvedCategory, topPage.currentPage()));
                    categories(uuid, resolvedCategory);
                })
                .ifAddLocal(topPage.hasNext(), "next", () -> top(uuid, resolvedCategory, topPage.currentPage() + 1))
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
                        () -> top(uuid, TopCategory.MINI_PVP, 1),
                        categoryButton(session, TopCategory.PLAYTIME, resolvedCategory),
                        () -> top(uuid, TopCategory.PLAYTIME, 1)
                )
                .addRow(categoryButton(session, TopCategory.HEXED, resolvedCategory), () -> top(uuid, TopCategory.HEXED, 1))
                .addNavigationRow()
                .show();
    }

    private void addPlayerRows(org.xcore.plugin.ui.MenuBuilder builder,
                               Session session,
                               TopMenuService.TopPage topPage,
                               TopCategory category) {
        for (int i = 0; i < topPage.players().size(); i++) {
            PlayerData player = topPage.players().get(i);
            int indexOnPage = i;

            builder.addRow(
                    playerButton(session, topPage, category, player, indexOnPage),
                    () -> openPlayerProfile(session, topPage, category, player)
            );
        }
    }

    private void openPlayerProfile(Session session,
                                   TopMenuService.TopPage topPage,
                                   TopCategory category,
                                   PlayerData playerData) {
        session.pushHistory(() -> top(session.data.uuid, category, topPage.currentPage()));
        playerMenu.player(session.data.uuid, playerData);
    }

    private String playerButton(Session session,
                                TopMenuService.TopPage topPage,
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

    private String selfRankLine(Localization local, TopMenuService.TopPage topPage) {
        if (topPage.selfRank() == null) {
            return local.t("top-menu-self-rank-unknown");
        }

        return local.t("top-menu-self-rank-known", args("rank", topPage.selfRank()));
    }

    private String rankLabel(int displayRank) {
        return switch (displayRank) {
            case 1 -> "[gold]1.[]";
            case 2 -> "[lightgray]2.[]";
            case 3 -> "[orange]3.[]";
            default -> "[lightgray]" + displayRank + ".[]";
        };
    }
}
