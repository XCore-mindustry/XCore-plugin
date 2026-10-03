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

class LadderServiceTest {
    private InMemoryLadderStore store;
    private LadderService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryLadderStore();
        service = new LadderService(store, new InMemoryIdempotencyLedger());
    }

    @Test
    @DisplayName("registering the same definition twice returns the same ladder")
    void register_isIdempotent() {
        LadderDefinition definition = new LadderDefinition("duel", "duel-name", RatingPolicy.teamEloV1());

        Ladder first = service.register(definition);
        Ladder second = service.register(new LadderDefinition("duel", "duel-name", RatingPolicy.teamEloV1()));

        assertThat(second).isSameAs(first);
        assertThat(service.find("duel")).containsSame(first);
        assertThat(service.find("missing")).isEmpty();
        assertThat(service.find(null)).isEmpty();
        assertThat(service.all()).containsExactly(first);
    }

    @Test
    @DisplayName("an ID cannot be reused for a different definition")
    void register_rejectsConflictingDefinition() {
        service.register(new LadderDefinition("duel", "duel-name", RatingPolicy.teamEloV1()));

        assertThatThrownBy(() -> service.register(
                new LadderDefinition("duel", "duel-name", RatingPolicy.placementEloV1())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ladder IDs are restricted to a storage-safe alphabet")
    void definition_validatesId() {
        assertThatThrownBy(() -> new LadderDefinition("Mini PvP", "name", RatingPolicy.teamEloV1()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LadderDefinition("", "name", RatingPolicy.teamEloV1()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ladders keep separate standings for the same player")
    void ladders_areIsolated() {
        Ladder duel = service.register(new LadderDefinition("duel", "duel-name", RatingPolicy.teamEloV1()));
        Ladder ffa = service.register(new LadderDefinition("ffa", "ffa-name", RatingPolicy.placementEloV1()));

        duel.settle(MatchSettlement.rated("m1", "v1", MatchSettlement.hash("m1"),
                List.of(new StandingMutation("a", 20, true))));

        assertThat(duel.rating("a")).isEqualTo(1020);
        assertThat(ffa.rating("a")).isEqualTo(1000);
        assertThat(ffa.count()).isZero();
    }

    @Test
    @DisplayName("mergePlayer folds a player's standings into another across ladders")
    void mergePlayer_combinesStandings() {
        Ladder duel = service.register(new LadderDefinition("duel", "duel-name", RatingPolicy.teamEloV1()));
        Ladder ffa = service.register(new LadderDefinition("ffa", "ffa-name", RatingPolicy.placementEloV1()));
        store.put(new LadderStanding("duel", 1, "old", 1300, 1400, 10, 6, Map.of("top3", 2)));
        store.put(new LadderStanding("duel", 1, "new", 1100, 1150, 4, 1, Map.of("top3", 1, "disconnects", 3)));
        store.put(new LadderStanding("ffa", 1, "old", 900, 1000, 5, 0, Map.of()));
        assertThat(duel.rating("new")).isEqualTo(1100); // cached before the merge

        int merged = service.mergePlayer(null, "old", "new");
        service.reloadCaches();

        assertThat(merged).isEqualTo(2);
        LadderStanding combined = duel.standing("new");
        assertThat(combined.rating()).isEqualTo(1300);
        assertThat(combined.peakRating()).isEqualTo(1400);
        assertThat(combined.matches()).isEqualTo(14);
        assertThat(combined.wins()).isEqualTo(7);
        assertThat(combined.stats()).containsEntry("top3", 3).containsEntry("disconnects", 3);
        // No standing of the target's own on this ladder: the source's is simply re-keyed.
        assertThat(ffa.fetchStanding("new").rating()).isEqualTo(900);
        assertThat(ffa.fetchStanding("new").matches()).isEqualTo(5);
        assertThat(store.find("duel", 1, "old")).isEmpty();
        assertThat(store.find("ffa", 1, "old")).isEmpty();
    }

    @Test
    @DisplayName("a match already counted for the old account is not counted again after a merge")
    void mergePlayer_keepsAppliedOperations() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("old", 10, true), 1000, 100);
        service.mergePlayer(null, "old", "new");

        LadderStore.ApplyResult replay = store.applyOnce("duel", 1, "op-1",
                new StandingMutation("new", 10, true), 1000, 100);

        assertThat(replay.applied()).isFalse();
        assertThat(replay.standing().rating()).isEqualTo(1010);
    }
}
