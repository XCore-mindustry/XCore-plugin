package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record SeasonPrizeV1(
        int placeFrom,
        int placeTo,
        SeasonPrizeV1Kind kind,
        String value,
        String description
) implements ProtocolPayload {
    public SeasonPrizeV1 {
        if (placeFrom < 1) {
            throw new IllegalArgumentException("placeFrom must be >= 1");
        }
        if (placeTo < 1) {
            throw new IllegalArgumentException("placeTo must be >= 1");
        }
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (value.length() < 1) {
            throw new IllegalArgumentException("value must be at least 1 characters");
        }
        if (description != null) {
            Objects.requireNonNull(description, "description must not be null");
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("placeFrom", placeFrom);
        payload.put("placeTo", placeTo);
        if (kind != null) {
            payload.put("kind", kind.toString());
        }
        payload.put("value", value);
        if (description != null) {
            payload.put("description", description);
        }
        return payload;
    }
}
