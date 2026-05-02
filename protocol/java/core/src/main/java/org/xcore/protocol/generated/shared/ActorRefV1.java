package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record ActorRefV1(
        String actorName,
        String actorDiscordId,
        ActorRefV1ActorType actorType
) {
    public ActorRefV1 {
        Objects.requireNonNull(actorName, "actorName must not be null");
        if (actorName.length() < 1) {
            throw new IllegalArgumentException("actorName must be at least 1 characters");
        }
        if (actorDiscordId != null) {
            Objects.requireNonNull(actorDiscordId, "actorDiscordId must not be null");
            if (actorDiscordId.length() < 1) {
                throw new IllegalArgumentException("actorDiscordId must be at least 1 characters");
            }
        }
        if (actorType != null) {
            Objects.requireNonNull(actorType, "actorType must not be null");
        }
    }
}
