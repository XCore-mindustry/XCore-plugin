package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record ExpirationInfoV1(
        String expiresAt,
        Boolean permanent
) implements ProtocolPayload {
    public ExpirationInfoV1 {
        if (expiresAt != null) {
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (expiresAt != null) {
            payload.put("expiresAt", expiresAt);
        }
        if (permanent != null) {
            payload.put("permanent", permanent);
        }
        return payload;
    }
}
