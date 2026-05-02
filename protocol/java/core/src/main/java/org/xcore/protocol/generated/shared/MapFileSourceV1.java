package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record MapFileSourceV1(
        String url,
        String fileName
) {
    public MapFileSourceV1 {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
    }
}
