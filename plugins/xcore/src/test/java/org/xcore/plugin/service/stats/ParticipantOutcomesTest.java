package org.xcore.plugin.service.stats;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.xcore.plugin.service.stats.ParticipantOutcomes.*;

class ParticipantOutcomesTest {
    static Document game(long end) {
        return new Document("_id", new ObjectId()).append("match_id", UUID.randomUUID().toString())
                .append("started_at", 1_000L).append("ended_at", end).append("winning_team", "blue")
                .append("stats_category", "PVP").append("counted_in_stats", true);
    }
    static Document row(long join, long leave, boolean atEnd, String team) {
        return new Document("uuid", "player").append("join_time", join).append("leave_time", leave)
                .append("played_to_end", atEnd).append("final_team", team).append("blocks_built", 5);
    }
    @Test void distinguishesLeftFromShortAndLateParticipation() {
        var game = game(1_001_000);
        assertThat(of(game, List.of(row(1_000, 100_999, false, "blue"))).outcome()).isEqualTo(Outcome.NOT_COUNTED);
        assertThat(of(game, List.of(row(1_000, 101_000, false, "blue"))).outcome()).isEqualTo(Outcome.LEFT);
        assertThat(of(game, List.of(row(751_001, 0, true, "blue"))).outcome()).isEqualTo(Outcome.NOT_COUNTED);
        assertThat(of(game, List.of(row(751_000, 0, true, "blue"))).outcome()).isEqualTo(Outcome.WIN);
        assertThat(of(game, List.of(row(1_000, 0, true, "red"))).outcome()).isEqualTo(Outcome.LOSS);
        assertThat(of(game(1_000), List.of(row(1_000, 0, true, "blue"))).outcome()).isEqualTo(Outcome.NOT_COUNTED);
    }
    @Test void minuteBoundaryAndExcludedPlacement() {
        assertThat(of(game(61_000), List.of(row(1_000, 0, true, "blue"))).outcome()).isEqualTo(Outcome.WIN);
        assertThat(of(game(60_999), List.of(row(1_000, 0, true, "blue"))).outcome()).isEqualTo(Outcome.NOT_COUNTED);
        var hexed = game(1_001_000).append("stats_category", "HEXED");
        var placed = row(1_000, 2_000, false, "blue").append("placement", 2);
        assertThat(of(hexed, List.of(placed)).outcome()).isEqualTo(Outcome.LOSS);
        hexed.put("counted_in_stats", false);
        assertThat(of(hexed, List.of(placed)).outcome()).isEqualTo(Outcome.NOT_COUNTED);
    }
    @Test void reconnectsAndMergedOverlapAreOneParticipation() {
        var game = game(1_001_000);
        var result = of(game, List.of(row(1_000, 501_000, false, "blue"), row(251_000, 0, true, "blue")));
        assertThat(result.presentMs()).isEqualTo(1_000_000);
        assertThat(result.built()).isEqualTo(10);
        assertThat(result.outcome()).isEqualTo(Outcome.WIN);
        assertThat(of(game, List.of(row(1_000, 501_000, false, "blue"), row(251_000, 0, true, "red"))).reason())
                .isEqualTo("conflicting_participation");
    }
    @Test void lastTeamWinsOverEarlierWinnerFlag() {
        var result = of(game(1_001_000), List.of(row(1_000, 501_000, false, "blue").append("isWinner", true),
                row(601_000, 0, true, "red")));
        assertThat(result.outcome()).isEqualTo(Outcome.LOSS);
    }
    @Test void counterFoldAndSharedFixture() throws Exception {
        var totals = PlayerTotals.empty("player");
        var game = game(1_001_000);
        var rows = List.of(row(1_000, 0, true, "blue"));
        PlayerTotals.apply(totals, game, of(game, rows));
        assertThat(number(totals.get("all", Document.class), "games")).isEqualTo(1);
        assertThat(totals.getList("recent", Document.class)).hasSize(1);
        try (var in = getClass().getResourceAsStream("/fixtures/player-totals.v1.json")) {
            var fixture = Document.parse(new String(Objects.requireNonNull(in).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            var view = PlayerTotalsView.overview(fixture);
            assertThat(view.overall().gamesPlayed()).isEqualTo(8);
            assertThat(view.overall().blocksBuilt()).isEqualTo(3_000_000_000L);
            assertThat(view.survival().winRatePercent()).isEqualTo(100);
            assertThat(view.hexed().bestPlacement()).isEqualTo(1);
        }
        assertThat(PlayerTotalsView.overview(null).status()).isEqualTo("ready");
        assertThat(PlayerTotalsView.overview(null).updating()).isFalse();
        assertThat(PlayerTotalsView.overview(new Document("status", "pending")).updating()).isTrue();
    }
}
