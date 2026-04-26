package org.xcore.protocol.generated.shared;

import java.util.Objects;

public record ExpirationInfoV1(
        String expiresAt,
        Boolean permanent
) {
    public ExpirationInfoV1 {
        if (expiresAt != null) {
            Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }
    }
}
