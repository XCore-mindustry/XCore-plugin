package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record CaseBlockAreaV1(
        int x1,
        int y1,
        int x2,
        int y2
) implements ProtocolPayload {
    public CaseBlockAreaV1 {
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("x1", x1);
        payload.put("y1", y1);
        payload.put("x2", x2);
        payload.put("y2", y2);
        return payload;
    }
}
