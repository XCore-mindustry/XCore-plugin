package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record MapFileSourceV1(
        String url,
        String fileName
) implements ProtocolPayload {
    public MapFileSourceV1 {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", url);
        payload.put("fileName", fileName);
        return payload;
    }
}
