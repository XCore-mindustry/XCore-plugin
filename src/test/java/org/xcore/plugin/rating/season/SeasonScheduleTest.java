package org.xcore.plugin.rating.season;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.rating.RatingPolicy;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonScheduleTest {
    private static final RatingPolicy POLICY = new RatingPolicy(1000, 100, 32, 2);

    @Test
    @DisplayName("the default configuration is three calendar months with a soft reset")
    void defaults() {
        SeasonSchedule schedule = SeasonSchedule.from(new TomlSecretsConfig().rating.seasons);

        assertThat(schedule.length()).isEqualTo(Period.ofMonths(3));
        assertThat(schedule.zone()).isEqualTo(ZoneId.of("UTC"));
        assertThat(schedule.notices()).extracting(SeasonNotice::key).containsExactly("7d", "3d", "24h", "1h");
        assertThat(schedule.settlementGrace()).isEqualTo(Duration.ofMinutes(5));
        assertThat(schedule.podiumSize()).isEqualTo(10);
        assertThat(schedule.podiumMinMatches()).isEqualTo(10);
        assertThat(schedule.reset()).isEqualTo(new SeasonResetPolicy(SeasonResetPolicy.Mode.SOFT, 0.5));
    }

    @Test
    @DisplayName("a season ends one length later, at midnight in the schedule's time zone")
    void endOf_alignsToMidnight() {
        var config = new TomlSecretsConfig.SeasonsConfig();
        config.timezone = "Europe/Kyiv";
        SeasonSchedule schedule = SeasonSchedule.from(config);

        // 17:42 UTC on 3 October is 20:42 in Kyiv (UTC+3); 3 January is in winter time (UTC+2).
        assertThat(schedule.endOf(Instant.parse("2026-10-03T17:42:13Z")))
                .isEqualTo(Instant.parse("2027-01-02T22:00:00Z"));
    }

    @Test
    @DisplayName("a season shorter than a day keeps its exact length")
    void endOf_shortSeason() {
        var config = new TomlSecretsConfig.SeasonsConfig();
        config.length = "6h";

        assertThat(SeasonSchedule.from(config).endOf(Instant.parse("2026-10-03T17:42:13Z")))
                .isEqualTo(Instant.parse("2026-10-03T23:42:13Z"));
    }

    @Test
    @DisplayName("notices are ordered earliest first whatever order they were configured in")
    void notices_sortedAndDue() {
        var config = new TomlSecretsConfig.SeasonsConfig();
        config.noticeThresholds = List.of("1h", "7D", " 24h ", "1h");
        SeasonSchedule schedule = SeasonSchedule.from(config);
        Instant end = Instant.parse("2027-01-01T00:00:00Z");

        assertThat(schedule.notices()).extracting(SeasonNotice::key).containsExactly("7d", "24h", "1h");
        assertThat(schedule.firstNotice()).map(SeasonNotice::lead).contains(Duration.ofDays(7));
        assertThat(schedule.dueNotices(end.minus(Duration.ofDays(8)), end)).isEmpty();
        assertThat(schedule.dueNotices(end.minus(Duration.ofDays(7)), end)).containsExactly("7d");
        assertThat(schedule.dueNotices(end.minus(Duration.ofMinutes(30)), end)).containsExactly("7d", "24h", "1h");
    }

    @Test
    @DisplayName("a bad value names the configuration key it came from")
    void invalidConfig_namesKey() {
        var badLength = new TomlSecretsConfig.SeasonsConfig();
        badLength.length = "soon";
        assertThatThrownBy(() -> SeasonSchedule.from(badLength))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rating.seasons.length");

        var badNotice = new TomlSecretsConfig.SeasonsConfig();
        badNotice.noticeThresholds = List.of("7d", "1mo");
        assertThatThrownBy(() -> SeasonSchedule.from(badNotice))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rating.seasons.notice_thresholds");

        var badZone = new TomlSecretsConfig.SeasonsConfig();
        badZone.timezone = "Mars/Olympus";
        assertThatThrownBy(() -> SeasonSchedule.from(badZone))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rating.seasons.timezone");

        var badReset = new TomlSecretsConfig.SeasonsConfig();
        badReset.reset = "gentle";
        assertThatThrownBy(() -> SeasonSchedule.from(badReset))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rating.seasons.reset");
    }

    @Test
    @DisplayName("time spans accept every unit and reject nonsense")
    void timeSpans() {
        assertThat(TimeSpans.duration("90s")).isEqualTo(Duration.ofSeconds(90));
        assertThat(TimeSpans.duration("5m")).isEqualTo(Duration.ofMinutes(5));
        assertThat(TimeSpans.duration("2w")).isEqualTo(Duration.ofDays(14));
        assertThat(TimeSpans.calendar("3mo")).isEqualTo(Period.ofMonths(3));
        assertThat(TimeSpans.calendar("1y")).isEqualTo(Period.ofYears(1));
        assertThat(TimeSpans.calendar("10d")).isEqualTo(Duration.ofDays(10));

        for (String bad : new String[]{"", "0d", "-3d", "3", "d", "3 days", "1d12h"}) {
            assertThatThrownBy(() -> TimeSpans.calendar(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> TimeSpans.duration("1mo")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("soft reset halves the distance to the default rating; hard and none do what they say")
    void resetPolicies() {
        assertThat(new SeasonResetPolicy(SeasonResetPolicy.Mode.SOFT, 0.5).seed(1400, POLICY)).isEqualTo(1200);
        assertThat(new SeasonResetPolicy(SeasonResetPolicy.Mode.SOFT, 0.5).seed(700, POLICY)).isEqualTo(850);
        assertThat(new SeasonResetPolicy(SeasonResetPolicy.Mode.SOFT, 0.5).seed(1001, POLICY)).isEqualTo(1001);
        assertThat(new SeasonResetPolicy(SeasonResetPolicy.Mode.SOFT, 0.25).seed(1400, POLICY)).isEqualTo(1100);
        assertThat(new SeasonResetPolicy(SeasonResetPolicy.Mode.HARD, 0.5).seed(1400, POLICY)).isEqualTo(1000);
        assertThat(new SeasonResetPolicy(SeasonResetPolicy.Mode.NONE, 0.5).seed(1400, POLICY)).isEqualTo(1400);

        RatingPolicy highFloor = new RatingPolicy(1000, 900, 32, 2);
        assertThat(new SeasonResetPolicy(SeasonResetPolicy.Mode.NONE, 0.5).seed(100, highFloor)).isEqualTo(900);

        assertThatThrownBy(() -> SeasonResetPolicy.of("soft", 1.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("command dates are read in the seasons time zone")
    void commandParser() {
        ZoneId kyiv = ZoneId.of("Europe/Kyiv");

        assertThat(SeasonCommandParser.dateTime("2027-01-31", kyiv)).isEqualTo(Instant.parse("2027-01-30T22:00:00Z"));
        assertThat(SeasonCommandParser.dateTime("2027-01-31T18:30", kyiv)).isEqualTo(Instant.parse("2027-01-31T16:30:00Z"));
        assertThat(SeasonCommandParser.dateTime("2027-01-31 18:30", kyiv)).isEqualTo(Instant.parse("2027-01-31T16:30:00Z"));
        assertThatThrownBy(() -> SeasonCommandParser.dateTime("tomorrow", kyiv))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("yyyy-MM-dd");

        assertThat(SeasonCommandParser.extend(Instant.parse("2027-01-31T00:00:00Z"), "1mo", ZoneId.of("UTC")))
                .isEqualTo(Instant.parse("2027-02-28T00:00:00Z"));
        assertThat(SeasonCommandParser.extend(Instant.parse("2027-01-31T00:00:00Z"), "36h", ZoneId.of("UTC")))
                .isEqualTo(Instant.parse("2027-02-01T12:00:00Z"));
    }
}
