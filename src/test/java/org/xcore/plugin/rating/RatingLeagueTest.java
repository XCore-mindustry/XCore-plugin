package org.xcore.plugin.rating;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RatingLeagueTest {

    @Test
    @DisplayName("fromRating resolves correct tiers across all thresholds")
    void fromRating_resolvesTiers() {
        assertThat(RatingLeague.fromRating(0)).isEqualTo(RatingLeague.SCRAP);
        assertThat(RatingLeague.fromRating(100)).isEqualTo(RatingLeague.SCRAP);
        assertThat(RatingLeague.fromRating(799)).isEqualTo(RatingLeague.SCRAP);
        assertThat(RatingLeague.fromRating(800)).isEqualTo(RatingLeague.COPPER);
        assertThat(RatingLeague.fromRating(999)).isEqualTo(RatingLeague.COPPER);
        assertThat(RatingLeague.fromRating(1000)).isEqualTo(RatingLeague.LEAD);
        assertThat(RatingLeague.fromRating(1199)).isEqualTo(RatingLeague.LEAD);
        assertThat(RatingLeague.fromRating(1200)).isEqualTo(RatingLeague.GRAPHITE);
        assertThat(RatingLeague.fromRating(1399)).isEqualTo(RatingLeague.GRAPHITE);
        assertThat(RatingLeague.fromRating(1400)).isEqualTo(RatingLeague.SILICON);
        assertThat(RatingLeague.fromRating(1599)).isEqualTo(RatingLeague.SILICON);
        assertThat(RatingLeague.fromRating(1600)).isEqualTo(RatingLeague.TITANIUM);
        assertThat(RatingLeague.fromRating(1799)).isEqualTo(RatingLeague.TITANIUM);
        assertThat(RatingLeague.fromRating(1800)).isEqualTo(RatingLeague.THORIUM);
        assertThat(RatingLeague.fromRating(1999)).isEqualTo(RatingLeague.THORIUM);
        assertThat(RatingLeague.fromRating(2000)).isEqualTo(RatingLeague.PLASTANIUM);
        assertThat(RatingLeague.fromRating(2199)).isEqualTo(RatingLeague.PLASTANIUM);
        assertThat(RatingLeague.fromRating(2200)).isEqualTo(RatingLeague.PHASE_FABRIC);
        assertThat(RatingLeague.fromRating(2499)).isEqualTo(RatingLeague.PHASE_FABRIC);
        assertThat(RatingLeague.fromRating(2500)).isEqualTo(RatingLeague.SURGE_ALLOY);
        assertThat(RatingLeague.fromRating(9999)).isEqualTo(RatingLeague.SURGE_ALLOY);
    }

    @Test
    @DisplayName("each league tier has non-blank icon, color tag, and localization key")
    void leagueMetadata_valid() {
        for (RatingLeague league : RatingLeague.values()) {
            assertThat(league.icon()).isNotBlank();
            assertThat(league.colorTag()).startsWith("[#").endsWith("]");
            assertThat(league.localizationKey()).isEqualTo("rating_league_" + league.id().replace('-', '_'));
            assertThat(league.localizationKey("hexed")).isEqualTo("hexed_league_" + league.id().replace('-', '_'));
        }
    }

    @Test
    @DisplayName("next and hasNext step linearly through material tiers")
    void next_stepsCorrectly() {
        assertThat(RatingLeague.SCRAP.next()).isEqualTo(RatingLeague.COPPER);
        assertThat(RatingLeague.COPPER.next()).isEqualTo(RatingLeague.LEAD);
        assertThat(RatingLeague.LEAD.next()).isEqualTo(RatingLeague.GRAPHITE);
        assertThat(RatingLeague.PHASE_FABRIC.next()).isEqualTo(RatingLeague.SURGE_ALLOY);
        assertThat(RatingLeague.SURGE_ALLOY.next()).isNull();
        assertThat(RatingLeague.SURGE_ALLOY.hasNext()).isFalse();
    }
}
