package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.model.PlayerPids;

@Singleton
public class V7__AssignBanIds implements Migration {
    @Override
    public int getVersion() {
        return 7;
    }

    @Override
    public String getDescription() {
        return "Assign ban IDs, back-fill player PIDs and index ban identity";
    }

    @Override
    public void up(MongoDatabase database) {
        var bans = database.getCollection("bans");
        var counters = database.getCollection("counters");
        var players = database.getCollection("players");

        bans.createIndex(new Document("ban_id", 1), new IndexOptions().unique(true)
                .partialFilterExpression(new Document("ban_id", new Document("$type", "int"))));
        database.getCollection("moderation_audit").createIndex(
                new Document("details.extra.ban_id", 1), new IndexOptions().sparse(true));

        for (var ban : bans.find().sort(new Document("_id", 1))) {
            if (!ban.containsKey("ban_id")) {
                var counter = counters.findOneAndUpdate(new Document("_id", "ban_id"),
                        new Document("$inc", new Document("seq", 1)),
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
                int banId = Math.toIntExact(((Number) counter.get("seq")).longValue());
                if (banId < 1) throw new IllegalStateException("Ban ID counter must be positive");
                bans.updateOne(new Document("_id", ban.get("_id"))
                                .append("ban_id", new Document("$exists", false)),
                        new Document("$set", new Document("ban_id", banId)));
            }
            if (!PlayerPids.isAssigned(ban.getInteger("pid")) && ban.getString("uuid") != null) {
                var player = players.find(new Document("uuid", ban.getString("uuid")))
                        .projection(new Document("pid", 1)).first();
                Integer pid = player == null ? null : PlayerPids.orNull(player.getInteger("pid"));
                if (pid != null) {
                    bans.updateOne(new Document("_id", ban.get("_id")).append("pid", null),
                            new Document("$set", new Document("pid", pid)));
                }
            }
        }
    }
}
