package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;
import jakarta.inject.Singleton;
import org.bson.Document;

@Singleton
public class V2__AddAdminSourceField implements Migration {
    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public String getDescription() {
        return "Initialize admin_source without auto-promoting legacy admins to Discord access";
    }

    @Override
    public void up(MongoDatabase database) {
        var players = database.getCollection("players");

        players.updateMany(
                new Document("admin_source", new Document("$exists", false))
                        .append("is_admin", true),
                new Document("$set", new Document("admin_source", "NONE"))
        );

        players.updateMany(
                new Document("admin_source", new Document("$exists", false))
                        .append("is_admin", false),
                new Document("$set", new Document("admin_source", "NONE"))
        );
    }
}
