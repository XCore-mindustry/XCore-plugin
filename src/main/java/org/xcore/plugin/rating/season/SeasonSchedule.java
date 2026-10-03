package org.xcore.plugin.rating.season;

import org.xcore.plugin.config.TomlSecretsConfig;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The rules every season of every ladder follows unless an administrator moves its end.
 *
 * @param length           default season length; may be in calendar months
 * @param zone             time zone deadlines are aligned to and shown in
 * @param notices          "ends soon" announcement points, earliest first
 * @param settlementGrace  how long a finished season waits for matches still settling
 * @param podiumSize       how many players a finished season's podium records
 * @param podiumMinMatches matches a player needs in a season to be eligible for its podium
 */
public record SeasonSchedule(
        TemporalAmount length,
        ZoneId zone,
        List<SeasonNotice> notices,
        Duration settlementGrace,
        int podiumSize,
        int podiumMinMatches,
        SeasonResetPolicy reset
) {
    public SeasonSchedule {
        notices = notices.stream().sorted(Comparator.comparing(SeasonNotice::lead).reversed()).toList();
        if (podiumSize < 1) throw new IllegalArgumentException("podiumSize must be positive");
        if (podiumMinMatches < 0) throw new IllegalArgumentException("podiumMinMatches must not be negative");
    }

    /** @throws IllegalArgumentException naming the offending {@code [rating.seasons]} key */
    public static SeasonSchedule from(TomlSecretsConfig.SeasonsConfig config) {
        ZoneId zone;
        try {
            zone = ZoneId.of(config.timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("rating.seasons.timezone: unknown time zone '" + config.timezone + "'");
        }
        return new SeasonSchedule(
                key("length", () -> TimeSpans.calendar(config.length)),
                zone,
                key("notice_thresholds", () -> config.noticeThresholds.stream()
                        .map(text -> text.strip().toLowerCase())
                        .distinct()
                        .map(text -> new SeasonNotice(text, TimeSpans.duration(text)))
                        .toList()),
                key("settlement_grace", () -> TimeSpans.duration(config.settlementGrace)),
                config.podiumSize,
                config.podiumMinMatches,
                key("reset", () -> SeasonResetPolicy.of(config.reset, config.resetCarry)));
    }

    private static <T> T key(String name, java.util.function.Supplier<T> value) {
        try {
            return value.get();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("rating.seasons." + name + ": " + e.getMessage());
        }
    }

    /**
     * The default deadline of a season that starts at {@code startsAt}: one season length
     * later, on the stroke of midnight in the schedule's time zone.
     */
    public Instant endOf(Instant startsAt) {
        ZonedDateTime end = startsAt.atZone(zone).plus(length);
        Instant aligned = end.truncatedTo(ChronoUnit.DAYS).toInstant();
        // Seasons shorter than a day have no midnight to align to.
        return aligned.isAfter(startsAt) ? aligned : end.toInstant();
    }

    /** Keys of the notices that are due once only {@code endsAt - now} is left. */
    public Set<String> dueNotices(Instant now, Instant endsAt) {
        Set<String> due = new LinkedHashSet<>();
        for (SeasonNotice notice : notices) {
            if (notice.dueAt(now, endsAt)) {
                due.add(notice.key());
            }
        }
        return due;
    }

    public Optional<SeasonNotice> notice(String key) {
        return notices.stream().filter(notice -> notice.key().equals(key)).findFirst();
    }

    /** The earliest notice; a season with less than this left is "ending soon". */
    public Optional<SeasonNotice> firstNotice() {
        return notices.stream().findFirst();
    }
}
