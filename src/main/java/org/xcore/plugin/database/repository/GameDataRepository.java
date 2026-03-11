package org.xcore.plugin.database.repository;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.MongoDatabase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.GameData;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.enums.GameStatsCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Singleton
public class GameDataRepository extends DataRepository<GameData> {
    private static final String COLLECTION_NAME = "games_v2";

    @Inject
    public GameDataRepository(MongoDatabase database, GlobalConfig globalConfig) {
        super(database, COLLECTION_NAME, GameData.class, globalConfig);

        collection.createIndex(new Document("map", 1));
        collection.createIndex(new Document("event", 1));
        collection.createIndex(new Document("server_name", 1));
        collection.createIndex(new Document("created_at", -1));
        collection.createIndex(new Document("player_stats.uuid", 1));
    }

    public AggregatedPlayerStats aggregatePlayerStats(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return AggregatedPlayerStats.EMPTY;
        }

        var results = database.getCollection(COLLECTION_NAME).aggregate(List.of(
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.match(Filters.eq("counted_in_stats", true)),
                Aggregates.unwind("$player_stats"),
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.group(null,
                        Accumulators.sum("gamesPlayed", 1),
                        Accumulators.sum("gamesWon", new Document("$cond", List.of("$player_stats.isWinner", 1, 0))),
                        Accumulators.sum("blocksBuilt", "$player_stats.blocks_built"),
                        Accumulators.sum("blocksDeconstructed", "$player_stats.blocks_deconstructed"),
                        Accumulators.sum("blocksDestroyed", "$player_stats.blocks_destroyed"),
                        Accumulators.sum("unitsProduced", "$player_stats.units_produced"),
                        Accumulators.sum("unitsDestroyed", "$player_stats.units_destroyed")
                ),
                Aggregates.project(Projections.excludeId())
        )).into(new java.util.ArrayList<>());

        if (results.isEmpty()) {
            return AggregatedPlayerStats.EMPTY;
        }

        var doc = results.getFirst();
        return new AggregatedPlayerStats(
                doc.getInteger("gamesPlayed", 0),
                doc.getInteger("gamesWon", 0),
                doc.getInteger("blocksBuilt", 0),
                doc.getInteger("blocksDeconstructed", 0),
                doc.getInteger("blocksDestroyed", 0),
                doc.getInteger("unitsProduced", 0),
                doc.getInteger("unitsDestroyed", 0)
        );
    }

    public PlayerStatsOverview aggregatePlayerStatsOverview(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return new PlayerStatsOverview(AggregatedPlayerStats.EMPTY, ModeStatsSummary.EMPTY, ModeStatsSummary.EMPTY, ModeStatsSummary.EMPTY);
        }

        AggregatedPlayerStats overall = aggregatePlayerStats(uuid);
        var modeStats = aggregateModeStats(uuid);

        return new PlayerStatsOverview(
                overall,
                modeStats.getOrDefault(GameStatsCategory.PVP, ModeStatsSummary.EMPTY),
                modeStats.getOrDefault(GameStatsCategory.SURVIVAL, ModeStatsSummary.EMPTY),
                modeStats.getOrDefault(GameStatsCategory.HEXED, ModeStatsSummary.EMPTY)
        );
    }

    private Map<GameStatsCategory, ModeStatsSummary> aggregateModeStats(String uuid) {
        var rows = database.getCollection(COLLECTION_NAME).aggregate(List.of(
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.match(Filters.eq("counted_in_stats", true)),
                Aggregates.unwind("$player_stats"),
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.group("$stats_category",
                        Accumulators.sum("gamesPlayed", 1),
                        Accumulators.sum("gamesWon", new Document("$cond", List.of("$player_stats.isWinner", 1, 0))),
                        Accumulators.max("bestWave", "$waves_reached"),
                        Accumulators.avg("averageWave", "$waves_reached"),
                        Accumulators.min("bestPlacement", new Document("$ifNull", List.of("$player_stats.placement", Integer.MAX_VALUE))),
                        Accumulators.sum("top3Finishes", new Document("$cond", List.of(
                                new Document("$and", List.of(
                                        new Document("$ne", Arrays.asList("$player_stats.placement", null)),
                                        new Document("$lte", List.of("$player_stats.placement", 3))
                                )),
                                1,
                                0
                        )))
                ),
                Aggregates.project(Projections.fields(
                        Projections.computed("statsCategory", "$_id"),
                        Projections.include("gamesPlayed", "gamesWon", "bestWave", "averageWave", "bestPlacement", "top3Finishes"),
                        Projections.excludeId()
                ))
        )).into(new ArrayList<>());

        Map<GameStatsCategory, ModeStatsSummary> result = new EnumMap<>(GameStatsCategory.class);
        for (var doc : rows) {
            String categoryName = doc.getString("statsCategory");
            if (categoryName == null || categoryName.isBlank()) {
                continue;
            }

            GameStatsCategory category;
            try {
                category = GameStatsCategory.valueOf(categoryName);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            int bestPlacement = doc.getInteger("bestPlacement", Integer.MAX_VALUE);
            if (bestPlacement == Integer.MAX_VALUE) {
                bestPlacement = 0;
            }

            double averageWave = doc.getDouble("averageWave") == null ? 0.0 : doc.getDouble("averageWave");

            result.put(category, new ModeStatsSummary(
                    doc.getInteger("gamesPlayed", 0),
                    doc.getInteger("gamesWon", 0),
                    doc.getInteger("bestWave", 0),
                    (int) Math.round(averageWave),
                    bestPlacement,
                    doc.getInteger("top3Finishes", 0)
            ));
        }

        return result;
    }
}
