package org.xcore.plugin.gamemode.pvp.guide;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.InMemoryLadderStore;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.ladder.LadderStanding;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MiniPvPGuideServiceTest {

    private static Ladder ladder(InMemoryLadderStore store) {
        return new LadderService(store, new InMemoryIdempotencyLedger())
                .register(new LadderDefinition("minipvp", "top-menu-category-mini-pvp", RatingPolicy.teamEloV1()));
    }

    private static LadderStanding standing(Ladder ladder, String uuid, int matches) {
        return new LadderStanding(ladder.id(), ladder.currentSeason(), uuid, 1000, 1000, matches, 0, Map.of());
    }

    @Test
    void aPlayerWithNoRatedRoundsIsANewcomer() {
        assertThat(MiniPvPGuideService.isNewcomer(ladder(new InMemoryLadderStore()), "fresh")).isTrue();
    }

    @Test
    void aFewRatedRoundsMakeARegular() {
        InMemoryLadderStore store = new InMemoryLadderStore();
        Ladder ladder = ladder(store);
        store.put(standing(ladder, "learning", MiniPvPGuideService.NEWCOMER_MATCHES - 1));
        store.put(standing(ladder, "regular", MiniPvPGuideService.NEWCOMER_MATCHES));

        assertThat(MiniPvPGuideService.isNewcomer(ladder, "learning")).isTrue();
        assertThat(MiniPvPGuideService.isNewcomer(ladder, "regular")).isFalse();
    }
}
