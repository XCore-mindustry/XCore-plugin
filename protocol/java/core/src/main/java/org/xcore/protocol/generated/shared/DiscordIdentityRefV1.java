package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record DiscordIdentityRefV1(
        String discordId,
        String discordUsername
) {
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
}
