package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record SeasonRefV1(
        String ladder,
        int season,
        String name,
        String startsAt,
        String endsAt
) implements ProtocolPayload {
    public SeasonRefV1 {
        Objects.requireNonNull(ladder, "ladder must not be null");
        if (ladder.length() < 1) {
            throw new IllegalArgumentException("ladder must be at least 1 characters");
        }
        if (season < 1) {
            throw new IllegalArgumentException("season must be >= 1");
        }
        Objects.requireNonNull(name, "name must not be null");
        if (name.length() < 1) {
            throw new IllegalArgumentException("name must be at least 1 characters");
        }
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ladder", ladder);
        payload.put("season", season);
        payload.put("name", name);
        payload.put("startsAt", startsAt);
        payload.put("endsAt", endsAt);
        return payload;
    }
}
