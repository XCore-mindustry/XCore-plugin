package org.xcore.plugin.rating.match;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a mode tells the match history about a finished match, alongside the settlement that
 * changes the ratings. The ladder adds what it alone knows (the season, the ratings that were
 * stored) and records it.
 *
 * @param finish how the match ended, such as {@code NATURAL} or {@code ADMIN_STOP}; {@code null} for an ordinary end
 * @param map    the map played, {@code null} in a mode that generates its own
 */
public record MatchReport(
        Instant startedAt,
        Instant endedAt,
        @Nullable String finish,
        @Nullable String map,
        List<MatchParticipant> participants
) {
    public MatchReport {
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        if (participants.stream().map(MatchParticipant::uuid).distinct().count() != participants.size()) {
            throw new IllegalArgumentException("Duplicate player UUID");
        }
    }
}
