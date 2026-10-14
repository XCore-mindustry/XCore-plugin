package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record CaseBlockEntryV1(
        String block,
        int built,
        int destroyed
) implements ProtocolPayload {
    public CaseBlockEntryV1 {
        Objects.requireNonNull(block, "block must not be null");
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("block", block);
        payload.put("built", built);
        payload.put("destroyed", destroyed);
        return payload;
    }
}
