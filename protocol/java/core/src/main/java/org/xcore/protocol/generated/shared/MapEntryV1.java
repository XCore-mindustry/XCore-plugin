package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record MapEntryV1(
        String name,
        String fileName,
        String author,
        Integer width,
        Integer height,
        Integer fileSizeBytes,
        Integer like,
        Integer dislike,
        Integer reputation,
        Double popularity,
        Double interest,
        String gameMode
) implements ProtocolPayload {
    public MapEntryV1 {
        Objects.requireNonNull(name, "name must not be null");
        if (name.length() < 1) {
            throw new IllegalArgumentException("name must be at least 1 characters");
        }
        Objects.requireNonNull(fileName, "fileName must not be null");
        if (fileName.length() < 1) {
            throw new IllegalArgumentException("fileName must be at least 1 characters");
        }
        Objects.requireNonNull(author, "author must not be null");
        if (author.length() < 1) {
            throw new IllegalArgumentException("author must be at least 1 characters");
        }
        if (width != null) {
            if (width < 0) {
                throw new IllegalArgumentException("width must be >= 0");
            }
        }
        if (height != null) {
            if (height < 0) {
                throw new IllegalArgumentException("height must be >= 0");
            }
        }
        if (fileSizeBytes != null) {
            if (fileSizeBytes < 0) {
                throw new IllegalArgumentException("fileSizeBytes must be >= 0");
            }
        }
        if (gameMode != null) {
            Objects.requireNonNull(gameMode, "gameMode must not be null");
            if (gameMode.length() < 1) {
                throw new IllegalArgumentException("gameMode must be at least 1 characters");
            }
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("fileName", fileName);
        payload.put("author", author);
        if (width != null) {
            payload.put("width", width);
        }
        if (height != null) {
            payload.put("height", height);
        }
        if (fileSizeBytes != null) {
            payload.put("fileSizeBytes", fileSizeBytes);
        }
        if (like != null) {
            payload.put("like", like);
        }
        if (dislike != null) {
            payload.put("dislike", dislike);
        }
        if (reputation != null) {
            payload.put("reputation", reputation);
        }
        if (popularity != null) {
            payload.put("popularity", popularity);
        }
        if (interest != null) {
            payload.put("interest", interest);
        }
        if (gameMode != null) {
            payload.put("gameMode", gameMode);
        }
        return payload;
    }
}
