package org.xcore.plugin.rating;

import mindustry.content.Items;
import mindustry.type.Item;

/**
 * Competitive Mindustry material tier leagues for Elo rating systems.
 */
public enum RatingLeague {
    SCRAP("scrap", 100, "[#7e7e7e]"),
    COPPER("copper", 800, "[#d37f47]"),
    LEAD("lead", 1000, "[#8c7fa9]"),
    GRAPHITE("graphite", 1200, "[#6e7b8c]"),
    SILICON("silicon", 1400, "[#53565c]"),
    TITANIUM("titanium", 1600, "[#8da1e3]"),
    THORIUM("thorium", 1800, "[#f9a3c7]"),
    PLASTANIUM("plastanium", 2000, "[#cbd97f]"),
    PHASE_FABRIC("phase-fabric", 2200, "[#ffd59e]"),
    SURGE_ALLOY("surge-alloy", 2500, "[#f3e979]");

    private static final RatingLeague[] VALUES = values();

    private final String id;
    private final int minimumRating;
    private final String colorTag;

    RatingLeague(String id, int minimumRating, String colorTag) {
        this.id = id;
        this.minimumRating = minimumRating;
        this.colorTag = colorTag;
    }

    public String id() {
        return id;
    }

    public int minimumRating() {
        return minimumRating;
    }

    public String colorTag() {
        return colorTag;
    }

    public String localizationKey() {
        return "rating_league_" + id.replace('-', '_');
    }

    public String localizationKey(String gamemodePrefix) {
        if (gamemodePrefix == null || gamemodePrefix.isBlank()) {
            return localizationKey();
        }
        return gamemodePrefix + "_league_" + id.replace('-', '_');
    }

    /**
     * Returns the native Mindustry item glyph emoji or fallback identifier.
     */
    public String icon() {
        Item item = item();
        if (item != null) {
            String emoji = item.emoji();
            if (emoji != null && !emoji.isBlank()) {
                return emoji;
            }
        }
        return ":" + id + ":";
    }

    public Item item() {
        return switch (this) {
            case SCRAP -> Items.scrap;
            case COPPER -> Items.copper;
            case LEAD -> Items.lead;
            case GRAPHITE -> Items.graphite;
            case SILICON -> Items.silicon;
            case TITANIUM -> Items.titanium;
            case THORIUM -> Items.thorium;
            case PLASTANIUM -> Items.plastanium;
            case PHASE_FABRIC -> Items.phaseFabric;
            case SURGE_ALLOY -> Items.surgeAlloy;
        };
    }

    public static RatingLeague fromRating(int rating) {
        RatingLeague result = SCRAP;
        for (RatingLeague league : VALUES) {
            if (rating >= league.minimumRating) {
                result = league;
            } else {
                break;
            }
        }
        return result;
    }
}
