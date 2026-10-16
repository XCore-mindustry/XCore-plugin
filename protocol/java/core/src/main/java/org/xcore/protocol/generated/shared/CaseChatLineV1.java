package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record CaseChatLineV1(
        long sentAt,
        String playerName,
        Integer playerPid,
        CaseChatLineV1Role role,
        String text,
        Boolean team
) implements ProtocolPayload {
    public CaseChatLineV1 {
        Objects.requireNonNull(playerName, "playerName must not be null");
        if (playerName.length() < 1) {
            throw new IllegalArgumentException("playerName must be at least 1 characters");
        }
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sentAt", sentAt);
        payload.put("playerName", playerName);
        if (playerPid != null) {
            payload.put("playerPid", playerPid);
        }
        if (role != null) {
            payload.put("role", role.toString());
        }
        payload.put("text", text);
        if (team != null) {
            payload.put("team", team);
        }
        return payload;
    }
}
