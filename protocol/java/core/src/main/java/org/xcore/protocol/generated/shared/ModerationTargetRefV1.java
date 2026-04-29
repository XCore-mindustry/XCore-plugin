package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record ModerationTargetRefV1(
        String playerUuid,
        Integer playerPid,
        String playerName,
        String ip
) {
    public ModerationTargetRefV1 {
        if (playerUuid != null) {
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
        }
        if (playerPid != null) {
            if (playerPid < 0) {
                throw new IllegalArgumentException("playerPid must be >= 0");
            }
        }
        if (playerName != null) {
            Objects.requireNonNull(playerName, "playerName must not be null");
            if (playerName.length() < 1) {
                throw new IllegalArgumentException("playerName must be at least 1 characters");
            }
        }
        if (ip != null) {
            Objects.requireNonNull(ip, "ip must not be null");
            if (ip.length() < 1) {
                throw new IllegalArgumentException("ip must be at least 1 characters");
            }
        }
        if (!(playerUuid != null || ip != null)) {
            throw new IllegalArgumentException("At least one of playerUuid, ip must be provided");
        }
    }
}
