package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record PlayerCommandTargetV1(
        String playerUuid,
        Integer playerPid,
        String playerName,
        String ip
) implements ProtocolPayload {
    public PlayerCommandTargetV1 {
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

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (playerUuid != null) {
            payload.put("playerUuid", playerUuid);
        }
        if (playerPid != null) {
            payload.put("playerPid", playerPid);
        }
        if (playerName != null) {
            payload.put("playerName", playerName);
        }
        if (ip != null) {
            payload.put("ip", ip);
        }
        return payload;
    }
}
