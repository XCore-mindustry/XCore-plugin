package org.xcore.plugin.map.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.xcore.plugin.model.ModelData;

import java.util.HashMap;
import java.util.Map;

/**
 * Domain entity representing a persistent Map across all gamemodes.
 * Decouples content-addressed physical identity from mutable display aliases and modes.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MapEntity extends ModelData {

    /** SHA-256 digest of exact file bytes. Primary index. */
    @BsonProperty("content_hash")
    @Builder.Default
    public String contentHash = null;

    /** Canonical slug (author/name) stripped of colors and normalized. Secondary index. */
    @BsonProperty("slug")
    @Builder.Default
    public String slug = null;

    @Builder.Default
    public String name = "Unknown";

    @Builder.Default
    public String author = "Unknown";

    @BsonProperty("file_name")
    @Builder.Default
    public String fileName = "Unknown";

    @Builder.Default
    public int width = 0;

    @Builder.Default
    public int height = 0;

    // --- Global Ratings & Popularity (across ALL gamemodes) ---
    @Builder.Default
    public int likes = 0;

    @Builder.Default
    public int dislikes = 0;

    @Builder.Default
    public int reputation = 0;

    @Builder.Default
    public double popularity = 0.0;

    @Builder.Default
    public double interest = 0.0;

    @BsonProperty("total_play_count")
    @Builder.Default
    public long totalPlayCount = 0L;

    @BsonProperty("last_played_at")
    @Builder.Default
    public long lastPlayedAt = 0L;

    /** Gamemode-partitioned play durations and telemetry matrix. */
    @BsonProperty("gamemode_stats")
    @Builder.Default
    public Map<String, GamemodeMetrics> gamemodeStats = new HashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GamemodeMetrics {
        @Builder.Default
        public long plays = 0L;

        @Builder.Default
        public long wins = 0L;

        @BsonProperty("minimum_duration_ms")
        @Builder.Default
        public long minimumDurationMs = 0L;

        @BsonProperty("average_duration_ms")
        @Builder.Default
        public long averageDurationMs = 0L;

        @BsonProperty("maximum_duration_ms")
        @Builder.Default
        public long maximumDurationMs = 0L;
    }
}
