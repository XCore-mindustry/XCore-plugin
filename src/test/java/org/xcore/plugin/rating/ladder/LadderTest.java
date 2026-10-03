package org.xcore.plugin.rating.ladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.rating.RatingPolicy;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LadderTest {
    private static final RatingPolicy POLICY = new RatingPolicy(1000, 100, 32, 2);

    private InMemoryLadderStore store;
    private InMemoryIdempotencyLedger ledger;
    private Ladder ladder;

    @BeforeEach
    void setUp() {
        store = new InMemoryLadderStore();
        ledger = new InMemoryIdempotencyLedger();
        ladder = new LadderService(store, ledger).register(new LadderDefinition("duel", "duel-name", POLICY));
    }

    private static MatchSettlement match(String matchId, StandingMutation... mutations) {
        return MatchSettlement.rated(matchId, "v1", MatchSettlement.hash(matchId), List.of(mutations));
    }

    @Test
    @DisplayName("a player without a standing has the starting rating and is not placed")
    void unknownPlayer_hasStartingRating() {
        assertThat(ladder.rating("nobody")).isEqualTo(1000);
        assertThat(ladder.rating(null)).isEqualTo(1000);
        assertThat(ladder.standing("nobody").placed()).isFalse();
        assertThat(ladder.rankOf("nobody")).isEmpty();
        assertThat(ladder.count()).isZero();
    }

    @Test
    @DisplayName("settle applies deltas, counters and stats, and caches the new standings")
    void settle_appliesMutations() {
        SettlementResult result = ladder.settle(match("m1",
                new StandingMutation("winner", 16, true, Map.of("top3", 1)),
                new StandingMutation("loser", -16, false)));

        assertThat(result.claimed()).isTrue();
        assertThat(result.applied()).isTrue();
        assertThat(result.operationId()).isEqualTo("duel:m1:rating:v1");

        LadderStanding winner = result.standings().get("winner");
        assertThat(winner.rating()).isEqualTo(1016);
        assertThat(winner.peakRating()).isEqualTo(1016);
        assertThat(winner.matches()).isEqualTo(1);
        assertThat(winner.wins()).isEqualTo(1);
        assertThat(winner.stat("top3")).isEqualTo(1);
        assertThat(winner.season()).isEqualTo(Ladder.FIRST_SEASON);

        LadderStanding loser = store.find("duel", Ladder.FIRST_SEASON, "loser").orElseThrow();
        assertThat(loser.rating()).isEqualTo(984);
        assertThat(loser.peakRating()).isEqualTo(984);
        assertThat(loser.wins()).isZero();

        assertThat(ladder.cachedRating("winner")).isEqualTo(1016);
        assertThat(ladder.rankOf("winner")).hasValue(1);
        assertThat(ladder.rankOf("loser")).hasValue(2);
        assertThat(ladder.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("settling the same match again changes nothing")
    void settle_isIdempotent() {
        MatchSettlement settlement = match("m1",
                new StandingMutation("a", 16, true), new StandingMutation("b", -16, false));
        ladder.settle(settlement);

        SettlementResult second = ladder.settle(settlement);

        assertThat(second.claimed()).isFalse();
        assertThat(second.applied()).isFalse();
        assertThat(second.reason()).isEqualTo("completed");
        assertThat(ladder.rating("a")).isEqualTo(1016);
        assertThat(ladder.standing("a").matches()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same match with a different result is rejected")
    void settle_rejectsDifferentResult() {
        ladder.settle(match("m1", new StandingMutation("a", 16, true), new StandingMutation("b", -16, false)));

        MatchSettlement tampered = MatchSettlement.rated("m1", "v1", MatchSettlement.hash("other"),
                List.of(new StandingMutation("a", 30, true)));

        assertThatThrownBy(() -> ladder.settle(tampered)).isInstanceOf(IllegalStateException.class);
        assertThat(ladder.rating("a")).isEqualTo(1016);
    }

    @Test
    @DisplayName("an unrated match is recorded once and leaves standings alone")
    void settle_unratedMatch() {
        MatchSettlement unrated = MatchSettlement.unrated("m1", "v1", MatchSettlement.hash("m1"), "not_enough_players");

        SettlementResult first = ladder.settle(unrated);
        SettlementResult second = ladder.settle(unrated);

        assertThat(first.claimed()).isTrue();
        assertThat(first.applied()).isFalse();
        assertThat(first.reason()).isEqualTo("not_enough_players");
        assertThat(second.claimed()).isFalse();
        assertThat(second.reason()).isEqualTo("skipped");
        assertThat(ladder.count()).isZero();
    }

    @Test
    @DisplayName("a settlement interrupted halfway is finished by a retry without double-applying")
    void settle_recoversInterruptedSettlement() {
        MatchSettlement settlement = match("m1",
                new StandingMutation("a", 16, true), new StandingMutation("b", -16, false));
        // The first attempt claimed the match and updated one player before the server died.
        String operationId = ladder.operationId("m1", "v1");
        ledger.claim(operationId, "RATING_SETTLEMENT", settlement.resultHash());
        store.applyOnce("duel", Ladder.FIRST_SEASON, operationId, settlement.mutations().getFirst(), 1000, 100);

        assertThat(ladder.settle(settlement).claimed()).isFalse(); // lease still held

        ledger.expireLeases();
        SettlementResult recovered = ladder.settle(settlement);

        assertThat(recovered.applied()).isTrue();
        assertThat(recovered.standings().get("a").rating()).isEqualTo(1016);
        assertThat(recovered.standings().get("a").matches()).isEqualTo(1);
        assertThat(recovered.standings().get("b").rating()).isEqualTo(984);
        assertThat(ledger.find(operationId).orElseThrow().status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("rating never drops below the policy minimum and the peak is kept")
    void settle_clampsToMinimumRating() {
        ladder.settle(match("m1", new StandingMutation("a", 50, true)));
        ladder.settle(match("m2", new StandingMutation("a", -5000, false)));

        LadderStanding standing = ladder.standing("a");
        assertThat(standing.rating()).isEqualTo(100);
        assertThat(standing.peakRating()).isEqualTo(1050);
        assertThat(standing.matches()).isEqualTo(2);
    }

    @Test
    @DisplayName("reloadCache picks up standings changed behind the ladder's back")
    void reloadCache_rereadsStorage() {
        ladder.settle(match("m1", new StandingMutation("a", 16, true)));
        store.put(new LadderStanding("duel", Ladder.FIRST_SEASON, "a", 1500, 1500, 9, 7, Map.of()));

        assertThat(ladder.cachedRating("a")).isEqualTo(1016);
        ladder.reloadCache();
        assertThat(ladder.cachedRating("a")).isEqualTo(1500);
    }

    @Test
    @DisplayName("top pages through standings best-first with a stable tie-break")
    void top_pagesWithCursor() {
        ladder.settle(match("m1",
                new StandingMutation("c", 10, true),
                new StandingMutation("a", 10, true),
                new StandingMutation("b", 30, true),
                new StandingMutation("d", -10, false)));

        StandingPage first = ladder.top(2, null);
        assertThat(first.standings()).extracting(LadderStanding::uuid).containsExactly("b", "a");
        assertThat(first.hasNext()).isTrue();

        StandingPage second = ladder.top(2, first.nextCursor());
        assertThat(second.standings()).extracting(LadderStanding::uuid).containsExactly("c", "d");
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }
}
