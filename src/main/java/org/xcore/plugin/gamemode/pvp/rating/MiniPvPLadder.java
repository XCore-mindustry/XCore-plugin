package org.xcore.plugin.gamemode.pvp.rating;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import mindustry.gen.Iconc;
import org.xcore.plugin.integration.profile.ProfileSectionRegistry;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.season.SeasonAnnouncer;
import org.xcore.plugin.rating.view.LadderViews;

import java.util.Optional;

/**
 * The MiniPvP ladder with its {@code /top} category and profile section. Registered on every
 * server so the rating is visible network-wide; matches are settled only by the MiniPvP server.
 */
@Singleton
public class MiniPvPLadder {
    public static final String LADDER_ID = "minipvp";
    public static final String TOP_CATEGORY_ID = "MINI_PVP";

    private final Ladder ladder;

    @Inject
    public MiniPvPLadder(LadderService ladders,
                         TopCategoryRegistry topCategories,
                         ProfileSectionRegistry profileSections,
                         LadderViews views,
                         SeasonAnnouncer seasonAnnouncer,
                         TomlXcoreConfig config) {
        this.ladder = ladders.register(
                new LadderDefinition(LADDER_ID, "top-menu-category-mini-pvp", RatingPolicy.teamEloV1()));

        boolean hostsMode = "mini-pvp".equals(config.server.name);
        topCategories.registerIfAbsent(views.topCategory(TOP_CATEGORY_ID, hostsMode ? 20 : 10, ladder));
        // Elsewhere in the network the section appears only for players who have played MiniPvP.
        profileSections.register(views.profileSection(ladder, String.valueOf(Iconc.modePvp), 10, hostsMode)
                .withDetail((target, local) -> target.legacyPvpRating > 0
                        ? Optional.of(local.t("player-stats-legacy-pvp-rating") + " [gray]" + target.legacyPvpRating + "[]")
                        : Optional.empty()));
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
