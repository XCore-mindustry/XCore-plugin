package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import jakarta.inject.Singleton;
import org.bson.Document;

@Singleton
public class V8__CreatePlayerTotals implements Migration {
    @Override
    public int getVersion() {
        return 8;
    }

    @Override
    public String getDescription() {
        return "Create materialized player statistics and schedule background backfill";
    }

    @Override
    public void up(MongoDatabase database) {
        // The rebuild worker walks dirty players oldest first and skips leased or backed-off ones;
        // range fields before queued_at would make every claim sort in memory.
        database.getCollection("player_totals").createIndex(new Document("rebuild.dirty", 1)
                .append("rebuild.queued_at", 1));
        // Replay reads one player's games in fold order.
        database.getCollection("games_v2").createIndex(new Document("player_stats.uuid", 1)
                .append("ended_at", 1).append("_id", 1));
        // Only marks the backfill as due; an enabled plugin performs it off the game thread.
        database.getCollection("settings").updateOne(new Document("_id", "player_totals_backfill"),
                new Document("$setOnInsert", new Document("done", false)), new UpdateOptions().upsert(true));
    }
}
