package org.xcore.plugin.gamemode.pvp.rating;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.ladder.LadderTopCategoryProvider;
import org.xcore.plugin.rating.season.SeasonAnnouncer;

/**
 * The MiniPvP ladder and its {@code /top} category. Registered on every server so the
 * leaderboard is visible network-wide; matches are settled only by the MiniPvP server.
 */
@Singleton
public class MiniPvPLadder {
    public static final String LADDER_ID = "minipvp";
    public static final String TOP_CATEGORY_ID = "MINI_PVP";

    private final Ladder ladder;

    @Inject
    public MiniPvPLadder(LadderService ladders,
                         TopCategoryRegistry topCategories,
                         PlayerDataRepository players,
                         SeasonAnnouncer seasonAnnouncer,
                         TomlXcoreConfig config) {
        this.ladder = ladders.register(
                new LadderDefinition(LADDER_ID, "top-menu-category-mini-pvp", RatingPolicy.teamEloV1()));

        boolean hostsMode = "mini-pvp".equals(config.server.name);
        topCategories.registerIfAbsent(
                new LadderTopCategoryProvider(TOP_CATEGORY_ID, hostsMode ? 20 : 10, ladder, players));
        if (hostsMode) {
            topCategories.setDefaultCategory(TOP_CATEGORY_ID);
            seasonAnnouncer.follow(ladder.definition());
        }
    }

    public Ladder ladder() {
        return ladder;
    }

    public RatingPolicy policy() {
        return ladder.definition().policy();
    }
}
