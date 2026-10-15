package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record MapInspectInfoV1(
        String name,
        String author,
        String description,
        int width,
        int height,
        Integer saveVersion,
        Integer build,
        String gameMode,
        String profile,
        Integer spawnCount,
        Map<String, Object> coresByTeam,
        Boolean waves,
        Boolean waveTimer,
        Integer spawnGroupCount,
        Double dropZoneRadius,
        List<String> mods,
        String sha256,
        int sizeBytes
) implements ProtocolPayload {
    public MapInspectInfoV1 {
        Objects.requireNonNull(name, "name must not be null");
        if (author != null) {
            Objects.requireNonNull(author, "author must not be null");
        }
        if (description != null) {
            Objects.requireNonNull(description, "description must not be null");
        }
        if (width < 0) {
            throw new IllegalArgumentException("width must be >= 0");
        }
        if (height < 0) {
            throw new IllegalArgumentException("height must be >= 0");
        }
        if (gameMode != null) {
            Objects.requireNonNull(gameMode, "gameMode must not be null");
        }
        if (profile != null) {
            Objects.requireNonNull(profile, "profile must not be null");
        }
        if (spawnCount != null) {
            if (spawnCount < 0) {
                throw new IllegalArgumentException("spawnCount must be >= 0");
            }
        }
        if (coresByTeam != null) {
            coresByTeam = Objects.requireNonNull(coresByTeam, "coresByTeam must not be null");
            for (Map.Entry<String, Object> entry : coresByTeam.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "coresByTeam keys must not be null");
                Objects.requireNonNull(entry.getValue(), "coresByTeam values must not be null");
                if (!(entry.getValue() instanceof Integer)) {
                    throw new IllegalArgumentException("coresByTeam values must be one of: integer");
                }
            }
            coresByTeam = Map.copyOf(coresByTeam);
        }
        if (spawnGroupCount != null) {
            if (spawnGroupCount < 0) {
                throw new IllegalArgumentException("spawnGroupCount must be >= 0");
            }
        }
        if (mods != null) {
            mods = Objects.requireNonNull(mods, "mods must not be null");
            mods = List.copyOf(mods);
            for (String item : mods) {
                Objects.requireNonNull(item, "mods[] must not be null");
            }
        }
        Objects.requireNonNull(sha256, "sha256 must not be null");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        if (author != null) {
            payload.put("author", author);
        }
        if (description != null) {
            payload.put("description", description);
        }
        payload.put("width", width);
        payload.put("height", height);
        if (saveVersion != null) {
            payload.put("saveVersion", saveVersion);
        }
        if (build != null) {
            payload.put("build", build);
        }
        if (gameMode != null) {
            payload.put("gameMode", gameMode);
        }
        if (profile != null) {
            payload.put("profile", profile);
        }
        if (spawnCount != null) {
            payload.put("spawnCount", spawnCount);
        }
        if (coresByTeam != null) {
            payload.put("coresByTeam", java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(coresByTeam)));
        }
        if (waves != null) {
            payload.put("waves", waves);
        }
        if (waveTimer != null) {
            payload.put("waveTimer", waveTimer);
        }
        if (spawnGroupCount != null) {
            payload.put("spawnGroupCount", spawnGroupCount);
        }
        if (dropZoneRadius != null) {
            payload.put("dropZoneRadius", dropZoneRadius);
        }
        if (mods != null) {
            payload.put("mods", List.copyOf(mods));
        }
        payload.put("sha256", sha256);
        payload.put("sizeBytes", sizeBytes);
        return payload;
    }
}
