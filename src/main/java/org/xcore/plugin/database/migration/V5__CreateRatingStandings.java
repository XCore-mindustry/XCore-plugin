package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.List;

/**
 * Copies both existing ratings into {@code rating_standings} as season 1 of their ladders.
 * The sources are left untouched, and a standing that already exists is never overwritten,
 * so the migration is safe to re-run.
 */
@Singleton
public class V5__CreateRatingStandings implements Migration {
    private static final String STANDINGS = "rating_standings";
    private static final List<String> STANDING_KEY = List.of("ladder", "season", "player_uuid");

    @Override
    public int getVersion() {
        return 5;
    }

    @Override
    public String getDescription() {
        return "Copy MiniPvP and HexedCore ratings into rating_standings as season 1 of the shared ladder engine";
    }

    @Override
    public void up(MongoDatabase database) {
        // $merge matches on the standing key, which requires this exact unique index.
        database.getCollection(STANDINGS).createIndex(
                new Document("ladder", 1).append("season", 1).append("player_uuid", 1),
                new IndexOptions().unique(true));

        database.getCollection("players").aggregate(List.of(
                new Document("$match", new Document("pvp_matches", new Document("$gt", 0))
                        .append("uuid", new Document("$not", new Document("$regex", "^merged:")))),
                standing("minipvp", "$uuid", "$pvp_rating", "$pvp_matches", "$pvp_wins", new Document()),
                mergeIntoStandings()
        )).toCollection();

        database.getCollection("xcore_plugin_hexedcore_rating_players").aggregate(List.of(
                new Document("$match", new Document("data.matches", new Document("$gt", 0))),
                standing("hexed", "$player_uuid", "$data.rating", "$data.matches", "$data.wins",
                        new Document("top3", orZero("$data.top3"))
                                .append("disconnects", orZero("$data.disconnects"))),
                mergeIntoStandings()
        )).toCollection();
    }

    private static Document standing(String ladder, String uuid, String rating, String matches, String wins,
                                     Document stats) {
        Document ratingOrDefault = new Document("$ifNull", List.of(rating, 1000));
        return new Document("$project", new Document("_id", 0)
                .append("ladder", new Document("$literal", ladder))
                .append("season", new Document("$literal", 1))
                .append("player_uuid", uuid)
                .append("rating", ratingOrDefault)
                .append("peak_rating", ratingOrDefault)
                .append("matches", matches)
                .append("wins", orZero(wins))
                .append("stats", stats.isEmpty() ? new Document("$literal", new Document()) : stats)
                .append("applied_operations", new Document("$literal", List.of()))
                .append("created_at", "$$NOW")
                .append("updated_at", "$$NOW"));
    }

    private static Document orZero(String field) {
        return new Document("$ifNull", List.of(field, 0));
    }

    private static Document mergeIntoStandings() {
        return new Document("$merge", new Document("into", STANDINGS)
                .append("on", STANDING_KEY)
                .append("whenMatched", "keepExisting")
                .append("whenNotMatched", "insert"));
    }
}
