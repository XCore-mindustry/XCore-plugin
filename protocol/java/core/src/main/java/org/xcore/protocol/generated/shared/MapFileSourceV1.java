package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record MapFileSourceV1(
        String url,
        String filename
) {
    public MapFileSourceV1 {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
    }
}
