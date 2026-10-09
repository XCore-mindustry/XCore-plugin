package org.xcore.plugin.rating.ladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.match.InMemoryMatchStore;
import org.xcore.plugin.rating.match.MatchParticipant;
import org.xcore.plugin.rating.match.MatchRecord;
import org.xcore.plugin.rating.match.MatchReport;
import org.xcore.plugin.rating.match.MatchStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class LadderMatchHistoryTest {
    private static final RatingPolicy POLICY = new RatingPolicy(1000, 100, 32, 2);
    private static final Instant ENDED = Instant.parse("2026-10-01T12:00:00Z");

    private InMemoryLadderStore store;
    private InMemoryIdempotencyLedger ledger;
    private InMemoryMatchStore matches;
    private Ladder ladder;

    @BeforeEach
    void setUp() {
        store = new InMemoryLadderStore();
        ledger = new InMemoryIdempotencyLedger();
        matches = new InMemoryMatchStore();
        ladder = ladder(matches);
    }

    private Ladder ladder(MatchStore history) {
        return new LadderService(store, ledger, LadderSeasons.single(), history)
                .register(new LadderDefinition("duel", "duel-name", POLICY));
    }

    private static MatchReport report(MatchParticipant... participants) {
        return new MatchReport(ENDED.minusSeconds(360), ENDED, null, "Glacier", List.of(participants));
    }

    private static MatchSettlement rated(String matchId) {
        return MatchSettlement.rated(matchId, "v1", MatchSettlement.hash(matchId), List.of(
                        new StandingMutation("winner", 16, true),
                        new StandingMutation("loser", -16, false)))
                .withEndedAt(ENDED)
                .withReport(report(
                        MatchParticipant.counted("winner", "Winner", 1, 1, true, 1000, 16, "winner"),
                        MatchParticipant.counted("loser", "Loser", 2, 2, false, 1000, -16, "defeated")));
    }

    @Test
    @DisplayName("a settled match goes into the history once, with the ratings the standings were left at")
    void settle_recordsMatch() {
        store.put(new LadderStanding("duel", Ladder.FIRST_SEASON, "loser", 110, 110, 3, 0, Map.of()));

        ladder.settle(rated("m1"));

        MatchRecord match = ladder.match("m1").orElseThrow();
        assertThat(match.rated()).isTrue();
        assertThat(match.season()).isEqualTo(Ladder.FIRST_SEASON);
        assertThat(match.algorithm()).isEqualTo("v1");
        assertThat(match.map()).isEqualTo("Glacier");
        assertThat(match.participant("winner").orElseThrow().ratingAfter()).isEqualTo(1016);
        // The store keeps a rating above the minimum: what is shown is what was stored.
        assertThat(match.participant("loser").orElseThrow().ratingAfter()).isEqualTo(POLICY.minimumRating());
        assertThat(ladder.matchCount("winner")).isEqualTo(1);
        assertThat(ladder.matches("loser", 10, null).matches()).hasSize(1);
        assertThat(ladder.historyStart()).contains(ENDED);
    }

    @Test
    @DisplayName("a match settled again does not write the history again")
    void duplicate_doesNotRecord() {
        ladder.settle(rated("m1"));
        MatchRecord first = ladder.match("m1").orElseThrow();
        matches.reassignPlayer(null, "winner", "renamed");

        SettlementResult again = ladder.settle(rated("m1"));

        assertThat(again.claimed()).isFalse();
        assertThat(matches.all()).hasSize(1);
        assertThat(ladder.match("m1").orElseThrow()).isNotEqualTo(first);
    }

    @Test
    @DisplayName("an unrated match is kept with its reason and changes no rating")
    void unrated_isRecorded() {
        ladder.settle(MatchSettlement.unrated("m2", "v1", MatchSettlement.hash("m2"), "not_enough_players")
                .withEndedAt(ENDED)
                .withReport(report(MatchParticipant.uncounted("solo", "Solo", 1, 1, true, 1000, "match_unrated"))));

        MatchRecord match = ladder.match("m2").orElseThrow();
        assertThat(match.rated()).isFalse();
        assertThat(match.skipReason()).isEqualTo("not_enough_players");
        assertThat(match.participant("solo").orElseThrow().ratingAfter()).isEqualTo(1000);
        assertThat(ladder.count()).isZero();
    }

    @Test
    @DisplayName("a settlement without a report keeps nothing in the history")
    void withoutReport_recordsNothing() {
        ladder.settle(MatchSettlement.rated("m3", "v1", MatchSettlement.hash("m3"), List.of(
                new StandingMutation("a", 16, true), new StandingMutation("b", -16, false))));

        assertThat(matches.all()).isEmpty();
        assertThat(ladder.rating("a")).isEqualTo(1016);
    }

    @Test
    @DisplayName("a history that fails to write does not undo or block the rating")
    void historyFailure_doesNotBlockSettlement() {
        MatchStore broken = spy(new InMemoryMatchStore());
        doThrow(new IllegalStateException("history is down")).when(broken).record(any());
        Ladder withBrokenHistory = ladder(broken);

        SettlementResult result = withBrokenHistory.settle(rated("m4"));

        assertThat(result.applied()).isTrue();
        assertThat(withBrokenHistory.rating("winner")).isEqualTo(1016);
        // The ledger completed the match, so a retry is a duplicate rather than a second settlement.
        assertThat(withBrokenHistory.settle(rated("m4")).claimed()).isFalse();
        assertThat(withBrokenHistory.standing("winner").matches()).isEqualTo(1);
    }

    @Test
    @DisplayName("merging accounts moves the matches along with the standings")
    void mergePlayer_movesMatches() {
        LadderService service = new LadderService(store, ledger, LadderSeasons.single(), matches);
        Ladder duel = service.register(new LadderDefinition("duel", "duel-name", POLICY));
        duel.settle(rated("m5"));

        service.mergePlayer(null, "winner", "main");

        assertThat(duel.matchCount("winner")).isZero();
        assertThat(duel.matchCount("main")).isEqualTo(1);
    }
}
