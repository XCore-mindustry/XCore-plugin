package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record ActorRefV1(
        String actorName,
        String actorDiscordId,
        ActorRefV1ActorType actorType
) implements ProtocolPayload {
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

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actorName", actorName);
        if (actorDiscordId != null) {
            payload.put("actorDiscordId", actorDiscordId);
        }
        if (actorType != null) {
            payload.put("actorType", actorType.toString());
        }
        return payload;
    }
}
