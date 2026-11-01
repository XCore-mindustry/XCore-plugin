package org.xcore.plugin.integration.gamehistory;

import java.util.List;
import java.util.Objects;

/** XCore-neutral immutable contract for plugins recording completed matches. */
public record MatchHistoryRecord(
        String matchId,
        String mode,
        String rulesVersion,
        long startedAt,
        long endedAt,
        String finishReason,
        String winnerUuid,
        List<MatchParticipantRecord> participants,
        boolean ranked
) {
    public MatchHistoryRecord {
        if (matchId == null || matchId.isBlank()) throw new IllegalArgumentException("matchId must not be blank");
        if (mode == null || mode.isBlank()) throw new IllegalArgumentException("mode must not be blank");
        if (rulesVersion == null || rulesVersion.isBlank()) throw new IllegalArgumentException("rulesVersion must not be blank");
        if (endedAt < startedAt) throw new IllegalArgumentException("endedAt must not precede startedAt");
        if (finishReason == null || finishReason.isBlank()) throw new IllegalArgumentException("finishReason must not be blank");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }

    public record MatchParticipantRecord(
            String uuid,
            String nickname,
            int placement,
            boolean winner,
            boolean playedToEnd
    ) {
        public MatchParticipantRecord {
            if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid must not be blank");
            if (placement < 1) throw new IllegalArgumentException("placement must be positive");
        }
    }
}
