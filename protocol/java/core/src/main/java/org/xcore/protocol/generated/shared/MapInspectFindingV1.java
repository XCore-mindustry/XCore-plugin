package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record MapInspectFindingV1(
        String code,
        MapInspectFindingV1Severity severity,
        String detail,
        Integer x,
        Integer y,
        Integer count
) implements ProtocolPayload {
    public MapInspectFindingV1 {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (detail != null) {
            Objects.requireNonNull(detail, "detail must not be null");
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        if (severity != null) {
            payload.put("severity", severity.toString());
        }
        if (detail != null) {
            payload.put("detail", detail);
        }
        if (x != null) {
            payload.put("x", x);
        }
        if (y != null) {
            payload.put("y", y);
        }
        if (count != null) {
            payload.put("count", count);
        }
        return payload;
    }
}
