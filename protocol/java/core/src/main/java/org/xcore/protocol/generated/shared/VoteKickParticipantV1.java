package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record VoteKickParticipantV1(
        String name,
        Integer pid,
        String discordId
) {
    public VoteKickParticipantV1 {
        Objects.requireNonNull(name, "name must not be null");
        if (name.length() < 1) {
            throw new IllegalArgumentException("name must be at least 1 characters");
        }
        if (pid != null) {
            if (pid < 0) {
                throw new IllegalArgumentException("pid must be >= 0");
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
