package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record CaseBlockSummaryV1(
        int windowSeconds,
        int built,
        int destroyed,
        int configured,
        boolean truncated,
        List<CaseBlockEntryV1> topBlocks,
        CaseBlockAreaV1 area,
        Integer lastActionSecondsAgo
) implements ProtocolPayload {
    public CaseBlockSummaryV1 {
        if (windowSeconds < 1) {
            throw new IllegalArgumentException("windowSeconds must be >= 1");
        }
        if (built < 0) {
            throw new IllegalArgumentException("built must be >= 0");
        }
        if (destroyed < 0) {
            throw new IllegalArgumentException("destroyed must be >= 0");
        }
        if (configured < 0) {
            throw new IllegalArgumentException("configured must be >= 0");
        }
        topBlocks = Objects.requireNonNull(topBlocks, "topBlocks must not be null");
        topBlocks = List.copyOf(topBlocks);
        for (CaseBlockEntryV1 item : topBlocks) {
            Objects.requireNonNull(item, "topBlocks[] must not be null");
        }
        if (area != null) {
            Objects.requireNonNull(area, "area must not be null");
        }
        if (lastActionSecondsAgo != null) {
            if (lastActionSecondsAgo < 0) {
                throw new IllegalArgumentException("lastActionSecondsAgo must be >= 0");
            }
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("windowSeconds", windowSeconds);
        payload.put("built", built);
        payload.put("destroyed", destroyed);
        payload.put("configured", configured);
        payload.put("truncated", truncated);
        if (topBlocks != null) {
            payload.put(
        "topBlocks",
        topBlocks.stream()
            .map(item -> item.toPayload())
            .toList()
    );
        }
        if (area != null) {
            payload.put("area", area.toPayload());
        }
        if (lastActionSecondsAgo != null) {
            payload.put("lastActionSecondsAgo", lastActionSecondsAgo);
        }
        return payload;
    }
}
