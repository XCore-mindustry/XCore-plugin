package org.xcore.plugin.rating.season;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One season of one ladder. Immutable: every change produces a copy that is written back
 * through {@link SeasonStore#update}, which rejects it when the stored season has moved on.
 *
 * @param name        optional custom title; blank means the generic "Season N"
 * @param endsAt      deadline, movable while the season is {@link SeasonStatus#ACTIVE}
 * @param sentNotices keys of the {@link SeasonNotice}s already announced network-wide
 * @param podium      best players, filled in when the season is archived
 * @param summary     totals, present once the season is archived
 * @param matches     rated matches settled into the season so far
 * @param revision    bumped by every stored change
 */
public record Season(
        String ladderId,
        int number,
        String name,
        Instant startsAt,
        Instant endsAt,
        SeasonStatus status,
        Set<String> sentNotices,
        List<SeasonPodiumEntry> podium,
        @Nullable SeasonSummary summary,
        List<SeasonReschedule> rescheduled,
        int matches,
        long revision
) {
    public Season {
        if (ladderId == null || ladderId.isBlank()) throw new IllegalArgumentException("ladderId must not be blank");
        if (number < 1) throw new IllegalArgumentException("number must be positive");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(status, "status");
        name = name == null ? "" : name;
        sentNotices = sentNotices == null ? Set.of() : Set.copyOf(sentNotices);
        podium = podium == null ? List.of() : List.copyOf(podium);
        rescheduled = rescheduled == null ? List.of() : List.copyOf(rescheduled);
    }

    /** A new active season with nothing recorded yet. */
    public static Season opening(String ladderId, int number, Instant startsAt, Instant endsAt) {
        return new Season(ladderId, number, "", startsAt, endsAt, SeasonStatus.ACTIVE,
                Set.of(), List.of(), null, List.of(), 0, 0);
    }

    public static String id(String ladderId, int number) {
        return ladderId + ":" + number;
    }

    public String id() {
        return id(ladderId, number);
    }

    public boolean active() {
        return status == SeasonStatus.ACTIVE;
    }

    /** Whether a match that ended at {@code when} falls inside this season. */
    public boolean covers(Instant when) {
        return !when.isBefore(startsAt) && when.isBefore(endsAt);
    }

    /** Time left until the deadline, zero once it has passed. */
    public Duration remaining(Instant now) {
        Duration left = Duration.between(now, endsAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public Season close() {
        return withStatus(SeasonStatus.CLOSING, podium, summary);
    }

    public Season archive(List<SeasonPodiumEntry> podium, SeasonSummary summary) {
        return withStatus(SeasonStatus.ARCHIVED, podium, Objects.requireNonNull(summary, "summary"));
    }

    public Season withSentNotices(Set<String> sentNotices) {
        return new Season(ladderId, number, name, startsAt, endsAt, status, sentNotices, podium, summary,
                rescheduled, matches, revision);
    }

    /** Moves the deadline, keeping only the notices that are still due under the new one. */
    public Season reschedule(SeasonReschedule change, Set<String> stillSentNotices) {
        List<SeasonReschedule> history = new ArrayList<>(rescheduled);
        history.add(change);
        return new Season(ladderId, number, name, startsAt, change.to(), status, stillSentNotices, podium, summary,
                history, matches, revision);
    }

    private Season withStatus(SeasonStatus status, List<SeasonPodiumEntry> podium, @Nullable SeasonSummary summary) {
        return new Season(ladderId, number, name, startsAt, endsAt, status, sentNotices, podium, summary,
                rescheduled, matches, revision);
    }
}
