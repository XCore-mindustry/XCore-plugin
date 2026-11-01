package org.xcore.plugin.service;

import arc.util.Log;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;

@Singleton
public class MapStatsService {

    private static final long MIN_COUNTED_DURATION_MILLIS = 120_000L;

    private final MapDataRepository mapDataRepository;

    public MapStatsService(MapDataRepository mapDataRepository) {
        this.mapDataRepository = mapDataRepository;
    }

    public boolean registerCompletedGame(String mapName,
                                         String fileName,
                                         String author,
                                         String modeName,
                                         long durationMillis,
                                         boolean isWin) {
        if (durationMillis <= MIN_COUNTED_DURATION_MILLIS) {
            return false;
        }
        if (mapName == null || fileName == null || author == null || modeName == null) {
            return false;
        }

        try {
            MapData stats = mapDataRepository.findOrCreate(mapName, fileName, author, modeName);
            stats.registerGame(durationMillis, isWin, modeName, author);
            boolean updated = mapDataRepository.registerGameStats(
                    stats.id,
                    durationMillis,
                    isWin,
                    author,
                    modeName,
                    stats.playedTimes,
                    stats.averageGameTime,
                    stats.minimumGameTime,
                    stats.maximumGameTime,
                    stats.playedTimesYear,
                    stats.lastPlayedTime,
                    stats.popularity,
                    stats.interest
            );

            if (updated) {
                Log.info("Map stats updated for '@'", mapName);
            }
            return updated;
        } catch (Exception e) {
            Log.err("Failed to update map stats", e);
            return false;
        }
    }
}
