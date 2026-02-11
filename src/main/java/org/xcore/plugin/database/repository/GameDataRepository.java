package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.model.GameData;

@Singleton
public class GameDataRepository extends DataRepository<GameData> {

    @Inject
    public GameDataRepository(MongoDatabase database) {
        super(database, "games", GameData.class);

        collection.createIndex(new Document("map", 1));
        collection.createIndex(new Document("event", 1));
//        collection.createIndex(new Document("server_name", 1));
//        collection.createIndex(new Document("created_at", -1));
    }
}