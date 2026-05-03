package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record DiscordIdentityRefV1(
        String discordId,
        String discordUsername
) implements ProtocolPayload {
    public DiscordIdentityRefV1 {
        Objects.requireNonNull(discordId, "discordId must not be null");
        if (discordId.length() < 1) {
            throw new IllegalArgumentException("discordId must be at least 1 characters");
        }
        if (discordUsername != null) {
            Objects.requireNonNull(discordUsername, "discordUsername must not be null");
            if (discordUsername.length() < 1) {
                throw new IllegalArgumentException("discordUsername must be at least 1 characters");
            }
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("discordId", discordId);
        if (discordUsername != null) {
            payload.put("discordUsername", discordUsername);
        }
        return payload;
    }
}
