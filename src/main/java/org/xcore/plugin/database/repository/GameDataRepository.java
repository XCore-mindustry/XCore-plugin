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

import java.util.List;

@Singleton
public class GameDataRepository extends DataRepository<GameData> {

    @Inject
    public GameDataRepository(MongoDatabase database, GlobalConfig globalConfig) {
        super(database, "games", GameData.class, globalConfig);

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

        var results = database.getCollection("games").aggregate(List.of(
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
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
}
