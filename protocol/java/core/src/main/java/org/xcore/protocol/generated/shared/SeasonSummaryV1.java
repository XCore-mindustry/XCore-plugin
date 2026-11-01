package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record SeasonSummaryV1(
        int participants,
        int matches
) implements ProtocolPayload {
    public SeasonSummaryV1 {
        if (participants < 0) {
            throw new IllegalArgumentException("participants must be >= 0");
        }
        if (matches < 0) {
            throw new IllegalArgumentException("matches must be >= 0");
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("participants", participants);
        payload.put("matches", matches);
        return payload;
    }
}
