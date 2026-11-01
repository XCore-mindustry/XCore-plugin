package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.List;

@Singleton
public class V4__MigrateLegacyPvpRating implements Migration {

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public String getDescription() {
        return "Preserve legacy PvP rating into legacy_pvp_rating and reset MiniPvP rating for new Elo engine";
    }

    @Override
    public void up(MongoDatabase database) {
        var players = database.getCollection("players");

        players.updateMany(
                new Document("legacy_pvp_rating", new Document("$exists", false)),
                List.of(
                        new Document("$set", new Document("legacy_pvp_rating", new Document("$ifNull", List.of("$pvp_rating", 0)))),
                        new Document("$set", new Document("pvp_rating", 1000)),
                        new Document("$set", new Document("pvp_matches", 0)),
                        new Document("$set", new Document("pvp_wins", 0))
                )
        );
    }
}
