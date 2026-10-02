package org.xcore.plugin.rating.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.rating.RatingPolicy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementEloCalculatorTest {

    private final PlacementEloCalculator calculator = new PlacementEloCalculator();
    private final RatingPolicy policy = RatingPolicy.placementEloV1();

    @Test
    @DisplayName("2-player equal rating match awards +16 to 1st place and -16 to 2nd place")
    void twoPlayers_equalRating() {
        var input = List.of(
                new PlacementEloCalculator.RatedPlayer("p1", 1000, 1),
                new PlacementEloCalculator.RatedPlayer("p2", 1000, 2)
        );

        var result = calculator.calculate(input, policy);

        assertThat(result.algorithmVersion()).isEqualTo(PlacementEloCalculator.ALGORITHM_VERSION);
        assertThat(result.deltas()).hasSize(2);

        var p1Delta = result.deltas().stream().filter(d -> d.uuid().equals("p1")).findFirst().orElseThrow();
        var p2Delta = result.deltas().stream().filter(d -> d.uuid().equals("p2")).findFirst().orElseThrow();

        assertThat(p1Delta.delta()).isEqualTo(16);
        assertThat(p1Delta.newRating()).isEqualTo(1016);

        assertThat(p2Delta.delta()).isEqualTo(-16);
        assertThat(p2Delta.newRating()).isEqualTo(984);
    }

    @Test
    @DisplayName("3-player match produces zero-sum deltas when ratings are equal")
    void threePlayers_zeroSum() {
        var input = List.of(
                new PlacementEloCalculator.RatedPlayer("p1", 1000, 1),
                new PlacementEloCalculator.RatedPlayer("p2", 1000, 2),
                new PlacementEloCalculator.RatedPlayer("p3", 1000, 3)
        );

        var result = calculator.calculate(input, policy);

        int totalDelta = result.deltas().stream().mapToInt(PlacementEloCalculator.PlayerRatingDelta::delta).sum();
        assertThat(totalDelta).isEqualTo(0);

        var p1 = result.deltas().stream().filter(d -> d.uuid().equals("p1")).findFirst().orElseThrow();
        var p2 = result.deltas().stream().filter(d -> d.uuid().equals("p2")).findFirst().orElseThrow();
        var p3 = result.deltas().stream().filter(d -> d.uuid().equals("p3")).findFirst().orElseThrow();

        assertThat(p1.delta()).isEqualTo(16);
        assertThat(p2.delta()).isEqualTo(0);
        assertThat(p3.delta()).isEqualTo(-16);
    }

    @Test
    @DisplayName("rating cannot drop below policy minimumRating")
    void minimumRating_clamped() {
        var input = List.of(
                new PlacementEloCalculator.RatedPlayer("p1", 1000, 1),
                new PlacementEloCalculator.RatedPlayer("p2", 105, 2)
        );

        var result = calculator.calculate(input, policy);
        var p2 = result.deltas().stream().filter(d -> d.uuid().equals("p2")).findFirst().orElseThrow();

        assertThat(p2.newRating()).isGreaterThanOrEqualTo(policy.minimumRating());
    }

    @Test
    @DisplayName("throws on non-contiguous placements or duplicate UUIDs")
    void validationErrors() {
        assertThatThrownBy(() -> calculator.calculate(List.of(
                new PlacementEloCalculator.RatedPlayer("p1", 1000, 1),
                new PlacementEloCalculator.RatedPlayer("p2", 1000, 3)
        ), policy)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> calculator.calculate(List.of(
                new PlacementEloCalculator.RatedPlayer("p1", 1000, 1),
                new PlacementEloCalculator.RatedPlayer("p1", 1000, 2)
        ), policy)).isInstanceOf(IllegalArgumentException.class);
    }
}
