package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record VoteKickParticipantV1(
        String playerName,
        Integer playerPid,
        String discordId
) {
    public VoteKickParticipantV1 {
        Objects.requireNonNull(playerName, "playerName must not be null");
        if (playerName.length() < 1) {
            throw new IllegalArgumentException("playerName must be at least 1 characters");
        }
        if (playerPid != null) {
            if (playerPid < 0) {
                throw new IllegalArgumentException("playerPid must be >= 0");
            }
        }
        if (discordId != null) {
            Objects.requireNonNull(discordId, "discordId must not be null");
            if (discordId.length() < 1) {
                throw new IllegalArgumentException("discordId must be at least 1 characters");
            }
        }
    }
}
