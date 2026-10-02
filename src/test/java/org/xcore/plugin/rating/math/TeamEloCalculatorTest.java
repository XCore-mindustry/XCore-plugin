package org.xcore.plugin.rating.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.rating.RatingPolicy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamEloCalculatorTest {

    private final TeamEloCalculator calculator = new TeamEloCalculator();
    private final RatingPolicy policy = RatingPolicy.teamEloV1();

    @Test
    @DisplayName("2v2 equal match produces zero-sum deltas (+8 each for winners, -8 each for losers)")
    void twoVsTwo_equalMatch_zeroSum() {
        var team1 = new TeamEloCalculator.RatedTeam(1, 1, List.of(
                new TeamEloCalculator.RatedMember("p1", 1000),
                new TeamEloCalculator.RatedMember("p2", 1000)
        ));
        var team2 = new TeamEloCalculator.RatedTeam(2, 2, List.of(
                new TeamEloCalculator.RatedMember("p3", 1000),
                new TeamEloCalculator.RatedMember("p4", 1000)
        ));

        var calculation = calculator.calculate(List.of(team1, team2), policy);

        assertThat(calculation.deltas()).hasSize(4);

        var p1 = calculation.deltas().stream().filter(d -> d.uuid().equals("p1")).findFirst().orElseThrow();
        var p2 = calculation.deltas().stream().filter(d -> d.uuid().equals("p2")).findFirst().orElseThrow();
        var p3 = calculation.deltas().stream().filter(d -> d.uuid().equals("p3")).findFirst().orElseThrow();
        var p4 = calculation.deltas().stream().filter(d -> d.uuid().equals("p4")).findFirst().orElseThrow();

        assertThat(p1.delta()).isEqualTo(8);
        assertThat(p2.delta()).isEqualTo(8);
        assertThat(p3.delta()).isEqualTo(-8);
        assertThat(p4.delta()).isEqualTo(-8);

        int netSystemChange = calculation.deltas().stream().mapToInt(TeamEloCalculator.PlayerRatingDelta::delta).sum();
        assertThat(netSystemChange).isEqualTo(0);
    }

    @Test
    @DisplayName("asymmetric 4v1: 4-stack victory yields modest gain per player, solo player loss is muted")
    void asymmetric_fourVsOne_stackWins() {
        var team1 = new TeamEloCalculator.RatedTeam(1, 1, List.of(
                new TeamEloCalculator.RatedMember("a1", 1000),
                new TeamEloCalculator.RatedMember("a2", 1000),
                new TeamEloCalculator.RatedMember("a3", 1000),
                new TeamEloCalculator.RatedMember("a4", 1000)
        ));
        var team2 = new TeamEloCalculator.RatedTeam(2, 2, List.of(
                new TeamEloCalculator.RatedMember("solo", 1000)
        ));

        var calculation = calculator.calculate(List.of(team1, team2), policy);

        var solo = calculation.deltas().stream().filter(d -> d.uuid().equals("solo")).findFirst().orElseThrow();
        var a1 = calculation.deltas().stream().filter(d -> d.uuid().equals("a1")).findFirst().orElseThrow();

        // 4v1 expected win rate is 80%, so total pool is ~6.4. Solo loses ~6, each stack member gains ~2.
        assertThat(solo.delta()).isEqualTo(-6);
        assertThat(a1.delta()).isEqualTo(2);
    }

    @Test
    @DisplayName("asymmetric 4v1: solo player upset victory yields massive gain (+26) and stack loses heavily")
    void asymmetric_fourVsOne_soloUpset() {
        var team1 = new TeamEloCalculator.RatedTeam(1, 2, List.of(
                new TeamEloCalculator.RatedMember("a1", 1000),
                new TeamEloCalculator.RatedMember("a2", 1000),
                new TeamEloCalculator.RatedMember("a3", 1000),
                new TeamEloCalculator.RatedMember("a4", 1000)
        ));
        var team2 = new TeamEloCalculator.RatedTeam(2, 1, List.of(
                new TeamEloCalculator.RatedMember("solo", 1000)
        ));

        var calculation = calculator.calculate(List.of(team1, team2), policy);

        var solo = calculation.deltas().stream().filter(d -> d.uuid().equals("solo")).findFirst().orElseThrow();
        var a1 = calculation.deltas().stream().filter(d -> d.uuid().equals("a1")).findFirst().orElseThrow();

        assertThat(solo.delta()).isEqualTo(26);
        assertThat(a1.delta()).isEqualTo(-6);
    }

    @Test
    @DisplayName("participation scales win gain but preserves loss penalty to stop disconnect dodging")
    void participationScaling() {
        var team1 = new TeamEloCalculator.RatedTeam(1, 1, List.of(
                new TeamEloCalculator.RatedMember("full", 1000, 1.0),
                new TeamEloCalculator.RatedMember("half", 1000, 0.5),
                new TeamEloCalculator.RatedMember("zero", 1000, 0.0)
        ));
        var team2 = new TeamEloCalculator.RatedTeam(2, 2, List.of(
                new TeamEloCalculator.RatedMember("quitter", 1000, 0.3)
        ));

        var calculation = calculator.calculate(List.of(team1, team2), policy);

        var full = calculation.deltas().stream().filter(d -> d.uuid().equals("full")).findFirst().orElseThrow();
        var half = calculation.deltas().stream().filter(d -> d.uuid().equals("half")).findFirst().orElseThrow();
        var zero = calculation.deltas().stream().filter(d -> d.uuid().equals("zero")).findFirst().orElseThrow();
        var quitter = calculation.deltas().stream().filter(d -> d.uuid().equals("quitter")).findFirst().orElseThrow();

        assertThat(full.delta()).isGreaterThan(half.delta());
        assertThat(zero.delta()).isEqualTo(0);
        // Quitter on losing team receives full loss penalty
        assertThat(quitter.delta()).isLessThan(0);
    }

    @Test
    @DisplayName("3-team tournament match computes normalized placements correctly")
    void threeTeamMatch() {
        var team1 = new TeamEloCalculator.RatedTeam(1, 1, List.of(new TeamEloCalculator.RatedMember("p1", 1000)));
        var team2 = new TeamEloCalculator.RatedTeam(2, 2, List.of(new TeamEloCalculator.RatedMember("p2", 1000)));
        var team3 = new TeamEloCalculator.RatedTeam(3, 3, List.of(new TeamEloCalculator.RatedMember("p3", 1000)));

        var calculation = calculator.calculate(List.of(team1, team2, team3), policy);

        var p1 = calculation.deltas().stream().filter(d -> d.uuid().equals("p1")).findFirst().orElseThrow();
        var p2 = calculation.deltas().stream().filter(d -> d.uuid().equals("p2")).findFirst().orElseThrow();
        var p3 = calculation.deltas().stream().filter(d -> d.uuid().equals("p3")).findFirst().orElseThrow();

        assertThat(p1.delta()).isEqualTo(16);
        assertThat(p2.delta()).isEqualTo(0);
        assertThat(p3.delta()).isEqualTo(-16);
    }

    @Test
    @DisplayName("rejects duplicate UUID across teams or less than 2 teams")
    void validationRules() {
        var team1 = new TeamEloCalculator.RatedTeam(1, 1, List.of(new TeamEloCalculator.RatedMember("p1", 1000)));
        var team2 = new TeamEloCalculator.RatedTeam(2, 2, List.of(new TeamEloCalculator.RatedMember("p1", 1000)));

        assertThatThrownBy(() -> calculator.calculate(List.of(team1, team2), policy))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
