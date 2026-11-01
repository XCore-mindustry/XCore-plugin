package org.xcore.plugin.service.map.inspect;

import org.xcore.plugin.config.TomlSecretsConfig.MapsConfig.ChecksConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable configuration captured before leaving the game thread. */
public record InspectionSettings(int maxFileBytes, int maxTiles, Map<String, Profile> profiles,
                                 Map<String, MapFinding.Severity> severities) {
    public static final int MAX_INFLATED_BYTES = 64 * 1024 * 1024;
    public static final int MAX_REGION_BYTES = 32 * 1024 * 1024;
    public static final int MAX_ALLOCATED_TILES = 1_000_000;

    public InspectionSettings {
        if (maxFileBytes <= 0 || maxTiles <= 0) throw new IllegalArgumentException("Positive map limits required");
        maxFileBytes = Math.min(maxFileBytes, MAX_INFLATED_BYTES);
        maxTiles = Math.min(maxTiles, MAX_ALLOCATED_TILES);
        profiles = Map.copyOf(profiles);
        var validSeverities = new LinkedHashMap<String, MapFinding.Severity>();
        severities.forEach((code, severity) -> {
            if (ChecksConfig.FINDING_CODES.contains(code) && severity != null) validSeverities.put(code, severity);
        });
        severities = Map.copyOf(validSeverities);
    }

    public InspectionSettings(int maxFileBytes, int maxTiles, Map<String, Profile> profiles) {
        this(maxFileBytes, maxTiles, profiles, Map.of());
    }

    public static InspectionSettings capture(ChecksConfig config) {
        var profiles = new LinkedHashMap<String, Profile>();
        config.profiles.forEach((id, profile) -> profiles.put(id,
                new Profile(profile.roadFloor, profile.borderWall, profile.borderTolerance, profile.requireDropZoneRadiusZero,
                        profile.minWidth, profile.maxWidth, profile.minHeight, profile.maxHeight)));
        var severities = new LinkedHashMap<String, MapFinding.Severity>();
        if (config.severities != null) config.severities.forEach((code, value) -> {
            String normalized = ChecksConfig.normalizeSeverity(code, value);
            if (normalized != null) severities.put(code, MapFinding.Severity.valueOf(normalized.toUpperCase(java.util.Locale.ROOT)));
        });
        return new InspectionSettings(config.maxFileBytes, config.maxTiles, profiles, severities);
    }

    public record Profile(String roadFloor, String borderWall, double borderTolerance, boolean requireDropZoneRadiusZero,
                          int minWidth, int maxWidth, int minHeight, int maxHeight) {
        public Profile(String roadFloor, String borderWall, double borderTolerance, boolean requireDropZoneRadiusZero) {
            this(roadFloor, borderWall, borderTolerance, requireDropZoneRadiusZero, 0, 0, 0, 0);
        }

        public Profile {
            roadFloor = roadFloor == null ? "" : roadFloor;
            borderWall = borderWall == null ? "" : borderWall;
            minWidth = Math.max(0, minWidth);
            maxWidth = Math.max(0, maxWidth);
            minHeight = Math.max(0, minHeight);
            maxHeight = Math.max(0, maxHeight);
            if (maxWidth > 0 && maxWidth < minWidth) maxWidth = minWidth;
            if (maxHeight > 0 && maxHeight < minHeight) maxHeight = minHeight;
            if (!Double.isFinite(borderTolerance) || borderTolerance < 0 || borderTolerance > 1) borderTolerance = 0.05;
        }
    }
}
