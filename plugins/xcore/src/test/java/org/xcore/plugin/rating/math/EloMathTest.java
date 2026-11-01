package org.xcore.plugin.rating.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EloMathTest {

    @Test
    @DisplayName("expectedScore returns 0.5 for equal ratings")
    void expectedScore_equalRatings() {
        assertThat(EloMath.expectedScore(1000, 1000)).isCloseTo(0.5, within(0.0001));
        assertThat(EloMath.expectedScore(2500, 2500)).isCloseTo(0.5, within(0.0001));
    }

    @Test
    @DisplayName("expectedScore is symmetric: E(A, B) + E(B, A) == 1.0")
    void expectedScore_symmetry() {
        double eA = EloMath.expectedScore(1200, 1000);
        double eB = EloMath.expectedScore(1000, 1200);
        assertThat(eA + eB).isCloseTo(1.0, within(0.0000001));
    }

    @Test
    @DisplayName("expectedScore follows standard 400 Elo point difference (approx 0.909)")
    void expectedScore_fourHundredDifference() {
        double expected = EloMath.expectedScore(1400, 1000);
        assertThat(expected).isCloseTo(10.0 / 11.0, within(0.001));
    }

    @Test
    @DisplayName("delta computes correct rounded integer deltas")
    void delta_computesRounded() {
        assertThat(EloMath.delta(32, 1.0, 0.5)).isEqualTo(16);
        assertThat(EloMath.delta(32, 0.0, 0.5)).isEqualTo(-16);
        assertThat(EloMath.delta(32, 0.5, 0.5)).isEqualTo(0);
    }

    @Test
    @DisplayName("effectiveTeamRating scales logarithmically with team size")
    void effectiveTeamRating_scalesWithTeamSize() {
        assertThat(EloMath.effectiveTeamRating(1000, 1)).isCloseTo(1000.0, within(0.0001));
        // 400 * log10(2) approx 120.412
        assertThat(EloMath.effectiveTeamRating(1000, 2)).isCloseTo(1120.412, within(0.01));
        // 400 * log10(4) approx 240.824
        assertThat(EloMath.effectiveTeamRating(1000, 4)).isCloseTo(1240.824, within(0.01));
        assertThat(EloMath.effectiveTeamRating(1000, 0)).isEqualTo(1000.0);
    }
}
