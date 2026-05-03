package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record VoteKickParticipantV1(
        String playerName,
        Integer playerPid,
        String discordId
) implements ProtocolPayload {
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

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerName", playerName);
        if (playerPid != null) {
            payload.put("playerPid", playerPid);
        }
        if (discordId != null) {
            payload.put("discordId", discordId);
        }
        return payload;
    }
}
