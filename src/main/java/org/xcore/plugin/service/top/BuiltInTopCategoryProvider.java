package org.xcore.plugin.service.top;

import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

public class BuiltInTopCategoryProvider implements TopCategoryProvider {

    private final TopCategory category;
    private final int priority;
    private final TopMenuService topMenuService;

    public BuiltInTopCategoryProvider(TopCategory category, int priority, TopMenuService topMenuService) {
        this.category = Objects.requireNonNull(category, "category");
        this.priority = priority;
        this.topMenuService = topMenuService;
    }

    @Override
    public String id() {
        return category.name();
    }

    @Override
    public String displayName(Localization local) {
        return local.t(category.bundleKey());
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public LeaderboardPage loadPage(LeaderboardPageRequest request) {
        if (topMenuService == null) {
            return LeaderboardPage.empty(request.page());
        }

        LeaderboardCursor cursor = LeaderboardCursorCodec.decode(request.cursor());
        TopMenuService.TopCursorPage cursorPage = topMenuService.loadCursorPage(
                category,
                cursor,
                request.page(),
                request.pageSize(),
                request.viewerData()
        );

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < cursorPage.players().size(); i++) {
            PlayerData player = cursorPage.players().get(i);
            int rank = cursorPage.displayRank(i);
            Map<String, String> attrs = new HashMap<>();
            if (category == TopCategory.HEXED && player.hexedRank() != null) {
                attrs.put("rankName", player.hexedRank().name());
            }
            entries.add(new LeaderboardEntry(
                    player.uuid,
                    rank,
                    player.nickname != null ? player.nickname : player.uuid,
                    formatPrimaryValue(player),
                    attrs,
                    ""
            ));
        }

        return new LeaderboardPage(
                cursorPage.currentPage(),
                entries,
                cursorPage.hasNext(),
                LeaderboardCursorCodec.encode(cursorPage.nextCursor()),
                cursorPage.totalEntries(),
                cursorPage.selfRank()
        );
    }

    @Override
    public String formatEntry(LeaderboardEntry entry, Localization local) {
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(local.getLocale());
        String rankLabel = rankLabel(entry.rank());

        return switch (category) {
            case MINI_PVP -> {
                long num = parseLongSafe(entry.primaryValue());
                yield local.t("top-menu-entry-mini-pvp", args(
                        "rankLabel", rankLabel,
                        "nickname", entry.displayName(),
                        "value", numberFormat.format(num)
                ));
            }
            case PLAYTIME -> {
                long time = parseLongSafe(entry.primaryValue());
                yield local.t("top-menu-entry-playtime", args(
                        "rankLabel", rankLabel,
                        "nickname", entry.displayName(),
                        "value", formatPlayTime(time, local)
                ));
            }
            case HEXED -> {
                long points = parseLongSafe(entry.primaryValue());
                String rankName = entry.attributes().getOrDefault("rankName", "newbie");
                yield local.t("top-menu-entry-hexed", args(
                        "rankLabel", rankLabel,
                        "nickname", entry.displayName(),
                        "rankName", local.t("hexed-ranks-" + rankName),
                        "value", numberFormat.format(points)
                ));
            }
        };
    }

    private String formatPrimaryValue(PlayerData player) {
        return switch (category) {
            case MINI_PVP -> String.valueOf(player.pvpRating);
            case PLAYTIME -> String.valueOf(player.totalPlayTime);
            case HEXED -> String.valueOf(player.hexedPoints);
        };
    }

    private static long parseLongSafe(String text) {
        if (text == null || text.isBlank()) return 0L;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String formatPlayTime(long totalPlayTime, Localization local) {
        long days = totalPlayTime / 86400;
        long hours = (totalPlayTime % 86400) / 3600;
        long minutes = (totalPlayTime % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(local.t("player-menu-time-days", args("value", days))).append(" ");
        if (hours > 0) sb.append(local.t("player-menu-time-hours", args("value", hours))).append(" ");
        if (minutes > 0 || sb.isEmpty()) sb.append(local.t("player-menu-time-minutes", args("value", minutes)));

        return sb.toString().trim();
    }

    private static String rankLabel(int displayRank) {
        return switch (displayRank) {
            case 1 -> "[gold]1.[]";
            case 2 -> "[lightgray]2.[]";
            case 3 -> "[orange]3.[]";
            default -> "[lightgray]" + displayRank + ".[]";
        };
    }

    public TopCategory category() {
        return category;
    }
}
