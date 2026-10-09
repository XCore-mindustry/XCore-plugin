package org.xcore.plugin.rating.match;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A match as the history keeps it: one per match and ladder, rated or not.
 *
 * <p>A record read for a list of one player's matches holds that player's entry only;
 * {@code players} and {@code teamSizes} still describe the whole match.</p>
 *
 * @param season     the season the match counts towards
 * @param skipReason why an unrated match changed nobody's rating, {@code null} for a rated one
 * @param algorithm  version of the calculator that produced the deltas
 * @param players    number of participants
 * @param teamSizes  participants per team, empty in a mode without teams
 */
public record MatchRecord(
        String ladder,
        int season,
        String matchId,
        Instant startedAt,
        Instant endedAt,
        @Nullable String skipReason,
        @Nullable String finish,
        @Nullable String map,
        String algorithm,
        int players,
        Map<Integer, Integer> teamSizes,
        List<MatchParticipant> participants
) {
    public MatchRecord {
        if (ladder == null || ladder.isBlank()) throw new IllegalArgumentException("ladder must not be blank");
        if (season < 1) throw new IllegalArgumentException("season must be positive");
        if (matchId == null || matchId.isBlank()) throw new IllegalArgumentException("matchId must not be blank");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        algorithm = algorithm == null ? "" : algorithm;
        teamSizes = teamSizes == null ? Map.of() : Map.copyOf(teamSizes);
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }

    /** A whole match as a ladder settles it. */
    public static MatchRecord of(String ladder, int season, String matchId, String algorithm,
                                 @Nullable String skipReason, MatchReport report) {
        Map<Integer, Integer> teams = new TreeMap<>();
        for (MatchParticipant participant : report.participants()) {
            if (participant.team() != null) {
                teams.merge(participant.team(), 1, Integer::sum);
            }
        }
        return new MatchRecord(ladder, season, matchId, report.startedAt(), report.endedAt(), skipReason,
                report.finish(), report.map(), algorithm, report.participants().size(), teams, report.participants());
    }

    /** The key the history stores the match under: a match ID is unique within its ladder only. */
    public String id() {
        return id(ladder, matchId);
    }

    public static String id(String ladder, String matchId) {
        return ladder + ":" + matchId;
    }

    public boolean rated() {
        return skipReason == null;
    }

    public boolean teams() {
        return !teamSizes.isEmpty();
    }

    public Duration duration() {
        return Duration.between(startedAt, endedAt).abs();
    }

    public Optional<MatchParticipant> participant(@Nullable String uuid) {
        if (uuid == null) return Optional.empty();
        return participants.stream().filter(p -> p.uuid().equals(uuid)).findFirst();
    }

    public MatchRecord withParticipants(List<MatchParticipant> replaced) {
        return new MatchRecord(ladder, season, matchId, startedAt, endedAt, skipReason, finish, map, algorithm,
                players, teamSizes, replaced);
    }
}
