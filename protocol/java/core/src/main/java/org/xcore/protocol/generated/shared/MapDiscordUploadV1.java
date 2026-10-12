package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record MapDiscordUploadV1(
        DiscordIdentityRefV1 uploadedBy,
        long uploadedAt,
        DiscordIdentityRefV1 creator,
        String uploadedByHandle,
        String creatorHandle
) implements ProtocolPayload {
    public MapDiscordUploadV1 {
        Objects.requireNonNull(uploadedBy, "uploadedBy must not be null");
        if (uploadedAt < 1) {
            throw new IllegalArgumentException("uploadedAt must be >= 1");
        }
        if (creator != null) {
            Objects.requireNonNull(creator, "creator must not be null");
        }
        if (uploadedByHandle != null) {
            Objects.requireNonNull(uploadedByHandle, "uploadedByHandle must not be null");
            if (uploadedByHandle.length() < 1) {
                throw new IllegalArgumentException("uploadedByHandle must be at least 1 characters");
            }
        }
        if (creatorHandle != null) {
            Objects.requireNonNull(creatorHandle, "creatorHandle must not be null");
            if (creatorHandle.length() < 1) {
                throw new IllegalArgumentException("creatorHandle must be at least 1 characters");
            }
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uploadedBy", uploadedBy.toPayload());
        payload.put("uploadedAt", uploadedAt);
        if (creator != null) {
            payload.put("creator", creator.toPayload());
        }
        if (uploadedByHandle != null) {
            payload.put("uploadedByHandle", uploadedByHandle);
        }
        if (creatorHandle != null) {
            payload.put("creatorHandle", creatorHandle);
        }
        return payload;
    }
}
