package org.xcore.plugin.map.domain;

import arc.util.Strings;
import org.xcore.plugin.model.MapData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consolidates mode-split legacy {@link MapData} records into a single unified {@link MapEntity}.
 * Decouples global ratings/reputation from gamemode-specific duration telemetry.
 */
public class MapDataConsolidator {

    public MapEntity consolidate(List<MapData> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        // Pick the most recently played record as primary metadata anchor
        MapData primary = records.stream()
                .max((a, b) -> Long.compare(a.lastPlayedTime, b.lastPlayedTime))
                .orElse(records.getFirst());

        String cleanName = Strings.stripColors(primary.name == null ? "Unknown" : primary.name).trim();
        String cleanAuthor = Strings.stripColors(primary.author == null ? "Unknown" : primary.author).trim();
        if (cleanName.isBlank()) cleanName = "Unknown";
        if (cleanAuthor.isBlank()) cleanAuthor = "Unknown";

        String slug = MapSlug.of(cleanAuthor, cleanName).asSlug();

        String contentHash = records.stream()
                .map(r -> r.contentHash)
                .filter(h -> h != null && !h.isBlank())
                .findFirst()
                .orElse(null);

        String fileName = records.stream()
                .map(r -> r.fileName)
                .filter(f -> f != null && !f.isBlank() && !"Unknown".equalsIgnoreCase(f))
                .findFirst()
                .orElse(primary.fileName);

        int totalLikes = records.stream().mapToInt(r -> r.like).sum();
        int totalDislikes = records.stream().mapToInt(r -> r.dislike).sum();
        int totalReputation = records.stream().mapToInt(r -> r.reputation).sum();
        double totalPopularity = records.stream().mapToDouble(r -> r.popularity).sum();
        double totalInterest = records.stream().mapToDouble(r -> r.interest).sum();
        long totalPlays = records.stream().mapToLong(r -> r.playedTimes).sum();
        long lastPlayed = records.stream().mapToLong(r -> r.lastPlayedTime).max().orElse(0L);

        Map<String, MapEntity.GamemodeMetrics> gamemodeStats = new HashMap<>();
        for (MapData record : records) {
            String mode = record.gameMode != null ? record.gameMode.trim().toLowerCase() : "survival";
            if (mode.isBlank()) mode = "survival";

            MapEntity.GamemodeMetrics existing = gamemodeStats.get(mode);
            if (existing == null) {
                gamemodeStats.put(mode, MapEntity.GamemodeMetrics.builder()
                        .plays(record.playedTimes)
                        .wins(0L)
                        .minimumDurationMs(record.minimumGameTime)
                        .averageDurationMs(record.averageGameTime)
                        .maximumDurationMs(record.maximumGameTime)
                        .build());
            } else {
                // Merge if duplicate gamemode records exist
                long combinedPlays = existing.plays + record.playedTimes;
                long minDur = (existing.minimumDurationMs == 0) ? record.minimumGameTime
                        : (record.minimumGameTime == 0) ? existing.minimumDurationMs
                        : Math.min(existing.minimumDurationMs, record.minimumGameTime);
                long maxDur = Math.max(existing.maximumDurationMs, record.maximumGameTime);
                long avgDur = combinedPlays == 0 ? 0 :
                        ((existing.averageDurationMs * existing.plays) + (record.averageGameTime * record.playedTimes)) / combinedPlays;

                existing.plays = combinedPlays;
                existing.minimumDurationMs = minDur;
                existing.averageDurationMs = avgDur;
                existing.maximumDurationMs = maxDur;
            }
        }

        return MapEntity.builder()
                .name(cleanName)
                .author(cleanAuthor)
                .fileName(fileName)
                .contentHash(contentHash)
                .slug(slug)
                .likes(totalLikes)
                .dislikes(totalDislikes)
                .reputation(totalReputation)
                .popularity(totalPopularity)
                .interest(totalInterest)
                .totalPlayCount(totalPlays)
                .lastPlayedAt(lastPlayed)
                .gamemodeStats(gamemodeStats)
                .build();
    }
}
